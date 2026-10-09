package com.selectiverendering.client.mixin;

import com.selectiverendering.client.SelectiveRenderingManager;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {
    @Inject(method = "extract", at = @At("RETURN"))
    private void selectiveRendering$nightVision(
            LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        if (!SelectiveRenderingManager.isNightVisionActive()) {
            return;
        }

        renderState.nightVisionEffectIntensity = 1.0F;
        renderState.blockFactor = 1.0F;
        renderState.skyFactor = 1.0F;
        renderState.needsUpdate = true;
    }
}
