package com.selectiverendering.client.mixin.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selectiverendering.client.BufferSourceHooks;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.raphimc.immediatelyfast.feature.core.BatchableBufferSource", remap = false)
public class ImmediatelyFastBufferSourceMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"), cancellable = true, remap = false)
    private void selectiveRendering$wrap(
            RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
        cir.setReturnValue(BufferSourceHooks.wrap(renderType, cir.getReturnValue()));
    }
}
