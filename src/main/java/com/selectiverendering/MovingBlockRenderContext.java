package com.selectiverendering;

/**
 * The alpha of the moving block being drawn right now.
 *
 * <p>Moving blocks are drawn one call to {@code tesselateBlock} at a time, and that call only
 * knows the quads it was handed - not which submit it belongs to, and with all of the position
 * folded into the pose, not even where it is. The two injections either side of it do know, being
 * handed the render state, so one of them leaves the alpha here and the other takes it away again.
 * It is never set outside that pair, which is what keeps a partly see-through block from bleeding
 * into the next one drawn. Kept per thread for the same reason: a value left standing would be
 * read as belonging to whatever is drawn after it.</p>
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

	/**
	 * @return the alpha of the moving block on the drawing stack, or {@code -1} when there is
	 *         none.
	 */
	public static int alpha() {
		Integer alpha = ALPHA.get();
		return alpha == null ? -1 : alpha;
	}
}
