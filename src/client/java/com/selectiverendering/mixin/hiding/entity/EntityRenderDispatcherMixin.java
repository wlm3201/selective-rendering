package com.selectiverendering.mixin.hiding.entity;

import com.selectiverendering.render.hiding.ProjectionPass;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.selectiverendering.blockentity.SelectiveSubmitNodeCollector;
import com.selectiverendering.render.hiding.EntityIdHolder;
import com.selectiverendering.region.RegionTargets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;

/** 按所在位置和实体类型隐藏实体，或将其提交到半透明管线。 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	@WrapMethod(method = "submit")
	private void selectiveRendering$submit(
		EntityRenderState renderState,
		CameraRenderState camera,
		double x,
		double y,
		double z,
		PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector,
		Operation<Void> original
	) {
		if (ProjectionPass.active()) {
			original.call(renderState, camera, x, y, z, poseStack, submitNodeCollector);
			return;
		}

		int alpha = RegionTargets.entityAlpha(renderState.entityType, ((EntityIdHolder) renderState).selectiveRendering$entityId(), renderState.x, renderState.y, renderState.z);
		if (alpha == 0) {
			return;
		}

		original.call(renderState, camera, x, y, z, poseStack, alpha > 0 ? new SelectiveSubmitNodeCollector(submitNodeCollector, alpha) : submitNodeCollector);
	}
}
