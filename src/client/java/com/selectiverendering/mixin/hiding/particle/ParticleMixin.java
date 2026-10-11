package com.selectiverendering.mixin.hiding.particle;

import com.selectiverendering.render.hiding.ParticleTypeHolder;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Particle.class)
public abstract class ParticleMixin implements ParticleTypeHolder {
	@Unique
	private ParticleType<?> selectiveRendering$type;

	@Override
	public void selectiveRendering$setType(ParticleType<?> type) {
		this.selectiveRendering$type = type;
	}

	@Override
	public ParticleType<?> selectiveRendering$type() {
		return this.selectiveRendering$type;
	}
}
