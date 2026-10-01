package com.selectiverendering;

import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * "这一帧里，哪些 {@link RenderType} 需要被强行套上 alpha"的临时标记集合。
 *
 * <p>它服务于方块实体（BE）那条链路，流程是：
 * <ol>
 *   <li>{@link TranslucentRenderTypes#translucentVariant} 为一个不透明的 RenderType
 *       造出它的半透明变体，并在这里 {@link #mark} 一笔；</li>
 *   <li>{@code RenderTypeFeatureRendererMixin} 在取 {@code VertexConsumer} 时
 *       看到标记就套一层 {@code AlphaVertexConsumer}；</li>
 *   <li>{@code FeatureRenderDispatcherMixin.prepareFrame} 在每帧末尾 {@link #clear()}。</li>
 * </ol>
 *
 * <h2>⚠ 一个设计上的粗糙点</h2>
 * <p>标记是按 <b>RenderType 实例</b>做的（用 {@link IdentityHashMap}），
 * 而半透明变体是 {@code RenderTypes.entityTranslucent(贴图)} 这种<b>共享实例</b>。
 * 也就是说：本帧内任何<b>其它</b>恰好用了同一张贴图的 BE / 实体，
 * 也会被一起套上隐藏用的 alpha。因为标记每帧清空，影响被限制在一帧内，
 * 但在有大量 BE 的场景下可能出现"不该变淡的东西闪一下"。
 *
 * <p>用 identity 而非 equals 是有意的：RenderType 的 {@code equals} 被 Mojang 重载过，
 * 不同实例可能"相等"，会误伤。
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

	public static void clear() {
		MARKED.clear();
	}
}
