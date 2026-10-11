package com.selectiverendering.mixin.hiding.blockentity;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 访问贴图绑定的资源位置。 */
@Mixin(targets = "net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding")
public interface TextureBindingAccessor {
	@Invoker("location")
	Identifier selectiveRendering$location();
}
