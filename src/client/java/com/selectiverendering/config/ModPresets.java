package com.selectiverendering.config;

import java.util.ArrayList;
import java.util.List;

import com.selectiverendering.Preset;
import com.selectiverendering.SelectiveRenderingManager;

/**
 * 预设的增删改：把"当前所有设置"拍一张快照存起来，之后一键恢复。
 *
 * <p>{@link #overwrite} 的实现比较特殊：它<b>原地修改</b>列表里那个 {@link Preset} 对象
 * （因为配置界面持有的是同一个引用），改完只调 {@code savePresets()} 落盘。
 * 依赖了 {@code Preset} 的可变性，改动前注意。
 */
public final class ModPresets {
	private ModPresets() {
	}

	/** 用当前设置新建一个预设，名字自动取 "预设 1 / 预设 2 …"（跳过已占用的）。 */
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
