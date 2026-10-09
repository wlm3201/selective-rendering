package com.selectiverendering.client.mixin;

import com.selectiverendering.client.WandHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class GuiMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void selectiveRendering$renderOverlay(
            GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        WandHud.render(graphics);
    }
}
