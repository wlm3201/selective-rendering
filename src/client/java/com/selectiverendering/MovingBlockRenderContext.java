package com.selectiverendering;

/**
 * 渲染"活塞推动中的方块"期间的临时上下文（仅当前线程可见）。
 *
 * <p>为什么需要单独一套：被活塞推着的方块 ({@code moving_piston}) 不在区块网格里，
 * 它是每帧临时用 {@code MovingBlockFeatureRenderer} 画出来的，
 * 走的完全是另一条渲染路径（BE 式），没法复用区块网格那套 alpha 注入。
 * 所以在 tesselate 之前把 alpha 塞进这里，取 buffer / 取 RenderType 时再读出来。
 *
 * <p>典型用法（见 {@code BlockFeatureRendererMixin}）：
 * <pre>
 *   MovingBlockRenderContext.set(alpha);
 *   try { original.call(...); } finally { MovingBlockRenderContext.clear(); }
 * </pre>
 * <b>一定要在 finally 里 clear</b>，否则残留的 alpha 会污染后续渲染。
 * （26.1.2 版用的是 HEAD/AFTER 两个 {@code @Inject}，没有 finally，异常时会泄漏。）
 *
 * <p>用 {@link ThreadLocal} 是因为区块构建在多个工作线程上，不能互相串味。
 */
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
