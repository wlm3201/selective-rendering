package com.selectiverendering.mixin;

import com.selectiverendering.HiddenRenderTypes;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forgets the marked render types once the frame has been drawn, so a type that belonged to a
 * hidden block entity this frame does not keep having transparency written into it on the next.
 */
@Mixin(FeatureRenderDispatcher.class)
public class FeatureRenderDispatcherMixin {
	@Inject(method = "endFrame", at = @At("HEAD"))
	private void selectiveRendering$clearMarkedTypes(CallbackInfo ci) {
		HiddenRenderTypes.clear();
	}
}
