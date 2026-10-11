package com.selectiverendering.mixin.hiding.blockentity;

import com.selectiverendering.blockentity.TranslucentRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 访问 RenderType 的渲染状态，用于构造半透明变体。 */
@Mixin(RenderType.class)
public interface RenderTypeAccessor {
	@Accessor("state")
	RenderSetup selectiveRendering$state();
}
