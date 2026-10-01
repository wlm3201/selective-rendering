package com.selectiverendering.mixin;

import com.selectiverendering.HiddenRenderTypes;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 每帧结束时清掉"本帧被标记要变透明的 RenderType"（{@link HiddenRenderTypes#clear()}）。
 * 标记只在<b>一帧之内</b>有效，否则会一直给后续帧的方块实体套 alpha。
 */
@Mixin(FeatureRenderDispatcher.class)
public class FeatureRenderDispatcherMixin {
	@Inject(method = "prepareFrame", at = @At("TAIL"))
	private void selectiveRendering$clearMarkedTypes(CallbackInfoReturnable<FeatureRenderDispatcher.PreparedFrame> cir) {
		HiddenRenderTypes.clear();
	}
}
