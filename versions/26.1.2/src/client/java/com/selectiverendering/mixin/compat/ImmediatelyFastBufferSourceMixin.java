package com.selectiverendering.mixin.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selectiverendering.BufferSourceHooks;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>ImmediatelyFast 兼容补丁</b>。
 *
 * <p>ImmediatelyFast 会把 {@code BufferSource} 换成自己的
 * {@code BatchableBufferSource}（为了批处理），于是 {@code BufferSourceMixin}
 * 就打不到了，必须再补一刀。逻辑完全一样。
 *
 * <p>{@code require = 0} + {@code remap = false} + 字符串 target：
 * 保证没装 ImmediatelyFast 时不会崩。
 *
 * <p>26.2+ 的渲染架构改了以后，取 buffer 的方式变了
 * （走 {@code RenderTypeFeatureRenderer}），这个补丁也就不再需要。
 */
@Mixin(targets = "net.raphimc.immediatelyfast.feature.core.BatchableBufferSource", remap = false)
public class ImmediatelyFastBufferSourceMixin {
	@Inject(method = "getBuffer", at = @At("RETURN"), cancellable = true, remap = false)
	private void selectiveRendering$wrap(RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
		cir.setReturnValue(BufferSourceHooks.wrap(renderType, cir.getReturnValue()));
	}
}
