package com.selectiverendering;

import com.selectiverendering.compat.Platform;
import com.selectiverendering.config.ModConfigs;

import java.util.List;

/**
 * 读取两个"修饰键"是否被按住，以及它们的显示名。
 *
 * <p>这两个键（默认 {@code 左Ctrl} = 选区键、{@code 左Alt} = 方块键）用来给魔杖的
 * 左右键赋予不同含义，见 {@code MouseHandlerMixin.onButton}：
 * <pre>
 *   无修饰：左键=角点1  右键=角点2
 *   选区键：左键=增删选区  右键=裁剪选区
 *   方块键：左键=增删方块  右键=增删带状态的方块
 * </pre>
 *
 * <p>它们被注册成 {@code KeybindSettings.MODIFIER_INGAME}，
 * 所以状态由 MaLiLib 维护，这里只是查询。
 */
public final class ModHotkeys {
	private ModHotkeys() {
	}

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
