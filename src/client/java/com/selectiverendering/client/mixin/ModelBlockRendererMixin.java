package com.selectiverendering.client.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.selectiverendering.client.MovingBlockRenderContext;
import com.selectiverendering.client.SelectiveRenderingManager;
import com.selectiverendering.client.compat.Platform;
import java.util.List;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {
    @Unique
    private static final ThreadLocal<Integer> selectiveRendering$blockAlpha =
            ThreadLocal.withInitial(() -> -1);

    @Shadow @Final private QuadInstance quadInstance;

    @Inject(method = "tesselateBlock", at = @At("HEAD"), require = 1)
    private void selectiveRendering$beginBlock(
            BlockQuadOutput output,
            float x,
            float y,
            float z,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState blockState,
            BlockStateModel model,
            long seed,
            CallbackInfo ci) {
        selectiveRendering$blockAlpha.set(
                MovingBlockRenderContext.alpha() != -1
                        ? -1
                        : SelectiveRenderingManager.getAlpha(blockState, pos));
    }

    @Inject(method = "tesselateBlock", at = @At("TAIL"), require = 1)
    private void selectiveRendering$endBlock(
            BlockQuadOutput output,
            float x,
            float y,
            float z,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState blockState,
            BlockStateModel model,
            long seed,
            CallbackInfo ci) {
        selectiveRendering$blockAlpha.set(-1);
    }

    @Inject(
            method = {"tesselateFlat", "tesselateAmbientOcclusion"},
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void selectiveRendering$tesselate(
            BlockQuadOutput output,
            float x,
            float y,
            float z,
            List<BlockStateModelPart> parts,
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos,
            CallbackInfo ci) {
        if (MovingBlockRenderContext.alpha() != -1) {
            return;
        }

        if (selectiveRendering$blockAlpha.get() == 0) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 1)
    private void selectiveRendering$shouldRenderFace(
            BlockAndTintGetter level,
            BlockState state,
            Direction direction,
            BlockPos neighbourPos,
            CallbackInfoReturnable<Boolean> cir) {
        if (SelectiveRenderingManager.getMode() == SelectiveRenderingManager.Mode.OFF) {
            return;
        }

        if (MovingBlockRenderContext.alpha() != -1) {
            return;
        }

        boolean hidden = selectiveRendering$blockAlpha.get() >= 0;

        BlockState neighbourState = level.getBlockState(neighbourPos);
        boolean neighbourHidden = SelectiveRenderingManager.isHidden(neighbourState, neighbourPos);

        if (hidden == neighbourHidden) {
            return;
        }

        cir.setReturnValue(true);
    }

    @Inject(method = "putQuadWithTint", at = @At("HEAD"), require = 1)
    private void selectiveRendering$putQuadWithTint(
            BlockQuadOutput output,
            float x,
            float y,
            float z,
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos,
            BakedQuad quad,
            CallbackInfo ci) {
        if (MovingBlockRenderContext.alpha() != -1) {
            return;
        }

        int alpha = selectiveRendering$blockAlpha.get();

        if (alpha > 0 && alpha < 255) {
            quadInstance.multiplyColor(ARGB.color(alpha, 255, 255, 255));
        }
    }

    @ModifyArg(
            method = "putQuadWithTint",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"),
            index = 3,
            require = 1)
    private BakedQuad selectiveRendering$adjustQuad(BakedQuad quad) {
        if (MovingBlockRenderContext.alpha() != -1) {
            return quad;
        }

        int alpha = selectiveRendering$blockAlpha.get();
        BakedQuad.MaterialInfo material = quad.materialInfo();
        if (alpha <= 0 || alpha >= 255 || material.layer() == ChunkSectionLayer.TRANSLUCENT) {
            return quad;
        }

        return new BakedQuad(
                quad.position0(),
                quad.position1(),
                quad.position2(),
                quad.position3(),
                quad.packedUV0(),
                quad.packedUV1(),
                quad.packedUV2(),
                quad.packedUV3(),
                quad.direction(),
                Platform.translucentMaterial(material));
    }
}
