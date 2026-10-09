package com.selectiverendering.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.selectiverendering.client.SelectiveRenderingManager;
import com.selectiverendering.client.SelectiveSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    @WrapOperation(
            method = "submit",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private <S extends BlockEntityRenderState> void selectiveRendering$submit(
            BlockEntityRenderer<?, S> renderer,
            S renderState,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraRenderState,
            Operation<Void> original) {
        int alpha = SelectiveRenderingManager.getAlphaAt(renderState.blockPos);
        if (alpha == 0) {
            return;
        }

        if (alpha > 0) {
            collector = new SelectiveSubmitNodeCollector(collector);
        }

        original.call(renderer, renderState, poseStack, collector, cameraRenderState);
    }
}
