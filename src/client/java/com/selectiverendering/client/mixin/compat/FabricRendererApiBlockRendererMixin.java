package com.selectiverendering.client.mixin.compat;

import com.selectiverendering.client.BlockPosScratch;
import com.selectiverendering.client.MovingBlockRenderContext;
import com.selectiverendering.client.SelectiveRenderingManager;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
        targets =
                "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl",
        remap = false)
public class FabricRendererApiBlockRendererMixin {
    @Unique private int selectiveRendering$alpha = -1;

    @Unique private BlockAndTintGetter selectiveRendering$level;

    @Unique private BlockPos selectiveRendering$pos;

    @Unique private BlockState selectiveRendering$blockState;

    @Inject(
            method = "tesselateBlock",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void selectiveRendering$beforeTesselateBlock(
            QuadEmitter output,
            float x,
            float y,
            float z,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState blockState,
            BlockStateModel model,
            long seed,
            CallbackInfo ci) {
        selectiveRendering$level = level;
        selectiveRendering$pos = pos;
        selectiveRendering$blockState = blockState;
        selectiveRendering$alpha = SelectiveRenderingManager.getAlpha(blockState, pos);

        if (selectiveRendering$alpha == 0) {
            ci.cancel();
        }
    }

    @Inject(method = "tesselateBlock", at = @At("TAIL"), remap = false, require = 0)
    private void selectiveRendering$afterTesselateBlock(CallbackInfo ci) {
        selectiveRendering$alpha = -1;
        selectiveRendering$level = null;
        selectiveRendering$pos = null;
        selectiveRendering$blockState = null;
    }

    @Inject(method = "transform", at = @At("RETURN"), remap = false, require = 0)
    private void selectiveRendering$onTransform(
            MutableQuadView quad, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }

        int alpha = selectiveRendering$alpha;
        if (alpha <= 0 || alpha >= 255) {
            return;
        }

        if (MovingBlockRenderContext.alpha() != -1) {
            return;
        }

        if (quad.chunkLayer() != ChunkSectionLayer.TRANSLUCENT) {
            quad.chunkLayer(ChunkSectionLayer.TRANSLUCENT);
        }

        quad.multiplyColor(ARGB.color(alpha, 255, 255, 255));
    }

    @Inject(
            method = "shouldCullFace",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void selectiveRendering$onShouldCullFace(
            Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (direction == null
                || SelectiveRenderingManager.getMode() == SelectiveRenderingManager.Mode.OFF) {
            return;
        }

        BlockAndTintGetter level = selectiveRendering$level;
        BlockPos pos = selectiveRendering$pos;
        BlockState blockState = selectiveRendering$blockState;
        if (level == null || pos == null || blockState == null) {
            return;
        }

        boolean hidden = selectiveRendering$alpha >= 0;

        BlockPos neighbourPos = BlockPosScratch.offset(pos, direction);
        boolean neighbourHidden =
                SelectiveRenderingManager.isHidden(level.getBlockState(neighbourPos), neighbourPos);

        if (hidden != neighbourHidden) {
            cir.setReturnValue(false);
        }
    }
}
