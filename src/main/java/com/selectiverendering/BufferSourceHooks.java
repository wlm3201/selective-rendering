package com.selectiverendering;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Decides what a render type's buffer has to go through before anything is written to it.
 *
 * <p>Block entities are the only thing left that needs this. Their geometry is drawn by renderers
 * of their own, long after the frame was collected, and those renderers ask a buffer source for a
 * buffer themselves - there is no quad left at that point for anyone to write into. The render
 * types belonging to hidden ones are marked while the frame is collected, see
 * {@link HiddenRenderTypes}, and when one of those is asked for here the consumer comes back
 * wrapped so the transparency lands in the vertex colours.</p>
 *
 * <p>Static blocks do not come through here: they are dealt with while they are still quads, in
 * {@code ModelBlockRendererMixin}, or in {@code BlockRendererMixin} where Sodium is installed,
 * which is where the alpha is multiplied into the quad and the quad is moved onto the translucent
 * layer. Moving ones do come through here, because both of the ways
 * one can be drawn - the vanilla one and the one Fabric Renderer API redirects it onto - end up
 * asking a buffer source for a buffer, and this is the one place both of them pass. Their alpha
 * is waiting in {@link MovingBlockRenderContext} while they are drawn, and the render type they
 * ask for is moved onto the translucent layer by {@code BlockFeatureRendererMixin} or by
 * {@code FabricRendererApiMovingBlockMixin}, whichever of the two is on that path.</p>
 *
 * <p>Anything else comes back exactly as it went in.</p>
 */
public final class BufferSourceHooks {
	private BufferSourceHooks() {
	}

	/**
	 * @param renderType the render type asked for.
	 * @param consumer   the buffer that source would have handed out.
	 * @return the consumer the caller should write to.
	 */
	public static VertexConsumer wrap(RenderType renderType, VertexConsumer consumer) {
		if (consumer != null) {
			// A moving block has its alpha written here rather than at the call that asked for
			// the buffer, so that whichever of the two paths it takes - the vanilla one, or the
			// one Fabric Renderer API redirects it onto - lands on the same consumer.
			int movingAlpha = MovingBlockRenderContext.alpha();

			if (movingAlpha > 0 && movingAlpha < 255) {
				return new AlphaVertexConsumer(consumer, movingAlpha);
			}

			if (HiddenRenderTypes.isMarked(renderType)) {
				return new AlphaVertexConsumer(consumer, SelectiveRenderingManager.getHiddenAlpha());
			}
		}

		return consumer;
	}
}
