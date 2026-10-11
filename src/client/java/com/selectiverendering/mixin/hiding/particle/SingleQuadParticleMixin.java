package com.selectiverendering.mixin.hiding.particle;

import org.spongepowered.asm.mixin.injection.ModifyVariable;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.Camera;
import com.selectiverendering.render.hiding.ProjectionPass;
import com.selectiverendering.render.hiding.ProjectionAreas;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.render.hiding.ParticleTypeHolder;
import com.selectiverendering.region.RegionTargets;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SingleQuadParticle.class)
public abstract class SingleQuadParticleMixin {
	@WrapOperation(
		method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/SingleQuadParticle;getLayer()Lnet/minecraft/client/particle/SingleQuadParticle$Layer;")
	)
	private SingleQuadParticle.Layer selectiveRendering$layer(SingleQuadParticle particle, Operation<SingleQuadParticle.Layer> original) {
		SingleQuadParticle.Layer layer = original.call(particle);

		if (selectiveRendering$alpha() < 0 || layer.translucent()) {
			return layer;
		}

		if (layer == SingleQuadParticle.Layer.OPAQUE_TERRAIN) {
			return SingleQuadParticle.Layer.TRANSLUCENT_TERRAIN;
		}

		if (layer == SingleQuadParticle.Layer.OPAQUE_ITEMS) {
			return SingleQuadParticle.Layer.TRANSLUCENT_ITEMS;
		}

		return layer == SingleQuadParticle.Layer.OPAQUE ? SingleQuadParticle.Layer.TRANSLUCENT : layer;
	}

	@WrapOperation(
		method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;colorFromFloat(FFFF)I")
	)
	private int selectiveRendering$color(float alpha, float red, float green, float blue, Operation<Integer> original) {
		int hidden = selectiveRendering$alpha();
		return original.call(hidden < 0 ? alpha : alpha * hidden / 255.0F, red, green, blue);
	}

	/** 投影那一遍只提取源范围里的粒子；提取时坐标由下面的三个 ModifyVariable 加上位移。 */
	@WrapMethod(method = "extract(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lnet/minecraft/client/Camera;F)V")
	private void selectiveRendering$project(QuadParticleRenderState state, Camera camera, float partialTickTime, Operation<Void> original) {
		ProjectionAreas.Active area = ProjectionPass.particleArea();

		if (area != null) {
			ParticlePosAccessor pos = (ParticlePosAccessor) (Object) this;
			if (!area.contains(pos.selectiveRendering$x(), pos.selectiveRendering$y(), pos.selectiveRendering$z())) {
				return;
			}
		}

		original.call(state, camera, partialTickTime);
	}

	@ModifyVariable(
		method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
		at = @At("HEAD"), ordinal = 0, argsOnly = true
	)
	private float selectiveRendering$shiftX(float x) {
		ProjectionAreas.Active area = ProjectionPass.particleArea();
		return area == null ? x : x + area.offsetX();
	}

	@ModifyVariable(
		method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
		at = @At("HEAD"), ordinal = 1, argsOnly = true
	)
	private float selectiveRendering$shiftY(float y) {
		ProjectionAreas.Active area = ProjectionPass.particleArea();
		return area == null ? y : y + area.offsetY();
	}

	@ModifyVariable(
		method = "extractRotatedQuad(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lorg/joml/Quaternionf;FFFF)V",
		at = @At("HEAD"), ordinal = 2, argsOnly = true
	)
	private float selectiveRendering$shiftZ(float z) {
		ProjectionAreas.Active area = ProjectionPass.particleArea();
		return area == null ? z : z + area.offsetZ();
	}

	@Unique
	private int selectiveRendering$alpha() {
		if (ProjectionPass.particleArea() != null) {
			return -1;
		}

		ParticlePosAccessor pos = (ParticlePosAccessor) (Object) this;
		ParticleTypeHolder holder = (ParticleTypeHolder) (Object) this;

		return RegionTargets.particleAlpha(holder.selectiveRendering$type(), pos.selectiveRendering$x(), pos.selectiveRendering$y(), pos.selectiveRendering$z());
	}
}
