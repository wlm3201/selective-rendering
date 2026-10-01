package com.selectiverendering.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.SelectiveRenderingManager;
import net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sodium 版"平滑光照当空气"：对应原版的 {@code BlockModelLighterMixin}。
 *
 * <p>判定已经在 {@link SelectiveRenderingManager#airIfHidden} 里收口，
 * 所以这里只剩一行。同理的还有 FRAPI 的
 * {@code compat.FabricRendererApiLightMixin}——三者各自维护一套光照数据结构，
 * 没有公共切入点，只能各打一遍。
 *
 * <p>本文件之所以能放在共享源码里（而不是 {@code versions/}）：
 * Sodium 的类名与方法签名在 26.1.2 / 26.2 / 26.3 上完全一致，
 * 三份原本就是逐字相同的复制品。
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
		return SelectiveRenderingManager.airIfHidden(original.call(level, pos), pos);
	}
}
