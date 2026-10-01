package com.selectiverendering.config;

import java.util.List;
import java.util.Locale;

import com.selectiverendering.SelectiveRenderingManager.Mode;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

/**
 * 把 {@link Mode} 包装成 MaLiLib 认识的下拉框选项（{@code IConfigOptionListEntry}）。
 *
 * <p>每个枚举值对应一个常量实例（这里手写 {@code values()} 是为了避免
 * 在静态初始化里引用自身的构造顺序问题），
 * {@code getStringValue()} 存进配置的就是枚举名。
 */
public final class ModeOption implements IConfigOptionListEntry {
	private static final ModeOption[] VALUES = values();

	private final Mode mode;

	private ModeOption(Mode mode) {
		this.mode = mode;
	}

	private static ModeOption[] values() {
		Mode[] modes = Mode.values();
		ModeOption[] options = new ModeOption[modes.length];

		for (int index = 0; index < modes.length; index++) {
			options[index] = new ModeOption(modes[index]);
		}

		return options;
	}

	public static ModeOption of(Mode mode) {
		return mode == null ? VALUES[Mode.OFF.ordinal()] : VALUES[mode.ordinal()];
	}

	public static List<ModeOption> all() {
		return List.of(VALUES);
	}

	public Mode mode() {
		return this.mode;
	}

	@Override
	public String getStringValue() {
		return this.mode.name();
	}

	@Override
	public String getDisplayName() {
		return this.mode.displayName().getString();
	}

	@Override
	public IConfigOptionListEntry cycle(boolean forward) {
		return of(this.mode.cycle(forward ? 1 : -1));
	}

	@Override
	public IConfigOptionListEntry fromString(String value) {
		if (value != null) {
			for (ModeOption option : VALUES) {
				if (option.mode.name().equalsIgnoreCase(value.trim().toUpperCase(Locale.ROOT))) {
					return option;
				}
			}
		}

		return of(Mode.OFF);
	}
}
