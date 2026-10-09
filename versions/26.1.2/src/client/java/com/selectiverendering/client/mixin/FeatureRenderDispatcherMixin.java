package com.selectiverendering.client.mixin;

import com.selectiverendering.client.HiddenRenderTypes;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FeatureRenderDispatcher.class)
public class FeatureRenderDispatcherMixin {
    @Inject(method = "endFrame", at = @At("HEAD"))
    private void selectiveRendering$clearMarkedTypes(CallbackInfo ci) {
        HiddenRenderTypes.clear();
    }
}
