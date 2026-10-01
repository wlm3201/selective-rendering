package com.selectiverendering.mixin.compat;

import com.selectiverendering.MovingBlockRenderContext;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FRAPI 版"移动方块用半透明 RenderType"。
 *
 * <p>{@code ChunkSectionLayerHelper.getMovingBlockRenderType} 决定被活塞推动的方块用哪套渲染状态。
 * 当这个方块该被淡化时，把结果换成 {@code RenderTypes.translucentMovingBlock()}，
 * 让它的顶点 alpha 能生效。
 *
 * <p>对应的原版/Sodium 路径见 {@code BlockFeatureRendererMixin}
 * （它在取 buffer 时把 RenderType 换成 {@code translucentMovingBlock}）。
 */
@Mixin(targets = "net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper", remap = false)
public class FabricRendererApiMovingBlockMixin {
	@Inject(
		method = "getMovingBlockRenderType",
		at = @At("RETURN"),
		cancellable = true,
		remap = false,
		require = 0
	)
	private static void selectiveRendering$translucentLayer(ChunkSectionLayer layer, CallbackInfoReturnable<RenderType> cir) {
		int alpha = MovingBlockRenderContext.alpha();

		if (alpha > 0 && alpha < 255) {
			cir.setReturnValue(RenderTypes.translucentMovingBlock());
		}
	}
}
