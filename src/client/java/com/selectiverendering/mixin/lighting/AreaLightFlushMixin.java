package com.selectiverendering.mixin.lighting;

import com.selectiverendering.lighting.LightSuppression;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在光照数据落地后处理客户端待重算区块。 */
@Mixin(LevelLightEngine.class)
public class AreaLightFlushMixin {
	@Inject(method = "checkBlock", at = @At("HEAD"))
	private void selectiveRendering$invalidateSource(BlockPos pos, CallbackInfo ci) {
		LightSuppression.invalidateColumn(pos.getX(), pos.getZ());
	}

	@Inject(method = "runLightUpdates", at = @At("RETURN"))
	private void selectiveRendering$flushRelight(CallbackInfoReturnable<Integer> cir) {
		LightSuppression.flush((LevelLightEngine) (Object) this);
	}
}
