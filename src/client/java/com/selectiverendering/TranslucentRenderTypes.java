package com.selectiverendering;

import com.selectiverendering.mixin.RenderSetupAccessor;
import com.selectiverendering.mixin.RenderTypeAccessor;
import com.selectiverendering.mixin.TextureBindingAccessor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * 为一个 {@link RenderType} 找出它的"半透明版本"。
 *
 * <p>背景：1.21.9+ 之后渲染走 {@code SubmitNodeCollector} + {@code RenderPipeline}，
 * 顶点不再由我们直接产出，想让方块实体变淡只剩一条路——把它的 RenderType
 * 换成一个带 blending 的变体（Lucidity 里对应的类是
 * {@code SelectiveRenderingRenderTypeWrapper}，思路一致）。
 *
 * <p>具体做法很"土"：通过 mixin accessor 钻进 {@code RenderType.state} →
 * {@code RenderSetup.textures}，抠出第一个贴图 {@link net.minecraft.resources.Identifier}，
 * 再拿它去造 {@code RenderTypes.entityTranslucent(贴图)}。
 *
 * <h2>降级路径（都会返回原 renderType，表现为"这个 BE 没变淡"而不是崩溃）</h2>
 * <ul>
 *   <li>原本就带 blending → 直接返回，只打个标记；</li>
 *   <li>抠不出贴图 → 返回原值；</li>
 *   <li>变体的顶点格式和原类型不一致 → 返回原值（顶点格式不匹配会导致渲染错乱，必须放弃）。</li>
 * </ul>
 *
 * <p>另外这里每次调用都会新建/查找一个 RenderType，没有缓存，
 * 在 BE 很多时是每帧的额外开销。
 */
public final class TranslucentRenderTypes {
	private TranslucentRenderTypes() {
	}

	public static RenderType translucentVariant(RenderType renderType) {
		if (renderType.hasBlending()) {
			HiddenRenderTypes.mark(renderType);
			return renderType;
		}

		Identifier texture = textureOf(renderType);
		if (texture == null) {
			return renderType;
		}

		RenderType variant = RenderTypes.entityTranslucent(texture);
		if (variant.format() != renderType.format()) {
			return renderType;
		}

		HiddenRenderTypes.mark(variant);
		return variant;
	}

	private static Identifier textureOf(RenderType renderType) {
		RenderSetupAccessor setup = (RenderSetupAccessor) (Object) ((RenderTypeAccessor) (Object) renderType).selectiveRendering$state();
		if (setup == null) {
			return null;
		}

		Map<?, ?> textures = setup.selectiveRendering$textures();
		if (textures == null) {
			return null;
		}

		for (Object binding : textures.values()) {
			Identifier location = ((TextureBindingAccessor) binding).selectiveRendering$location();
			if (location != null) {
				return location;
			}
		}

		return null;
	}
}
