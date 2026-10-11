package com.selectiverendering.mixin.hiding.particle;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticlePosAccessor {
	@Accessor("x")
	double selectiveRendering$x();

	@Accessor("y")
	double selectiveRendering$y();

	@Accessor("z")
	double selectiveRendering$z();
}
