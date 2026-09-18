package com.selectiverendering.mixin;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The location of a texture binding. The class is package private, so the mixin is declared by
 * name, and the accessor returns the path of the texture the render type samples.
 */
@Mixin(targets = "net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding")
public interface TextureBindingAccessor {
	@Invoker("location")
	Identifier selectiveRendering$location();
}
