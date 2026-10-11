package com.selectiverendering.config.entrypoint;

import com.selectiverendering.interaction.ProjectionControls;
import net.minecraft.network.chat.Component;
import com.selectiverendering.region.RegionHistory;
import com.selectiverendering.matchingrule.BlockListMessage;
import com.google.gson.JsonObject;
import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.SelectiveRenderingManager.Mode;
import com.selectiverendering.SelectiveRenderingManager.RecordApplyMode;
import com.selectiverendering.SelectiveRenderingManager.RecordMode;
import com.selectiverendering.config.suggest.SuggestKind;
import com.selectiverendering.config.suggest.SuggestingStringList;
import com.selectiverendering.recording.BlockChangeRecorder;
import net.minecraft.client.Minecraft;

import java.util.List;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.data.ModInfo;

/** 连接 MaLiLib 配置控件与运行时设置。 */
public final class ModConfigs {
	public static final String KEY = SelectiveRendering.MOD_ID + ".config";
	private static final String MOD_NAME = "Selective Rendering";
	private static final String HOTKEY_CATEGORY = "Hotkeys";

	public static final ModeConfig MODE = new ModeConfig("mode").apply(KEY);
	public static final ConfigBoolean INVERT = new ConfigBoolean("invert", SelectiveRenderingManager.DEFAULT_INVERT).apply(KEY);
	public static final TransparencyConfig TRANSPARENCY = new TransparencyConfig("transparency").apply(KEY);
	public static final SuggestingStringList RULES = new SuggestingStringList("items", SuggestKind.BLOCK).apply(KEY);
	public static final ConfigHotkey OPERATION_KEY = new ConfigHotkey("operation_key", "LEFT_ALT", KeybindSettings.MODIFIER_INGAME).apply(KEY);
	public static final ConfigHotkey REGION_KEY = new ConfigHotkey("region_key", "LEFT_CONTROL", KeybindSettings.MODIFIER_INGAME).apply(KEY);
	public static final ConfigHotkey UNDO_KEY = new ConfigHotkey("undo_key", "LEFT_CONTROL,Z", KeybindSettings.DEFAULT).apply(KEY);
	public static final ConfigHotkey REDO_KEY = new ConfigHotkey("redo_key", "LEFT_CONTROL,Y", KeybindSettings.DEFAULT).apply(KEY);
	public static final ConfigHotkey PROJECT_LEFT = new ConfigHotkey("project_left", "LEFT_CONTROL,LEFT", KeybindSettings.DEFAULT).apply(KEY);
	public static final ConfigHotkey PROJECT_RIGHT = new ConfigHotkey("project_right", "LEFT_CONTROL,RIGHT", KeybindSettings.DEFAULT).apply(KEY);
	public static final ConfigHotkey PROJECT_FORWARD = new ConfigHotkey("project_forward", "LEFT_CONTROL,UP", KeybindSettings.DEFAULT).apply(KEY);
	public static final ConfigHotkey PROJECT_BACK = new ConfigHotkey("project_back", "LEFT_CONTROL,DOWN", KeybindSettings.DEFAULT).apply(KEY);
	public static final ConfigHotkey OPEN_CONFIG_GUI = new ConfigHotkey("open_config_gui", "X,V", KeybindSettings.DEFAULT).apply(KEY);
	public static final ConfigString WAND = new ConfigString("wand", SelectiveRenderingManager.DEFAULT_WAND).apply(KEY);
	public static final ConfigBoolean FULL_BRIGHT = new ConfigBoolean("full_bright", SelectiveRenderingManager.DEFAULT_FULL_BRIGHT).apply(KEY);
	public static final ConfigBoolean BUILD_HIDDEN_MESH = new ConfigBoolean("build_hidden_mesh", SelectiveRenderingManager.DEFAULT_BUILD_HIDDEN_MESH).apply(KEY);
	public static final ConfigBooleanHotkeyed PASS_THROUGH = new ConfigBooleanHotkeyed("pass_through", SelectiveRenderingManager.DEFAULT_PASS_THROUGH, "").apply(KEY);
	public static final SuggestingStringList RECORDED = new SuggestingStringList("recorded", SuggestKind.BLOCK).apply(KEY);
	public static final ConfigOptionList RECORD_MODE = new ConfigOptionList("record_mode", SelectiveRenderingManager.DEFAULT_RECORD_MODE).apply(KEY);
	public static final ConfigOptionList RECORD_APPLY_MODE = new ConfigOptionList("record_apply_mode", SelectiveRenderingManager.DEFAULT_RECORD_APPLY_MODE).apply(KEY);
	public static final ConfigHotkey RECORD_KEY = new ConfigHotkey("record_key", "", KeybindSettings.DEFAULT).apply(KEY);

	public static final List<IConfigBase> OPTIONS = List.of(
		MODE,
		INVERT,
		TRANSPARENCY,
		RULES,
		OPERATION_KEY,
		REGION_KEY,
		UNDO_KEY,
		REDO_KEY,
		PROJECT_LEFT,
		PROJECT_RIGHT,
		PROJECT_FORWARD,
		PROJECT_BACK,
		OPEN_CONFIG_GUI,
		WAND,
		FULL_BRIGHT,
		BUILD_HIDDEN_MESH,
		PASS_THROUGH,
		RECORDED,
		RECORD_MODE,
		RECORD_APPLY_MODE,
		RECORD_KEY
	);

	private static final List<ConfigHotkey> HOTKEY_LIST = List.of(OPEN_CONFIG_GUI, REGION_KEY, OPERATION_KEY, RECORD_KEY, UNDO_KEY, REDO_KEY, PROJECT_LEFT, PROJECT_RIGHT, PROJECT_FORWARD, PROJECT_BACK);
	// 仅保存键位；开关值由 manager 保存。
	private static final List<IHotkey> TOGGLE_LIST = List.of(PASS_THROUGH);

	private static boolean syncing = false;

	private ModConfigs() {
	}

	public static void init() {
		MODE.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setMode(MODE.mode())));
		INVERT.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setInvert(config.getBooleanValue())));
		TRANSPARENCY.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setTransparency(config.getIntegerValue())));
		RULES.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setRuleSources(config.getStrings())));
		WAND.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setWand(config.getStringValue())));
		FULL_BRIGHT.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setFullBright(config.getBooleanValue())));
		BUILD_HIDDEN_MESH.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setBuildHiddenMesh(config.getBooleanValue())));
		PASS_THROUGH.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setPassThrough(config.getBooleanValue())));
		RECORDED.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setRecordSources(config.getStrings())));
		RECORD_MODE.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setRecordMode((RecordMode) config.getOptionListValue())));
		RECORD_APPLY_MODE.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setRecordApplyMode((RecordApplyMode) config.getOptionListValue())));

		TickHandler.getInstance().registerClientTickHandler(BlockChangeRecorder::onTick);

		// 加载键位会触发值回调，期间禁止默认值写回 manager。
		syncing = true;
		try {
			readHotkeys();
		} finally {
			syncing = false;
		}

		OPEN_CONFIG_GUI.getKeybind().setCallback((action, key) -> {
			if (action == KeyAction.PRESS) {
				ModConfigScreen.open(Minecraft.getInstance());
			}
			return true;
		});
		bindProjection(PROJECT_LEFT, ProjectionControls.Move.LEFT);
		bindProjection(PROJECT_RIGHT, ProjectionControls.Move.RIGHT);
		bindProjection(PROJECT_FORWARD, ProjectionControls.Move.FORWARD);
		bindProjection(PROJECT_BACK, ProjectionControls.Move.BACK);
		UNDO_KEY.getKeybind().setCallback((action, key) -> {
			if (action == KeyAction.PRESS) {
				BlockListMessage.note(Component.translatable(SelectiveRendering.MOD_ID + (RegionHistory.undo() ? ".message.undo" : ".message.nothing_to_undo")).getString());
			}
			return true;
		});
		REDO_KEY.getKeybind().setCallback((action, key) -> {
			if (action == KeyAction.PRESS) {
				BlockListMessage.note(Component.translatable(SelectiveRendering.MOD_ID + (RegionHistory.redo() ? ".message.redo" : ".message.nothing_to_redo")).getString());
			}
			return true;
		});
		RECORD_KEY.getKeybind().setCallback((action, key) -> {
			if (action == KeyAction.PRESS) {
				BlockChangeRecorder.toggle();
			}
			return true;
		});
		InputEventHandler.getKeybindManager().registerKeybindProvider(new KeybindProvider());

		Registry.CONFIG_SCREEN.registerConfigScreenFactory(
			new ModInfo(SelectiveRendering.MOD_ID, MOD_NAME, () -> new ModConfigGui(GuiUtils.getCurrentScreen()))
		);
		ConfigManager.getInstance().registerConfigHandler(SelectiveRendering.MOD_ID, new Handler());

		syncFromManager();
	}

	public static void syncFromManager() {
		syncing = true;
		try {
			MODE.setOptionListValue(SelectiveRenderingManager.getMode());
			INVERT.setBooleanValue(SelectiveRenderingManager.isInvert());
			TRANSPARENCY.setExactValue(SelectiveRenderingManager.getTransparency());
			RULES.setStrings(SelectiveRenderingManager.ruleSources());
			WAND.setValueFromString(SelectiveRenderingManager.getWand());
			FULL_BRIGHT.setBooleanValue(SelectiveRenderingManager.isFullBright());
			BUILD_HIDDEN_MESH.setBooleanValue(SelectiveRenderingManager.isBuildHiddenMesh());
			PASS_THROUGH.setBooleanValue(SelectiveRenderingManager.isPassThrough());
			RECORDED.setStrings(SelectiveRenderingManager.recordSources());
			RECORD_MODE.setOptionListValue(SelectiveRenderingManager.getRecordMode());
			RECORD_APPLY_MODE.setOptionListValue(SelectiveRenderingManager.getRecordApplyMode());
		} finally {
			syncing = false;
		}
	}

	public static String text(String key) {
		return StringUtils.translate(KEY + "." + key);
	}

	private static void write(Runnable apply) {
		if (!syncing) {
			apply.run();
		}
	}

	private static void bindProjection(ConfigHotkey key, ProjectionControls.Move move) {
		key.getKeybind().setCallback((action, ignored) -> action == KeyAction.PRESS && ProjectionControls.move(move));
	}

	private static void readHotkeys() {
		JsonObject stored = ModConfig.get().hotkeys;
		if (stored == null) {
			return;
		}

		if (stored.has("blocks_key") && !stored.has("operation_key")) {
			stored.add("operation_key", stored.get("blocks_key"));
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

	public static final class ModeConfig extends ConfigOptionList {
		public ModeConfig(String name) {
			super(name, SelectiveRenderingManager.DEFAULT_MODE);
		}

		public Mode mode() {
			return (Mode) this.getOptionListValue();
		}

		@Override
		public ModeConfig apply(String translationPrefix) {
			super.apply(translationPrefix);
			return this;
		}
	}

	/** 透明度滑块按设定步长吸附，输入框允许任意整数。 */
	public static final class TransparencyConfig extends ConfigInteger {
		public TransparencyConfig(String name) {
			super(name, SelectiveRenderingManager.DEFAULT_TRANSPARENCY, 0, SelectiveRenderingManager.MAX_TRANSPARENCY, true);
		}

		@Override
		public void setIntegerValue(int value) {
			int step = SelectiveRenderingManager.TRANSPARENCY_STEP;
			super.setIntegerValue(step <= 1 ? value : Math.round((float) value / step) * step);
		}

		public void setExactValue(int value) {
			super.setIntegerValue(value);
		}

		@Override
		public void setValueFromString(String value) {
			try {
				this.setExactValue(Integer.parseInt(value.trim()));
			} catch (NumberFormatException ignored) {
			}
		}

		@Override
		public TransparencyConfig apply(String translationPrefix) {
			super.apply(translationPrefix);
			return this;
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
			manager.addKeybindToMap(OPERATION_KEY.getKeybind());
			manager.addKeybindToMap(UNDO_KEY.getKeybind());
			manager.addKeybindToMap(PROJECT_LEFT.getKeybind());
			manager.addKeybindToMap(PROJECT_RIGHT.getKeybind());
			manager.addKeybindToMap(PROJECT_FORWARD.getKeybind());
			manager.addKeybindToMap(PROJECT_BACK.getKeybind());
			manager.addKeybindToMap(REDO_KEY.getKeybind());
			manager.addKeybindToMap(PASS_THROUGH.getKeybind());
		}

		@Override
		public void addHotkeys(IKeybindManager manager) {
			manager.addHotkeysForCategory(
				MOD_NAME,
				SelectiveRendering.MOD_ID + ".hotkeys.category.main",
				List.<IHotkey>of(RECORD_KEY, REGION_KEY, OPERATION_KEY, UNDO_KEY, REDO_KEY, PROJECT_LEFT, PROJECT_RIGHT, PROJECT_FORWARD, PROJECT_BACK, PASS_THROUGH, OPEN_CONFIG_GUI)
			);
		}
	}
}
