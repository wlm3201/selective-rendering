package com.selectiverendering.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selectiverendering.BufferSourceHooks;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 旧版（26.1.2）拿 {@code VertexConsumer} 的入口：{@code BufferSource.getBuffer}。
 *
 * <p>26.2+ 换成了 {@code RenderTypeFeatureRenderer.getVertexBuilder}
 * （见 {@code RenderTypeFeatureRendererMixin}），这里只在新架构之前有效。
 *
 * <p>如果玩家装了 ImmediatelyFast，它有自己的 {@code BatchableBufferSource}，
 * 所以要再补一个 {@code ImmediatelyFastBufferSourceMixin}。
 */
@Mixin(MultiBufferSource.BufferSource.class)
public class BufferSourceMixin {
	@Inject(method = "getBuffer", at = @At("RETURN"), cancellable = true)
	private void selectiveRendering$wrap(RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
		cir.setReturnValue(BufferSourceHooks.wrap(renderType, cir.getReturnValue()));
	}
}
