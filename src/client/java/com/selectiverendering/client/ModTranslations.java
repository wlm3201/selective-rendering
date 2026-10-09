package com.selectiverendering.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

public final class ModTranslations {
    private static final String PATH = "/assets/" + SelectiveRendering.MOD_ID + "/lang/";
    private static final String DEFAULT = "zh_cn";
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Gson GSON = new Gson();

    private static final Map<String, Map<String, String>> BY_CODE = new ConcurrentHashMap<>();

    private ModTranslations() {}

    public static String get(String key) {
        return byCode(currentCode()).get(key);
    }

    private static String currentCode() {
        String selected = selected();
        return selected == null || selected.isBlank() ? DEFAULT : selected;
    }

    private static Map<String, String> byCode(String code) {
        return BY_CODE.computeIfAbsent(
                code,
                wanted -> {
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
            return minecraft == null || minecraft.getLanguageManager() == null
                    ? null
                    : minecraft.getLanguageManager().getSelected();
        } catch (Exception e) {
            return null;
        }
    }
}
