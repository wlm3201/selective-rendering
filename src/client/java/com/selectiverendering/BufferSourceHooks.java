package com.selectiverendering;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * 取 {@code VertexConsumer} 时的统一挂钩点（{@code RenderTypeFeatureRendererMixin} /
 * {@code BufferSourceMixin} / {@code ImmediatelyFastBufferSourceMixin} 都调它）。
 *
 * <p>两种情形会套一层 {@link AlphaVertexConsumer}：
 * <ol>
 *   <li>当前正在渲染"活塞推动中的方块"（{@link MovingBlockRenderContext} 有值）——
 *       此时用该方块自己的 alpha；</li>
 *   <li>这个 RenderType 本帧被 {@link HiddenRenderTypes} 标记过——用全局隐藏 alpha。</li>
 * </ol>
 */
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
				return new AlphaVertexConsumer(consumer, SelectiveRenderingManager.getHiddenAlpha());
			}
		}

		return consumer;
	}
}
