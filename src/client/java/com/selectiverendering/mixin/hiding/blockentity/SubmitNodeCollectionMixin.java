package com.selectiverendering.mixin.hiding.blockentity;

import com.selectiverendering.render.hiding.AlphaGetter;
//? if >=26.2 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
//?}
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
//? if >=26.2 {
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 跳过完全隐藏的移动方块，并将淡化方块送入半透明排序阶段。 */
@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {
	@Inject(method = "submitMovingBlock", at = @At("HEAD"), cancellable = true, require = 1)
	//? if >=26.2 {
	private void selectiveRendering$submitMovingBlock(PoseStack poseStack, MovingBlockRenderState renderState, int outlineColor, CallbackInfo ci) {
		//?} else {
		/*private void selectiveRendering$submitMovingBlock(PoseStack poseStack, MovingBlockRenderState renderState, CallbackInfo ci) {
		*///?}
		if (AlphaGetter.getAlpha(renderState.blockState, renderState.blockPos, true) == 0) {
			ci.cancel();
		}
	}
	//? if >=26.2 {

	@WrapOperation(method = "submitMovingBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;hasMaterialFlag(I)Z"), require = 1)
	private boolean selectiveRendering$forceTranslucentPhase(
		BlockStateModel model,
		int flag,
		Operation<Boolean> original,
		@Local MovingBlockRenderState renderState) {
		boolean hasTranslucentMaterial = original.call(model, flag);
		if (hasTranslucentMaterial || flag != BakedQuad.FLAG_TRANSLUCENT) {
			return hasTranslucentMaterial;
		}

		int alpha = AlphaGetter.getAlpha(renderState.blockState, renderState.blockPos, true);
		return alpha > 0 && alpha < 255;
	}
	//?}
}
