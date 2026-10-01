package com.selectiverendering.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * FRAPI 版"AO 计算当空气"：对应原版 {@code BlockModelLighterMixin}，逻辑一致。
 * 三份光照补丁之一，详见 {@code LightDataAccessMixin} 的注释。
 */
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator", remap = false)
public class FabricRendererApiLightMixin {
	@WrapOperation(
		method = "computeFace(Lnet/fabricmc/fabric/impl/client/indigo/renderer/aocalc/AoFaceData;Lnet/minecraft/core/Direction;ZZ)V",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/BlockAndTintGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
		),
		remap = false,
		require = 0
	)
	private static BlockState selectiveRendering$hideBlock(
		BlockAndTintGetter level,
		BlockPos pos,
		Operation<BlockState> original
	) {
		return SelectiveRenderingManager.airIfHidden(original.call(level, pos), pos);
	}
}
