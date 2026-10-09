package com.selectiverendering.client.mixin.sodium;

import com.selectiverendering.client.BlockPosScratch;
import com.selectiverendering.client.BlockRenderContextAccess;
import com.selectiverendering.client.MovingBlockRenderContext;
import com.selectiverendering.client.SelectiveRenderingManager;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractBlockRenderContext.class, remap = false)
public class AbstractBlockRenderContextMixin implements BlockRenderContextAccess {
    @Shadow protected BlockAndTintGetter level;

    @Shadow protected BlockState state;

    @Shadow protected BlockPos pos;

    @Unique @Nullable private Integer selectiveRendering$alpha;

    @Override
    @Nullable
    public Integer selectiveRendering$alpha() {
        return this.selectiveRendering$alpha;
    }

    @Override
    public void selectiveRendering$setAlpha(@Nullable Integer alpha) {
        this.selectiveRendering$alpha = alpha;
    }

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private void selectiveRendering$shouldDrawSide(
            Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (SelectiveRenderingManager.getMode() == SelectiveRenderingManager.Mode.OFF) {
            return;
        }

        if (MovingBlockRenderContext.alpha() != -1) {
            return;
        }

        if (level == null || state == null || pos == null) {
            return;
        }

        Integer cached = this.selectiveRendering$alpha;
        boolean hidden =
                cached != null ? cached >= 0 : SelectiveRenderingManager.isHidden(state, pos);

        BlockPos neighbourPos = BlockPosScratch.offset(pos, direction);
        BlockState neighbourState = level.getBlockState(neighbourPos);
        boolean neighbourHidden = SelectiveRenderingManager.isHidden(neighbourState, neighbourPos);

        if (hidden == neighbourHidden) {
            return;
        }

        cir.setReturnValue(true);
    }
}
