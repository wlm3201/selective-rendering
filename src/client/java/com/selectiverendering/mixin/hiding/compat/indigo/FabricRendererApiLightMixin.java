package com.selectiverendering.mixin.hiding.compat.indigo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.lighting.LightSuppression;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 在 Indigo 的 AO 计算中将隐藏方块视为空气。 */
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
		return LightSuppression.airIfHidden(original.call(level, pos), pos);
	}
}
