package com.selectiverendering;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Writes the chosen alpha over whatever colour the renderer asked for, leaving the channels
 * alone. Everything else goes straight through, and the default methods on {@link VertexConsumer}
 * build on these, so they pick the alpha up as well.
 *
 * <p>This is the draw time half of the hiding, and {@link BufferSourceHooks} is the only thing that
 * hands one out. It reaches the two things that are still geometry by the time a frame is drawn:
 * block entities, whose renderers build their own vertices long after the frame was collected, and
 * moving blocks, which never belong to a section mesh at all. Static blocks are written into their
 * quads while the section is compiled and never reach a consumer.</p>
 */
public class AlphaVertexConsumer implements VertexConsumer {
	private final VertexConsumer base;
	private final int alpha;

	public AlphaVertexConsumer(VertexConsumer base, int alpha) {
		this.base = base;
		this.alpha = alpha;
	}

	@Override
	public VertexConsumer setColor(int red, int green, int blue, int alpha) {
		base.setColor(red, green, blue, this.alpha);
		return this;
	}

	@Override
	public VertexConsumer setColor(int argb) {
		base.setColor((argb & 0x00FFFFFF) | (alpha << 24));
		return this;
	}

	@Override
	public VertexConsumer addVertex(float x, float y, float z) {
		base.addVertex(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer setUv(float u, float v) {
		base.setUv(u, v);
		return this;
	}

	@Override
	public VertexConsumer setUv1(int u, int v) {
		base.setUv1(u, v);
		return this;
	}

	@Override
	public VertexConsumer setUv2(int u, int v) {
		base.setUv2(u, v);
		return this;
	}

	@Override
	public VertexConsumer setNormal(float x, float y, float z) {
		base.setNormal(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer setLineWidth(float width) {
		base.setLineWidth(width);
		return this;
	}
}
