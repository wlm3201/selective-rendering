package com.selectiverendering;

import com.selectiverendering.region.RegionHistory;
import com.selectiverendering.render.hiding.RetainedTerrain;
import com.selectiverendering.config.entrypoint.ModConfig;
import com.selectiverendering.config.presets.Preset;
import com.selectiverendering.config.region.RenderConfig;
import com.selectiverendering.matchingrule.BlockMatchRule;
import com.selectiverendering.region.Area;
import com.selectiverendering.region.Region;
import com.selectiverendering.region.RegionOptions;
import com.selectiverendering.render.hiding.RebuildScheduler;
import com.selectiverendering.util.Log;
import net.minecraft.core.BlockPos;
import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** 管理渲染设置、名单、选区和预设，并发布配置快照及保存配置。 */
public final class SelectiveRenderingManager {
	public static final int TRANSPARENCY_STEP = 1;
	public static final int MAX_TRANSPARENCY = 100;

	public static final Mode DEFAULT_MODE = Mode.OFF;
	public static final int DEFAULT_TRANSPARENCY = 50;
	public static final String DEFAULT_WAND = "minecraft:breeze_rod";
	public static final boolean DEFAULT_FULL_BRIGHT = true;

	/** 按范围和名单条件决定淡化对象；枚举顺序也是界面和滚轮的切换顺序。 */
	public enum Mode implements IConfigOptionListEntry {
		OFF("off"), REGION_INSIDE_LISTED("region_inside_listed"), REGION_INSIDE_UNLISTED("region_inside_unlisted"), REGION_INSIDE("region_inside"), REGION_OUTSIDE_LISTED(
			"region_outside_listed"), REGION_OUTSIDE_UNLISTED("region_outside_unlisted"), REGION_OUTSIDE("region_outside"), BLACKLIST("blacklist"), WHITELIST("whitelist");

		private final String key;

		Mode(String key) {
			this.key = key;
		}

		public Component displayName() {
			return Component.translatable(SelectiveRendering.MOD_ID + ".mode." + key);
		}

		public Mode cycle(int direction) {
			Mode[] values = values();
			return values[Math.floorMod(ordinal() + direction, values.length)];
		}

		@Override
		public String getStringValue() {
			return name();
		}

		@Override
		public String getDisplayName() {
			return displayName().getString();
		}

		@Override
		public Mode cycle(boolean forward) {
			return cycle(forward ? 1 : -1);
		}

		@Override
		public Mode fromString(String value) {
			if (value != null) {
				for (Mode entry : values()) {
					if (entry.name().equalsIgnoreCase(value.trim())) {
						return entry;
					}
				}
			}

			return OFF;
		}

		public boolean usesRegion() {
			return this != OFF && this != BLACKLIST && this != WHITELIST;
		}

		public boolean usesList() {
			return this != OFF && this != REGION_INSIDE && this != REGION_OUTSIDE;
		}

		/** 当前模式是否淡化名单内的方块。 */
		public boolean hidesListed() {
			return this == BLACKLIST || this == REGION_INSIDE_LISTED || this == REGION_OUTSIDE_LISTED;
		}
	}

	public static final Object LOCK = new Object();
	private static final List<BlockMatchRule> RULES = new ArrayList<>();

	private static final List<BlockMatchRule> RECORDED = new ArrayList<>();

	/** 用户写的每一项原样保留（含无法解析的），RULES / RECORDED 只是其中能解析的部分。 */
	private static final List<String> RULE_SOURCES = new ArrayList<>();
	private static final List<String> RECORD_SOURCES = new ArrayList<>();

	public static final List<Region> REGIONS = new ArrayList<>();

	private static final List<Preset> PRESETS = new ArrayList<>();

	private static volatile List<String> ruleSourceInput = List.of();
	private static volatile List<String> recordSourceInput = List.of();

	/** 记录器的名单过滤方式，独立于渲染名单。 */
	public enum RecordMode implements IConfigOptionListEntry {
		LISTED("record_listed"), UNLISTED("record_unlisted");

		private final String key;

		RecordMode(String key) {
			this.key = key;
		}

		public Component displayName() {
			return Component.translatable(SelectiveRendering.MOD_ID + ".mode." + key);
		}

		@Override
		public String getStringValue() {
			return name();
		}

		@Override
		public String getDisplayName() {
			return displayName().getString();
		}

		@Override
		public RecordMode cycle(boolean forward) {
			RecordMode[] values = values();
			return values[Math.floorMod(ordinal() + (forward ? 1 : -1), values.length)];
		}

		@Override
		public RecordMode fromString(String value) {
			if (value != null) {
				for (RecordMode entry : values()) {
					if (entry.name().equalsIgnoreCase(value.trim())) {
						return entry;
					}
				}
			}

			return UNLISTED;
		}
	}

	public static final RecordMode DEFAULT_RECORD_MODE = RecordMode.LISTED;

	/** 记录结果应用到选区的方式。 */
	public enum RecordApplyMode implements IConfigOptionListEntry {
		/** 把记录位置合并为选区并添加。 */
		ADD("record_add"),

		/** 从已有选区中裁掉记录位置。 */
		SUBTRACT("record_subtract");

		private final String key;

		RecordApplyMode(String key) {
			this.key = key;
		}

		public Component displayName() {
			return Component.translatable(SelectiveRendering.MOD_ID + ".mode." + key);
		}

		@Override
		public String getStringValue() {
			return name();
		}

		@Override
		public String getDisplayName() {
			return displayName().getString();
		}

		@Override
		public RecordApplyMode cycle(boolean forward) {
			RecordApplyMode[] values = values();
			return values[Math.floorMod(ordinal() + (forward ? 1 : -1), values.length)];
		}

		@Override
		public RecordApplyMode fromString(String value) {
			if (value != null) {
				for (RecordApplyMode entry : values()) {
					if (entry.name().equalsIgnoreCase(value.trim())) {
						return entry;
					}
				}
			}

			return ADD;
		}
	}

	public static final RecordApplyMode DEFAULT_RECORD_APPLY_MODE = RecordApplyMode.ADD;

	public static final boolean DEFAULT_INVERT = false;

	public static final boolean DEFAULT_PASS_THROUGH = true;

	public static final boolean DEFAULT_PASS_THROUGH_ENTITIES = true;

	// 可变状态须持有 LOCK 读写，渲染侧读取 config 快照。

	private static Mode mode = DEFAULT_MODE;
	private static int transparency = DEFAULT_TRANSPARENCY;
	private static RecordMode recordMode = DEFAULT_RECORD_MODE;
	private static RecordApplyMode recordApplyMode = DEFAULT_RECORD_APPLY_MODE;
	private static boolean invert = DEFAULT_INVERT;
	private static String wand = DEFAULT_WAND;
	private static boolean fullBright = DEFAULT_FULL_BRIGHT;
	private static boolean passThrough = DEFAULT_PASS_THROUGH;
	private static boolean passThroughEntities = DEFAULT_PASS_THROUGH_ENTITIES;
	public static BlockPos corner1;
	public static BlockPos corner2;
	private static AABB region;

	/** 供渲染侧读取的配置快照，由 publish 整体替换。 */
	private static volatile RenderConfig config = new RenderConfig.Builder().build();

	public static RenderConfig config() {
		return config;
	}

	/** 当前选中的角点，-1 表示未选中；仅影响编辑界面。 */
	public static volatile int selectedCorner = -1;

	/**
	 * 两个角点围出的整个框被选中。
	 * 选中目标三选一：某个角点（selectedCorner）、整个框（本字段）、某个选区（selectedRegionId），
	 * 由 {@link com.selectiverendering.region.RegionEditor} 的选择方法保证互斥。不保存。
	 */
	public static volatile boolean selectionSelected = false;

	/** 魔杖中键选中的选区，不保存。 */
	public static volatile java.util.UUID selectedRegionId;

	private SelectiveRenderingManager() {
	}

	/** 从磁盘配置恢复运行时状态并发布快照。 */
	public static void load() {
		ModConfig config = ModConfig.get();

		synchronized (LOCK) {
			RULES.clear();
			RECORDED.clear();
			REGIONS.clear();

			mode = config.mode == null ? Mode.OFF : config.mode;
			transparency = Math.clamp(config.transparency, 0, MAX_TRANSPARENCY);
			recordMode = config.recordMode == null ? DEFAULT_RECORD_MODE : config.recordMode;
			recordApplyMode = config.recordApplyMode == null ? DEFAULT_RECORD_APPLY_MODE : config.recordApplyMode;
			invert = config.invert;
			wand = config.wand == null || config.wand.isBlank() ? DEFAULT_WAND : config.wand.trim();
			fullBright = config.fullBright;
			passThrough = config.passThrough;
			passThroughEntities = config.passThroughEntities;

			setRules(config.rules);

			corner1 = toBlockPos(config.regionPos1);
			corner2 = toBlockPos(config.regionPos2);
			selectionSelected = false;
			selectedCorner = corner2 != null ? 1 : corner1 != null ? 0 : -1;
			updateRegion();

			setRecorded(config.recorded);

			for (ModConfig.RegionEntry entry : config.regionEntries) {
				Region added = toRegion(entry);
				if (added != null && !REGIONS.contains(added)) {
					REGIONS.add(added);
				}
			}

			if (config.presets != null) {
				for (Preset preset : config.presets) {
					if (preset != null) {
						PRESETS.add(preset);
					}
				}
			}

			publish();
			rememberSources();
			RegionHistory.reset();
		}

		Log.debug("[state] loaded: mode {}, transparency {}%, {} rule(s), {} region(s), {} preset(s)", mode, transparency, RULES.size(), REGIONS.size(), PRESETS.size());
	}

	public static Mode getMode() {
		return config.mode();
	}

	public static void setMode(Mode newMode) {
		synchronized (LOCK) {
			if (mode == newMode) {
				return;
			}

			mode = newMode;
			commit();
		}

		Log.debug("[state] mode {}", newMode.name());
		RebuildScheduler.rebuildChunks();
	}

	public static void cycleMode(int direction) {
		setMode(getMode().cycle(direction));
	}

	public static int getTransparency() {
		return config.transparency();
	}

	/** 设置透明度百分比：100 为完全隐藏，0 为不透明。 */
	public static void setTransparency(int value) {
		int clamped = Math.clamp(value, 0, MAX_TRANSPARENCY);
		int previous;

		synchronized (LOCK) {
			if (clamped == transparency) {
				return;
			}

			previous = transparency;
			transparency = clamped;
			commit();
		}

		Log.debug("[state] transparency {}%", clamped);

		// 两端都是半透明时只有透明度数值变了，常驻缓冲在绘制时取最新透明度，不用重建区块。
		// 完全隐藏（MAX）不建网格，进出这个状态都要重建。
		boolean partial = previous > 0 && previous < MAX_TRANSPARENCY && clamped > 0 && clamped < MAX_TRANSPARENCY;
		if (partial && RetainedTerrain.alphaFollowsDraw()) {
			return;
		}

		RebuildScheduler.rebuildHidden();
	}

	public static List<Region> regions() {
		return config.regions();
	}

	/** 当前选中的选区；没有选中或已被删除时为 null。 */
	public static Region selectedRegion() {
		java.util.UUID id = selectedRegionId;

		if (id != null) {
			for (Region region : config.regions()) {
				if (region.id().equals(id)) {
					return region;
				}
			}
		}

		return null;
	}

	public static List<Region> blockRegions() {
		return config.blockRegions();
	}

	public static List<String> regionSources() {
		return config.regions().stream().map(Region::toSource).toList();
	}

	public static void updateRegion() {
		if (corner1 == null || corner2 == null) {
			region = null;
			return;
		}

		region = new AABB(
			Math.min(corner1.getX(), corner2.getX()),
			Math.min(corner1.getY(), corner2.getY()),
			Math.min(corner1.getZ(), corner2.getZ()),
			Math.max(corner1.getX(), corner2.getX()) + 1,
			Math.max(corner1.getY(), corner2.getY()) + 1,
			Math.max(corner1.getZ(), corner2.getZ()) + 1);
	}

	public static boolean addRule(String source) {
		BlockMatchRule rule = BlockMatchRule.parse(source);
		if (rule == null) {
			return false;
		}

		synchronized (LOCK) {
			if (hasRule(rule.source())) {
				return false;
			}

			RULE_SOURCES.add(rule.source());
			RULES.add(rule);
			commitAndRemember();
		}

		Log.debug("[state] rule added: {}", rule.source());
		RebuildScheduler.rebuildAfterListChange(true);
		return true;
	}

	public static boolean removeRule(String source) {
		synchronized (LOCK) {
			if (!RULE_SOURCES.removeIf(existing -> existing.equalsIgnoreCase(source.trim()))) {
				return false;
			}

			RULES.removeIf(rule -> rule.source().equalsIgnoreCase(source.trim()));

			commitAndRemember();
		}

		Log.debug("[state] rule removed: {}", source.trim());
		RebuildScheduler.rebuildAfterListChange(false);
		return true;
	}

	public static List<String> ruleSources() {
		return config.ruleSources();
	}

	public static void setRuleSources(List<String> sources) {
		List<String> wanted = sources == null ? List.of() : List.copyOf(sources);
		if (wanted.equals(ruleSourceInput)) {
			return;
		}

		ruleSourceInput = wanted;

		synchronized (LOCK) {
			setRules(sources);
			commit();
		}

		if (getMode().usesList()) {
			RebuildScheduler.rebuildChunks();
		}
	}

	public static boolean hasRule(String source) {
		String trimmed = source.trim();
		for (String existing : RULE_SOURCES) {
			if (existing.equalsIgnoreCase(trimmed)) {
				return true;
			}
		}

		return false;
	}

	public static List<String> recordSources() {
		return config.recordSources();
	}

	/** 更新记录名单，同时检查输入缓存与实际名单是否一致。 */
	public static void setRecordSources(List<String> sources) {
		List<String> wanted = sources == null ? List.of() : List.copyOf(sources);

		synchronized (LOCK) {
			if (wanted.equals(recordSourceInput) && wanted.equals(RECORD_SOURCES)) {
				return;
			}

			recordSourceInput = wanted;
			setRecorded(wanted);
			commit();
		}
	}

	private static void setRules(List<String> sources) {
		RULE_SOURCES.clear();
		RULES.clear();
		parseInto(sources, RULE_SOURCES, RULES);
	}

	private static void setRecorded(List<String> sources) {
		RECORD_SOURCES.clear();
		RECORDED.clear();
		parseInto(sources, RECORD_SOURCES, RECORDED);
	}

	/** 每一项去掉首尾空白、去重后原样保留，能解析的另存一份用于匹配。 */
	private static void parseInto(List<String> sources, List<String> kept, List<BlockMatchRule> parsed) {
		if (sources == null) {
			return;
		}

		for (String source : sources) {
			String text = source == null ? "" : source.trim();
			if (text.isEmpty() || kept.stream().anyMatch(existing -> existing.equalsIgnoreCase(text))) {
				continue;
			}

			kept.add(text);

			BlockMatchRule rule = BlockMatchRule.parse(text);
			if (rule != null) {
				parsed.add(rule);
			}
		}
	}

	public static boolean hasRecord(String source) {
		String trimmed = source.trim();
		for (String existing : RECORD_SOURCES) {
			if (existing.equalsIgnoreCase(trimmed)) {
				return true;
			}
		}

		return false;
	}

	public static RecordMode getRecordMode() {
		return config.recordMode();
	}

	public static void setRecordMode(RecordMode newMode) {
		synchronized (LOCK) {
			if (newMode == null || newMode == recordMode) {
				return;
			}

			recordMode = newMode;
			commit();
		}

		Log.debug("[state] record mode {}", newMode.name());
	}

	public static RecordApplyMode getRecordApplyMode() {
		return config.recordApplyMode();
	}

	public static void setRecordApplyMode(RecordApplyMode newMode) {
		if (newMode == null) {
			return;
		}

		synchronized (LOCK) {
			if (newMode == recordApplyMode) {
				return;
			}

			recordApplyMode = newMode;
			commit();
		}

		Log.debug("[state] record apply mode {}", newMode.name());
	}

	public static boolean isInvert() {
		return config.invert();
	}

	public static void setInvert(boolean value) {
		synchronized (LOCK) {
			if (value == invert) {
				return;
			}

			invert = value;
			commit();
		}

		Log.debug("[state] invert {}", value);
		RebuildScheduler.rebuildChunks();
	}

	/** 返回保存的穿透交互偏好。 */
	public static boolean isPassThrough() {
		return config.passThrough();
	}

	/** 穿透交互是否生效；手持魔杖时关闭，以便选中淡化方块。 */
	public static boolean isPassThroughActive() {
		RenderConfig current = config;
		return current.passThrough() && current.mode() != Mode.OFF && !SelectiveRendering.isWandHeld();
	}

	/** 返回保存的穿透实体偏好。 */
	public static boolean isPassThroughEntities() {
		return config.passThroughEntities();
	}

	/** 穿透实体是否生效：穿透交互开着才算，和方块同一套门控。 */
	public static boolean isPassThroughEntitiesActive() {
		RenderConfig current = config;
		return current.passThroughEntities() && current.passThrough() && current.mode() != Mode.OFF && !SelectiveRendering.isWandHeld();
	}

	public static void setPassThrough(boolean value) {
		synchronized (LOCK) {
			if (value == passThrough) {
				return;
			}

			passThrough = value;
			commit();
		}

		Log.debug("[state] pass through {}", value);
	}

	public static void setPassThroughEntities(boolean value) {
		synchronized (LOCK) {
			if (value == passThroughEntities) {
				return;
			}

			passThroughEntities = value;
			commit();
		}

		Log.debug("[state] pass through entities {}", value);
	}

	/** 按记录名单和过滤模式判断是否记录；空名单表示不过滤。 */
	public static boolean matchesRecorded(BlockState state, boolean moving) {
		RenderConfig current = config;
		List<BlockMatchRule> rules = current.recorded();
		if (rules.isEmpty()) {
			return true;
		}

		boolean listed = false;

		for (BlockMatchRule rule : rules) {
			if (rule.matches(state, moving)) {
				listed = true;
				break;
			}
		}

		return (current.recordMode() == RecordMode.LISTED) == listed;
	}

	public static String getWand() {
		return config.wand();
	}

	public static void setWand(String value) {
		String trimmed = value == null ? "" : value.trim();
		if (trimmed.isEmpty()) {
			trimmed = DEFAULT_WAND;
		}

		synchronized (LOCK) {
			if (trimmed.equals(wand)) {
				return;
			}

			wand = trimmed;
			commit();
		}
	}

	public static boolean isFullBright() {
		return config.fullBright();
	}

	public static boolean isNightVisionActive() {
		RenderConfig current = config;
		return current.mode() != Mode.OFF && current.fullBright();
	}

	/** 切换全亮前先还原旧光照，再按新设置重建。 */
	public static void setFullBright(boolean value) {
		if (value == isFullBright()) {
			return;
		}

		RebuildScheduler.rebuildChunks();

		synchronized (LOCK) {
			fullBright = value;
			commit();
		}

		RebuildScheduler.rebuildChunks();
	}

	private static BlockPos toBlockPos(int[] values) {
		return values == null || values.length != 3 ? null : new BlockPos(values[0], values[1], values[2]);
	}

	private static int[] toArray(BlockPos pos) {
		return pos == null ? null : new int[] {pos.getX(), pos.getY(), pos.getZ()};
	}

	private static int[] toArray(Area area) {
		return new int[] {
				area.min().getX(), area.min().getY(), area.min().getZ(),
				area.max().getX(), area.max().getY(), area.max().getZ()
		};
	}

	private static ModConfig.RegionEntry toEntry(Region region) {
		ModConfig.RegionEntry entry = new ModConfig.RegionEntry();
		entry.areas = region.areas().stream().map(SelectiveRenderingManager::toArray).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		entry.color = region.color();
		entry.blocks = region.options().blocks();
		entry.entities = region.options().entities();
		entry.particles = region.options().particles();
		entry.rules = new ArrayList<>(region.options().ruleSources());
		entry.entityTypes = new ArrayList<>(region.options().entityTypes());
		entry.particleTypes = new ArrayList<>(region.options().particleTypes());
		entry.alphaOverride = region.options().transparencyOverride();
		entry.offsetX = region.options().offsetX();
		entry.offsetY = region.options().offsetY();
		entry.offsetZ = region.options().offsetZ();
		return entry;
	}

	private static Region toRegion(ModConfig.RegionEntry entry) {
		if (entry == null) {
			return null;
		}

		List<Area> areas = new ArrayList<>();

		if (entry.areas != null) {
			for (int[] values : entry.areas) {
				Area area = toArea(values);
				if (area != null) {
					areas.add(area);
				}
			}
		}

		Area legacy = toArea(entry.box);
		if (areas.isEmpty() && legacy != null) {
			areas.add(legacy);
		}

		if (areas.isEmpty()) {
			return null;
		}

		return new Region(
			java.util.UUID.randomUUID(),
			entry.color == null ? Region.randomColor() : entry.color,
			areas,
			new RegionOptions(entry.blocks, entry.entities, entry.particles, entry.rules, entry.entityTypes, entry.particleTypes, entry.alphaOverride, entry.offsetX, entry.offsetY, entry.offsetZ));
	}

	private static Area toArea(int[] values) {
		return values == null || values.length != 6
			? null
			: new Area(
				new BlockPos(values[0], values[1], values[2]),
				new BlockPos(values[3], values[4], values[5]));
	}

	public static List<Preset> presets() {
		return config.presets();
	}

	/** 整体应用预设并重建区块。 */
	public static void applyPreset(Preset preset) {
		synchronized (LOCK) {
			mode = preset.mode == null ? DEFAULT_MODE : preset.mode;
			transparency = Math.clamp(preset.transparency, 0, MAX_TRANSPARENCY);
			invert = preset.invert;

			setRules(preset.rules);

			REGIONS.clear();
			if (preset.regions != null) {
				for (String source : preset.regions) {
					Region added = Region.parse(source);
					if (added != null && !REGIONS.contains(added)) {
						REGIONS.add(added);
					}
				}
			}

			commitAndRemember();
		}

		Log.debug("[state] preset applied: {}", preset.displayName());
		RebuildScheduler.rebuildChunks();
	}

	/** 保存原地修改后的预设。 */
	public static void savePresets() {
		synchronized (LOCK) {
			persist();
		}
	}

	public static void setPresets(List<Preset> presets) {
		synchronized (LOCK) {
			PRESETS.clear();

			if (presets != null) {
				for (Preset preset : presets) {
					if (preset != null) {
						PRESETS.add(preset);
					}
				}
			}

			commit();
		}
	}

	/** 刷新快照并请求保存；调用方须持有 LOCK。 */
	public static void commit() {
		publish();
		persist();
	}

	/** 刷新快照、同步界面输入缓存并请求保存；调用方须持有 LOCK。 */
	public static void commitAndRemember() {
		publish();
		rememberSources();
		persist();
		RegionHistory.record();
	}

	/** 发布新的配置快照并使判定缓存失效；调用方须持有 LOCK。 */
	private static void publish() {
		RenderConfig.Builder next = new RenderConfig.Builder();
		next.mode = mode;
		next.transparency = transparency;
		next.invert = invert;
		next.rules = RULES;
		next.recorded = RECORDED;
		next.ruleSources = RULE_SOURCES;
		next.recordSources = RECORD_SOURCES;
		next.regions = REGIONS;
		next.presets = PRESETS;
		next.recordMode = recordMode;
		next.recordApplyMode = recordApplyMode;
		next.wand = wand;
		next.fullBright = fullBright;
		next.passThrough = passThrough;
		next.passThroughEntities = passThroughEntities;
		next.corner1 = corner1;
		next.corner2 = corner2;
		next.region = region;
		config = next.build();
	}

	/** 缓存界面输入，避免配置同步触发重复写回。 */
	private static void rememberSources() {
		ruleSourceInput = List.copyOf(RULE_SOURCES);
		recordSourceInput = List.copyOf(RECORD_SOURCES);
	}

	/** 将运行时状态写回磁盘配置并请求保存。 */
	private static void persist() {
		ModConfig config = ModConfig.get();
		config.mode = mode;
		config.transparency = transparency;
		config.recordMode = recordMode;
		config.recordApplyMode = recordApplyMode;
		config.invert = invert;
		config.wand = wand;
		config.fullBright = fullBright;
		config.passThrough = passThrough;
		config.passThroughEntities = passThroughEntities;
		config.rules = List.copyOf(RULE_SOURCES);
		config.recorded = List.copyOf(RECORD_SOURCES);
		config.regionPos1 = toArray(corner1);
		config.regionPos2 = toArray(corner2);
		config.regionEntries = REGIONS.stream().map(SelectiveRenderingManager::toEntry).toList();
		config.presets = new ArrayList<>(PRESETS);
		ModConfig.save();
	}
}
