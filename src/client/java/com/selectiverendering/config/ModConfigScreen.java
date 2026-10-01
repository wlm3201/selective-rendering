package com.selectiverendering.config;

import com.selectiverendering.compat.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 配置界面的打开入口：ModMenu（{@code ModMenuIntegration}）、
 * MaLiLib 的屏幕工厂、以及打开配置的快捷键回调，都汇聚到这里。
 *
 * <p>另外提供两个取"按键显示名"的静态方法给 HUD 提示用。
 */
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
