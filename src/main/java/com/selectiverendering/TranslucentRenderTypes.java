package com.selectiverendering;

import com.selectiverendering.mixin.RenderSetupAccessor;
import com.selectiverendering.mixin.RenderTypeAccessor;
import com.selectiverendering.mixin.TextureBindingAccessor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Swaps an opaque render type for a translucent look-alike, so a hidden block entity can be drawn
 * with blending on.
 *
 * <p>Render types cannot be subclassed here - in 26.1 the constructor is private - so the stand-in
 * has to be a real type picked from {@link RenderTypes}. {@code entityTranslucent} with the texture
 * of the original fits: same vertex format for entity models, blending on. Types that already blend
 * are kept and only marked, and types whose texture or format does not line up are left alone,
 * which leaves that one block entity solid rather than breaking the frame.</p>
 *
 * <p>Reading a texture out of a render type works on the set up the type carries in this version
 * and nowhere else.</p>
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
		// Item render types use a different vertex format than entity models, and swapping between
		// the two would feed the buffer garbage. Not worth it for the odd item stand.
		if (variant.format() != renderType.format()) {
			return renderType;
		}

		HiddenRenderTypes.mark(variant);
		return variant;
	}

	/**
	 * The texture the render type is set up to sample, read out of its setup. The map of bindings
	 * is what the type was built from, as opposed to the map of live GPU views that the public
	 * getter hands out, and only the former still knows the path.
	 */
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
