package com.selectiverendering.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.selectiverendering.client.SelectiveRenderingManager;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {
    @Inject(method = "submitMovingBlock", at = @At("HEAD"), cancellable = true, require = 1)
    private void selectiveRendering$submitMovingBlock(
            PoseStack poseStack, MovingBlockRenderState renderState, CallbackInfo ci) {
        if (SelectiveRenderingManager.getAlpha(renderState.blockState, renderState.blockPos, true)
                == 0) {
            ci.cancel();
        }
    }
}
