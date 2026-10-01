package com.selectiverendering.config;

import java.util.List;
import java.util.Locale;

import com.selectiverendering.SelectiveRenderingManager.RecordApplyMode;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;

/**
 * 把 {@link RecordApplyMode} 包装成 MaLiLib 认识的下拉框选项。
 *
 * <p>和 {@link RecordModeOption} 是一对双胞胎，只是枚举不同。
 */
public final class RecordApplyModeOption implements IConfigOptionListEntry {
	private static final RecordApplyModeOption[] VALUES = values();

	private final RecordApplyMode mode;

	private RecordApplyModeOption(RecordApplyMode mode) {
		this.mode = mode;
	}

	private static RecordApplyModeOption[] values() {
		RecordApplyMode[] modes = RecordApplyMode.values();
		RecordApplyModeOption[] options = new RecordApplyModeOption[modes.length];

		for (int index = 0; index < modes.length; index++) {
			options[index] = new RecordApplyModeOption(modes[index]);
		}

		return options;
	}

	public static RecordApplyModeOption of(RecordApplyMode mode) {
		return mode == null ? VALUES[RecordApplyMode.ADD.ordinal()] : VALUES[mode.ordinal()];
	}

	public static List<RecordApplyModeOption> all() {
		return List.of(VALUES);
	}

	public RecordApplyMode applyMode() {
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
		RecordApplyMode[] modes = RecordApplyMode.values();
		int next = Math.floorMod(this.mode.ordinal() + (forward ? 1 : -1), modes.length);

		return of(modes[next]);
	}

	@Override
	public IConfigOptionListEntry fromString(String value) {
		if (value != null) {
			for (RecordApplyModeOption option : VALUES) {
				if (option.mode.name().equalsIgnoreCase(value.trim().toUpperCase(Locale.ROOT))) {
					return option;
				}
			}
		}

		return of(RecordApplyMode.ADD);
	}
}
