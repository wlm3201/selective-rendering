package com.selectiverendering.blockentity;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jspecify.annotations.NonNull;

/** 包装顶点消费者，把顶点透明度乘上给定的 alpha。 */
public class AlphaVertexConsumer implements VertexConsumer {
	private final VertexConsumer base;
	private final int alpha;

	public AlphaVertexConsumer(VertexConsumer base, int alpha) {
		this.base = base;
		this.alpha = alpha;
	}

	private int scale(int existing) {
		return (existing == 0 ? 255 : existing) * alpha / 255;
	}

	@Override
	public @NonNull VertexConsumer setColor(int red, int green, int blue, int alpha) {
		base.setColor(red, green, blue, scale(alpha));
		return this;
	}

	@Override
	public @NonNull VertexConsumer setColor(int argb) {
		base.setColor((argb & 0x00FFFFFF) | (scale(argb >>> 24) << 24));
		return this;
	}

	@Override
	public @NonNull VertexConsumer addVertex(float x, float y, float z) {
		base.addVertex(x, y, z);
		return this;
	}

	@Override
	public @NonNull VertexConsumer setUv(float u, float v) {
		base.setUv(u, v);
		return this;
	}

	@Override
	public @NonNull VertexConsumer setUv1(int u, int v) {
		base.setUv1(u, v);
		return this;
	}

	@Override
	public @NonNull VertexConsumer setUv2(int u, int v) {
		base.setUv2(u, v);
		return this;
	}

	@Override
	public @NonNull VertexConsumer setNormal(float x, float y, float z) {
		base.setNormal(x, y, z);
		return this;
	}

	@Override
	public @NonNull VertexConsumer setLineWidth(float width) {
		base.setLineWidth(width);
		return this;
	}
	//? if >=26.3 {

	@Override
	public @NonNull VertexConsumer setUv3(float u, float v) {
		base.setUv3(u, v);
		return this;
	}
	//?}
}
