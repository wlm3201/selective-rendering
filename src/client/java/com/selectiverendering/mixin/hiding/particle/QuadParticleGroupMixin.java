package com.selectiverendering.mixin.hiding.particle;

import com.selectiverendering.render.hiding.ProjectionAreas;
import com.selectiverendering.render.hiding.ProjectionPass;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 源范围里的粒子可能不在视野里，不处理的话会被原版的视锥剔除掉
 */
@Mixin(QuadParticleGroup.class)
public abstract class QuadParticleGroupMixin extends ParticleGroup<SingleQuadParticle> {
	@Shadow
	@Final
	private QuadParticleRenderState particleTypeRenderState;

	protected QuadParticleGroupMixin(ParticleEngine engine) {
		super(engine);
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void selectiveRendering$project(Frustum frustum, Camera camera, float partialTickTime, CallbackInfoReturnable<ParticleGroupRenderState> cir) {
		List<ProjectionAreas.Active> areas = ProjectionAreas.current();
		if (areas.isEmpty()) {
			return;
		}

		Vec3 position = camera.position();

		for (ProjectionAreas.Active area : areas) {
			Frustum shifted = new Frustum(frustum);
			shifted.prepare(position.x - area.offsetX(), position.y - area.offsetY(), position.z - area.offsetZ());
			ProjectionPass.beginParticle(area);

			try {
				for (SingleQuadParticle particle : this.particles) {
					double x = ((ParticlePosAccessor) particle).selectiveRendering$x();
					double y = ((ParticlePosAccessor) particle).selectiveRendering$y();
					double z = ((ParticlePosAccessor) particle).selectiveRendering$z();

					if (area.contains(x, y, z) && shifted.pointInFrustum(x, y, z)) {
						particle.extract(this.particleTypeRenderState, camera, partialTickTime);
					}
				}
			}
			finally {
				ProjectionPass.endParticle();
			}
		}
	}
}
