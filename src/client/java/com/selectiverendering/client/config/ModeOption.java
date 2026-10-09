package com.selectiverendering.client.config;

import com.selectiverendering.client.SelectiveRenderingManager.Mode;
import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import java.util.List;
import java.util.Locale;

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
