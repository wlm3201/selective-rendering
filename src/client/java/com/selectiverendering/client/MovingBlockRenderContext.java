package com.selectiverendering.client;

public final class MovingBlockRenderContext {
    private static final ThreadLocal<Integer> ALPHA = new ThreadLocal<>();

    private MovingBlockRenderContext() {}

    public static void set(int alpha) {
        ALPHA.set(alpha);
    }

    public static void clear() {
        ALPHA.remove();
    }

    public static int alpha() {
        Integer alpha = ALPHA.get();
        return alpha == null ? -1 : alpha;
    }
}
