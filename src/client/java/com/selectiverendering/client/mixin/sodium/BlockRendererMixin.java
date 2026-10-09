package com.selectiverendering.client.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.selectiverendering.client.BlockRenderContextAccess;
import com.selectiverendering.client.SelectiveRenderingManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockRenderer.class, remap = false)
public class BlockRendererMixin {
    @Unique @Nullable private BlockRenderContextAccess selectiveRendering$context;

    @WrapMethod(method = "renderModel")
    private void selectiveRendering$renderModel(
            BlockStateModel model,
            BlockState state,
            BlockPos pos,
            BlockPos origin,
            Operation<Void> original) {
        BlockRenderContextAccess context = this.selectiveRendering$context();
        int alpha = SelectiveRenderingManager.getAlpha(state, pos);

        context.selectiveRendering$setAlpha(alpha);

        try {
            if (alpha != 0) {
                original.call(model, state, pos, origin);
            }
        } finally {
            context.selectiveRendering$setAlpha(null);
        }
    }

    @Unique
    private BlockRenderContextAccess selectiveRendering$context() {
        BlockRenderContextAccess context = this.selectiveRendering$context;
        if (context == null) {
            context = (BlockRenderContextAccess) (Object) this;
            this.selectiveRendering$context = context;
        }

        return context;
    }

    @Inject(method = "bufferQuad", at = @At("HEAD"))
    private void selectiveRendering$onBufferQuad(
            MutableQuadViewImpl quad, float[] brightnesses, Material material, CallbackInfo ci) {
        int alpha = this.selectiveRendering$alpha();
        if (alpha <= 0 || alpha >= 255) {
            return;
        }

        for (int i = 0; i < 4; i++) {
            int color = quad.getColor(i);
            quad.setColor(i, ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF));
        }
    }

    @ModifyArg(
            method = "processQuad",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;bufferQuad(Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V"),
            index = 2)
    private Material selectiveRendering$useTranslucentMaterial(Material material) {
        int alpha = this.selectiveRendering$alpha();
        return alpha > 0 && alpha < 255 ? DefaultMaterials.TRANSLUCENT : material;
    }

    @Unique
    private int selectiveRendering$alpha() {
        Integer alpha = this.selectiveRendering$context().selectiveRendering$alpha();
        return alpha == null ? -1 : alpha;
    }
}
