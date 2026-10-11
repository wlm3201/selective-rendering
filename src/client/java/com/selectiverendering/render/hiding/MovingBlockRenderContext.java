package com.selectiverendering.render.hiding;

/** 保存当前线程移动方块的渲染透明度，使用后须在 finally 中清理。 */
public final class MovingBlockRenderContext {
	private static final ThreadLocal<Integer> ALPHA = new ThreadLocal<>();

	private MovingBlockRenderContext() {
	}

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
