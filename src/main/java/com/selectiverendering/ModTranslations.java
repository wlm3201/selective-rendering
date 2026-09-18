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
 * This mod's lang files, read straight off the class path.
 *
 * <p>Under Fabric Loader 0.19.5 on 26.1.2 the mod resource pack is missing from the resource
 * manager, the log says {@code Reloading ResourceManager: vanilla}, so nothing under
 * {@code assets/selective_rendering/lang} is ever loaded and every key reaches the screen as
 * itself. This reads the same files from the class path instead, so they stay the single source of
 * truth. {@code ClientLanguageMixin} hands the result to the game's own translator.</p>
 *
 * <p>Once the resource pack is loaded again this changes nothing, the values are the same, and any
 * key that is not ours simply is not in the map.</p>
 *
 * <p>One table per language code, built the first time that code is asked for. Asking the game on
 * every lookup rather than caching once matters at start up: a key can be wanted before the option
 * exists, and a table built then would be English for the rest of the session. Keeping the code it
 * was built for means the first real answer replaces it, and switching language in the options
 * takes effect without a restart.</p>
 */
public final class ModTranslations {
	private static final String PATH = "/assets/" + SelectiveRendering.MOD_ID + "/lang/";
	private static final String DEFAULT = "en_us";
	private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
	private static final Gson GSON = new Gson();

	private static final Map<String, Map<String, String>> BY_CODE = new ConcurrentHashMap<>();

	private ModTranslations() {
	}

	/**
	 * @return the translation for a key of this mod, or null when the key is not ours.
	 */
	public static String get(String key) {
		return byCode(currentCode()).get(key);
	}

	/**
	 * The game's answer, or the default while it has none to give.
	 */
	private static String currentCode() {
		String selected = selected();
		return selected == null || selected.isBlank() ? DEFAULT : selected;
	}

	/**
	 * English first so a language that only translates some keys still shows text for the rest.
	 */
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

	/**
	 * The language the game is set to. Null during start up, before the option exists, which just
	 * means we fall back to the default.
	 */
	private static String selected() {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			return minecraft == null || minecraft.getLanguageManager() == null ? null : minecraft.getLanguageManager().getSelected();
		} catch (Exception e) {
			return null;
		}
	}
}
