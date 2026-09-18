package com.selectiverendering.mixin;

import com.selectiverendering.ModTranslations;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies this mod's translations when the resource manager does not have them.
 *
 * <p>{@link net.minecraft.locale.Language#getOrDefault(String)} delegates to the two argument
 * method, and that is the only place {@code TranslatableContents} reads translations from, so
 * answering here covers every key this mod renders. Arguments are still substituted by the game, so
 * the lang files keep their {@code %s} and {@code %%} as they are.</p>
 *
 * <p>Keys that are not ours return null from {@link ModTranslations} and fall through untouched.</p>
 */
@Mixin(ClientLanguage.class)
public class ClientLanguageMixin {
	@Inject(method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$getOrDefault(String key, String fallback, CallbackInfoReturnable<String> cir) {
		String translation = ModTranslations.get(key);
		if (translation != null) {
			cir.setReturnValue(translation);
		}
	}
}
