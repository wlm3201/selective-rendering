package com.selectiverendering.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 透明度 100% 时直接不让"移动方块"进入提交队列（alpha == 0 就 cancel）。
 * 与 {@code BlockFeatureRendererMixin} 是一对：那边管半透明，这边管完全不画。
 */
@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {
	@Inject(method = "submitMovingBlock", at = @At("HEAD"), cancellable = true, require = 1)
	private void selectiveRendering$submitMovingBlock(PoseStack poseStack, MovingBlockRenderState renderState, CallbackInfo ci) {
		if (SelectiveRenderingManager.getAlpha(renderState.blockState, renderState.blockPos, true) == 0) {
			ci.cancel();
		}
	}
}
