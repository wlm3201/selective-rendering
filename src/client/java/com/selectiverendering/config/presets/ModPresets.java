package com.selectiverendering.config.presets;

import java.util.ArrayList;
import java.util.List;

import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.config.entrypoint.ModConfigs;

/** 创建、覆盖、重命名和删除设置预设。 */
public final class ModPresets {
	private ModPresets() {
	}

	public static void create() {
		List<Preset> presets = new ArrayList<>(SelectiveRenderingManager.presets());
		presets.add(capture(nextName(presets)));
		SelectiveRenderingManager.setPresets(presets);
	}

	public static void apply(Preset preset) {
		SelectiveRenderingManager.applyPreset(preset);
		ModConfigs.syncFromManager();
	}

	public static void overwrite(Preset preset) {
		Preset current = capture(preset.name());
		preset.mode = current.mode;
		preset.transparency = current.transparency;
		preset.invert = current.invert;
		preset.rules = current.rules;
		preset.regions = current.regions;

		SelectiveRenderingManager.savePresets();
	}

	public static void remove(Preset preset) {
		List<Preset> presets = new ArrayList<>(SelectiveRenderingManager.presets());
		presets.remove(preset);
		SelectiveRenderingManager.setPresets(presets);
	}

	public static Preset capture(String name) {
		return Preset.of(
			name,
			SelectiveRenderingManager.getMode(),
			SelectiveRenderingManager.getTransparency(),
			SelectiveRenderingManager.isInvert(),
			SelectiveRenderingManager.ruleSources(),
			SelectiveRenderingManager.regionSources()
		);
	}

	private static String nextName(List<Preset> presets) {
		String base = ModConfigs.text("presets.new");

		for (int index = 1; index <= presets.size() + 1; index++) {
			String candidate = base + " " + index;
			if (isFree(presets, candidate)) {
				return candidate;
			}
		}

		return base;
	}

	private static boolean isFree(List<Preset> presets, String name) {
		for (Preset preset : presets) {
			if (preset.name().equalsIgnoreCase(name)) {
				return false;
			}
		}

		return true;
	}
}
