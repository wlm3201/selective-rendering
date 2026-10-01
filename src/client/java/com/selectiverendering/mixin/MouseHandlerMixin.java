package com.selectiverendering.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.selectiverendering.BlockListMessage;
import com.selectiverendering.ModHotkeys;
import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.compat.Platform;
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
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	private static final int ACTION_PRESS = 1;
	private static final float STEEP_PITCH = 60.0F;

	/**
	 * 魔杖的投射距离。和 litematica 一样写死 200，不看玩家的手长（{@code blockInteractionRange}）——
	 * 选点本来就不是"够不够得着"的问题。
	 */
	private static final double MAX_TRACE_DISTANCE = 200.0;

	private static final float PARTIAL_TICK = 1.0F;

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
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
			SelectiveRenderingManager.selectCorner(rayStart(camera), rayEnd(camera));
			ci.cancel();
			return;
		}

		// 增删选区不要求点到方块：拿"眼睛 → 视线尽头"这条线段去截已存选区的盒子，
		// 删掉最近的那个（对标 litematica 的元素选择）。一个都没碰到时才回落到"加当前选区"
		if (ModHotkeys.regionHeld()) {
			if (buttonInfo.button() == Platform.MOUSE_LEFT) {
				SelectiveRenderingManager.toggleRegionAlongRay(rayStart(camera), rayEnd(camera));
				ci.cancel();
			}
			else if (buttonInfo.button() == Platform.MOUSE_RIGHT) {
				SelectiveRenderingManager.clipRegions();
				ci.cancel();
			}

			return;
		}

		// 剩下的操作都要落到具体方块上，所以这里才要求命中
		HitResult trace = camera.pick(MAX_TRACE_DISTANCE, PARTIAL_TICK, false);

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

	private static Vec3 rayStart(Entity camera) {
		return camera.getEyePosition(PARTIAL_TICK);
	}

	/** 视线的尽头。选区的盒子是拿"起点→终点"这条线段去截的，所以中间隔着 air / transparent 方块也能选中。 */
	private static Vec3 rayEnd(Entity camera) {
		return rayStart(camera).add(camera.getViewVector(PARTIAL_TICK).scale(MAX_TRACE_DISTANCE));
	}

	private static Direction lookingDirection(Minecraft minecraft) {
		Entity entity = minecraft.getCameraEntity();
		if (entity == null) {
			return Direction.NORTH;
		}

		float pitch = entity.getXRot();
		if (pitch > STEEP_PITCH) {
			return Direction.DOWN;
		}

		if (-pitch > STEEP_PITCH) {
			return Direction.UP;
		}

		return Direction.fromYRot(entity.getYRot());
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
