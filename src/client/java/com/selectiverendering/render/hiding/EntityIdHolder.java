package com.selectiverendering.render.hiding;

/** 实体渲染状态上记着它是由哪个实体生成的，渲染时才能回头取实体的 NBT。 */
public interface EntityIdHolder {
	void selectiveRendering$setEntityId(int id);

	int selectiveRendering$entityId();
}
