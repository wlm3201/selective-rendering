package com.selectiverendering.mixin.render;

import com.selectiverendering.render.information.WandHudRenderer;
import net.minecraft.client.DeltaTracker;
//? if <26.2 {
/*import net.minecraft.client.gui.Gui;
*///?}
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 HUD 状态提取后绘制魔杖提示。 */
//? if >=26.2 {
@Mixin(Hud.class)
//?} else {
/*@Mixin(Gui.class)
*///?}
public class GuiMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void selectiveRendering$renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		WandHudRenderer.render(graphics);
	}
}
