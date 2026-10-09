package com.selectiverendering.client;

import org.jetbrains.annotations.Nullable;

public interface BlockRenderContextAccess {
    @Nullable
    Integer selectiveRendering$alpha();

    void selectiveRendering$setAlpha(@Nullable Integer alpha);
}
