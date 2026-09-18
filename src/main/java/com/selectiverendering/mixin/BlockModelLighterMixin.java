package com.selectiverendering.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stops a hidden block from shading its neighbours.
 *
 * <p>Smooth lighting looks at the blocks around a face and darkens it for every one of them that
 * is solid, which is what puts a shadow in a corner. A block the mod is drawing see-through is
 * still solid as far as the world is concerned, so it keeps casting that shadow over exactly the
 * faces it now lets you see. Answering air for the hidden ones leaves their corners unshaded, the
 * same as if the block were not there, and the light value they are read for comes from
 * {@code LightPass}.</p>
 */
@Mixin(BlockModelLighter.class)
public class BlockModelLighterMixin {
	@WrapOperation(
		method = "prepareQuadAmbientOcclusion",
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
}
