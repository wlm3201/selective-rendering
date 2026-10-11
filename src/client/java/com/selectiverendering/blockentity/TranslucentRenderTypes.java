package com.selectiverendering.blockentity;

import com.selectiverendering.mixin.hiding.blockentity.RenderSetupAccessor;
import com.selectiverendering.mixin.hiding.blockentity.RenderTypeAccessor;
import com.selectiverendering.mixin.hiding.blockentity.TextureBindingAccessor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** 构造 RenderType 的半透明变体。 */
public final class TranslucentRenderTypes {
	private TranslucentRenderTypes() {
	}

	/** 返回可以带 alpha 绘制的变体，透明度由调用方通过顶点颜色提供。 */
	public static RenderType variant(RenderType renderType) {
		if (renderType.hasBlending()) {
			return renderType;
		}

		Identifier texture = textureOf(renderType);
		if (texture == null) {
			return renderType;
		}

		RenderType variant = translucentOf(renderType, texture);
		return variant == null ? renderType : variant;
	}

	/** 返回半透明变体并标，本帧所有使用该变体的绘制都会套上隐藏透明度。 */
	public static RenderType translucentVariant(RenderType renderType) {
		if (renderType.hasBlending()) {
			HiddenRenderTypes.mark(renderType);
			return renderType;
		}

		Identifier texture = textureOf(renderType);
		if (texture == null) {
			return renderType;
		}

		RenderType variant = translucentOf(renderType, texture);
		if (variant == null) {
			return renderType;
		}

		HiddenRenderTypes.mark(variant);
		return variant;
	}

	/** 找顶点格式和原类型一致的半透明类型：普通实体贴图用 entityTranslucent，信标光柱用它自己的半透明版本。 */
	private static RenderType translucentOf(RenderType original, Identifier texture) {
		RenderType entity = RenderTypes.entityTranslucent(texture);
		if (entity.format() == original.format()) {
			return entity;
		}

		RenderType beam = RenderTypes.beaconBeam(texture, true);
		return beam.format() == original.format() ? beam : null;
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
