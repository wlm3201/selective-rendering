package com.selectiverendering.blockentity;

import com.selectiverendering.render.hiding.AlphaGetter;
import com.selectiverendering.render.hiding.MovingBlockRenderContext;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

/** 为移动方块的渲染缓冲应用当前透明度。 */
public final class BufferSourceHooks {
	private BufferSourceHooks() {
	}

	public static VertexConsumer wrap(RenderType renderType, VertexConsumer consumer) {
		if (consumer != null) {
			int movingAlpha = MovingBlockRenderContext.alpha();

			if (movingAlpha > 0 && movingAlpha < 255) {
				return new AlphaVertexConsumer(consumer, movingAlpha);
			}

			if (HiddenRenderTypes.isMarked(renderType)) {
				return new AlphaVertexConsumer(consumer, AlphaGetter.getHiddenAlpha());
			}
		}

		return consumer;
	}
}
