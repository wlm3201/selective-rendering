package com.selectiverendering.client;

import com.selectiverendering.client.mixin.RenderSetupAccessor;
import com.selectiverendering.client.mixin.RenderTypeAccessor;
import com.selectiverendering.client.mixin.TextureBindingAccessor;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public final class TranslucentRenderTypes {
    private TranslucentRenderTypes() {}

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
        RenderSetupAccessor setup =
                (RenderSetupAccessor)
                        (Object)
                                ((RenderTypeAccessor) (Object) renderType)
                                        .selectiveRendering$state();
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
