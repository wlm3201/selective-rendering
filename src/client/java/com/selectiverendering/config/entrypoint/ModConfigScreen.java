package com.selectiverendering.config.entrypoint;

import com.selectiverendering.compat.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** 提供配置界面的打开入口和按键显示名。 */
public final class ModConfigScreen {
	private ModConfigScreen() {
	}

	public static void open(Minecraft minecraft) {
		Platform.setScreen(minecraft, create(Platform.screen(minecraft)));
	}

	public static Screen create(Screen parent) {
		ModConfigs.syncFromManager();
		return new ModConfigGui(parent);
	}

	public static String configKeyName() {
		String keys = ModConfigs.OPEN_CONFIG_GUI.getKeybind().getKeysDisplayString();
		return keys == null || keys.isBlank() ? null : keys;
	}

	public static String recordKeyName() {
		String keys = ModConfigs.RECORD_KEY.getKeybind().getKeysDisplayString();
		return keys == null || keys.isBlank() ? null : keys;
	}

	public static void register() {
		ModConfigs.init();
	}
}
