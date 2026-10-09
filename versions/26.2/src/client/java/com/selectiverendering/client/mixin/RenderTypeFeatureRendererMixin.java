package com.selectiverendering.client.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selectiverendering.client.BufferSourceHooks;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTypeFeatureRenderer.class)
public class RenderTypeFeatureRendererMixin {
    @Inject(method = "getVertexBuilder", at = @At("RETURN"), cancellable = true)
    private void selectiveRendering$wrap(
            RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
        cir.setReturnValue(BufferSourceHooks.wrap(renderType, cir.getReturnValue()));
    }
}
