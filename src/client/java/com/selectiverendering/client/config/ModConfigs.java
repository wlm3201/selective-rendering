package com.selectiverendering.client.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.selectiverendering.client.BlockChangeRecorder;
import com.selectiverendering.client.ModConfig;
import com.selectiverendering.client.SelectiveRendering;
import com.selectiverendering.client.SelectiveRenderingManager;
import com.selectiverendering.client.SelectiveRenderingManager.Mode;
import com.selectiverendering.client.SelectiveRenderingManager.RecordMode;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.data.ModInfo;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ModConfigs {
    private static final String KEY = SelectiveRendering.MOD_ID + ".config";

    private static final String MOD_NAME = "Selective Rendering";

    private static final String HOTKEY_CATEGORY = "Hotkeys";

    public static final OpenConfigHotkey OPEN_CONFIG_GUI = new OpenConfigHotkey();

    public static final RegionHotkey REGION_KEY = new RegionHotkey();

    public static final BlocksHotkey BLOCKS_KEY = new BlocksHotkey();

    public static final ModeConfig MODE = new ModeConfig();
    public static final TransparencyConfig TRANSPARENCY = new TransparencyConfig();
    public static final RuleListConfig RULES = new RuleListConfig();
    public static final RegionListConfig REGIONS = new RegionListConfig();
    public static final RecordListConfig RECORDED = new RecordListConfig();
    public static final RecordModeConfig RECORD_MODE = new RecordModeConfig();
    public static final RecordApplyModeConfig RECORD_APPLY_MODE = new RecordApplyModeConfig();
    public static final WandConfig WAND = new WandConfig();
    public static final FullBrightConfig FULL_BRIGHT = new FullBrightConfig();
    public static final InvertConfig INVERT = new InvertConfig();
    public static final PassThroughConfig PASS_THROUGH = new PassThroughConfig();

    public static final RecordHotkey RECORD_KEY = new RecordHotkey();

    private static boolean syncing = false;

    private static final List<ConfigHotkey> HOTKEY_LIST =
            List.of(OPEN_CONFIG_GUI, REGION_KEY, BLOCKS_KEY, RECORD_KEY);

    private static final List<IHotkey> TOGGLE_LIST = List.of(PASS_THROUGH);

    private ModConfigs() {}

    public static void init() {
        MODE.setValueChangeCallback(config -> writeMode((ModeConfig) config));
        TRANSPARENCY.setValueChangeCallback(
                config ->
                        write(
                                () ->
                                        SelectiveRenderingManager.setTransparency(
                                                config.getIntegerValue())));
        RULES.setValueChangeCallback(
                config ->
                        write(() -> SelectiveRenderingManager.setRuleSources(config.getStrings())));
        REGIONS.setValueChangeCallback(
                config ->
                        write(
                                () ->
                                        SelectiveRenderingManager.setRegionSources(
                                                config.getStrings())));
        RECORDED.setValueChangeCallback(
                config ->
                        write(
                                () ->
                                        SelectiveRenderingManager.setRecordSources(
                                                config.getStrings())));
        RECORD_MODE.setValueChangeCallback(
                config ->
                        write(
                                () ->
                                        SelectiveRenderingManager.setRecordMode(
                                                ((RecordModeConfig) config).recordMode())));
        RECORD_APPLY_MODE.setValueChangeCallback(
                config ->
                        write(
                                () ->
                                        SelectiveRenderingManager.setRecordApplyMode(
                                                ((RecordApplyModeConfig) config).applyMode())));
        WAND.setValueChangeCallback(
                config -> write(() -> SelectiveRenderingManager.setWand(config.getStringValue())));
        FULL_BRIGHT.setValueChangeCallback(
                config ->
                        write(
                                () ->
                                        SelectiveRenderingManager.setFullBright(
                                                config.getBooleanValue())));
        INVERT.setValueChangeCallback(
                config ->
                        write(() -> SelectiveRenderingManager.setInvert(config.getBooleanValue())));
        PASS_THROUGH.setValueChangeCallback(
                config ->
                        write(
                                () ->
                                        SelectiveRenderingManager.setPassThrough(
                                                config.getBooleanValue())));

        TickHandler.getInstance().registerClientTickHandler(BlockChangeRecorder::onTick);

        syncing = true;

        try {
            readHotkeys();
        } finally {
            syncing = false;
        }

        OPEN_CONFIG_GUI.getKeybind().setCallback(new OpenConfigCallback());
        RECORD_KEY.getKeybind().setCallback(new RecordCallback());
        InputEventHandler.getKeybindManager().registerKeybindProvider(new KeybindProvider());

        Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new ModInfo(
                        SelectiveRendering.MOD_ID,
                        MOD_NAME,
                        () -> new ModConfigGui(GuiUtils.getCurrentScreen())));

        ConfigManager.getInstance().registerConfigHandler(SelectiveRendering.MOD_ID, new Handler());
        syncFromManager();
    }

    public static void syncFromManager() {
        syncing = true;

        try {
            MODE.setOptionListValue(ModeOption.of(SelectiveRenderingManager.getMode()));
            TRANSPARENCY.setExactValue(SelectiveRenderingManager.getTransparency());
            RULES.setStrings(SelectiveRenderingManager.ruleSources());
            RECORDED.setStrings(SelectiveRenderingManager.recordSources());
            RECORD_MODE.setOptionListValue(
                    RecordModeOption.of(SelectiveRenderingManager.getRecordMode()));
            RECORD_APPLY_MODE.setOptionListValue(
                    RecordApplyModeOption.of(SelectiveRenderingManager.getRecordApplyMode()));
            REGIONS.setStrings(SelectiveRenderingManager.regionSources());
            WAND.setValueFromString(SelectiveRenderingManager.getWand());
            FULL_BRIGHT.setBooleanValue(SelectiveRenderingManager.isFullBright());
            INVERT.setBooleanValue(SelectiveRenderingManager.isInvert());
            PASS_THROUGH.setBooleanValue(SelectiveRenderingManager.isPassThrough());
        } finally {
            syncing = false;
        }
    }

    private static void writeMode(ModeConfig config) {
        write(() -> SelectiveRenderingManager.setMode(config.mode()));
    }

    private static void readHotkeys() {
        JsonObject stored = ModConfig.get().hotkeys;
        if (stored == null) {
            return;
        }

        JsonObject holder = new JsonObject();
        holder.add(HOTKEY_CATEGORY, stored);
        ConfigUtils.readConfigBase(holder, HOTKEY_CATEGORY, HOTKEY_LIST);
        ConfigUtils.readHotkeys(holder, HOTKEY_CATEGORY, TOGGLE_LIST);
    }

    private static void writeHotkeys() {
        JsonObject holder = new JsonObject();
        ConfigUtils.writeConfigBase(holder, HOTKEY_CATEGORY, HOTKEY_LIST);
        ConfigUtils.writeHotkeys(holder, HOTKEY_CATEGORY, TOGGLE_LIST);

        ModConfig.get().hotkeys = holder.getAsJsonObject(HOTKEY_CATEGORY);
        ModConfig.save();
    }

    private static void write(Runnable apply) {
        if (syncing == false) {
            apply.run();
        }
    }

    public static String text(String key) {
        return Component.translatable(KEY + "." + key).getString();
    }

    public static MutableComponent textComponent(String key) {
        return Component.translatable(KEY + "." + key);
    }

    public static final class ModeConfig extends ConfigOptionList {
        public ModeConfig() {
            super("mode", ModeOption.of(SelectiveRenderingManager.DEFAULT_MODE));
        }

        public Mode mode() {
            return ((ModeOption) this.getOptionListValue()).mode();
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("mode");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("mode.description");
        }
    }

    public static final class TransparencyConfig extends ConfigInteger {
        public TransparencyConfig() {
            super(
                    "transparency",
                    SelectiveRenderingManager.DEFAULT_TRANSPARENCY,
                    0,
                    SelectiveRenderingManager.MAX_TRANSPARENCY,
                    true);
        }

        @Override
        public void setIntegerValue(int value) {
            int step = SelectiveRenderingManager.TRANSPARENCY_STEP;
            int snapped = step <= 1 ? value : Math.round((float) value / step) * step;

            super.setIntegerValue(snapped);
        }

        public void setExactValue(int value) {
            super.setIntegerValue(value);
        }

        @Override
        public void setValueFromString(String value) {
            try {
                setExactValue(Integer.parseInt(value.trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("transparency");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("transparency.description");
        }
    }

    public static final class RuleListConfig extends ConfigStringList {
        public RuleListConfig() {
            super("items", ImmutableList.of());
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("items");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("items.description");
        }
    }

    public static final class RegionListConfig extends ConfigStringList {
        public RegionListConfig() {
            super("regions", ImmutableList.of());
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("regions");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("regions.description");
        }
    }

    public static final class OpenConfigHotkey extends ConfigHotkey {
        public OpenConfigHotkey() {
            super("open_config_gui", "X,V", KeybindSettings.DEFAULT);
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("open_config_gui");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("open_config_gui.description");
        }
    }

    public static final class RegionHotkey extends ConfigHotkey {
        public RegionHotkey() {
            super("region_key", "LEFT_CONTROL", KeybindSettings.MODIFIER_INGAME);
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("region_key");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("region_key.description");
        }
    }

    public static final class BlocksHotkey extends ConfigHotkey {
        public BlocksHotkey() {
            super("blocks_key", "LEFT_ALT", KeybindSettings.MODIFIER_INGAME);
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("blocks_key");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("blocks_key.description");
        }
    }

    public static final class RecordListConfig extends ConfigStringList {
        public RecordListConfig() {
            super("recorded", ImmutableList.of());
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("recorded");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("recorded.description");
        }
    }

    public static final class RecordModeConfig extends ConfigOptionList {
        public RecordModeConfig() {
            super(
                    "record_mode",
                    RecordModeOption.of(SelectiveRenderingManager.DEFAULT_RECORD_MODE));
        }

        public RecordMode recordMode() {
            return ((RecordModeOption) this.getOptionListValue()).mode();
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("record_mode");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("record_mode.description");
        }
    }

    public static final class RecordApplyModeConfig extends ConfigOptionList {
        public RecordApplyModeConfig() {
            super(
                    "record_apply_mode",
                    RecordApplyModeOption.of(SelectiveRenderingManager.DEFAULT_RECORD_APPLY_MODE));
        }

        public SelectiveRenderingManager.RecordApplyMode applyMode() {
            return ((RecordApplyModeOption) this.getOptionListValue()).applyMode();
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("record_apply_mode");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("record_apply_mode.description");
        }
    }

    public static final class RecordHotkey extends ConfigHotkey {
        public RecordHotkey() {
            super("record_key", "", KeybindSettings.DEFAULT);
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("record_key");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("record_key.description");
        }
    }

    public static final class WandConfig extends ConfigString {
        public WandConfig() {
            super("wand", SelectiveRenderingManager.DEFAULT_WAND);
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("wand");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("wand.description");
        }
    }

    public static final class FullBrightConfig extends ConfigBoolean {
        public FullBrightConfig() {
            super("full_bright", SelectiveRenderingManager.DEFAULT_FULL_BRIGHT);
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("full_bright");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("full_bright.description");
        }
    }

    public static final class InvertConfig extends ConfigBoolean {
        public InvertConfig() {
            super("invert", SelectiveRenderingManager.DEFAULT_INVERT);
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("invert");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("invert.description");
        }
    }

    public static final class PassThroughConfig extends ConfigBooleanHotkeyed {
        public PassThroughConfig() {
            super("pass_through", SelectiveRenderingManager.DEFAULT_PASS_THROUGH, "");
        }

        @Override
        public String getPrettyName() {
            return text("pass_through");
        }

        @Override
        public String getConfigGuiDisplayName() {
            return text("pass_through");
        }

        @Override
        public MutableComponent getCommentComponent() {
            return textComponent("pass_through.description");
        }
    }

    private static final class Handler implements IConfigHandler {
        @Override
        public void load() {
            syncFromManager();
        }

        @Override
        public void save() {
            writeHotkeys();
            ModConfig.flush();
        }
    }

    private static final class KeybindProvider implements IKeybindProvider {
        @Override
        public void addKeysToMap(IKeybindManager manager) {
            manager.addKeybindToMap(OPEN_CONFIG_GUI.getKeybind());
            manager.addKeybindToMap(RECORD_KEY.getKeybind());
            manager.addKeybindToMap(REGION_KEY.getKeybind());
            manager.addKeybindToMap(BLOCKS_KEY.getKeybind());
            manager.addKeybindToMap(PASS_THROUGH.getKeybind());
        }

        @Override
        public void addHotkeys(IKeybindManager manager) {
            manager.addHotkeysForCategory(
                    MOD_NAME,
                    SelectiveRendering.MOD_ID + ".hotkeys.category.main",
                    List.<IHotkey>of(
                            RECORD_KEY, REGION_KEY, BLOCKS_KEY, PASS_THROUGH, OPEN_CONFIG_GUI));
        }
    }

    private static final class RecordCallback implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            if (action == KeyAction.PRESS) {
                BlockChangeRecorder.toggle();
            }

            return true;
        }
    }

    private static final class OpenConfigCallback implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            if (action == KeyAction.PRESS) {
                ModConfigScreen.open(Minecraft.getInstance());
            }

            return true;
        }
    }
}
