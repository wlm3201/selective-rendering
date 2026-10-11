package com.selectiverendering.render.hiding;

import net.minecraft.core.particles.ParticleType;

public interface ParticleTypeHolder {
	void selectiveRendering$setType(ParticleType<?> type);

	ParticleType<?> selectiveRendering$type();
}
