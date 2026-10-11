package com.selectiverendering.mixin.hiding.entity;

import com.selectiverendering.render.hiding.EntityIdHolder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements EntityIdHolder {
	@Unique
	private int selectiveRendering$entityId = -1;

	@Override
	public void selectiveRendering$setEntityId(int id) {
		this.selectiveRendering$entityId = id;
	}

	@Override
	public int selectiveRendering$entityId() {
		return this.selectiveRendering$entityId;
	}
}
