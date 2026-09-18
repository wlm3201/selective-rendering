package com.selectiverendering.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selectiverendering.BufferSourceHooks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wraps the buffers the vanilla buffer source hands out, so that what is written into them comes
 * out see-through.
 *
 * <p>Two things need it. Block entities are one: their geometry is drawn by renderers of their
 * own, long after the frame was collected, and those renderers ask a buffer source for a buffer
 * themselves - there is no quad left at that point for anyone to write into. Moving blocks are
 * the other: whichever of the two ways one is drawn - the vanilla one, or the one Fabric Renderer
 * API redirects it onto - it ends up asking here, which makes this the one place their alpha can
 * be written for either. What is done to the consumer is {@link BufferSourceHooks}' business.</p>
 *
 * <p>This is the vanilla buffer source, the one every renderer is handed unless something swapped
 * it out. A class that overrides {@code getBuffer} - ImmediatelyFast's batching one does exactly
 * that - never runs an injection placed on the vanilla class, because the override is what gets
 * called, so such a source needs the same hook where it answers; that is what
 * {@code ImmediatelyFastBufferSourceMixin} is.</p>
 */
@Mixin(MultiBufferSource.BufferSource.class)
public class BufferSourceMixin {
	@Inject(method = "getBuffer", at = @At("RETURN"), cancellable = true)
	private void selectiveRendering$wrap(RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
		cir.setReturnValue(BufferSourceHooks.wrap(renderType, cir.getReturnValue()));
	}
}
