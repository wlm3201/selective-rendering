package com.selectiverendering.mixin;

import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Moves fluids onto the translucent layer while blocks are being faded out.
 *
 * <p>{@link FluidModel#layer()} is what decides where a fluid's vertices are written: vanilla's
 * {@code FluidRenderer} asks the model for it and hands the answer to its output, which is what
 * picks the builder. Lava answers the solid layer, and on that layer an alpha in the vertices is
 * ignored outright, so a hidden pool of lava stays opaque however much alpha was written into it.
 * Every fluid is answered as translucent while the mod is fading things out, where blending
 * works.</p>
 *
 * <p>Which fluids are being faded is not decided here, because this answer has no position to go
 * by: it is a blanket one for as long as anything is being faded at all. Choosing per position is
 * what {@code FluidRendererMixin} does with the alpha, being handed the position with the fluid. A
 * fluid that is not being hidden keeps its own alpha and only changes which layer it is drawn on,
 * which is nothing a player can see, so the blanket answer is not the coupling it looks like:
 * hidden fluids come out see-through and every other one comes out exactly as it does with the mod
 * off.</p>
 *
 * <p>Sodium is not on this path. Its renderer is handed the same model but only ever asks it for
 * textures, and what decides the layer there is the material, which
 * {@code DefaultFluidRendererMixin} swaps per position. A hook here does nothing for it, and the
 * two answers do not collide.</p>
 */
@Mixin(FluidModel.class)
public class FluidModelMixin {
	@Inject(method = "layer", at = @At("RETURN"), cancellable = true)
	private void selectiveRendering$fluidLayer(CallbackInfoReturnable<ChunkSectionLayer> cir) {
		if (SelectiveRenderingManager.shouldRenderFluidsTranslucent()) {
			cir.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
		}
	}
}
