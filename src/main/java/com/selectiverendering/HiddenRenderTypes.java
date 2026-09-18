package com.selectiverendering;

import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The render types that belong to hidden block entities this frame.
 *
 * <p>Block entities are collected in one pass and drawn in another: the renderer only records what
 * to draw along with the render type it wants, and the feature renderers turn those records into
 * vertices much later. A translucent block entity therefore has to leave a note on the way through
 * the first pass saying "when you get to this render type, write the transparency into the
 * colours", and this set is that note.</p>
 *
 * <p>Membership is by instance, because the translucent stand-ins come from the shared
 * {@code RenderTypes} cache and a value equality would drag look-alikes in. Everything happens on
 * the render thread, so a plain set is enough.</p>
 */
public final class HiddenRenderTypes {
	private static final Set<RenderType> MARKED = Collections.newSetFromMap(new IdentityHashMap<>());

	private HiddenRenderTypes() {
	}

	public static void mark(RenderType renderType) {
		MARKED.add(renderType);
	}

	public static boolean isMarked(RenderType renderType) {
		return MARKED.contains(renderType);
	}

	/**
	 * Called when the frame is over; the next frame marks its own types as it collects them.
	 */
	public static void clear() {
		MARKED.clear();
	}
}
