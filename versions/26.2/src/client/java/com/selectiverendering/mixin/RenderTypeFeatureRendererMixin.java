package com.selectiverendering.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selectiverendering.BufferSourceHooks;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2+ 拿 {@code VertexConsumer} 的统一入口（一个点覆盖所有 feature renderer）。
 * 实际判断逻辑都在 {@link BufferSourceHooks#wrap}。
 */
@Mixin(RenderTypeFeatureRenderer.class)
public class RenderTypeFeatureRendererMixin {
	@Inject(method = "getVertexBuilder", at = @At("RETURN"), cancellable = true)
	private void selectiveRendering$wrap(RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
		cir.setReturnValue(BufferSourceHooks.wrap(renderType, cir.getReturnValue()));
	}
}
