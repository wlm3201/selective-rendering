package com.selectiverendering.client.mixin;

import com.selectiverendering.client.ModTranslations;
import com.selectiverendering.client.SelectiveRendering;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLanguage.class)
public class ClientLanguageMixin {
    private static final String PREFIX = SelectiveRendering.MOD_ID + ".";

    @Inject(
            method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true)
    private void selectiveRendering$getOrDefault(
            String key, String fallback, CallbackInfoReturnable<String> cir) {
        if (key == null || !key.startsWith(PREFIX)) {
            return;
        }

        String translation = ModTranslations.get(key);
        if (translation != null) {
            cir.setReturnValue(translation);
        }
    }
}
