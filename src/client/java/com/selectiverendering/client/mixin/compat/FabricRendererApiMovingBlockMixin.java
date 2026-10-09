package com.selectiverendering.client.mixin.compat;

import com.selectiverendering.client.MovingBlockRenderContext;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
        targets = "net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper",
        remap = false)
public class FabricRendererApiMovingBlockMixin {
    @Inject(
            method = "getMovingBlockRenderType",
            at = @At("RETURN"),
            cancellable = true,
            remap = false,
            require = 0)
    private static void selectiveRendering$translucentLayer(
            ChunkSectionLayer layer, CallbackInfoReturnable<RenderType> cir) {
        int alpha = MovingBlockRenderContext.alpha();

        if (alpha > 0 && alpha < 255) {
            cir.setReturnValue(RenderTypes.translucentMovingBlock());
        }
    }
}
