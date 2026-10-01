package com.selectiverendering.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 移动方块（moving_piston）的提交阶段处理，两点：
 *
 * <h2>1. alpha == 0 时直接不提交</h2>
 * <p>和 {@code BlockFeatureRendererMixin} 是一对：那边管"半透明"，
 * 这边管"完全不画"。完全隐藏必须在<b>提交阶段</b>就拦掉，
 * 否则后面换 RenderType 也救不回来。
 *
 * <h2>2. 该淡化的移动方块必须进"半透明相位"（这是 26.2 的关键修复）</h2>
 * <p>原版用 {@code model.hasMaterialFlag(FLAG_TRANSLUCENT)} 决定这个移动方块
 * 进 {@code translucentBlocksAndItems}（按距离排序的半透明相位）
 * 还是 {@code solid}（先画、不排序的不透明相位）。
 *
 * <p>问题在于：我们让一个<b>本来不透明</b>的方块（比如石头）变淡时，
 * 它的模型并没有半透明材质标志，于是被扔进了 solid 相位——
 * 但 {@code putBakedQuad} 那边又把它的 RenderType 换成了 {@code translucentMovingBlock()}。
 * 结果是：它的几何在 solid 相位里被先画出来（此时它身后的东西还没画），
 * depth 已经写上，等轮到身后的方块时就全部被深度测试挡掉了。
 * 表现为"从某些朝向看，淡化中的移动方块把身后<b>没</b>淡化的移动方块整个挡住"，
 * 而且是否复现取决于这两个方块的提交顺序，所以是"有的朝向才看得见"。
 *
 * <p>修法与 26.1.2 的 {@code BlockFeatureRendererMixin#selectiveRendering$translucentPass} 一致：
 * 只要这个移动方块正被淡化，就谎报"我有半透明材质"，让它进半透明相位参与排序。
 * （26.2 的 {@code MovingBlockFeatureRenderer.Submit} 还没有 {@code forceTranslucent} 字段，
 * 每个 quad 用哪一层仍由 {@code quad.materialInfo().layer()} 决定；
 * 不过 {@code putBakedQuad} 的补丁已经会把 RenderType 换成半透明版，所以进对相位就够。）
 */
@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {
	@Inject(method = "submitMovingBlock", at = @At("HEAD"), cancellable = true, require = 1)
	private void selectiveRendering$submitMovingBlock(PoseStack poseStack, MovingBlockRenderState renderState, int outlineColor, CallbackInfo ci) {
		if (SelectiveRenderingManager.getAlpha(renderState.blockState, renderState.blockPos, true) == 0) {
			ci.cancel();
		}
	}

	@WrapOperation(
		method = "submitMovingBlock",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;hasMaterialFlag(I)Z"
		),
		require = 1
	)
	private boolean selectiveRendering$forceTranslucentPhase(
		BlockStateModel model,
		int flag,
		Operation<Boolean> original,
		@Local MovingBlockRenderState renderState
	) {
		boolean hasTranslucentMaterial = original.call(model, flag);
		if (hasTranslucentMaterial || flag != BakedQuad.FLAG_TRANSLUCENT) {
			return hasTranslucentMaterial;
		}

		int alpha = SelectiveRenderingManager.getAlpha(renderState.blockState, renderState.blockPos, true);
		return alpha > 0 && alpha < 255;
	}
}
