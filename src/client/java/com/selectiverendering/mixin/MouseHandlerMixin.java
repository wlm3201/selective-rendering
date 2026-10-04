package com.selectiverendering.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.selectiverendering.BlockListMessage;
import com.selectiverendering.CameraRay;
import com.selectiverendering.ModHotkeys;
import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.compat.Platform;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 魔杖的全部鼠标交互。
 *
 * <p>完整操作表（都必须<b>手持魔杖</b>且当前没有打开 GUI）：
 * <pre>
 *   左键                 设置角点 1
 *   右键                 设置角点 2
 *   中键                 选中视线穿过的那个角点（点空处 = 取消选中）
 *   选区键 + 左键        删除视线穿过的<b>最近那个</b>选区；没碰到任何选区则把当前选区加进去
 *   选区键 + 右键        用当前选区裁剪已有选区
 *   方块键 + 左键        增删"方块 id"规则
 *   方块键 + 右键        增删"带完整状态"的规则
 *   左Ctrl + 滚轮        循环切换模式
 *   左Alt  + 滚轮        沿视线方向移动选中的角点
 * </pre>
 *
 * <p>射线是自己打的、不是复用的 {@code Minecraft.hitResult}：那条是准星射线，
 * 距离只有玩家手长，远处根本点不到。这里一律用 {@code MAX_TRACE_DISTANCE} 格，
 * 和 litematica 的 {@code maxDistance = 200} 一致。
 *
 * <p>射线的<b>起点和方向都取自渲染相机</b>（{@link CameraRay}），不是玩家实体：
 * 这样开环绕 / 出窍相机后，点到的就是画面上看到的方块（鼠标没被锁时还会跟着指针走），
 * 而不需要和相机 mod 有任何约定。
 *
 * <p>但"必须指到方块"这件事和 litematica 保持一致：射线 MISS 就直接什么都不做
 * （对标 {@code RayTraceUtils.getTargetedPosition} 返回 null），
 * 不会退回到"空气里的某个坐标"，手感才和它一样。
 * 唯一的例外是上面的"选区键 + 左键"：它删的就是选区盒子本身，
 * 所以用 litematica {@code traceToSelectionBoxBody} 的办法——
 * 拿视线去截盒子，不需要中间有方块。
 *
 * <p>所有分支命中后都会 {@code ci.cancel()}——也就是说拿着魔杖时
 * <b>无法破坏/放置方块</b>，鼠标事件被完全吃掉。这是刻意的（否则选点会误挖方块）。
 *
 * <p>鼠标按键码通过 {@code Platform.MOUSE_LEFT / MOUSE_RIGHT} 取，
 * 因为 26.3 起的 {@code MouseButtonInfo} 编号和旧版不一样（旧版 0/1，26.3 是 1/3）。
 *
 * <h2>为什么本类带 {@code priority = 2000}</h2>
 * <p>OrbitCam 之类的相机 mod 也在 {@code MouseHandler#onButton / onScroll} 的 HEAD
 * 上注入并 {@code ci.cancel()}（它用左键旋转、Alt+左键平移、滚轮缩放）。
 * 两边互不认识，谁生效取决于 Mixin 的回调顺序。
 *
 * <p>"手持魔杖"是一个远比"操作相机"特殊的状态，所以我们要<b>确定性地先跑</b>。
 *
 * <h3>两条实测结论（探针实验，见 {@code docs/known-issues.md} 第 13 条）</h3>
 * <ol>
 *   <li><b>{@code priority} 数值小的先跑。</b>本类 2000、OrbitCam 的
 *       {@code MouseHandlerMixin} 3000，日志里本类的回调确实先执行。</li>
 *   <li><b>Mixin 在某个回调 cancel 之后就此停止，不再调用剩余回调。</b>
 *       所以先跑者 cancel 后，相机 mod 的 handler <b>根本不会被调用</b>。</li>
 * </ol>
 *
 * <p>因此下面那个 {@code ci.isCancelled()} 判断在"只有我们和 OrbitCam"时是死代码，
 * 但保留它有价值：若有第三方 mod 的 priority <b>比 2000 更小</b>（即比我们更先），
 * 它一旦 cancel，我们会正确礼让而不是硬抢。
 */
@Mixin(value = MouseHandler.class, priority = 2000)
public class MouseHandlerMixin {
	private static final int ACTION_PRESS = 1;
	private static final float STEEP_PITCH = 60.0F;

	/**
	 * 魔杖的投射距离。和 litematica 一样写死 200，不看玩家的手长（{@code blockInteractionRange}）——
	 * 选点本来就不是"够不够得着"的问题。
	 */
	private static final double MAX_TRACE_DISTANCE = 200.0;

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (ci.isCancelled()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (Platform.screen(minecraft) != null || !SelectiveRendering.isWandHeld()) {
			return;
		}

		if (yOffset == 0) {
			return;
		}

		int direction = yOffset > 0 ? 1 : -1;
		Window handle = minecraft.getWindow();

		if (Platform.isKeyDown(handle, InputConstants.KEY_LCONTROL)) {
			SelectiveRenderingManager.cycleMode(direction);
			ci.cancel();
			return;
		}

		if (Platform.isKeyDown(handle, InputConstants.KEY_LALT)) {
			SelectiveRenderingManager.moveSelectedCorner(lookingDirection(minecraft), direction);
			ci.cancel();
		}
	}

	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
		if (ci.isCancelled()) {
			return;
		}

		if (action != ACTION_PRESS) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (Platform.screen(minecraft) != null || minecraft.level == null || !SelectiveRendering.isWandHeld()) {
			return;
		}

		Entity camera = minecraft.getCameraEntity();
		if (camera == null) {
			return;
		}

		// 中键 = litematica 的 "选择元素"：选中视线穿过的那个角点，之后左Alt+滚轮就移动它。
		// 它不依赖任何方块，也不吃修饰键，所以放在最前面
		if (buttonInfo.button() == Platform.MOUSE_MIDDLE) {
			SelectiveRenderingManager.selectCorner(rayStart(minecraft), rayEnd(minecraft));
			ci.cancel();
			return;
		}

		// 增删选区不要求点到方块：拿"眼睛 → 视线尽头"这条线段去截已存选区的盒子，
		// 删掉最近的那个（对标 litematica 的元素选择）。一个都没碰到时才回落到"加当前选区"
		if (ModHotkeys.regionHeld()) {
			if (buttonInfo.button() == Platform.MOUSE_LEFT) {
				SelectiveRenderingManager.toggleRegionAlongRay(rayStart(minecraft), rayEnd(minecraft));
				ci.cancel();
			}
			else if (buttonInfo.button() == Platform.MOUSE_RIGHT) {
				SelectiveRenderingManager.clipRegions();
				ci.cancel();
			}

			return;
		}

		// 剩下的操作都要落到具体方块上，所以这里才要求命中
		HitResult trace = CameraRay.pick(minecraft, camera, MAX_TRACE_DISTANCE);

		if (!(trace instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
			return;
		}

		if (ModHotkeys.blocksHeld()) {
			if (buttonInfo.button() == Platform.MOUSE_LEFT) {
				toggleBlockId(hit, minecraft);
				ci.cancel();
			}
			else if (buttonInfo.button() == Platform.MOUSE_RIGHT) {
				toggleBlockStates(hit, minecraft);
				ci.cancel();
			}

			return;
		}

		if (buttonInfo.button() == Platform.MOUSE_LEFT) {
			SelectiveRenderingManager.setCorner(0, hit.getBlockPos());
			ci.cancel();
		}
		else if (buttonInfo.button() == Platform.MOUSE_RIGHT) {
			SelectiveRenderingManager.setCorner(1, hit.getBlockPos());
			ci.cancel();
		}
	}

	/**
	 * 射线的起点与尽头都交给 {@link CameraRay}：从<b>渲染相机</b>出发、穿过鼠标指针。
	 *
	 * <p>原来这里用的是玩家实体的眼睛与视线，开环绕相机后和画面不一致
	 * （点到的是玩家朝向的方块，不是画面上那个）。详见 {@link CameraRay}。
	 *
	 * <p>选区的盒子是拿"起点→终点"这条线段去截的，
	 * 所以中间隔着 air / transparent 方块也能选中。
	 */
	private static Vec3 rayStart(Minecraft minecraft) {
		return CameraRay.start(minecraft);
	}

	private static Vec3 rayEnd(Minecraft minecraft) {
		return CameraRay.end(minecraft, MAX_TRACE_DISTANCE);
	}

	/**
	 * "视线方向"同样取自<b>渲染相机</b>，与 {@link CameraRay} 保持同一个来源。
	 *
	 * <p>原来读的是玩家实体的 {@code getXRot / getYRot}。OrbitCam 会把玩家朝向同步成
	 * 相机姿态，所以那边看起来是对的；但过渡插值期间会落后一帧，
	 * 而且不同步身体朝向的相机 mod 就会错。统一读相机更稳。
	 */
	private static Direction lookingDirection(Minecraft minecraft) {
		Camera camera = Platform.camera(minecraft);

		float pitch = camera.xRot();
		if (pitch > STEEP_PITCH) {
			return Direction.DOWN;
		}

		if (-pitch > STEEP_PITCH) {
			return Direction.UP;
		}

		return Direction.fromYRot(camera.yRot());
	}

	private static void toggleBlockId(BlockHitResult hit, Minecraft minecraft) {
		toggleRuleAt(hit, minecraft, false);
	}

	private static void toggleBlockStates(BlockHitResult hit, Minecraft minecraft) {
		toggleRuleAt(hit, minecraft, true);
	}

	/**
	 * 增删一条方块规则。
	 *
	 * @param withStates false → 只写 {@code minecraft:chest}；
	 *                   true  → 把该方块<b>当前所有状态</b>都写进去，
	 *                           例如 {@code minecraft:chest[waterlogged=false,facing=north]}。
	 *                           注意这样生成的规则非常具体，之后只有<b>完全相同</b>的状态组合才能再次命中删除。
	 */
	private static void toggleRuleAt(BlockHitResult hit, Minecraft minecraft, boolean withStates) {
		BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
		if (state.isAir()) {
			return;
		}

		Block block = state.getBlock();
		String source = BuiltInRegistries.BLOCK.getKey(block).toString();

		if (withStates && !block.getStateDefinition().getProperties().isEmpty()) {
			StringBuilder builder = new StringBuilder(source).append('[');
			boolean first = true;

			for (Property<?> property : block.getStateDefinition().getProperties()) {
				if (!first) {
					builder.append(',');
				}

				first = false;
				builder.append(property.getName()).append('=').append(value(state, property));
			}

			source = builder.append(']').toString();
		}

		boolean listed = SelectiveRenderingManager.hasRule(source);
		if (listed) {
			SelectiveRenderingManager.removeRule(source);
		}
		else {
			SelectiveRenderingManager.addRule(source);
		}

		BlockListMessage.show(source, !listed);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static String value(BlockState state, Property<?> property) {
		return String.valueOf(state.getValue((Property) property));
	}
}
