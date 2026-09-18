package com.selectiverendering.mixin;

import com.selectiverendering.TranslucentRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the setup a render type was built from, for {@link TranslucentRenderTypes}, which reads
 * the texture out of it.
 */
@Mixin(RenderType.class)
public interface RenderTypeAccessor {
	@Accessor("state")
	RenderSetup selectiveRendering$state();
}
