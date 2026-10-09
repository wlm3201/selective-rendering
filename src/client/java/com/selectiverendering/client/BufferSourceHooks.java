package com.selectiverendering.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class BufferSourceHooks {
    private BufferSourceHooks() {}

    public static VertexConsumer wrap(RenderType renderType, VertexConsumer consumer) {
        if (consumer != null) {
            int movingAlpha = MovingBlockRenderContext.alpha();

            if (movingAlpha > 0 && movingAlpha < 255) {
                return new AlphaVertexConsumer(consumer, movingAlpha);
            }

            if (HiddenRenderTypes.isMarked(renderType)) {
                return new AlphaVertexConsumer(
                        consumer, SelectiveRenderingManager.getHiddenAlpha());
            }
        }

        return consumer;
    }
}
