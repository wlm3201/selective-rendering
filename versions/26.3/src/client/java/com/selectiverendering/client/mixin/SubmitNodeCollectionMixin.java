package com.selectiverendering.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.selectiverendering.client.SelectiveRenderingManager;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public class SubmitNodeCollectionMixin {
    @Inject(method = "submitMovingBlock", at = @At("HEAD"), cancellable = true, require = 1)
    private void selectiveRendering$submitMovingBlock(
            PoseStack poseStack,
            MovingBlockRenderState renderState,
            int outlineColor,
            CallbackInfo ci) {
        if (SelectiveRenderingManager.getAlpha(renderState.blockState, renderState.blockPos, true)
                == 0) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "submitMovingBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;hasMaterialFlag(I)Z"),
            require = 1)
    private boolean selectiveRendering$forceTranslucentPhase(
            BlockStateModel model,
            int flag,
            Operation<Boolean> original,
            @Local MovingBlockRenderState renderState) {
        boolean hasTranslucentMaterial = original.call(model, flag);
        if (hasTranslucentMaterial || flag != BakedQuad.FLAG_TRANSLUCENT) {
            return hasTranslucentMaterial;
        }

        int alpha =
                SelectiveRenderingManager.getAlpha(
                        renderState.blockState, renderState.blockPos, true);
        return alpha > 0 && alpha < 255;
    }
}
