package com.selectiverendering.mixin.lighting;

import com.selectiverendering.lighting.LightSuppression;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 推进光照重算用的帧号。
 *
 * <p>{@code ClientLevel.update()} 是先 {@code pollLightUpdates()} 再 {@code runLightUpdates()}，
 * 重算任务跑在前者里，因此每帧最多做一批，不会在同一帧里被反复执行。
 * 这里不再直接重算：重算任务由 {@link LightSuppression} 自己通过
 * {@code ClientLevel.queueLightUpdate} 排队，避免在光照引擎的更新点里同步灌入成千上万个
 * {@code checkBlock}。
 */
@Mixin(LevelLightEngine.class)
public class AreaLightFlushMixin {
	@Inject(method = "runLightUpdates", at = @At("RETURN"))
	private void selectiveRendering$endFrame(CallbackInfoReturnable<Integer> cir) {
		LightSuppression.endFrame();
	}
}
