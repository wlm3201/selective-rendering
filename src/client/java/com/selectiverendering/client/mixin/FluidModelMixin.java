package com.selectiverendering.client.mixin;

import com.selectiverendering.client.SelectiveRenderingManager;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidModel.class)
public class FluidModelMixin {
    @Inject(method = "layer", at = @At("RETURN"), cancellable = true)
    private void selectiveRendering$fluidLayer(CallbackInfoReturnable<ChunkSectionLayer> cir) {
        if (SelectiveRenderingManager.shouldRenderFluidsTranslucent()) {
            cir.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
        }
    }
}
