package com.selectiverendering.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 平滑光照（AO）计算时同样把隐藏方块当空气。
 *
 * <p>和 {@code LightEngineMixin} 目的一致，只是作用在不同的量上：
 * 这里影响的是方块表面四个角的<b>环境光遮蔽</b>强度。
 * 不处理的话，被隐藏方块旁边会留下一圈不自然的暗角。
 *
 * <p>Sodium 与 FRAPI(Indigo) 各有自己的一套 AO 实现，
 * 对应的补丁分别在 {@code sodium.LightDataAccessMixin} 和
 * {@code compat.FabricRendererApiLightMixin}，逻辑完全相同。
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
		return SelectiveRenderingManager.airIfHidden(original.call(level, pos), pos);
	}
}
