package com.selectiverendering.render.hiding;

import org.jetbrains.annotations.Nullable;

/** 共享 Sodium 当前方块的 alpha；null 表示无上下文，-1 表示正常渲染，0..255 表示淡化。 */
public interface BlockRenderContextAccess {
	@Nullable
	Integer selectiveRendering$alpha();

	void selectiveRendering$setAlpha(@Nullable Integer alpha);

	/** 这个方块里因为投影而被强制保留的面（按 Direction.ordinal 的位掩码），这些面只给投影用 */
	int selectiveRendering$forcedFaces();

	void selectiveRendering$setForcedFaces(int mask);
}
