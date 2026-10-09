package com.selectiverendering.client;

import com.selectiverendering.client.compat.Platform;
import com.selectiverendering.client.config.ModConfigs;
import java.util.List;

public final class ModHotkeys {
    private ModHotkeys() {}

    public static boolean regionHeld() {
        return ModConfigs.REGION_KEY.getKeybind().isKeybindHeld();
    }

    public static boolean blocksHeld() {
        return ModConfigs.BLOCKS_KEY.getKeybind().isKeybindHeld();
    }

    public static String regionKeyName() {
        var keybind = ModConfigs.REGION_KEY.getKeybind();
        return names(keybind.getKeys(), keybind.getKeysDisplayString());
    }

    public static String blocksKeyName() {
        var keybind = ModConfigs.BLOCKS_KEY.getKeybind();
        return names(keybind.getKeys(), keybind.getKeysDisplayString());
    }

    private static String names(List<Integer> keys, String fallback) {
        if (keys.isEmpty()) {
            return fallback;
        }

        StringBuilder names = new StringBuilder();

        for (int key : keys) {
            if (names.length() > 0) {
                names.append(" + ");
            }

            names.append(key < 0 ? fallback : Platform.keyName(key));
        }

        return names.toString();
    }
}
