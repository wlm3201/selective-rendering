package com.selectiverendering.client.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.client.SelectiveRenderingManager;
import net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LightDataAccess.class, remap = false)
public class LightDataAccessMixin {
    @WrapOperation(
            method = "compute",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private static BlockState selectiveRendering$hideBlock(
            BlockAndTintGetter level, BlockPos pos, Operation<BlockState> original) {
        return SelectiveRenderingManager.airIfHidden(original.call(level, pos), pos);
    }
}
