package com.selectiverendering.mixin;

import com.selectiverendering.ModTranslations;
import com.selectiverendering.SelectiveRendering;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 劫持 Minecraft 的翻译查询，让 Mod（以及 MaLiLib 界面）能读到我们自带的词条。
 *
 * <p>详见 {@link ModTranslations} 的类注释，特别是"强制中文"和"全局劫持"两个副作用。
 *
 * <p>这里选的是 {@code getOrDefault(String, String)} 这个重载，
 * 它是 {@code ClientLanguage} 里最靠下的兜底方法，基本所有查询都会流经它。
 */
@Mixin(ClientLanguage.class)
public class ClientLanguageMixin {
	/** 只处理本 Mod 自己的词条；原版/MOD 的其它翻译一律不管。 */
	private static final String PREFIX = SelectiveRendering.MOD_ID + ".";

	@Inject(method = "getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$getOrDefault(String key, String fallback, CallbackInfoReturnable<String> cir) {
		// 这个入口每帧被调用成千上万次（所有原版文本都会流经它），
		// 所以先把不属于本 Mod 的 key 挡掉，避免无谓的 map 查找。
		if (key == null || !key.startsWith(PREFIX)) {
			return;
		}

		String translation = ModTranslations.get(key);
		if (translation != null) {
			cir.setReturnValue(translation);
		}
	}
}
