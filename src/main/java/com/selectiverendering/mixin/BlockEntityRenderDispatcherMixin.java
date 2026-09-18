package com.selectiverendering.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.SelectiveSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hides what a block entity draws on top of its block, like a chest lid or the text on a sign,
 * when that block is hidden.
 *
 * <p>The block itself is part of the chunk mesh and is hidden by {@code ModelBlockRendererMixin},
 * or by {@code BlockRendererMixin} where Sodium is installed, but these additions go through a
 * renderer of their own that the mesh hooks never see.</p>
 *
 * <p>At full transparency the renderer is simply not called. Partly see-through is the interesting
 * half: the renderer is called with a wrapped collector, and the wrapper exchanges every render
 * type it submits for a translucent stand-in and marks it, so that when the frame is drawn the
 * buffer source writes the transparency into the vertices of exactly those types. See
 * {@link SelectiveSubmitNodeCollector}.</p>
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
	@Redirect(
		method = "submit",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
		)
	)
	private <S extends BlockEntityRenderState> void selectiveRendering$submit(
			BlockEntityRenderer<?, S> renderer, S renderState, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraRenderState) {
		int alpha = SelectiveRenderingManager.getAlphaAt(renderState.blockPos);
		if (alpha == 0) {
			return;
		}

		if (alpha > 0) {
			collector = new SelectiveSubmitNodeCollector(collector);
		}

		renderer.submit(renderState, poseStack, collector, cameraRenderState);
	}
}
