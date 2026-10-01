package com.selectiverendering.mixin;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 取 {@code RenderSetup.TextureBinding} 的贴图位置。
 * 用 {@code @Invoker} 而不是 {@code @Accessor}，是因为源码里它是个 record 组件方法
 * （{@code location()}，带括号），不是字段。
 */
@Mixin(targets = "net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding")
public interface TextureBindingAccessor {
	@Invoker("location")
	Identifier selectiveRendering$location();
}
