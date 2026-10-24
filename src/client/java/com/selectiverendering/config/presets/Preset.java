package com.selectiverendering.config.presets;

import com.selectiverendering.SelectiveRenderingManager.Mode;
import com.selectiverendering.SelectiveRenderingManager;

import java.util.ArrayList;
import java.util.List;

/** 保存一组可恢复的渲染设置；界面与预设列表共享可变对象。 */
public final class Preset {
	public String name;
	public Mode mode;
	public int transparency;
	public boolean invert;
	public List<String> rules;
	public List<String> regions;

	public Preset() {
		this(
			"",
			SelectiveRenderingManager.DEFAULT_MODE,
			SelectiveRenderingManager.DEFAULT_TRANSPARENCY,
			SelectiveRenderingManager.DEFAULT_INVERT,
			new ArrayList<>(),
			new ArrayList<>());
	}

	private Preset(String name, Mode mode, int transparency, boolean invert, List<String> rules, List<String> regions) {
		this.name = name == null ? "" : name;
		this.mode = mode == null ? SelectiveRenderingManager.DEFAULT_MODE : mode;
		this.transparency = transparency;
		this.invert = invert;
		this.rules = rules == null ? new ArrayList<>() : new ArrayList<>(rules);
		this.regions = regions == null ? new ArrayList<>() : new ArrayList<>(regions);
	}

	public static Preset of(String name, Mode mode, int transparency, boolean invert, List<String> rules, List<String> regions) {
		return new Preset(name, mode, transparency, invert, rules, regions);
	}

	public String name() {
		return name == null ? "" : name;
	}

	public String displayName() {
		return name().isBlank() ? "-" : name();
	}

	public String summary() {
		Mode shown = mode == null ? SelectiveRenderingManager.DEFAULT_MODE : mode;

		return shown.displayName().getString()
			+ (invert ? " ↺" : "")
			+ " · " + transparency + "%"
			+ " · " + (rules == null ? 0 : rules.size())
			+ " · " + (regions == null ? 0 : regions.size());
	}
}
