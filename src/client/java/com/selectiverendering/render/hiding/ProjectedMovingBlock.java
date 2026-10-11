package com.selectiverendering.render.hiding;

/** 标记一个活塞移动方块的渲染状态是投影出来的：它的透明度不跟随源位置，按正常方块渲染。 */
public interface ProjectedMovingBlock {
	boolean selectiveRendering$projected();
}
