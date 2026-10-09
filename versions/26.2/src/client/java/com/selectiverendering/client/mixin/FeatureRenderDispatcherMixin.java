package com.selectiverendering.client.mixin;

import com.selectiverendering.client.HiddenRenderTypes;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FeatureRenderDispatcher.class)
public class FeatureRenderDispatcherMixin {
    @Inject(method = "prepareFrame", at = @At("TAIL"))
    private void selectiveRendering$clearMarkedTypes(
            CallbackInfoReturnable<FeatureRenderDispatcher.PreparedFrame> cir) {
        HiddenRenderTypes.clear();
    }
}
