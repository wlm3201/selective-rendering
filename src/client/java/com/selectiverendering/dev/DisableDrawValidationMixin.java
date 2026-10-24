package com.selectiverendering.dev;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 开发环境里渲染后端会打开绘制校验：{@code GlRenderPass.VALIDATION = SharedConstants.IS_RUNNING_IN_IDE}。
 * validateDraw 固定遍历 16 个顶点缓冲槽去比对 {@code pipeline.info().getVertexFormatBindings()}，
 * 而 Iris 的阴影管线只声明了 1 个绑定，于是一进世界就 ArrayIndexOutOfBoundsException。
 * 正式版 IS_RUNNING_IN_IDE 恒为 false，校验根本不跑，所以只有 runClient 会崩、打包后没事。
 *
 * <p>这里只把 GlRenderPass 初始化时的那一次取值改成 false，等价于官方发行版的行为；
 * 不动 IS_RUNNING_IN_IDE 本身，免得影响 malilib 依赖它的调试功能。
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlRenderPass", remap = false)
public class DisableDrawValidationMixin {
	@ModifyExpressionValue(method = "<clinit>", at = @At(value = "FIELD", target = "Lnet/minecraft/SharedConstants;IS_RUNNING_IN_IDE:Z"), require = 0)
	private static boolean selectiveRendering$disableValidation(boolean original) {
		return false;
	}
}
