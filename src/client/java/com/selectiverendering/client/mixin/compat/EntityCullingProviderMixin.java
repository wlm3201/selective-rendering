package com.selectiverendering.client.mixin.compat;

import com.selectiverendering.client.SelectiveRenderingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.tr7zw.entityculling.Provider", remap = false)
public class EntityCullingProviderMixin {
    @Inject(
            method = "isOpaqueFullCube(III)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void selectiveRendering$fadedBlocksAreTransparent(
            int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (SelectiveRenderingManager.isFadedCube(x, y, z)) {
            cir.setReturnValue(false);
        }
    }
}
