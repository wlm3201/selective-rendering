package com.selectiverendering.mixin.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selectiverendering.BufferSourceHooks;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the buffer source ImmediatelyFast substitutes for the vanilla one.
 *
 * <p>Its {@code BatchableBufferSource} extends the vanilla buffer source and overrides
 * {@code getBuffer}, so the injection placed on the vanilla class never runs for it: the override
 * is what gets called, and an injection into the method it overrides has nothing to say about it.
 * This puts the same hook on the class that actually answers.</p>
 *
 * <p>What it is here for is block entities, whose renderers ask for buffers of their own long
 * after the quads are gone, and moving blocks, whose alpha is written here as well - see
 * {@link BufferSourceHooks}, which decides what happens to the consumer either way. Static blocks
 * still do not need it: their transparency is written into the quads before any buffer is
 * involved, see {@code ModelBlockRendererMixin}, or {@code BlockRendererMixin} under Sodium.</p>
 *
 * <p>Targeted by name, and never imported: ImmediatelyFast is optional, and a mod that is not
 * there must not be needed to build. The method is matched by name too, being the override of a
 * vanilla one and so already named as the vanilla one is. What happens to the consumer is not
 * this class's business, see {@link BufferSourceHooks}.</p>
 */
@Mixin(targets = "net.raphimc.immediatelyfast.feature.core.BatchableBufferSource", remap = false)
public class ImmediatelyFastBufferSourceMixin {
	@Inject(method = "getBuffer", at = @At("RETURN"), cancellable = true, remap = false)
	private void selectiveRendering$wrap(RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
		cir.setReturnValue(BufferSourceHooks.wrap(renderType, cir.getReturnValue()));
	}
}
