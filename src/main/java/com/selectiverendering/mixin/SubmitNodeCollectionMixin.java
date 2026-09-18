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
 * Drops a moving block - a piston carrying one, or one falling - outright when the block it
 * carries is fully hidden.
 *
 * <p>The render state of a moving block already holds the state the block really is, and its
 * position is the cell the block is on its way out of - the pair {@link
 * SelectiveRenderingManager#getAlpha} wants, and the same pair {@code BlockFeatureRendererMixin}
 * decides with. Reading the world instead would answer one cell along: for a block a piston
 * carries the level is already updated, so the destination holds the moving piston and the cell
 * the block left is empty. Partly see-through ones pass through here untouched and are dealt with
 * where their quads are written, in {@code BlockFeatureRendererMixin}.</p>
 */
@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {
	@Inject(method = "submitMovingBlock", at = @At("HEAD"), cancellable = true, require = 1)
	private void selectiveRendering$submitMovingBlock(PoseStack poseStack, MovingBlockRenderState renderState, CallbackInfo ci) {
		if (SelectiveRenderingManager.getAlpha(renderState.blockState, renderState.blockPos) == 0) {
			ci.cancel();
		}
	}
}
