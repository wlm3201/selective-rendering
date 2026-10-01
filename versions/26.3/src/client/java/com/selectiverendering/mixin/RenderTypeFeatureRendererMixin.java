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
 * 26.2+ 版本里拿 {@code VertexConsumer} 的统一入口。
 *
 * <p>旧版（26.1.2）是在 {@code MultiBufferSource.BufferSource.getBuffer}
 * 和 ImmediatelyFast 的 {@code BatchableBufferSource.getBuffer} 上打补丁；
 * 新版改成 {@code RenderTypeFeatureRenderer.getVertexBuilder}，
 * 一个点就能覆盖所有 feature renderer。
 *
 * <p>实际判断逻辑都在 {@link BufferSourceHooks#wrap}。
 */
@Mixin(RenderTypeFeatureRenderer.class)
public class RenderTypeFeatureRendererMixin {
	@Inject(method = "getVertexBuilder", at = @At("RETURN"), cancellable = true)
	private void selectiveRendering$wrap(RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
		cir.setReturnValue(BufferSourceHooks.wrap(renderType, cir.getReturnValue()));
	}
}
