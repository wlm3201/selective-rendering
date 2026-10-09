package com.selectiverendering.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class HiddenRenderTypes {
    private static final Set<RenderType> MARKED =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private HiddenRenderTypes() {}

    public static void mark(RenderType renderType) {
        MARKED.add(renderType);
    }

    public static boolean isMarked(RenderType renderType) {
        return MARKED.contains(renderType);
    }

    public static void clear() {
        MARKED.clear();
    }
}
