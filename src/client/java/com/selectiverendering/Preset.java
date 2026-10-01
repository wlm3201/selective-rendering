package com.selectiverendering;

import com.selectiverendering.SelectiveRenderingManager.Mode;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个预设：把"模式 + 透明度 + 方块名单 + 选区"整套快照存下来，方便一键切换。
 *
 * <p>字段是 public 可变的，原因有两个：
 * <ul>
 *   <li>Gson 需要一个无参构造 + 直接读写字段（所以 {@code Preset()} 不能删）；</li>
 *   <li>{@code ModPresets.overwrite} 是<b>原地修改</b>列表里的那个对象再存盘的，
 *       靠的就是可变性。</li>
 * </ul>
 * 这是个偏脆弱的设计：预设对象同时存在于 {@code PRESETS} 列表和配置界面里，
 * 任何一处拿到引用都能改。若要重构，建议改成不可变 + 提供 copy-with。
 *
 * <p>注意：{@code rules}/{@code regions} 存的是<b>原始字符串</b>，不是解析后的对象——
 * 这样预设可以跨越"方块 id 尚未注册"的时机加载。
 */
public final class Preset {
	public String name;
	public Mode mode;
	public int transparency;
	/** 是否"反转"：淡化没命中的那一批。见 {@link SelectiveRenderingManager#isInvert()}。 */
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
			new ArrayList<>()
		);
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
