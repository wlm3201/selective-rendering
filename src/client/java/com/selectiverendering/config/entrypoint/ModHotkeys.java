package com.selectiverendering.config.entrypoint;

import com.selectiverendering.compat.Platform;

import java.util.List;

/** 读取魔杖修饰键的状态和显示名。 */
public final class ModHotkeys {
	private ModHotkeys() {
	}

	public static boolean regionHeld() {
		return ModConfigs.REGION_KEY.getKeybind().isKeybindHeld();
	}

	public static boolean operationHeld() {
		return ModConfigs.OPERATION_KEY.getKeybind().isKeybindHeld();
	}

	public static String regionKeyName() {
		var keybind = ModConfigs.REGION_KEY.getKeybind();
		return names(keybind.getKeys(), keybind.getKeysDisplayString());
	}

	public static String operationKeyName() {
		var keybind = ModConfigs.OPERATION_KEY.getKeybind();
		return names(keybind.getKeys(), keybind.getKeysDisplayString());
	}

	private static String names(List<Integer> keys, String fallback) {
		if (keys.isEmpty()) {
			return fallback;
		}

		StringBuilder names = new StringBuilder();

		for (int key : keys) {
			if (!names.isEmpty()) {
				names.append(" + ");
			}

			names.append(key < 0 ? fallback : Platform.keyName(key));
		}

		return names.toString();
	}
}
