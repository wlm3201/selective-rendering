package com.selectiverendering.mixin;

import com.selectiverendering.HiddenRenderTypes;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 每帧结束时清掉"本帧被标记要变透明的 RenderType"。
 *
 * <p>见 {@link HiddenRenderTypes}：标记只在<b>一帧之内</b>有效，
 * 不然上一帧的标记会一直给后续所有帧的方块实体套 alpha。
 * {@code prepareFrame} 是每帧都会走一次的入口，选它当清理点。
 */
@Mixin(FeatureRenderDispatcher.class)
public class FeatureRenderDispatcherMixin {
	@Inject(method = "prepareFrame", at = @At("TAIL"))
	private void selectiveRendering$clearMarkedTypes(CallbackInfoReturnable<FeatureRenderDispatcher.PreparedFrame> cir) {
		HiddenRenderTypes.clear();
	}
}
