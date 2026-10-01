package com.selectiverendering;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自带翻译表：直接读 {@code assets/selective_rendering/lang/*.json}，
 * 再通过 {@code ClientLanguageMixin} 覆盖 {@code ClientLanguage.getOrDefault}。
 *
 * <h2>为什么要自己搞一套，而不是用 {@code fabric.mod.json} 的语言文件</h2>
 * <p>MaLiLib 的配置项名称/注释不走 Minecraft 的语言系统，
 * 它自己调 {@code StringUtils.translate(...)} → 最终落到 {@code ClientLanguage}。
 * 想让 MaLiLib 的界面显示中文，只能从 {@code ClientLanguage} 这一层劫持。
 *
 * <h2>⚠ 两个要注意的行为</h2>
 * <ol>
 *   <li><b>中文兜底</b>：{@link #byCode} 先读 {@code zh_cn}，再用目标语言覆盖。
 *       所以只有自带文件的语言（目前 zh_cn / en_us）才是"完整翻译"，
 *       其它语言会看到中文。</li>
 *   <li><b>全局劫持，但已收窄</b>：{@code getOrDefault} 是 Minecraft 所有翻译的统一入口，
 *       每帧被调用成千上万次。{@code ClientLanguageMixin} 会先按
 *       {@code selective_rendering.} 前缀过滤，不属于本 Mod 的 key 直接放行，
 *       所以热路径上的开销只是一次 {@code startsWith}。</li>
 * </ol>
 */
public final class ModTranslations {
	private static final String PATH = "/assets/" + SelectiveRendering.MOD_ID + "/lang/";
	private static final String DEFAULT = "zh_cn";
	private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
	private static final Gson GSON = new Gson();

	private static final Map<String, Map<String, String>> BY_CODE = new ConcurrentHashMap<>();

	private ModTranslations() {
	}

	public static String get(String key) {
		return byCode(currentCode()).get(key);
	}

	private static String currentCode() {
		String selected = selected();
		return selected == null || selected.isBlank() ? DEFAULT : selected;
	}

	private static Map<String, String> byCode(String code) {
		return BY_CODE.computeIfAbsent(code, wanted -> {
			Map<String, String> result = new HashMap<>();
			read(result, DEFAULT);

			if (!wanted.equalsIgnoreCase(DEFAULT)) {
				read(result, wanted);
			}

			Log.say("[state] lang {} ({} keys)", wanted, result.size());
			return result;
		});
	}

	private static void read(Map<String, String> into, String code) {
		try (InputStream in = ModTranslations.class.getResourceAsStream(PATH + code + ".json")) {
			if (in == null) {
				return;
			}

			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				Map<String, String> parsed = GSON.fromJson(reader, MAP_TYPE);
				if (parsed != null) {
					into.putAll(parsed);
				}
			}
		} catch (Exception e) {
			Log.error("Could not read {} lang file", code, e);
		}
	}

	private static String selected() {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			return minecraft == null || minecraft.getLanguageManager() == null ? null : minecraft.getLanguageManager().getSelected();
		} catch (Exception e) {
			return null;
		}
	}
}
