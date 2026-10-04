package com.selectiverendering.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.selectiverendering.BlockChangeRecorder;
import com.selectiverendering.ModConfig;
import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.SelectiveRenderingManager.Mode;
import com.selectiverendering.SelectiveRenderingManager.RecordApplyMode;
import com.selectiverendering.SelectiveRenderingManager.RecordMode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

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
import fi.dy.masa.malilib.util.data.ModInfo;
import fi.dy.masa.malilib.util.GuiUtils;

/**
 * MaLiLib 配置层的定义与接线。
 *
 * <h2>它负责三件事</h2>
 * <ol>
 *   <li><b>定义配置项</b>（{@code ConfigHotkey} / {@code ConfigInteger} /
 *       {@code ConfigStringList} / {@code ConfigOptionList} …），
 *       每个内部类都只是"一个配置项 + 它的显示名 + 说明"；</li>
 *   <li><b>把配置变化转发给 {@link SelectiveRenderingManager}</b>——
 *       注意 MaLiLib 的配置项自己也要存一份值，所以这里是"双向"的：
 *       manager 是权威数据源，GUI 只是视图；</li>
 *   <li><b>注册</b>快捷键回调、客户端 tick 回调、ModMenu 配置界面工厂、
 *       以及 MaLiLib 的 {@code ConfigManager} 读写处理器。</li>
 * </ol>
 *
 * <h2>防回环机制（关键）</h2>
 * <p>当我们把 manager 的值写回 MaLiLib 控件（{@link #syncFromManager}）时，
 * 会触发控件的 {@code setValueChangeCallback} → 又会去改 manager。
 * 所以用一个 {@code syncing} 标志位在回灌期间屏蔽回调（见 {@link #write}）。
 * <b>新增配置项时务必照这个模式接线，否则会出现改一次触发两次重建。</b>
 *
 * <h2>为什么不用 YetAnotherConfigLib 之类的</h2>
 * <p>本 Mod 移植自 Lucidity，Lucidity 用 MaLiLib；而且 MaLiLib 自带
 * 字符串列表编辑器、快捷键冲突检测、导入导出，正好对上需求。
 */
public final class ModConfigs {
	private static final String KEY = SelectiveRendering.MOD_ID + ".config";

	private static final String MOD_NAME = "Selective Rendering";

	/** 快捷键在配置文件里那一节的名字；MaLiLib 自己 / Litematica 用的就是 "Hotkeys"。 */
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

	/**
	 * 纯快捷键配置项。和 MaLiLib / Litematica 里的 {@code HOTKEY_LIST} 一个意思：
	 * 集中放在一个不可变 List 里，配合 {@code ConfigUtils} 一次性读写。
	 */
	private static final List<ConfigHotkey> HOTKEY_LIST = List.of(OPEN_CONFIG_GUI, REGION_KEY, BLOCKS_KEY, RECORD_KEY);

	/**
	 * "布尔 + 快捷键"的配置项（{@link fi.dy.masa.malilib.config.IHotkeyTogglable}）。
	 *
	 * <p>不能塞进 {@link #HOTKEY_LIST}：那个用的是 {@code ConfigUtils.writeConfigBase}，
	 * 会把整个 {@code {enabled, hotkey}} 对象写进去，于是布尔值同时出现在
	 * {@code ModConfig.passThrough} 和 {@code hotkeys} 两处 —— 两个来源，
	 * 迟早会不一致。这里单独用 {@code writeHotkeys / readHotkeys}，
	 * <b>只落盘键位</b>，布尔值仍然只由 manager 经 {@code ModConfig.passThrough} 管。
	 */
	private static final List<IHotkey> TOGGLE_LIST = List.of(PASS_THROUGH);

	private ModConfigs() {
	}

	public static void init() {
		MODE.setValueChangeCallback(config -> writeMode((ModeConfig) config));
		TRANSPARENCY.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setTransparency(config.getIntegerValue())));
		RULES.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setRuleSources(config.getStrings())));
		REGIONS.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setRegionSources(config.getStrings())));
		RECORDED.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setRecordSources(config.getStrings())));
		RECORD_MODE.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setRecordMode(((RecordModeConfig) config).recordMode())));
		RECORD_APPLY_MODE.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setRecordApplyMode(((RecordApplyModeConfig) config).applyMode())));
		WAND.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setWand(config.getStringValue())));
		FULL_BRIGHT.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setFullBright(config.getBooleanValue())));
		INVERT.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setInvert(config.getBooleanValue())));
		PASS_THROUGH.setValueChangeCallback(config -> write(() -> SelectiveRenderingManager.setPassThrough(config.getBooleanValue())));

		TickHandler.getInstance().registerClientTickHandler(BlockChangeRecorder::onTick);

		/**
		 * ⚠ 必须包在 {@code syncing} 里。
		 *
		 * <p>{@link ConfigBooleanHotkeyed} 的 {@code ConfigUtils.readHotkeys} 末尾会调
		 * {@code checkIfClean()}：只要读到的键位和默认值不同，它就认为"值变了"并触发
		 * {@code onValueChanged()} —— 即使这次只改了键位、压根没碰布尔值。
		 *
		 * <p>而回调（上面刚设好的那个）会把 {@code config.getBooleanValue()} 写回 manager。
		 * 此刻控件里还是<b>默认值</b>（真值是从 {@code ModConfig} 读进 manager 的，
		 * 还没回灌到控件上），于是玩家存下来的 {@code false} 会被无声地改回 {@code true}。
		 * 屏蔽掉这个回调，真值交给末尾的 {@code syncFromManager()} 回灌。
		 */
		syncing = true;

		try {
			readHotkeys();
		}
		finally {
			syncing = false;
		}

		OPEN_CONFIG_GUI.getKeybind().setCallback(new OpenConfigCallback());
		RECORD_KEY.getKeybind().setCallback(new RecordCallback());
		InputEventHandler.getKeybindManager().registerKeybindProvider(new KeybindProvider());

		Registry.CONFIG_SCREEN.registerConfigScreenFactory(
			new ModInfo(SelectiveRendering.MOD_ID, MOD_NAME, () -> new ModConfigGui(GuiUtils.getCurrentScreen()))
		);

		ConfigManager.getInstance().registerConfigHandler(SelectiveRendering.MOD_ID, new Handler());
		syncFromManager();
	}

	/**
	 * 把 manager 的当前值回灌到所有 MaLiLib 控件（打开配置界面时、以及应用预设后调用）。
	 * 期间用 {@code syncing} 屏蔽回写，避免死循环。
	 */
	public static void syncFromManager() {
		syncing = true;

		try {
			MODE.setOptionListValue(ModeOption.of(SelectiveRenderingManager.getMode()));
			// 用 setExactValue 而不是 setIntegerValue：manager 里的值可能不是 5 的倍数（输入框填的），
			// 回灌到控件上时不该顺便把它吸附掉
			TRANSPARENCY.setExactValue(SelectiveRenderingManager.getTransparency());
			RULES.setStrings(SelectiveRenderingManager.ruleSources());
			RECORDED.setStrings(SelectiveRenderingManager.recordSources());
			RECORD_MODE.setOptionListValue(RecordModeOption.of(SelectiveRenderingManager.getRecordMode()));
			RECORD_APPLY_MODE.setOptionListValue(RecordApplyModeOption.of(SelectiveRenderingManager.getRecordApplyMode()));
			REGIONS.setStrings(SelectiveRenderingManager.regionSources());
			WAND.setValueFromString(SelectiveRenderingManager.getWand());
			FULL_BRIGHT.setBooleanValue(SelectiveRenderingManager.isFullBright());
			INVERT.setBooleanValue(SelectiveRenderingManager.isInvert());
			PASS_THROUGH.setBooleanValue(SelectiveRenderingManager.isPassThrough());
		}
		finally {
			syncing = false;
		}
	}

	private static void writeMode(ModeConfig config) {
		write(() -> SelectiveRenderingManager.setMode(config.mode()));
	}

	/**
	 * 快捷键持久化：{@code ConfigUtils} 是 MaLiLib 官方给的工具，
	 * MaLiLib 自己和 Litematica 都用它（{@code ConfigUtils.readConfigBase(root, "Hotkeys", ...)}）。
	 *
	 * <h2>为什么必须自己做</h2>
	 * <p>起初以为注册了 MaLiLib 的 {@code ConfigHandler} 就等于"配置都会被存下来"，
	 * 其实 {@code IConfigManager} 只有 {@code load()} / {@code save()} 两个回调，
	 * persist 整个甩给 mod 自己。而我们以前只在
	 * {@link SelectiveRenderingManager#persist} 里存模式 / 名单 / 选区，
	 * <b>四个快捷键一个都没存</b>，于是每次重进游戏都被打回默认值（如 {@code X,V}）。
	 *
	 * <h2>不用轮询的理由</h2>
	 * <p>改按键确实不触发 {@code ConfigHotkey} 的 {@code valueChangeCallback}
	 * （MaLiLib 的 keybind 界面是直接改 {@code KeybindMulti} 对象的），
	 * 但它有 {@code isDirty} 机制：GUI 关闭时
	 * {@code GuiConfigsBase.removed()} 发现 {@code wereConfigsModified()} 就会
	 * {@code onSettingsChanged()} → {@code ConfigManager.onConfigsChanged(modId)}
	 * → {@code handler.onConfigsChanged()} → <b>{@code save(); load();}</b>。
	 * 另外退出世界时 MaLiLib 会 {@code saveAllConfigs()}。这两个时机足够可靠。
	 *
	 * <p>顺带一提：{@code onConfigsChanged()} 的默认实现是
	 * {@code save(); load();}，而我们的 {@code load()} 是
	 * {@link #syncFromManager}——它只回灌模式 / 名单 / 选区，<b>不碰快捷键</b>，
	 * 所以不会把刚存下的按键又刷回默认值。
	 */
	private static void readHotkeys() {
		JsonObject stored = ModConfig.get().hotkeys;
		if (stored == null) {
			return;
		}

		// ConfigUtils 要的是"带 category 那一层的 root"，这里把存下来的那一节挂回去
		JsonObject holder = new JsonObject();
		holder.add(HOTKEY_CATEGORY, stored);
		ConfigUtils.readConfigBase(holder, HOTKEY_CATEGORY, HOTKEY_LIST);
		// 只读键位，不读布尔值（见 TOGGLE_LIST 的注释）
		ConfigUtils.readHotkeys(holder, HOTKEY_CATEGORY, TOGGLE_LIST);
	}

	/** 把当前按键写进磁盘配置。重复调用安全：{@link ModConfig#save()} 自带内容去重。 */
	private static void writeHotkeys() {
		JsonObject holder = new JsonObject();
		ConfigUtils.writeConfigBase(holder, HOTKEY_CATEGORY, HOTKEY_LIST);
		ConfigUtils.writeHotkeys(holder, HOTKEY_CATEGORY, TOGGLE_LIST);

		ModConfig.get().hotkeys = holder.getAsJsonObject(HOTKEY_CATEGORY);
		ModConfig.save();
	}

	/**
	 * 只有"用户真的改了控件"时才往 manager 写。
	 * {@link #syncFromManager} 回灌期间为 true，此时直接丢弃回调。
	 */
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

	/**
	 * 透明度。5 格一档的吸附<b>只对拖动条生效</b>，输入框可以随便填（37% 就是 37%）。
	 *
	 * <p>MaLiLib 里这两条写入路径最终都进 {@code ConfigInteger.setValueFromString} /
	 * {@code setIntegerValue}：
	 * <pre>
	 *   拖动条   SliderCallbackInteger.setValueRelative() → setIntegerValue()   → 吸附到 5 的倍数
	 *   输入框   WidgetConfigOption.applyNewValueToConfig() → setValueFromString() → setIntegerValue()
	 * </pre>
	 * 因为 {@code ConfigInteger#setValueFromString} 的实现就是转调用 {@code setIntegerValue}，
	 * 光覆写 {@code setIntegerValue} 会把手动输入也一起吸附掉——
	 * 所以输入框那条必须再 {@code super} 一层绕开吸附（见 {@link #setExactValue}）。
	 */
	public static final class TransparencyConfig extends ConfigInteger {
		public TransparencyConfig() {
			super(
				"transparency",
				SelectiveRenderingManager.DEFAULT_TRANSPARENCY,
				0,
				SelectiveRenderingManager.MAX_TRANSPARENCY,
				true
			);
		}

		@Override
		public void setIntegerValue(int value) {
			int step = SelectiveRenderingManager.TRANSPARENCY_STEP;
			int snapped = step <= 1 ? value : Math.round((float) value / step) * step;

			super.setIntegerValue(snapped);
		}

		/**
		 * 原样写一个值，不吸附。给输入框和 {@link ModConfigs#syncFromManager()} 用——
		 * 后者的场景是"manager 里存了 37%"，回灌到控件上时不该被改写成 35%。
		 */
		public void setExactValue(int value) {
			super.setIntegerValue(value);
		}

		@Override
		public void setValueFromString(String value) {
			try {
				setExactValue(Integer.parseInt(value.trim()));
			}
			catch (NumberFormatException ignored) {
				// MaLiLib 原本在这里也只是 log 一句，半截输入（空串、"-") 不算错误
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
			super("record_mode", RecordModeOption.of(SelectiveRenderingManager.DEFAULT_RECORD_MODE));
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

	/**
	 * 录制结束后，记录到的位置是<b>新增</b>进选区列表，还是从已有选区里<b>裁剪</b>掉。
	 *
	 * <p>和 {@link RecordModeConfig}（哪些方块的变化值得记录）是两个正交的维度。
	 */
	public static final class RecordApplyModeConfig extends ConfigOptionList {
		public RecordApplyModeConfig() {
			super("record_apply_mode", RecordApplyModeOption.of(SelectiveRenderingManager.DEFAULT_RECORD_APPLY_MODE));
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

	/**
	 * 记录器开关按键。
	 *
	 * <p><b>默认不绑定任何键</b>：记录器一开就会在后台持续改变选区，
	 * 绑一个默认键很容易被不懂的人误触（原本是 {@code N}）。
	 * 需要的人自己去设置里绑。
	 */
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

	/**
	 * "夜视"：全局按全亮渲染，并跳过所有光照相关的计算。默认开启。
	 *
	 * <p>参照 meteor 的 Xray——它把"全亮"硬编码了（激活即刷白光照贴图），
	 * 这里做成可开关的选项，见 {@link SelectiveRenderingManager#isFullBright()}。
	 */
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

	/**
	 * "反转"：把"哪一批被淡化"整个翻过来——原来命中的那批保持正常，其余全部淡化。
	 *
	 * <p>用途举例：模式选「选区内·指定类型」再打开反转，效果就是
	 * "只显示选区内的指定方块"，这在原来的 9 个模式里表达不出来。
	 */
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

	/**
	 * "穿透交互"：准星射线把被淡化的方块当空气，能瞄准到它后面的方块。
	 *
	 * <p>描边 / 裂纹 / 破坏 / 右键交互全部跟着准星走，所以这一个开关就够；
	 * 关掉则淡化方块照样挡住准星（= 本功能上线前的行为）。
	 *
	 * <p>注意它<b>不触发区块重建</b>——只影响射线怎么算，不参与画面渲染。
	 *
	 * <h2>为什么是 {@code ConfigBooleanHotkeyed} 而不是"布尔 + 一条快捷键"</h2>
	 * <p>MaLiLib 的这个类型在 GUI 里渲染成<b>一行</b>：左边布尔值、右边键位、
	 * 再右边重置按钮（{@code WidgetConfigOption} 里有专门的
	 * {@code ConfigBooleanHotkeyed} 分支）。拆成两个 {@code ConfigBoolean} +
	 * {@code ConfigHotkey} 就成了两行，而且键位那一行看不出它属于谁。
	 *
	 * <p>它还自带 {@code KeyCallbackToggleBooleanConfigWithMessage}：
	 * 按键直接切换本布尔值（走 {@code valueChangeCallback} 同步给 manager），
	 * 并顺手弹一条 MaLiLib 标准的动作栏消息（绿色 ON / 红色 OFF）。
	 * 所以不需要自己写回调，也不需要自己拼文案。
	 *
	 * <p><b>默认不绑定任何键</b>：这是个会改变交互手感的开关，绑默认键容易误触。
	 * 注意键位只落盘到 {@code hotkeys} 那一节（见 {@link ModConfigs#TOGGLE_LIST}），
	 * 布尔值仍然只由 manager 管。
	 */
	public static final class PassThroughConfig extends ConfigBooleanHotkeyed {
		public PassThroughConfig() {
			super("pass_through", SelectiveRenderingManager.DEFAULT_PASS_THROUGH, "");
		}

		/**
		 * 动作栏消息里的名字。{@code KeyCallbackToggleBooleanConfigWithMessage} 用的是
		 * {@code getPrettyName()}，默认会退化成 {@code splitCamelCase("pass_through")}，
		 * 于是中文环境下弹出一条英文。覆写成我们的翻译 key 即可（走同一套
		 * {@code ClientLanguage} 劫持，和 GUI 上显示的名字一致）。
		 */
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

		/**
		 * MaLiLib 保存配置时走这里。除了刷现有状态，还要<b>顺手把按键捞一遍</b>——
		 * 按键改动不会经过任何 MaLiLib 回调（见 {@link ModConfigs#readHotkeys}），
		 * 这是少数几个能确定被调用的时机之一（关闭配置界面 / 退出游戏）。
		 *
		 * <p>用 {@link ModConfig#flush()} 而不是 {@code save()}：
		 * 这里是"玩家点了保存"的语义，必须立刻落盘，不能还留在 300ms 的防抖窗口里。
		 */
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
			// 显式指定 List<IHotkey>：这一串里混了 ConfigHotkey 和 ConfigBooleanHotkeyed
			// 两种类型，不写类型见证的话推导出的公共父类型可能不是 IHotkey
			manager.addHotkeysForCategory(
				MOD_NAME,
				SelectiveRendering.MOD_ID + ".hotkeys.category.main",
				List.<IHotkey>of(RECORD_KEY, REGION_KEY, BLOCKS_KEY, PASS_THROUGH, OPEN_CONFIG_GUI)
			);
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
