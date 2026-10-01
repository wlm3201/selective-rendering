package com.selectiverendering.mixin;

import com.selectiverendering.WandHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD 覆盖层的注入点（26.2 版）。
 *
 * <p>只负责把 {@code Hud.extractRenderState} 末尾交给 {@link WandHud}，
 * 全部绘制逻辑在共享的 {@link WandHud} 里，26.1.2 / 26.3 的 {@code GuiMixin} 与之逐字相同。
 *
 * <p>26.2 起 HUD 从 {@code Gui} 拆成了 {@code Hud}，但注入点方法名 {@code extractRenderState} 没变。
 */
@Mixin(Hud.class)
public class GuiMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void selectiveRendering$renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		WandHud.render(graphics);
	}
}
