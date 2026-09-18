package com.selectiverendering.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.LightPass;
import com.selectiverendering.SelectiveRenderingManager;
import net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The same two answers as the vanilla hooks, given to Sodium's own light pipeline.
 *
 * <p>Sodium works out the light and the shading of a position once and keeps the result, so a
 * hidden block has to be answered for twice over: as air, so it stops counting as an occluder and
 * stops being given the dark shade a solid block gets, and through {@code LightPass}, so the value
 * that is kept for it is the one the faces behind it can be lit by.</p>
 */
@Mixin(value = LightDataAccess.class, remap = false)
public class LightDataAccessMixin {
	@WrapOperation(
		method = "compute",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
		)
	)
	private static BlockState selectiveRendering$hideBlock(
		BlockAndTintGetter level,
		BlockPos pos,
		Operation<BlockState> original
	) {
		if (!SelectiveRenderingManager.isHidden(level, pos)) {
			return original.call(level, pos);
		}

		return Blocks.AIR.defaultBlockState();
	}

	@ModifyArg(
		method = "compute",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;getLightCoords(Lnet/minecraft/client/renderer/LevelRenderer$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I"
		),
		index = 0
	)
	private static LevelRenderer.BrightnessGetter selectiveRendering$lightPass(LevelRenderer.BrightnessGetter original) {
		return LightPass.GETTER;
	}
}
