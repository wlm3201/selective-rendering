package com.selectiverendering.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selectiverendering.client.MovingBlockRenderContext;
import com.selectiverendering.client.SelectiveRenderingManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockFeatureRenderer.class)
public class BlockFeatureRendererMixin {
    @Unique
    private static final String selectiveRendering$TESSELATE_BLOCK =
            "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V";

    @Unique
    private static final String selectiveRendering$GET_BUFFER =
            "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;";

    @WrapOperation(
            method = "renderMovingBlockSubmits",
            at = @At(value = "INVOKE", target = selectiveRendering$TESSELATE_BLOCK),
            require = 1)
    private void selectiveRendering$aroundTesselateBlock(
            ModelBlockRenderer blockRenderer,
            BlockQuadOutput output,
            float x,
            float y,
            float z,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            Operation<Void> original,
            @Local MovingBlockRenderState renderState) {
        MovingBlockRenderContext.set(
                SelectiveRenderingManager.getAlpha(
                        renderState.blockState, renderState.blockPos, true));

        try {
            original.call(blockRenderer, output, x, y, z, level, pos, state, model, seed);
        } finally {
            MovingBlockRenderContext.clear();
        }
    }

    @WrapOperation(
            method = "renderMovingBlockSubmits",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;hasMaterialFlag(I)Z"),
            require = 1)
    private boolean selectiveRendering$translucentPass(
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

    @WrapOperation(
            method = "putBakedQuad",
            at = @At(value = "INVOKE", target = selectiveRendering$GET_BUFFER),
            require = 0)
    private static VertexConsumer selectiveRendering$translucentBuffer(
            MultiBufferSource.BufferSource bufferSource,
            RenderType renderType,
            Operation<VertexConsumer> original) {
        int alpha = MovingBlockRenderContext.alpha();
        if (alpha <= 0 || alpha >= 255) {
            return original.call(bufferSource, renderType);
        }

        return original.call(bufferSource, RenderTypes.translucentMovingBlock());
    }
}
