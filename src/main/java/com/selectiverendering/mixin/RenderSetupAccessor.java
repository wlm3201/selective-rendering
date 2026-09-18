package com.selectiverendering.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Exposes the texture bindings a render setup was built from. The values are
 * {@code RenderSetup.TextureBinding}, which is package private and cannot be spelled out here, so
 * the map comes back raw and the one method needed on the values is reached through
 * {@link TextureBindingAccessor}.
 */
@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {
	@Accessor("textures")
	Map selectiveRendering$textures();
}
