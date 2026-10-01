package com.selectiverendering;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.selectiverendering.SelectiveRenderingManager.Mode;
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

/**
 * 磁盘配置文件（{@code <game>/config/selective_rendering.json}）的 POJO + 读写器。
 *
 * <p>用 Gson 直接序列化字段，所以<b>字段名就是 JSON 里的键</b>，改名会破坏旧存档配置。
 *
 * <h2>注意：这里不维护"运行时状态"</h2>
 * <p>真正的运行时状态在 {@link SelectiveRenderingManager}。本类只是：
 * <ul>
 *   <li>启动时 {@link #load()} 一次，把内容交给 manager 消化；</li>
 *   <li>之后所有修改都由 manager 通过 {@link #save()} 单向写回。</li>
 * </ul>
 * 换句话说，本类的字段在启动之后基本只作为"落盘的中间载体"存在。
 *
 * <h2>落盘策略</h2>
 * <p>{@link #save()} 可能被高频调用（例如拖透明度滑块、滚动滚轮移动角点，
 * 每次都触发一次）。所以它不是同步写文件，而是：
 * <ol>
 *   <li>先在调用线程把 {@link #instance} 序列化成字符串（快，且能拿到一致快照）；</li>
 *   <li>内容没变化时直接跳过；</li>
 *   <li>否则交给后台单线程延迟 {@code SAVE_DELAY_MS} 写入，期间的新 save 会合并；</li>
 *   <li>写入用"临时文件 + {@code ATOMIC_MOVE}"，避免崩溃留下半个文件；</li>
 *   <li>JVM 退出时 {@code flush()} 兜底，保证不丢最后一次修改。</li>
 * </ol>
 *
 * <p>{@code blocks} 是老版本配置用的旧字段名，仅在 {@link #migrate} 中用于搬运到
 * {@code rules}，为兼容旧文件而保留。
 */
public final class ModConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger(SelectiveRendering.MOD_ID);
	private static final String FILE_NAME = SelectiveRendering.MOD_ID + ".json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final int VERSION = 4;

	/** 防抖窗口。连续改动在这段时间内只会写一次盘。 */
	private static final long SAVE_DELAY_MS = 300L;

	private static final Object SAVE_LOCK = new Object();

	private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, SelectiveRendering.MOD_ID + "-config-save");
		thread.setDaemon(true);
		return thread;
	});

	/** 待写入的内容（{@code null} 表示没有待写）。 */
	private static String pendingJson;

	/** 上一次真正写进文件的内容，用于去重。 */
	private static String writtenJson;

	private static ModConfig instance = new ModConfig();

	static {
		// 退出前兜底刷一次，避免最后 300ms 内的改动丢失。
		Runtime.getRuntime().addShutdownHook(new Thread(ModConfig::writePending, SelectiveRendering.MOD_ID + "-config-shutdown"));
	}

	public int version = VERSION;

	/** 当前渲染模式，见 {@link SelectiveRenderingManager.Mode}。 */
	public Mode mode = Mode.OFF;
	/** 隐藏方块的透明度百分比：100 = 完全隐藏，0 = 不透明。 */
	public int transparency = 50;
	/** 渲染用的方块名单（原始规则字符串）。 */
	public List<String> rules = new ArrayList<>();

	/**
	 * 是否"反转"：命中的那一批<b>不</b>淡化，其余全部淡化。
	 * 配 {@code mode} 一起用，可以表达"只显示选区内的指定方块"这类取反条件。
	 */
	public boolean invert = SelectiveRenderingManager.DEFAULT_INVERT;

	/** 记录器用的方块名单，与 {@link #rules} 是两份独立的名单。 */
	public List<String> recorded = new ArrayList<>();

	/** 记录器的过滤方向。 */
	public SelectiveRenderingManager.RecordMode recordMode = SelectiveRenderingManager.DEFAULT_RECORD_MODE;

	/** 录制结束后，记录到的位置是新增进选区还是从选区里裁剪掉。 */
	public SelectiveRenderingManager.RecordApplyMode recordApplyMode = SelectiveRenderingManager.DEFAULT_RECORD_APPLY_MODE;

	/** 选区角点 1，{@code [x,y,z]}；没设置过就是 null。 */
	public int[] regionPos1 = null;
	/** 选区角点 2，{@code [x,y,z]}；没设置过就是 null。 */
	public int[] regionPos2 = null;

	/** 已保存的选区，每个是 {@code [minX,minY,minZ,maxX,maxY,maxZ]} 共 6 个数。 */
	public List<int[]> regions = new ArrayList<>();

	public List<Preset> presets = new ArrayList<>();

	/**
	 * 快捷键 / 按键，存的是 MaLiLib {@code ConfigUtils.writeConfigBase} 产出的
	 * 那个 category 对象（键 = 配置项名，值 = {@code ConfigHotkey} 的 json）。
	 *
	 * <p><b>为什么必须自己存</b>：MaLiLib 的 {@code IConfigManager} 只有
	 * {@code load()} / {@code save()} 两个回调，persist 这件事整个甩给 mod 自己，
	 * 它<b>不会</b>替第三方 mod 序列化任何配置项——快捷键也不例外。
	 * 不自己存，改好的按键重进游戏就会被打回默认值（如 {@code X,V}）。
	 *
	 * <p>用 {@code JsonObject} 而不是手搓 {@code Map<String, String>}，是为了直接用
	 * MaLiLib 官方的 {@code ConfigUtils}（和 MaLiLib 自己 / Litematica 的做法一致），
	 * 按键的高级设置（顺序敏感 / 独占 / 触发时机……）也能一并存下来。
	 */
	public JsonObject hotkeys = null;

	/** 已废弃：老版本配置里叫这个名字，仅在 {@link #migrate} 中用于搬运到 {@code rules}。 */
	public transient List<String> blocks = null;

	/** 魔杖物品 id，默认旋风棒（breeze rod）。 */
	public String wand = "minecraft:breeze_rod";

	/** 夜视：全局按全亮渲染，并跳过所有光照相关的计算。 */
	public boolean fullBright = SelectiveRenderingManager.DEFAULT_FULL_BRIGHT;

	private ModConfig() {
	}

	public static ModConfig get() {
		return instance;
	}

	/**
	 * 读配置文件；文件不存在就保持默认值（首次启动）。
	 * 解析出错时记日志并<b>保留默认配置</b>，不让一个坏配置把游戏卡在启动阶段。
	 */
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

	/**
	 * 兼容老配置：版本号低于 {@link #VERSION} 时把旧字段 {@code blocks} 搬到 {@code rules}。
	 * 集合字段一律补成非 null（坏配置 / 手改过的配置里可能是 null）。
	 */
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

		if (config.regions == null) {
			config.regions = new ArrayList<>();
		}

		// 快捷键：null 表示"文件里还没有这一节"（首次升级上来），交给调用方去写
		if (config.hotkeys != null && !config.hotkeys.isJsonObject()) {
			config.hotkeys = null;
		}

		config.blocks = null;
		config.version = VERSION;
	}

	/**
	 * 请求一次落盘。真正的写入会延后 {@code SAVE_DELAY_MS}，期间重复调用会被合并。
	 *
	 * <p>调用方（{@code SelectiveRenderingManager.persist()}）通常在锁内调用本方法，
	 * 所以这里只做一次序列化（快），不做任何 I/O。
	 */
	public static void save() {
		String json;

		synchronized (SAVE_LOCK) {
			json = GSON.toJson(instance);

			// 内容没变就不排队，避免"改了个值又改回来"或重复 persist 造成无谓写盘。
			if (json.equals(pendingJson) || json.equals(writtenJson)) {
				return;
			}

			pendingJson = json;
		}

		SCHEDULER.schedule(ModConfig::writePending, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
	}

	/**
	 * 立即把待写内容刷到磁盘，<b>会阻塞到写完</b>。
	 *
	 * <p>用于"必须现在就存住"的场合：MaLiLib 的配置保存、JVM 退出前的兜底。
	 * 普通的改动走 {@link #save()}（防抖、后台写）就够了。
	 */
	public static void flush() {
		writePending();
	}

	/** 真正写文件：临时文件 + ATOMIC_MOVE，避免崩溃留下半个配置文件。 */
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
				// 某些文件系统（如网络盘）不支持原子移动，退化成普通替换。
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
