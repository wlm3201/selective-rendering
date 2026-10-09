package com.selectiverendering.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

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

    @Override
    public VertexConsumer setUv3(float u, float v) {
        base.setUv3(u, v);
        return this;
    }
}
