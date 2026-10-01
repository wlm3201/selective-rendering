package com.selectiverendering.mixin;

import com.selectiverendering.Region;
import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 选区线框的绘制：手持魔杖时把已保存的选区（青色）和当前双角点选区（白色）
 * 以及两个角点本身（红 / 蓝，被选中的角点带填充）画出来。
 *
 * <p>用的是 26.1+ 的 {@code Gizmos} 接口：只要在收集 gizmo 的回调里
 * 往全局 {@code Gizmos} 塞图元即可，不需要自己拿 {@code PoseStack} /
 * {@code MultiBufferSource} 画，也不用管渲染状态。
 *
 * <h2>两个注入点并存的原因</h2>
 * <p>这个方法改名过一次：
 * <pre>
 *   26.1.2 → collectPerFrameGizmos
 *   26.2+  → collectPerFrameRenderThreadGizmos
 * </pre>
 * 两者参数签名完全一样（返回 {@code Gizmos.TemporaryCollection}），
 * 所以这里写两个 {@code require = 0} 的入口，各自委托给同一个 {@code emit()}，
 * 哪个版本有哪个就生效哪个，省掉三份复制品。
 *
 * <p>⚠ 如果将来某个版本两个方法名<b>同时</b>存在，线框会被画两遍
 * （视觉上是描边变粗，不影响功能）。真遇到了就删掉其中一个注入点。
 *
 * <p>配色是硬编码的常量，见下面几个 {@code 0xFF......}。
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	private static final int SELECTION_STROKE = 0xFFFFFFFF;
	private static final int CORNER_1_STROKE = 0xFFFF3333;
	private static final int CORNER_2_STROKE = 0xFF3355FF;
	private static final int CORNER_1_FILL = 0x33FF3333;
	private static final int CORNER_2_FILL = 0x333355FF;
	private static final int ADDED_STROKE = 0xFF00E5FF;
	private static final int ADDED_FILL = 0x1800E5FF;
	private static final float STROKE_WIDTH = 2.0F;

	/**
	 * 上一帧的 gizmo 是否已经被 {@code finalizeGizmoCollection} 画出来并清空。
	 *
	 * <h2>为什么需要这个守卫</h2>
	 * <p>{@code Gizmos} 这个 API 的"收集"和"落地"是<b>一对隐式的帧内契约</b>，分属两个调用点：
	 * <pre>
	 *   收集：Minecraft.renderFrame → LevelRenderer.collectPerFrameRenderThreadGizmos（本类注入了这里）
	 *   落地：Minecraft.renderFrame → GameRenderer.render → LevelRenderer.render
	 *         → submitFeatures → finalizeGizmoCollection（drain 并清空）
	 * </pre>
	 * 中间任何一步抛异常，{@code finalizeGizmoCollection} 就不会执行，
	 * 我们上一帧加进去的 gizmo 会<b>留在 collector 里</b>；下一帧再往里加，
	 * 就变成了"同一批线框在同一个位置叠了两份"。而填充色的 alpha 只有 0x18（≈9%），
	 * 叠十几次就接近不透明——表现就是"线框整体变成实心色块"。
	 *
	 * <p>现实中最容易踩到的是 26.3 的这条：
	 * {@code LevelRenderer.render} 开头会调
	 * {@code submitNodeStorage.setUseImprovedTransparency(...)}，
	 * 而它在 storage 非空时会抛 {@code IllegalStateException}（原版注释自己写着
	 * "Improved transparency is likely toggled in a wrong place"）。
	 * 装了"失去焦点时降画质"的 mod（例如 Dynamic-FPS）时，切换画质就会踩中，
	 * 于是渲染中断、drain 被跳过。
	 *
	 * <p>下游 mod 在错误的时机改选项，我们管不了；但我们可以保证<b>自己不再往已经脏了的
	 * collector 里继续加东西</b>：只要发现上一帧没 drain 成功，这一帧就干脆不画。
	 * 等渲染恢复正常（drain 成功）后自动恢复。
	 */
	@Unique
	private static boolean selectiveRendering$gizmosDrained = true;

	@Inject(method = "collectPerFrameRenderThreadGizmos", at = @At("TAIL"), require = 0)
	private void selectiveRendering$emitRegionGizmo(CallbackInfoReturnable<Gizmos.TemporaryCollection> cir) {
		emit();
	}

	@Inject(method = "collectPerFrameGizmos", at = @At("TAIL"), require = 0)
	private void selectiveRendering$emitRegionGizmoLegacy(CallbackInfoReturnable<Gizmos.TemporaryCollection> cir) {
		emit();
	}

	/** 记录"这一帧的 gizmo 已经被真正画出来并清空了"，解除上面的守卫。 */
	@Inject(method = "finalizeGizmoCollection", at = @At("TAIL"), require = 0)
	private void selectiveRendering$markGizmosDrained(CallbackInfo ci) {
		selectiveRendering$gizmosDrained = true;
	}

	private static void emit() {
		if (!selectiveRendering$gizmosDrained) {
			// 上一帧画的 gizmo 没被清掉，这一帧再往里加就会叠加。见字段注释。
			return;
		}

		if (!SelectiveRendering.isWandHeld()) {
			return;
		}

		for (Region added : SelectiveRenderingManager.regions()) {
			Gizmos.cuboid(added.box(), GizmoStyle.strokeAndFill(ADDED_STROKE, STROKE_WIDTH, ADDED_FILL));
		}

		BlockPos first = SelectiveRenderingManager.getCorner(0);
		BlockPos second = SelectiveRenderingManager.getCorner(1);

		if (first != null && second != null) {
			AABB box = SelectiveRenderingManager.regionBox();
			if (box != null) {
				Gizmos.cuboid(box, GizmoStyle.stroke(SELECTION_STROKE, STROKE_WIDTH));
			}
		}

		corner(0, first);
		corner(1, second);

		// 这一帧已经往 collector 里放了东西，等 finalizeGizmoCollection 来清
		selectiveRendering$gizmosDrained = false;
	}

	private static void corner(int index, BlockPos pos) {
		if (pos == null) {
			return;
		}

		boolean first = index == 0;
		int stroke = first ? CORNER_1_STROKE : CORNER_2_STROKE;

		if (SelectiveRenderingManager.isCornerSelected(index)) {
			int fill = first ? CORNER_1_FILL : CORNER_2_FILL;
			Gizmos.cuboid(pos, GizmoStyle.strokeAndFill(stroke, STROKE_WIDTH, fill));
			return;
		}

		Gizmos.cuboid(pos, GizmoStyle.stroke(stroke, STROKE_WIDTH));
	}
}
