package com.selectiverendering;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * {@link VertexConsumer} 装饰器：<b>强行覆盖顶点 alpha</b>，其余全部原样转发。
 *
 * <p>用途：方块实体 / 移动方块这类"拿不到 quad、只能拿 buffer"的渲染路径，
 * 想改透明度就只能从 {@code setColor} 这一层下手。
 *
 * <p>{@code setColor(r,g,b,a)} 里<b>丢弃了传入的 a</b>，一律换成我们指定的 alpha—
 * 也就是说原本就半透明的纹理（比如染色玻璃）会失去它自己的透明度。
 * 对我们"整体变淡"的目标来说可以接受，但要知道这个行为。
 *
 * <p>⚠ 这个类必须<b>逐个版本</b>复制维护：{@code VertexConsumer} 是 Mojang 的大接口，
 * 几乎每个版本都会增删方法（所以它被放在 {@code versions/} 而不是共享源码里）。
 * 漏了新方法不会编译报错，但会丢功能。
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

	@Override
	public VertexConsumer setUv3(float u, float v) {
		base.setUv3(u, v);
		return this;
	}
}
