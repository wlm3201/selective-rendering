package com.selectiverendering.mixin.hiding.entity;

import com.selectiverendering.render.hiding.EntityIdHolder;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	@Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
	private void selectiveRendering$recordEntity(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
		((EntityIdHolder) cir.getReturnValue()).selectiveRendering$setEntityId(entity.getId());
	}
}
