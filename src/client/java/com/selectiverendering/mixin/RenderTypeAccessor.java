package com.selectiverendering.mixin;

import com.selectiverendering.TranslucentRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 三个 accessor 是一套的：为了从 {@link RenderType} 里抠出它用的贴图。
 *
 * <pre>
 *   RenderType.state ──► RenderSetup.textures ──► TextureBinding.location ──► Identifier
 *   (RenderTypeAccessor)   (RenderSetupAccessor)     (TextureBindingAccessor)
 * </pre>
 *
 * 用途见 {@link TranslucentRenderTypes}：拿贴图去造一个半透明的 RenderType 变体。
 * 这是纯粹靠反射式访问私有字段的 hack，MC 改了 RenderType 内部结构就会失效
 * （失效时 {@code TranslucentRenderTypes} 会降级成"不变淡"而不是崩溃）。
 */
@Mixin(RenderType.class)
public interface RenderTypeAccessor {
	@Accessor("state")
	RenderSetup selectiveRendering$state();
}
