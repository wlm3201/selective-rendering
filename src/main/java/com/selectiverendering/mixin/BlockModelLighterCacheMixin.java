package com.selectiverendering.mixin;

import com.selectiverendering.LightPass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.BlockModelLighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Routes every light lookup the block renderer makes through {@link LightPass}.
 *
 * <p>The vanilla renderer asks one object for the light of a position, and everything it draws goes
 * through it, so swapping that one object covers the flat path and the smooth one at once. The
 * question itself is unchanged for anything that is not hidden.</p>
 */
@Mixin(BlockModelLighter.Cache.class)
public class BlockModelLighterCacheMixin {
	@ModifyArg(
		method = "getLightCoords",
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
