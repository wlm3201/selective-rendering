package com.selectiverendering.config.entrypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager.Mode;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.config.presets.Preset;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 磁盘配置的数据结构及读写入口；字段名对应 JSON 键。 */
public final class ModConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger(SelectiveRendering.MOD_ID);
	private static final String FILE_NAME = SelectiveRendering.MOD_ID + ".json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final int VERSION = 4;

	private static final long SAVE_DELAY_MS = 300L;

	private static final Object SAVE_LOCK = new Object();

	private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, SelectiveRendering.MOD_ID + "-config-save");
		thread.setDaemon(true);
		return thread;
	});

	private static String pendingJson;

	private static String writtenJson;

	private static ModConfig instance = new ModConfig();

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(ModConfig::writePending, SelectiveRendering.MOD_ID + "-config-shutdown"));
	}

	public int version = VERSION;

	public Mode mode = Mode.OFF;
	/** 透明度百分比：100 为完全隐藏，0 为不透明。 */
	public int transparency = 50;
	public List<String> rules = new ArrayList<>();

	/** 反转淡化条件，保留命中的方块并淡化其余方块。 */
	public boolean invert = SelectiveRenderingManager.DEFAULT_INVERT;

	/** 记录器名单，独立于渲染名单。 */
	public List<String> recorded = new ArrayList<>();

	public SelectiveRenderingManager.RecordMode recordMode = SelectiveRenderingManager.DEFAULT_RECORD_MODE;

	public SelectiveRenderingManager.RecordApplyMode recordApplyMode = SelectiveRenderingManager.DEFAULT_RECORD_APPLY_MODE;

	/** 角点坐标 [x,y,z]，null 表示未设置。 */
	public int[] regionPos1 = null;
	/** 角点坐标 [x,y,z]，null 表示未设置。 */
	public int[] regionPos2 = null;

	public List<RegionEntry> regionEntries = new ArrayList<>();

	public List<Preset> presets = new ArrayList<>();

	/** MaLiLib 序列化的快捷键配置。 */
	public JsonObject hotkeys = null;

	/** 旧版字段，仅用于迁移到 rules。 */
	public transient List<String> blocks = null;

	public String wand = "minecraft:breeze_rod";

	public boolean fullBright = SelectiveRenderingManager.DEFAULT_FULL_BRIGHT;

	public boolean passThrough = SelectiveRenderingManager.DEFAULT_PASS_THROUGH;

	/** 穿透交互是否连被淡化的实体一起无视。 */
	public boolean passThroughEntities = SelectiveRenderingManager.DEFAULT_PASS_THROUGH_ENTITIES;

	private ModConfig() {
	}

	/** 单个选区的落盘形式。 */
	public static final class RegionEntry {
		/** 每个范围 [minX,minY,minZ,maxX,maxY,maxZ] */
		public List<int[]> areas = new ArrayList<>();
		/** RGB，没有时读取时随机分配 */
		public Integer color;
		/** 旧版只有一个范围时的写法，只读取 */
		public int[] box;
		public boolean blocks = true;
		public boolean entities = false;
		public boolean particles = false;
		public List<String> rules = new ArrayList<>();
		public List<String> entityTypes = new ArrayList<>();
		public List<String> particleTypes = new ArrayList<>();
		/** -1 跟随全局，0..255 为这个选区自己的 alpha */
		public int alphaOverride = -1;
		public int offsetX;
		public int offsetY;
		public int offsetZ;
	}

	public static ModConfig get() {
		return instance;
	}

	/** 读取配置；文件不存在或解析失败时保留默认值。 */
	public static void load() {
		Path path = path();
		if (!Files.isRegularFile(path)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
			if (loaded != null) {
				migrate(loaded);
				instance = loaded;
			}
		} catch (Exception e) {
			LOGGER.error("Could not read {}, keeping defaults", FILE_NAME, e);
		}
	}

	/** 迁移旧版字段并补齐空集合。 */
	private static void migrate(ModConfig config) {
		if (config.version < VERSION && config.rules == null) {
			config.rules = config.blocks == null ? new ArrayList<>() : config.blocks;
		}

		if (config.presets == null) {
			config.presets = new ArrayList<>();
		}

		if (config.recorded == null) {
			config.recorded = new ArrayList<>();
		}

		if (config.rules == null) {
			config.rules = new ArrayList<>();
		}

		if (config.regionEntries == null) {
			config.regionEntries = new ArrayList<>();
		}

		if (config.hotkeys != null && !config.hotkeys.isJsonObject()) {
			config.hotkeys = null;
		}

		config.blocks = null;
		config.version = VERSION;
	}

	/** 请求后台保存，合并防抖窗口内的重复请求。 */
	public static void save() {
		String json;

		synchronized (SAVE_LOCK) {
			json = GSON.toJson(instance);

			if (json.equals(pendingJson) || json.equals(writtenJson)) {
				return;
			}

			pendingJson = json;
		}

		SCHEDULER.schedule(ModConfig::writePending, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
	}

	/** 立即写入待保存内容，阻塞至完成。 */
	public static void flush() {
		writePending();
	}

	/** 通过临时文件替换配置文件，优先使用原子移动。 */
	private static void writePending() {
		String json;

		synchronized (SAVE_LOCK) {
			json = pendingJson;
			pendingJson = null;

			if (json == null || json.equals(writtenJson)) {
				return;
			}
		}

		try {
			Path path = path();
			Files.createDirectories(path.getParent());

			Path temp = Files.createTempFile(path.getParent(), FILE_NAME, ".tmp");

			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				writer.write(json);
			}

			try {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
			}

			synchronized (SAVE_LOCK) {
				writtenJson = json;
			}
		} catch (IOException e) {
			LOGGER.error("Could not write {}", FILE_NAME, e);
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}
}
