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
 * Moves a see-through moving block onto the translucent layer when Fabric Renderer API is the
 * one drawing it.
 *
 * <p>With the API installed the vanilla way of drawing a moving block is gone: it redirects
 * {@code tesselateBlock} onto its own renderer, which hands the quads to an emitter instead of to
 * the vanilla quad writer, and the writer in {@code BlockFeatureRendererMixin} - the one that
 * would have asked for a translucent buffer - is left with nothing to do. What decides the buffer
 * on this path is the layer each quad carries, which this maps to a render type, so this is where
 * it can still be changed.</p>
 *
 * <p>An opaque layer is drawn without blending, where an alpha in the vertices shows nothing at
 * all, so the translucent moving block type is answered instead. The alpha itself is written
 * later, into the consumer the buffer source hands out; see {@code BufferSourceHooks}.</p>
 *
 * <p>Targeted by name, and never imported: Fabric Renderer API is optional. The layer is only
 * moved while a moving block is being drawn, so nothing else that maps a layer to a render type
 * sees any of this.</p>
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
