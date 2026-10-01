package com.selectiverendering.config;

import java.util.List;
import java.util.Locale;

import com.selectiverendering.SelectiveRenderingManager.RecordMode;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

/**
 * 记录模式的下拉框选项，和 {@link ModeOption} 是一对双胞胎，只是枚举不同。
 * （两份代码几乎逐行相同，可以抽一个泛型基类，但 MaLiLib 的接口签名限制较多，先留着。）
 */
public final class RecordModeOption implements IConfigOptionListEntry {
	private static final RecordModeOption[] VALUES = values();

	private final RecordMode mode;

	private RecordModeOption(RecordMode mode) {
		this.mode = mode;
	}

	private static RecordModeOption[] values() {
		RecordMode[] modes = RecordMode.values();
		RecordModeOption[] options = new RecordModeOption[modes.length];

		for (int index = 0; index < modes.length; index++) {
			options[index] = new RecordModeOption(modes[index]);
		}

		return options;
	}

	public static RecordModeOption of(RecordMode mode) {
		return mode == null ? VALUES[RecordMode.UNLISTED.ordinal()] : VALUES[mode.ordinal()];
	}

	public static List<RecordModeOption> all() {
		return List.of(VALUES);
	}

	public RecordMode mode() {
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
		RecordMode[] modes = RecordMode.values();
		int next = Math.floorMod(this.mode.ordinal() + (forward ? 1 : -1), modes.length);

		return of(modes[next]);
	}

	@Override
	public IConfigOptionListEntry fromString(String value) {
		if (value != null) {
			for (RecordModeOption option : VALUES) {
				if (option.mode.name().equalsIgnoreCase(value.trim().toUpperCase(Locale.ROOT))) {
					return option;
				}
			}
		}

		return of(RecordMode.UNLISTED);
	}
}
