package com.selectiverendering.mixin.hiding.blockentity;

import com.selectiverendering.blockentity.HiddenRenderTypes;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=26.2 {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/** 在帧边界清除 RenderType 的透明度标记。 */
@Mixin(FeatureRenderDispatcher.class)
public class FeatureRenderDispatcherMixin {
	//? if >=26.2 {
	@Inject(method = "prepareFrame", at = @At("TAIL"))
	private void selectiveRendering$clearMarkedTypes(CallbackInfoReturnable<FeatureRenderDispatcher.PreparedFrame> cir) {
	//?} else {
	/*@Inject(method = "endFrame", at = @At("HEAD"))
	private void selectiveRendering$clearMarkedTypes(CallbackInfo ci) {
	*///?}
		HiddenRenderTypes.clear();
	}
}
