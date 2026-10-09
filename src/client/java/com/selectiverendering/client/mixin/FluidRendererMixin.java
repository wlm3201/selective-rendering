package com.selectiverendering.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.client.BlockPosScratch;
import com.selectiverendering.client.SelectiveRenderingManager;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public class FluidRendererMixin {
    private static final ThreadLocal<Integer> ALPHA = ThreadLocal.withInitial(() -> -1);

    private static final ThreadLocal<BlockPos> POS = new ThreadLocal<>();

    private static final ThreadLocal<BlockGetter> LEVEL = new ThreadLocal<>();

    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void selectiveRendering$onTesselate(
            BlockAndTintGetter level,
            BlockPos pos,
            FluidRenderer.Output output,
            BlockState state,
            FluidState fluidState,
            CallbackInfo ci) {
        int alpha = SelectiveRenderingManager.getFluidAlpha(state, pos);
        ALPHA.set(alpha);
        POS.set(pos);
        LEVEL.set(level);

        if (alpha == 0) {
            ci.cancel();
            return;
        }
    }

    @WrapOperation(
            method = "tesselate",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/block/FluidRenderer;isFaceOccludedByNeighbor(Lnet/minecraft/core/Direction;FLnet/minecraft/world/level/block/state/BlockState;)Z"))
    private static boolean selectiveRendering$isFaceOccludedByNeighbor(
            Direction direction, float height, BlockState neighbour, Operation<Boolean> original) {
        if (!original.call(direction, height, neighbour)) {
            return false;
        }

        if (direction.getAxis().isVertical()) {
            return true;
        }

        BlockPos pos = POS.get();
        if (pos == null) {
            return true;
        }

        BlockPos neighbourPos = BlockPosScratch.offset(pos, direction);
        return !SelectiveRenderingManager.isHiddenIn(LEVEL.get(), neighbourPos);
    }

    @ModifyVariable(method = "vertex", at = @At("HEAD"), ordinal = 0)
    private int selectiveRendering$applyAlpha(int color) {
        int alpha = ALPHA.get();
        return alpha < 0 ? color : (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}
