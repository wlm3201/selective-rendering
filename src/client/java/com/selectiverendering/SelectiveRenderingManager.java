package com.selectiverendering;

import com.selectiverendering.compat.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局状态中心：模式 / 透明度 / 方块名单 / 选区 / 预设，以及"某方块该不该被淡化"这个核心判定。
 *
 * <h2>它在架构里的位置</h2>
 * <pre>
 *   配置界面(ModConfigs)  ─┐
 *   魔杖交互(MouseHandler)─┼─► 本类(写状态, persist 落盘, 触发区块重建)
 *   记录器(BlockChangeRecorder)─┘
 *                                    │
 *   渲染管线(mixin 们)  ─────────────┴─► getAlpha(state,pos)  ← 每帧/每次网格重建被调用几十万次
 * </pre>
 *
 * <h2>两套数据：可变集合 + 不可变快照</h2>
 * <p>{@link #RULES} / {@link #REGIONS} / {@link #PRESETS} 是真正被修改的集合，
 * 修改时必须持 {@link #LOCK}；修改完调用 {@link #publish()} 生成不可变快照
 * （{@code ruleSnapshot} 等 {@code volatile} 字段）。
 * 渲染线程只读快照，因此遍历时不需要加锁——这是本类能在渲染热路径上
 * 无锁工作的关键。<b>代价：所有写操作后都必须记得调用 {@code publish()}，
 * 漏掉就会出现"改了但画面没变"。</b>
 *
 * <h2>返回值的约定（非常重要）</h2>
 * <p>{@link #getAlpha} 返回 {@code -1} 表示"这个方块正常渲染"；
 * 返回 {@code 0..255} 表示"这个方块要被淡化"，值就是顶点 alpha。
 * <ul>
 *   <li>{@code alpha == 0}　→ 完全隐藏（透明度过 100%），mixin 直接 {@code ci.cancel()}；</li>
 *   <li>{@code 0 < alpha < 255} → 半透明，保留几何但改顶点色 + 换到半透明层；</li>
 *   <li>{@code -1}　　　　　　→ 原样渲染。</li>
 * </ul>
 *
 * <h2>已知问题（保留现状，改动前请先读）</h2>
 * <ul>
 *   <li>{@link #getAlpha} 是 O(规则数 + 选区数) 的线性扫描，且会被区块构建工作线程、
 *       光照线程并发调用，没有任何缓存；选区/名单很大时这里会成为热点。</li>
 *   <li>{@link #getAlpha} 里为了处理"活塞推动中的方块"会去访问
 *       {@code Minecraft.getInstance().level}（客户端世界），这是<b>跨线程访问客户端世界</b>，
 *       只在 {@code carried} 为真时触发，属于可接受的脆弱点但值得警惕。</li>
 *   <li>{@link #getAlpha} 有副作用：命中时会往 {@link HiddenSections} 里记账。
 *       一个"查询"方法改全局状态，阅读时容易看漏。</li>
 * </ul>
 *
 * <h2>修改状态的标准姿势</h2>
 * <pre>
 *   synchronized (LOCK) {
 *       ...改 RULES / REGIONS / PRESETS / 各标量字段...
 *       commit();              // 或 commitAndRemember()
 *   }
 *   ...在锁外做重建 / 重算光照等耗时操作...
 * </pre>
 * {@link #commit()} 与 {@link #commitAndRemember()} 内部会做
 * {@code publish() + persist()}，并要求调用者已持有 {@link #LOCK}。
 * <b>不要</b>再单独调用 {@code publish()} / {@code persist()}。
 */
public final class SelectiveRenderingManager {
	public static final int TRANSPARENCY_STEP = 1;
	public static final int MAX_TRANSPARENCY = 100;

	public static final Mode DEFAULT_MODE = Mode.OFF;
	public static final int DEFAULT_TRANSPARENCY = 50;
	public static final String DEFAULT_WAND = "minecraft:breeze_rod";
	public static final boolean DEFAULT_FULL_BRIGHT = true;

	/**
	 * 渲染模式。名字的读法是「<b>范围</b> + <b>名单条件</b>」：
	 * 范围决定"在哪儿"，名单条件决定"哪种方块"，两者同时满足才被淡化。
	 *
	 * <p>对照表（"隐藏"= 被淡化/半透明）：
	 * <pre>
	 *   OFF                     不隐藏任何东西（功能关闭）
	 *   REGION_INSIDE           选区内全部隐藏
	 *   REGION_INSIDE_LISTED    选区内 且 在名单里的
	 *   REGION_INSIDE_UNLISTED  选区内 且 不在名单里的
	 *   REGION_OUTSIDE          选区外全部隐藏
	 *   REGION_OUTSIDE_LISTED   选区外 且 在名单里的
	 *   REGION_OUTSIDE_UNLISTED 选区外 且 不在名单里的
	 *   BLACKLIST               全局，名单里的隐藏
	 *   WHITELIST               全局，名单外的隐藏（只剩名单里的可见）
	 * </pre>
	 *
	 * <p>枚举顺序 = 滚轮循环顺序 = 配置下拉框顺序，不要随意调整。
	 */
	public enum Mode {
		OFF("off"),
		REGION_INSIDE_LISTED("region_inside_listed"),
		REGION_INSIDE_UNLISTED("region_inside_unlisted"),
		REGION_INSIDE("region_inside"),
		REGION_OUTSIDE_LISTED("region_outside_listed"),
		REGION_OUTSIDE_UNLISTED("region_outside_unlisted"),
		REGION_OUTSIDE("region_outside"),
		BLACKLIST("blacklist"),
		WHITELIST("whitelist");

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

		public boolean usesRegion() {
			return this != OFF && this != BLACKLIST && this != WHITELIST;
		}

		public boolean usesList() {
			return this != OFF && this != REGION_INSIDE && this != REGION_OUTSIDE;
		}

		/**
		 * "名单里的是被隐藏的那一批吗？"
		 *
		 * <p>用于 {@link #rebuildAfterListChange}：往名单里加一条规则时，
		 * 如果本模式隐藏的正是名单里的方块（黑名单语义），那新增的那一个方块
		 * 从可见变隐藏，可以只重建它所在的 section；
		 * 反之（白名单语义）新增规则意味着名单外的方块……其实没有变化，
		 * 只有"删除规则"才会让方块从隐藏变可见。
		 */
		public boolean hidesListed() {
			return this == BLACKLIST || this == REGION_INSIDE_LISTED || this == REGION_OUTSIDE_LISTED;
		}
	}

	/** 光最多传播 15 格，所以重算光照的范围要在选区外扩一圈。 */
	private static final int LIGHT_RANGE = 15;

	/** 裁剪允许的最大碎片数，超过就放弃整个操作（见 {@link #subtractRegions}）。 */
	private static final int MAX_SUBTRACT_FRAGMENTS = 4096;

	/** 光照任务代际号：每排一个新任务就 +1，旧任务看到代号变了就自行退出。 */
	private static final AtomicLong RELIGHT_GENERATION = new AtomicLong();

	/**
	 * "曾经被隐藏过、但现在已不在 {@link #REGIONS} 里"的区域。
	 *
	 * <p>删掉 / 裁掉一个选区之后，那块地方的光照仍然带着我们污染过的数据
	 * （当时那些方块在光照引擎里被当成空气），必须再算一遍才能恢复。
	 * 但它们已经不在 {@link #regionSnapshot} 里，所以要单独记一份，
	 * 每次重算光照时一起带上。
	 *
	 * <p>只增不减：见 {@link #relight()} 里"新任务的目标必须是旧任务的超集"的说明。
	 * 元素数等于"历史上删/裁过的选区总数"，每个只是一个 record，可以忽略。
	 */
	private static final List<Region> RETIRED_REGIONS = new ArrayList<>();

	private static final Object LOCK = new Object();
	private static final List<BlockMatchRule> RULES = new ArrayList<>();

	private static final List<BlockMatchRule> RECORDED = new ArrayList<>();

	private static final List<Region> REGIONS = new ArrayList<>();

	private static final List<Preset> PRESETS = new ArrayList<>();

	private static volatile List<String> ruleSourceInput = List.of();
	private static volatile List<String> regionSourceInput = List.of();
	private static volatile List<String> recordSourceInput = List.of();

	/**
	 * 记录器（{@link BlockChangeRecorder}）的过滤方式：
	 * 一次"记录"只关心某一类方块的变化。
	 * <ul>
	 *   <li>{@link #LISTED}　只记录<b>名单内</b>方块发生变化的坐标；</li>
	 *   <li>{@link #UNLISTED} 只记录<b>名单外</b>方块发生变化的坐标。</li>
	 * </ul>
	 * 名单是配置里的 {@code recorded}（对应 {@link #RECORDED}），
	 * 和渲染用的 {@link #RULES} 是两份独立的名单。
	 */
	public enum RecordMode {
		LISTED("record_listed"),
		UNLISTED("record_unlisted");

		private final String key;

		RecordMode(String key) {
			this.key = key;
		}

		public Component displayName() {
			return Component.translatable(SelectiveRendering.MOD_ID + ".mode." + key);
		}
	}

	/**
	 * 记录器默认走<b>白名单</b>（{@link RecordMode#LISTED}）：只记录名单里的方块发生变化的位置。
	 *
	 * <p>早期默认是黑名单（记录名单外的所有变化），实际用起来很容易录进一堆无关方块——
	 * 大部分人录的时候心里想的是"我要这一类"，所以默认改成白名单。
	 *
	 * <p>注意白名单 + 空名单会退化成"什么都录不到"，因此
	 * {@link #matchesRecorded} 对空名单做了兜底（当成不过滤），并在开始记录时提示一句。
	 */
	public static final RecordMode DEFAULT_RECORD_MODE = RecordMode.LISTED;

	/**
	 * 录制结束后，记录到的位置要怎么作用到选区列表上。
	 *
	 * <p>和 {@link RecordMode} 是两个正交的维度：
	 * {@code RecordMode} 决定"哪些方块的变化值得记录"（名单内 / 名单外），
	 * 本枚举决定"记录到的位置最终怎么用"。
	 */
	public enum RecordApplyMode {
		/** 新增：把记录到的位置合并成长方体，加进选区列表。 */
		ADD("record_add"),

		/** 裁剪：把记录到的位置从已有选区里挖掉。 */
		SUBTRACT("record_subtract");

		private final String key;

		RecordApplyMode(String key) {
			this.key = key;
		}

		public Component displayName() {
			return Component.translatable(SelectiveRendering.MOD_ID + ".mode." + key);
		}
	}

	public static final RecordApplyMode DEFAULT_RECORD_APPLY_MODE = RecordApplyMode.ADD;

	/**
	 * "反转"是否默认开启。默认关闭：行为与老版本一致（命中的那批被淡化）。
	 *
	 * <p>开启后含义变成"<b>除了</b>命中的那批，其余全部淡化"，
	 * 于是"选区内 + 名单内 + 反转"就是"只显示选区内的指定方块"——
	 * 这是不引入 6 个新模式枚举就能表达"取反"的办法。
	 */
	public static final boolean DEFAULT_INVERT = false;

	/**
	 * "穿透交互"是否默认开启：准星射线把被淡化的方块当空气，
	 * 于是能瞄准、描边、破坏、右键它<b>后面</b>的方块。
	 *
	 * <p>默认开启——这正是本功能的全部意义；关掉就退回"淡化方块照样挡住准星"。
	 */
	public static final boolean DEFAULT_PASS_THROUGH = true;

	private static volatile Mode mode = DEFAULT_MODE;
	private static volatile int transparency = DEFAULT_TRANSPARENCY;
	private static volatile RecordMode recordMode = DEFAULT_RECORD_MODE;
	private static volatile RecordApplyMode recordApplyMode = DEFAULT_RECORD_APPLY_MODE;
	private static volatile boolean invert = DEFAULT_INVERT;
	private static volatile List<BlockMatchRule> ruleSnapshot = List.of();
	private static volatile List<BlockMatchRule> recordSnapshot = List.of();
	private static volatile List<Region> regionSnapshot = List.of();
	private static volatile List<Preset> presetSnapshot = List.of();

	private static volatile String wand = DEFAULT_WAND;

	/**
	 * "夜视"是否开启（默认开启）。
	 *
	 * <p>开启后两件事：
	 * <ol>
	 *   <li>{@code LightmapMixin} 把光照贴图整块刷成白色，全局按全亮渲染
	 *       ——参照 meteor 的 Xray（它把这一步硬编码了，这里做成可开关的选项）；</li>
	 *   <li><b>彻底跳过光照相关的计算</b>：既不在光照引擎 / AO 里把隐藏方块伪装成空气
	 *       （那是全 Mod 最热的调用点），也不再排光照重算任务（{@link #relight()}）。</li>
	 * </ol>
	 * 第 2 点是性能大头：既然画面本来就是全亮的，"让光穿过被隐藏的方块"这件事
	 * 就完全没有意义了，省掉它能把 {@code LightEngine.getState} 上百万次的
	 * 判定开销直接抹掉。
	 */
	private static volatile boolean fullBright = DEFAULT_FULL_BRIGHT;

	private static volatile boolean passThrough = DEFAULT_PASS_THROUGH;

	private static volatile BlockPos corner1;
	private static volatile BlockPos corner2;

	private static volatile AABB region;

	private static volatile int selectedCorner = -1;

	private SelectiveRenderingManager() {
	}

	/**
	 * 从磁盘配置恢复运行时状态。只在 {@link SelectiveRendering#onInitializeClient()} 里调用一次。
	 *
	 * <p>所有字段都在 {@link #LOCK} 内赋值，末尾 {@link #publish()} + {@link #rememberSources()}
	 * 保证快照和"上一次用户输入的文本"同步，避免配置界面一打开就把值写回去。
	 */
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

			if (config.rules != null) {
				for (String source : config.rules) {
					BlockMatchRule rule = BlockMatchRule.parse(source);
					if (rule != null) {
						RULES.add(rule);
					}
				}
			}

			corner1 = toBlockPos(config.regionPos1);
			corner2 = toBlockPos(config.regionPos2);
			selectedCorner = corner2 != null ? 1 : corner1 != null ? 0 : -1;
			updateRegion();

			if (config.recorded != null) {
				for (String source : config.recorded) {
					BlockMatchRule rule = BlockMatchRule.parse(source);
					if (rule != null) {
						RECORDED.add(rule);
					}
				}
			}

			if (config.regions != null) {
				for (int[] values : config.regions) {
					Region added = toRegion(values);
					if (added != null) {
						REGIONS.add(added);
					}
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
		}

		Log.say("[state] loaded: mode {}, transparency {}%, {} rule(s), {} region(s), {} preset(s)", mode, transparency, RULES.size(), REGIONS.size(), PRESETS.size());
	}

	public static Mode getMode() {
		return mode;
	}

	/**
	 * 切换模式。代价很大：{@link #rebuildChunks()} 会重建<b>全部</b>区块并重算选区光照。
	 * 滚轮连续切换时会明显卡顿，属于预期行为。
	 */
	public static void setMode(Mode newMode) {
		synchronized (LOCK) {
			if (mode == newMode) {
				return;
			}

			mode = newMode;
			commit();
		}

		Log.say("[state] mode {}", newMode.name());
		rebuildChunks();
	}

	public static void cycleMode(int direction) {
		setMode(mode.cycle(direction));
	}

	public static int getTransparency() {
		return transparency;
	}

	/**
	 * 设置透明度（百分比）。100% = alpha 0 = 完全隐藏，0% = alpha 255 = 完全不透明。
	 *
	 * <p>只改顶点 alpha、不改几何，所以不需要重建区块：
	 * {@link #rebuildHidden()} 优先只重建"被隐藏过的 section"（见 {@link HiddenSections}），
	 * 只有记账太多时才退化成全量重建。
	 */
	public static void setTransparency(int value) {
		int clamped = Math.clamp(value, 0, MAX_TRANSPARENCY);

		synchronized (LOCK) {
			if (clamped == transparency) {
				return;
			}

			transparency = clamped;
			commit();
		}

		Log.say("[state] transparency {}%", clamped);
		rebuildHidden();
	}

	public static BlockPos getCorner(int index) {
		return index == 0 ? corner1 : corner2;
	}

	public static AABB regionBox() {
		return region;
	}

	public static void setCorner(int index, BlockPos pos) {
		synchronized (LOCK) {
			if (index == 0) {
				corner1 = pos.immutable();
			}
			else {
				corner2 = pos.immutable();
			}

			selectedCorner = index;
			updateRegion();
			commit();
		}
	}

	public static boolean isCornerSelected(int index) {
		return selectedCorner == index;
	}

	/**
	 * 中键"选中角点"：视线穿过哪个角点的方块就选中哪个，两个都碰到时取近的那个。
	 *
	 * <p>对标 litematica 的 {@code RayTraceUtils.traceToSelectionBoxCorner}——
	 * 判定的就是"角点那一格"（1×1×1 的盒子），不是含有它的选区，
	 * 所以两个角点哪怕只差一格也能精准分开。
	 *
	 * <p>和 {@link #setCorner} 的区别：这里<b>只是"把哪个角点设为当前角点"</b>，
	 * 不落盘、不重建，改完直接体现在线框的填充色上
	 * （{@code LevelRendererMixin} 用 {@link #isCornerSelected} 决定画不画填充）。
	 *
	 * @return true 表示选中了某个角点；false 表示两个角点都没碰到（此时取消选中）
	 */
	public static boolean selectCorner(Vec3 start, Vec3 end) {
		int closest;

		synchronized (LOCK) {
			closest = -1;
			double closestDistance = Double.MAX_VALUE;

			for (int index = 0; index < 2; index++) {
				BlockPos pos = getCorner(index);
				if (pos == null) {
					continue;
				}

				Optional<Vec3> hit = cornerBox(pos).clip(start, end);
				if (hit.isEmpty()) {
					continue;
				}

				double distance = start.distanceToSqr(hit.get());
				if (distance < closestDistance) {
					closestDistance = distance;
					closest = index;
				}
			}

			// 点空处 = 取消选中（litematica 的 changeSelection 在 MISS 时也是清空选择）
			selectedCorner = closest;
		}

		return closest >= 0;
	}

	private static AABB cornerBox(BlockPos pos) {
		return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
	}

	/**
	 * 把"当前选中的那个角点"沿 {@code direction} 平移 {@code amount} 格（左 Alt + 滚轮）。
	 *
	 * <p>每滚一格都会 {@link #commit()} → 请求一次落盘。
	 * {@code ModConfig.save()} 带防抖，所以连续滚动不会打出一串磁盘写。
	 */
	public static void moveSelectedCorner(Direction direction, int amount) {
		synchronized (LOCK) {
			int index = selectedCorner;
			if (index < 0) {
				return;
			}

			BlockPos pos = index == 0 ? corner1 : corner2;
			if (pos == null) {
				return;
			}

			BlockPos moved = pos.relative(direction, amount).immutable();
			if (index == 0) {
				corner1 = moved;
			}
			else {
				corner2 = moved;
			}

			updateRegion();
			commit();
		}
	}

	public static boolean addRegionFromSelection() {
		BlockPos first = corner1;
		BlockPos second = corner2;
		if (first == null || second == null) {
			return false;
		}

		return addRegion(Region.of(first, second));
	}

	public static int addRegions(List<Region> boxes) {
		if (boxes.isEmpty()) {
			return 0;
		}

		int added = 0;

		synchronized (LOCK) {
			for (Region box : boxes) {
				if (!REGIONS.contains(box)) {
					REGIONS.add(box);
					added++;
				}
			}

			if (added == 0) {
				return 0;
			}

			commitAndRemember();
		}

		Log.say("[state] {} region(s) recorded", added);

		if (mode.usesRegion()) {
			for (Region box : boxes) {
				HiddenSections.mark(box.box());
			}

			// 只排一次；每个选区各排一次会把同一片区域的光照任务叠出好几份
			relight();
		}

		return added;
	}

	@Nullable
	public static Region selection() {
		BlockPos first = corner1;
		BlockPos second = corner2;

		return first == null || second == null ? null : Region.of(first, second);
	}

	/**
	 * 从所有已存选区里<b>挖掉</b> {@code cuts}（集合减法）。
	 *
	 * <h2>为什么是"先合并、再裁剪"</h2>
	 * <p>调用方给的是一堆散点坐标。如果逐点裁剪，每挖掉一个方块位置都要把选区
	 * 切成最多 6 块，几万个位置瞬间就会碎片爆炸。所以流程必须是：
	 * <ol>
	 *   <li>先用 {@link RegionMerger#merge} 把散点贪心压成尽量少的长方体（且不重叠）；</li>
	 *   <li>再用这些长方体做集合减法。</li>
	 * </ol>
	 *
	 * <h2>减法本身</h2>
	 * <p>{@link Region#subtract} 一次只能挖掉一个盒子，所以这里维护一个"当前碎片列表"，
	 * 逐个 cut 去切它。因为 cut 之间互不重叠，已经被某个 cut 切出来的碎片不会再被
	 * 同一个 cut 切到，碎片增长比最坏情况（6^N）温和得多。
	 *
	 * <p>碎片数仍有上限 {@link #MAX_SUBTRACT_FRAGMENTS}：一旦超过就直接放弃整个操作
	 * （<b>不修改</b>选区列表），避免出现"剪了一半"的不一致状态。真超了说明
	 * 记录到的形状过于破碎，提示用户缩小范围重录。
	 *
	 * @return true 表示确实改动了选区列表
	 */
	public static boolean subtractRegions(List<Region> cuts) {
		if (cuts.isEmpty()) {
			return false;
		}

		int before;
		int kept;

		synchronized (LOCK) {
			if (REGIONS.isEmpty()) {
				return false;
			}

			before = REGIONS.size();

			List<Region> current = new ArrayList<>(REGIONS);

			for (Region cut : cuts) {
				List<Region> next = new ArrayList<>(current.size() + 4);

				for (Region region : current) {
					next.addAll(region.subtract(cut));
				}

				if (next.size() > MAX_SUBTRACT_FRAGMENTS) {
					Log.say("[state] 裁剪产生的碎片过多（>{}），已放弃：选区形状过于破碎", MAX_SUBTRACT_FRAGMENTS);
					return false;
				}

				current = next;
			}

			REGIONS.clear();
			for (Region region : current) {
				if (!REGIONS.contains(region)) {
					REGIONS.add(region);
				}
			}

			kept = REGIONS.size();
			commitAndRemember();
		}

		Log.say("[state] regions clipped by recorder: {} -> {}", before, kept);

		if (mode.usesRegion()) {
			// 这里拿不到被挖掉的具体范围（裁剪是逐碎片进行的），直接全量重建最简单可靠
			rebuildChunks();
		}

		return true;
	}

	public static boolean addRegion(Region newRegion) {
		synchronized (LOCK) {
			if (REGIONS.contains(newRegion)) {
				Log.say("[state] region already added: {}", newRegion.toSource());
				return false;
			}

			REGIONS.add(newRegion);
			commitAndRemember();
		}

		Log.say("[state] region added: {}", newRegion.toSource());

		if (mode.usesRegion()) {
			HiddenSections.mark(newRegion.box());
			relight();
		}

		return true;
	}

	public static boolean removeRegionAt(BlockPos pos) {
		synchronized (LOCK) {
			return removeRegions(REGIONS.stream().filter(added -> added.contains(pos)).toList());
		}
	}

	/**
	 * 删掉这批选区，并做完收尾（落盘 / 标记 section / 还原光照）。
	 *
	 * <p>{@link #removeRegionAt}、以及"对着框点一下就删"都走这里。
	 */
	public static boolean removeRegions(List<Region> targets) {
		List<Region> dropped;

		synchronized (LOCK) {
			dropped = targets.stream().filter(REGIONS::contains).toList();
			if (dropped.isEmpty()) {
				return false;
			}

			REGIONS.removeAll(dropped);
			commitAndRemember();
		}

		Log.say("[state] {} region(s) removed", dropped.size());

		if (mode.usesRegion()) {
			for (Region region : dropped) {
				HiddenSections.mark(region.box());
			}

			// 被删掉的选区已经不在 regionSnapshot 里了，但它的光照仍需还原
			retire(dropped);
			relight();
		}

		return true;
	}

	/**
	 * 视线（起点 {@code start} → 终点 {@code end}）穿过的所有选区里，<b>最近的那个</b>。
	 *
	 * <p>对标 litematica 的 {@code RayTraceUtils.traceToSelectionBoxBody}：不要求
	 * "点到的方块在选区内"，而是直接拿射线去截选区的盒子，
	 * 所以隔着空气、隔着被淡化的方块也能选中它。
	 *
	 * @return 最近被命中的选区；一个都没碰到时为 null
	 */
	@Nullable
	public static Region pickRegion(Vec3 start, Vec3 end) {
		synchronized (LOCK) {
			Region closest = null;
			double closestDistance = Double.MAX_VALUE;

			for (Region region : REGIONS) {
				Optional<Vec3> hit = region.box().clip(start, end);
				if (hit.isEmpty()) {
					continue;
				}

				double distance = start.distanceToSqr(hit.get());
				if (distance < closestDistance) {
					closestDistance = distance;
					closest = region;
				}
			}

			return closest;
		}
	}

	/**
	 * 魔杖左键的"增删选区"：<b>"删"看射线穿过的最近选区</b>，"加"用当前双角点选区。
	 *
	 * <p>这条分支不要求点到方块——只要视线穿过某个已存选区的盒子就删它
	 * （见 {@link #pickRegion}），这也是 litematica 的元素选择手感。
	 */
	public static boolean toggleRegionAlongRay(Vec3 start, Vec3 end) {
		Region hit = pickRegion(start, end);
		return hit != null ? removeRegions(List.of(hit)) : addRegionFromSelection();
	}

	/**
	 * "裁剪"：把当前双角点围出的盒子从所有已存选区里<b>挖掉</b>（集合减法）。
	 * 一个选区被挖掉中间一块后会被 {@link Region#subtract} 拆成最多 6 个小盒子。
	 *
	 * <p>裁剪<b>不会</b>动当前选区的两个角点——裁完还能继续用同一个框去挖别的地方。
	 * （早期实现裁完会把角点清空，等于当前选区丢失，不好用。）
	 */
	public static boolean clipRegions() {
		BlockPos first = corner1;
		BlockPos second = corner2;
		if (first == null || second == null) {
			return false;
		}

		Region cut = Region.of(first, second);
		List<Region> before;
		int kept;

		synchronized (LOCK) {
			if (REGIONS.isEmpty()) {
				return false;
			}

			before = List.copyOf(REGIONS);
			REGIONS.clear();

			for (Region region : before) {
				for (Region part : region.subtract(cut)) {
					if (!REGIONS.contains(part)) {
						REGIONS.add(part);
					}
				}
			}

			kept = REGIONS.size();

			commitAndRemember();
		}

		Log.say("[state] regions clipped by {}: {} -> {}", cut.toSource(), before.size(), kept);

		if (mode.usesRegion()) {
			for (Region region : before) {
				HiddenSections.mark(region.box());
			}

			// before 是裁剪前的选区，裁剪后其中一部分已经不在列表里了，同样要还原光照
			retire(before);
			relight();
		}

		return true;
	}

	public static List<Region> regions() {
		return regionSnapshot;
	}

	public static List<String> regionSources() {
		return regionSnapshot.stream().map(Region::toSource).toList();
	}

	public static void setRegionSources(List<String> sources) {
		List<String> wanted = sources == null ? List.of() : List.copyOf(sources);
		if (wanted.equals(regionSourceInput)) {
			return;
		}

		regionSourceInput = wanted;

		synchronized (LOCK) {
			REGIONS.clear();

			if (sources != null) {
				for (String source : sources) {
					Region parsed = Region.parse(source);
					if (parsed != null && !REGIONS.contains(parsed)) {
						REGIONS.add(parsed);
					}
				}
			}

			commit();
		}

		if (mode.usesRegion()) {
			rebuildChunks();
		}
	}

	private static void updateRegion() {
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
			Math.max(corner1.getZ(), corner2.getZ()) + 1
		);
	}

	public static boolean addRule(String source) {
		BlockMatchRule rule = BlockMatchRule.parse(source);
		if (rule == null) {
			return false;
		}

		synchronized (LOCK) {
			for (BlockMatchRule existing : RULES) {
				if (existing.source().equalsIgnoreCase(rule.source())) {
					return false;
				}
			}

			RULES.add(rule);
			commitAndRemember();
		}

		Log.say("[state] rule added: {}", rule.source());
		rebuildAfterListChange(true);
		return true;
	}

	public static boolean removeRule(String source) {
		synchronized (LOCK) {
			if (!RULES.removeIf(rule -> rule.source().equalsIgnoreCase(source.trim()))) {
				return false;
			}

			commitAndRemember();
		}

		Log.say("[state] rule removed: {}", source.trim());
		rebuildAfterListChange(false);
		return true;
	}

	public static List<String> ruleSources() {
		return ruleSnapshot.stream().map(BlockMatchRule::source).toList();
	}

	public static void setRuleSources(List<String> sources) {
		List<String> wanted = sources == null ? List.of() : List.copyOf(sources);
		if (wanted.equals(ruleSourceInput)) {
			return;
		}

		ruleSourceInput = wanted;

		synchronized (LOCK) {
			RULES.clear();

			if (sources != null) {
				for (String source : sources) {
					BlockMatchRule rule = BlockMatchRule.parse(source);
					if (rule != null && !hasRule(rule.source())) {
						RULES.add(rule);
					}
				}
			}

			commit();
		}

		if (mode.usesList()) {
			rebuildChunks();
		}
	}

	public static boolean hasRule(String source) {
		String trimmed = source.trim();
		for (BlockMatchRule rule : RULES) {
			if (rule.source().equalsIgnoreCase(trimmed)) {
				return true;
			}
		}

		return false;
	}

	public static List<String> recordSources() {
		return recordSnapshot.stream().map(BlockMatchRule::source).toList();
	}

	/**
	 * 配置界面（或 MaLiLib 的"重置"按钮）写入记录名单。
	 *
	 * <p><b>判重必须同时比对"当前真实名单"</b>，不能只看 {@code recordSourceInput}：
	 * 那份缓存只在本方法和 {@link #rememberSources()} 里更新，
	 * 一旦名单被别的路径改过而缓存没跟上，把名单重置成空就会因为
	 * "空 == 缓存里的空"被当成没变化直接 return——
	 * 界面上的列表清空了，内部名单却还在，正是"重置按钮没用"的原因。
	 */
	public static void setRecordSources(List<String> sources) {
		List<String> wanted = sources == null ? List.of() : List.copyOf(sources);

		synchronized (LOCK) {
			if (wanted.equals(recordSourceInput) && wanted.equals(sourcesOf(RECORDED))) {
				return;
			}

			recordSourceInput = wanted;
			RECORDED.clear();

			for (String source : wanted) {
				BlockMatchRule rule = BlockMatchRule.parse(source);
				if (rule != null && !hasRecord(rule.source())) {
					RECORDED.add(rule);
				}
			}

			commit();
		}
	}

	private static List<String> sourcesOf(List<BlockMatchRule> rules) {
		return rules.stream().map(BlockMatchRule::source).toList();
	}

	public static boolean hasRecord(String source) {
		String trimmed = source.trim();
		for (BlockMatchRule rule : RECORDED) {
			if (rule.source().equalsIgnoreCase(trimmed)) {
				return true;
			}
		}

		return false;
	}

	public static RecordMode getRecordMode() {
		return recordMode;
	}

	public static void setRecordMode(RecordMode newMode) {
		synchronized (LOCK) {
			if (newMode == null || newMode == recordMode) {
				return;
			}

			recordMode = newMode;
			commit();
		}

		Log.say("[state] record mode {}", newMode.name());
	}

	public static RecordApplyMode getRecordApplyMode() {
		return recordApplyMode;
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

		Log.say("[state] record apply mode {}", newMode.name());
	}

	public static boolean isInvert() {
		return invert;
	}

	/**
	 * 切换"反转"。
	 *
	 * <p>代价同 {@link #setMode}：被淡化的那一批整体翻转，等于全世界的可见性都变了，
	 * 只能全量重建（外加 {@link #rebuildChunks()} 里的光照重算）。
	 */
	public static void setInvert(boolean value) {
		synchronized (LOCK) {
			if (value == invert) {
				return;
			}

			invert = value;
			commit();
		}

		Log.say("[state] invert {}", value);
		rebuildChunks();
	}

	/**
	 * 配置项"穿透交互"的<b>原始值</b>（就是落盘的那个布尔量）。
	 *
	 * <p>配置界面读写用这个：它必须是"玩家自己勾的什么"，
	 * 不能掺进"模式是不是关着"——否则模式关闭时会回灌一个 false 到复选框上，
	 * 玩家再勾一次反而把偏好值改没了。
	 */
	public static boolean isPassThrough() {
		return passThrough;
	}

	/**
	 * 射线这一帧<b>实际</b>要不要穿透（{@code EntityPickMixin} 用这个）。
	 *
	 * <p>比 {@link #isPassThrough} 多两个前置条件：
	 * <ol>
	 *   <li><b>模式是否关闭</b>：关闭时 {@link #alphaNow} 一律返回 -1，
	 *       套外壳只会白白分配一个对象，结果完全一样，直接短路掉；</li>
	 *   <li><b>是否手持魔杖</b>：见下面。</li>
	 * </ol>
	 *
	 * <h2>为什么手持魔杖时强制关闭</h2>
	 * <p>魔杖的选点也是用 {@code Entity.pick} 打射线的
	 * （{@code MouseHandlerMixin} 里 {@code camera.pick(MAX_TRACE_DISTANCE, ...)}，
	 * 距离 200 格，专门用来点远处的角点）。
	 * 穿透一旦生效，魔杖就会选到淡化方块<b>后面</b>的那个方块，
	 * 而玩家看到、想选的正是那个淡化方块本身——手感完全反了。
	 *
	 * <p>所以手持魔杖时一律退回"淡化方块照样挡住射线"，
	 * 与魔杖上线前的行为一致。
	 */
	public static boolean isPassThroughActive() {
		return passThrough && mode != Mode.OFF && !SelectiveRendering.isWandHeld();
	}

	/**
	 * 切换"穿透交互"。
	 *
	 * <p>与 {@link #setMode} / {@link #setInvert} 不同，<b>不需要重建区块</b>：
	 * 本开关只影响"射线怎么算"，不参与画面渲染，下一帧的准星就变了。
	 * 但仍然要 {@code commit()}——它得落盘。
	 */
	public static void setPassThrough(boolean value) {
		synchronized (LOCK) {
			if (value == passThrough) {
				return;
			}

			passThrough = value;
			commit();
		}

		Log.say("[state] pass through {}", value);
	}

	/**
	 * 记录器用的判定：这个方块是不是"我们关心的那一类"。
	 * 名单是 {@link #RECORDED}（配置里的 {@code recorded}），过滤方向由 {@link #recordMode} 决定。
	 * 与渲染用的 {@link #RULES} 完全独立。
	 *
	 * <p><b>名单为空时一律返回 true</b>（不过滤）。理由：默认现在是白名单，
	 * 而"白名单 + 空名单"按字面语义是什么都录不到，玩家会以为记录器坏了。
	 * 与其静默失效，不如退化成"记录所有变化"，并在 {@code BlockChangeRecorder.start()} 里提示一句。
	 */
	public static boolean matchesRecorded(BlockState state, boolean moving) {
		List<BlockMatchRule> rules = recordSnapshot;
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

		return recordMode == RecordMode.LISTED ? listed : !listed;
	}

	public static int getAlpha(BlockState state, BlockPos pos) {
		return getAlpha(state, pos, false);
	}

	/**
	 * <b>整个 Mod 的心脏</b>：判断一个方块是否该被淡化，返回它的顶点 alpha。
	 *
	 * <p>调用者遍布渲染管线（见 {@code mixin/}）：区块网格构建、面剔除、
	 * 平滑光照、流体渲染、方块实体渲染……全都问这一个问题。
	 * 因此它在热路径上，实现里刻意避免加锁（只读不可变快照）。
	 *
	 * @param state  方块状态（一般是 {@code level.getBlockState(pos)}）
	 * @param pos    世界坐标；注意"活塞移动中的方块"会被换算回它的来源坐标
	 * @param moving 该方块是否正被活塞推动（此时 {@code state} 是 {@code moving_piston} 的壳）
	 * @return {@code -1} 正常渲染；{@code 0..255} 顶点 alpha（0 = 完全不渲染）
	 */
	public static int getAlpha(BlockState state, BlockPos pos, boolean moving) {
		int alpha = alphaNow(state, pos, moving);
		if (alpha < 0) {
			return -1;
		}

		// 副作用：记下"这个位置被隐藏过"。改透明度时可以只重建这些 section，
		// 不必全图重建。只有真正参与画面渲染的路径需要记账，
		// 光照/AO 那种极热的路径走 {@link #alphaNow}，不记账。
		HiddenSections.note(pos);
		return alpha;
	}

	/**
	 * 只做判定，<b>不记账</b>。
	 *
	 * <p>光照引擎与 AO 计算会在光照线程上以百万次的量级调用它，
	 * 往 {@link HiddenSections} 里写既没有意义（很多位置根本不在已加载的 section 里）
	 * 又是从非渲染线程改全局状态。所以这两条路径用本方法。
	 *
	 * @return 同 {@link #getAlpha}：-1 表示正常渲染，否则是顶点 alpha
	 */
	public static int alphaNow(BlockState state, BlockPos pos, boolean moving) {
		Mode current = mode;
		if (current == Mode.OFF) {
			return -1;
		}

		boolean needsRegion = current.usesRegion();
		boolean needsList = current.usesList();

		List<Region> regions = needsRegion ? regionSnapshot : List.of();
		List<BlockMatchRule> rules = needsList ? ruleSnapshot : List.of();

		// 条件不齐就直接不做任何淡化。
		// 这一点在"反转"下尤其重要：反转会把命中集合取反，
		// 若此时名单/选区是空的，取反就变成"整个世界都要淡化"。
		if ((needsRegion && regions.isEmpty()) || (needsList && rules.isEmpty())) {
			return -1;
		}

		boolean carried = moving || state.getBlock() instanceof MovingPistonBlock;
		if (carried) {
			Minecraft minecraft = Minecraft.getInstance();
			BlockEntity entity = state.getBlock() instanceof MovingPistonBlock && minecraft.level != null
				? minecraft.level.getBlockEntity(pos)
				: null;
			if (entity instanceof PistonMovingBlockEntity piston) {
				BlockState moved = piston.getMovedState();
				if (moved != null) {
					state = moved;
					pos = pos.relative(piston.getMovementDirection().getOpposite());
				}
			}
		}

		// 注意：下面两次判定都是线性扫描，规模 = 名单条数 + 选区块数，没有缓存。
		boolean listed = matches(state, carried, rules);

		boolean inside = isInside(pos, regions);

		boolean matched = switch (current) {
			case REGION_INSIDE_LISTED -> inside && listed;
			case REGION_INSIDE_UNLISTED -> inside && !listed;
			case REGION_INSIDE -> inside;
			case REGION_OUTSIDE_LISTED -> !inside && listed;
			case REGION_OUTSIDE_UNLISTED -> !inside && !listed;
			case REGION_OUTSIDE -> !inside;
			case BLACKLIST -> listed;
			case WHITELIST -> !listed;
			case OFF -> false;
		};

		// 反转：改成淡化"没命中"的那一批。于是"选区内 + 名单内 + 反转"
		// 就等于"只显示选区内的指定方块"，这是原来 9 个模式表达不出来的。
		boolean hidden = invert != matched;

		if (!hidden) {
			return -1;
		}

		return alpha();
	}

	public static int getFluidAlpha(BlockState state, BlockPos pos) {
		return getAlpha(state, pos);
	}

	/**
	 * 是否把所有流体强制塞进 {@code TRANSLUCENT} 层（见 {@code FluidModelMixin}）。
	 *
	 * <p>为什么要这样：流体默认走 {@code translucent} 之外的层（如 {@code cutout}），
	 * 顶点 alpha 不会生效。想让水变淡，就必须让它走半透明管线。
	 *
	 * <p>代价是<b>全局生效</b>：只要开着本 Mod 且透明度 &lt; 100%，
	 * 世界上所有水/熔岩都会进半透明层，排序开销变大。100%（完全隐藏）时不需要，
	 * 因为那时流体直接被 {@code ci.cancel()} 掉了。
	 */
	public static boolean shouldRenderFluidsTranslucent() {
		if (mode == Mode.OFF) {
			return false;
		}

		return transparency < MAX_TRANSPARENCY;
	}

	public static boolean isHidden(BlockState state, BlockPos pos) {
		return getAlpha(state, pos) >= 0;
	}

	public static boolean isHidden(BlockGetter level, BlockPos pos) {
		return getAlpha(level.getBlockState(pos), pos) >= 0;
	}

	/**
	 * {@link #isHidden} 的<b>纯查询版</b>：不往 {@link HiddenSections} 记账。
	 *
	 * <p>准星射线（{@code FadedBlockGetter}）用它判断"这一格要不要当空气"。
	 * 射线在客户端主线上跑、每帧两次，不属于渲染管线，
	 * 记账只会污染"改透明度时要重建哪些 section"的集合，所以必须走 {@link #alphaNow}。
	 *
	 * <p>与 {@link #alphaAt} 的区别：那个只收坐标、自己现查方块状态；
	 * 这里由调用方把状态传进来（射线本来就要先取状态做后续判定，不重复查表）。
	 */
	public static boolean isFaded(BlockState state, BlockPos pos) {
		return alphaNow(state, pos, false) >= 0;
	}

	/**
	 * 光照 / 平滑光照补丁的统一入口：被隐藏的方块在计算里一律当成空气。
	 *
	 * <p>原版、Sodium、Indigo 各有自己的一套光照数据结构，所以三个（实际是五个）mixin
	 * 都得各打一遍补丁。判定收在这里之后，每个注入点只剩一行。
	 *
	 * @param state 该位置真实的方块状态（由调用方先取好）
	 */
	public static BlockState airIfHidden(BlockState state, BlockPos pos) {
		// 模式关闭 = 本 Mod 对整个管线零干预。
		// 下面那个 fullBright 分支不看分方块、一律"当空气"，如果这里不先短路，
		// 夜视（默认开启）+ 模式关闭时，光照引擎和 AO 会被全局改成"当空气"——
		// 结果就是全世界的 AO 消失、客户端光照数据按到处通透来算。
		if (mode == Mode.OFF) {
			return state;
		}

		if (fullBright) {
			// 夜视：一律当空气，不再逐方块判定。
			//
			// 对光照引擎来说：画面本来就全亮，"让光穿过被淡化的方块"毫无意义，
			// 而这是全 Mod 最热的一条路径（光照线程上百万次量级），直接抹掉。
			//
			// 对平滑光照(AO)来说：等于"没有任何方块产生遮蔽"。这样被淡化的方块
			// 周围不会留下遮挡暗角（否则会看到一圈像被隐形东西挡住的暗边），
			// 代价是画面失去 AO 的立体感——夜视本来就是平的，可以接受。
			// 这里参考了 meteor 的 Xray，它在 Xray 激活时直接把 AO 拉满。
			return Blocks.AIR.defaultBlockState();
		}

		return alphaNow(state, pos, false) >= 0 ? Blocks.AIR.defaultBlockState() : state;
	}

	public static String getWand() {
		return wand;
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
		return fullBright;
	}

	/**
	 * 夜视是否真的生效。
	 *
	 * <p>模式为 {@link Mode#OFF} 时必须返回 false——那时整个 Mod 不应该对原版
	 * 有任何影响，夜视（改光照贴图）也不例外。
	 */
	public static boolean isNightVisionActive() {
		return mode != Mode.OFF && fullBright;
	}

	/**
	 * 切换"夜视"。
	 *
	 * <p>切换前会先在当前状态下做一次全量重建 + 光照重算，把之前可能污染过的
	 * 光照数据还原干净，然后再切标志并按新行为重建一次。
	 * 顺序不能反——{@link #relight()} 是否工作取决于当前 {@code fullBright} 的值。
	 */
	public static void setFullBright(boolean value) {
		if (value == fullBright) {
			return;
		}

		rebuildChunks();

		synchronized (LOCK) {
			fullBright = value;
			commit();
		}

		rebuildChunks();
	}

	public static boolean isHiddenAt(BlockPos pos) {
		return getAlphaAt(pos) >= 0;
	}

	/**
	 * 只知道坐标、不知道方块状态时用的便捷版本（方块实体渲染用）。
	 * 它会现查 {@code minecraft.level.getBlockState(pos)}，
	 * 因此<b>只能在客户端线程调用</b>，不要从区块构建线程用。
	 */
	public static int getAlphaAt(BlockPos pos) {
		if (mode == Mode.OFF) {
			return -1;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return -1;
		}

		return getAlpha(minecraft.level.getBlockState(pos), pos);
	}

	/**
	 * {@link #getAlphaAt(BlockPos)} 的<b>纯查询版</b>：不往 {@link HiddenSections} 里记账。
	 *
	 * <p>EntityCulling 的剔除线程会调用它（见 {@code EntityCullingProviderMixin}）；
	 * 记账会把非渲染线程的写入混进渲染状态，所以给兼容层用的必须是这条无副作用的路径。
	 */
	public static int alphaAt(BlockPos pos) {
		if (mode == Mode.OFF) {
			return -1;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return -1;
		}

		return alphaNow(minecraft.level.getBlockState(pos), pos, false);
	}

	/**
	 * EntityCulling 兼容层用：世界坐标 {@code (x, y, z)} 这一格是否被淡化。
	 *
	 * <p>被淡化 = 视觉上是透明的，所以在剔除方的体素判定里<b>不能当实心墙</b>——
	 * 否则隔着几格淡化方块看活塞/生物，它们会被误剔除（时隐时现）。
	 */
	public static boolean isFadedCube(int x, int y, int z) {
		return alphaAt(new BlockPos(x, y, z)) >= 0;
	}

	public static int getHiddenAlpha() {
		return alpha();
	}

	private static boolean matches(BlockState state, boolean moving, List<BlockMatchRule> rules) {
		for (BlockMatchRule rule : rules) {
			if (rule.matches(state, moving)) {
				return true;
			}
		}

		return false;
	}



	/**
	 * "增量重建"：只重建被隐藏过、且数量不多的那些 section。
	 * {@link HiddenSections#mark()} 返回 false 表示记账太多（不值得精算）或世界还没准备好，
	 * 这时退化成全量重建。
	 */
	private static void rebuildHidden() {
		if (!HiddenSections.mark()) {
			rebuildChunks();
		}
	}

	/**
	 * 名单（{@link #RULES}）增删一条后的重建策略。
	 *
	 * @param added true = 往名单里加了一条规则
	 *              <p>逻辑：如果"新增的这批方块正好是本模式要隐藏的那一批"
	 *              （{@link Mode#hidesListed()} == added），受影响的只有刚加入的那一个方块，
	 *              增量重建即可；反之意味着大量方块的可见性翻转，只能全量重建。
	 */
	private static void rebuildAfterListChange(boolean added) {
		if (!mode.usesList()) {
			return;
		}

		if (invert) {
			// 反转下"命中集合"和"被淡化集合"是互补的，上面的增量推断整个反了过来，
			// 直接全量重建最稳（名单增删本来就不是高频操作）。
			rebuildChunks();
			return;
		}

		if (mode.hidesListed() == added) {
			rebuildChunks();
			return;
		}

		rebuildHidden();
	}

	/**
	 * 全量重建：清空隐藏记账 → {@code LevelRenderer.allChanged()}（所有 section 重新烘焙）
	 * → 对所有选区重算光照。
	 *
	 * <p>必须重算光照的原因：我们把被隐藏的方块在光照引擎里伪装成空气
	 * （见 {@code LightEngineMixin}），光照数据已经变了，不重算的话
	 * 隐藏/恢复切换后会残留错误的亮度。
	 */
	public static void rebuildChunks() {
		HiddenSections.clear();
		Platform.rebuildAll();
		relight();
	}

	/**
	 * 把选区内（外扩 {@link #LIGHT_RANGE} 格）的光照重新算一遍。
	 *
	 * <p>为什么必须重算：我们把被隐藏的方块在光照引擎里伪装成空气
	 * （见 {@code LightEngineMixin}），光照数据已经变了。
	 *
	 * <p>见内部 {@link Relight}：它不是一次算完，而是把自己反复塞回
	 * {@code level.queueLightUpdate(...)}，每批只做 2000 个位置，避免卡主线程。
	 */
	private static void relight() {
		if (fullBright) {
			// 夜视模式下我们不伪造光照，也就没有"需要还原"的数据
			return;
		}

		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}

		// 目标 = 当前所有选区 + 历史删除过的区域。两者都是"光照可能被我们污染过"的地方。
		List<Region> targets;
		synchronized (RETIRED_REGIONS) {
			targets = new ArrayList<>(regionSnapshot.size() + RETIRED_REGIONS.size());
			targets.addAll(regionSnapshot);

			for (Region region : RETIRED_REGIONS) {
				if (!targets.contains(region)) {
					targets.add(region);
				}
			}
		}

		if (targets.isEmpty()) {
			return;
		}

		// 排新任务时把代际号推进一格：之前排队但还没跑完的旧任务会在自己的下一次
		// run() 里发现代号变了并立刻退出。
		//
		// 没有这一步的话，切换模式 / 连续增删选区会把同一片区域的光照任务叠成好几份，
		// 每一份都还在一批批地往主线程上挤（而且各自的 run() 结尾都会
		// queueLightUpdate(this) 续命），帧率掉下去就再也回不来——只能退世界重进。
		//
		// 这个"作废旧的"只有在"新目标 ⊇ 旧目标"时才安全：上面的 targets 永远包含
		// 全部 regionSnapshot 和全部 RETIRED_REGIONS，所以成立。
		long generation = RELIGHT_GENERATION.incrementAndGet();
		level.queueLightUpdate(new Relight(targets, generation));
	}

	/** 把"已经不在选区列表里、但光照仍需还原"的区域记下来，供下一次 {@link #relight()} 使用。 */
	private static void retire(List<Region> regions) {
		synchronized (RETIRED_REGIONS) {
			for (Region region : regions) {
				if (!RETIRED_REGIONS.contains(region)) {
					RETIRED_REGIONS.add(region);
				}
			}
		}
	}

	/**
	 * 分批重算光照的可续跑任务。
	 *
	 * <p>把一个选区外扩后的长方体按 (y, x, z) 的次序线性展开成一个计数器
	 * {@code progress}，每次 {@link #run()} 只推进 {@code LIMIT} 步，
	 * 然后把自己重新排进光照队列，直到跑完所有选区。
	 */
	private static final class Relight implements Runnable {
		private static final int LIMIT = 2000;

		private final List<Region> regions;
		private final long generation;
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		private int regionIndex;
		private long progress;

		/** 是否已经算过当前选区的遍历范围；跨选区时要重置。 */
		private boolean started;

		private int minX;
		private int minZ;
		private int bottomY;
		private int topY;
		private int width;
		private int depth;
		private int height;

		Relight(List<Region> regions, long generation) {
			this.regions = regions;
			this.generation = generation;
		}

		@Override
		public void run() {
			if (this.generation != RELIGHT_GENERATION.get()) {
				// 已经有更新的光照任务在跑了，这份作废（见 relight() 的说明）
				return;
			}

			ClientLevel level = Minecraft.getInstance().level;
			if (level == null) {
				return;
			}

			int budget = LIMIT;
			while (budget > 0 && this.regionIndex < this.regions.size()) {
				if (!this.started) {
					beginRegion(level, this.regions.get(this.regionIndex));
					this.started = true;
				}

				if (this.width <= 0 || this.depth <= 0 || this.height <= 0) {
					// 选区完全在世界之外，跳过
					this.regionIndex++;
					this.started = false;
					continue;
				}

				long total = (long) this.width * this.depth * this.height;

				while (this.progress < total && budget > 0) {
					int y = this.bottomY + (int) (this.progress % this.height);
					long column = this.progress / this.height;
					int x = this.minX + (int) (column % this.width);
					int z = this.minZ + (int) (column / this.width);

					level.getLightEngine().checkBlock(this.pos.set(x, y, z));
					this.progress++;
					budget--;
				}

				if (this.progress >= total) {
					this.regionIndex++;
					this.progress = 0;
					this.started = false;
				}
			}

			if (this.regionIndex < this.regions.size()) {
				level.queueLightUpdate(this);
			}
		}

		/**
		 * 算出当前选区需要重算的立方范围。
		 *
		 * <p><b>Y 方向只用选区自己的高度外扩 {@link #LIGHT_RANGE} 格</b>，
		 * 而不是像早期实现那样直接扫整个世界高度（26.x 是 -64..320，共 384 层）。
		 * 光在竖直方向同样最多传播 15 格，所以结果一样，但一个 5 格高的选区
		 * 从 384 层降到 35 层，工作量差十倍以上——这正是"选区一多、切次模式就掉帧"
		 * 的主要来源之一。
		 */
		private void beginRegion(ClientLevel level, Region region) {
			this.minX = region.min().getX() - LIGHT_RANGE;
			this.minZ = region.min().getZ() - LIGHT_RANGE;
			this.width = region.max().getX() - region.min().getX() + LIGHT_RANGE * 2 + 1;
			this.depth = region.max().getZ() - region.min().getZ() + LIGHT_RANGE * 2 + 1;
			this.bottomY = Math.max(level.getMinY(), region.min().getY() - LIGHT_RANGE);
			this.topY = Math.min(level.getMaxY(), region.max().getY() + LIGHT_RANGE);
			this.height = this.topY - this.bottomY + 1;
		}
	}

	private static BlockPos toBlockPos(int[] values) {
		return values == null || values.length != 3 ? null : new BlockPos(values[0], values[1], values[2]);
	}

	private static int[] toArray(BlockPos pos) {
		return pos == null ? null : new int[] {pos.getX(), pos.getY(), pos.getZ()};
	}

	private static int[] toArray(Region region) {
		return new int[] {
			region.min().getX(), region.min().getY(), region.min().getZ(),
			region.max().getX(), region.max().getY(), region.max().getZ()
		};
	}

	private static Region toRegion(int[] values) {
		return values == null || values.length != 6 ? null : new Region(
			new BlockPos(values[0], values[1], values[2]),
			new BlockPos(values[3], values[4], values[5])
		);
	}

	/**
	 * 透明度百分比 → 顶点 alpha。
	 * 透明度 100% → alpha 0（完全隐藏）；透明度 0% → alpha 255（不透明）。
	 */
	private static int alpha() {
		return Math.round(255F * (MAX_TRANSPARENCY - transparency) / MAX_TRANSPARENCY);
	}

	private static boolean isInside(BlockPos pos, List<Region> regions) {
		for (Region added : regions) {
			if (added.contains(pos)) {
				return true;
			}
		}

		return false;
	}

	public static List<Preset> presets() {
		return presetSnapshot;
	}

	/**
	 * 应用一个预设：整体替换模式 / 透明度 / 名单 / 选区，然后全量重建。
	 *
	 * <p>注意它<b>不走</b> {@link #setMode}，而是直接改 {@code mode} 字段再统一 rebuild，
	 * 避免中间态触发两次全量重建。
	 */
	public static void applyPreset(Preset preset) {
		synchronized (LOCK) {
			mode = preset.mode == null ? DEFAULT_MODE : preset.mode;
			transparency = Math.clamp(preset.transparency, 0, MAX_TRANSPARENCY);
			invert = preset.invert;

			RULES.clear();
			if (preset.rules != null) {
				for (String source : preset.rules) {
					BlockMatchRule rule = BlockMatchRule.parse(source);
					if (rule != null) {
						RULES.add(rule);
					}
				}
			}

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

		Log.say("[state] preset applied: {}", preset.displayName());
		rebuildChunks();
	}

	/** 预设对象被原地改名后调用：只落盘，不需要动快照。 */
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

	/**
	 * 必须在 {@link #LOCK} 内调用：刷新不可变快照并请求一次落盘。
	 *
	 * <p>用于"输入文本已经记过"的路径（{@code setRuleSources} 等），
	 * 不需要再写 {@link #rememberSources()}。
	 */
	private static void commit() {
		publish();
		persist();
	}

	/**
	 * 必须在 {@link #LOCK} 内调用：刷新快照 + 记住用户输入的原始文本（防回环）+ 落盘。
	 *
	 * <p>用于"由魔杖 / 记录器改动了内部结构"的路径，此时界面上的文本也要跟着更新。
	 */
	private static void commitAndRemember() {
		publish();
		rememberSources();
		persist();
	}

	/**
	 * 用当前可变集合刷新不可变快照。<b>必须在持有 {@link #LOCK} 时调用。</b>
	 * 渲染线程只读快照，所以这里必须整体替换（不能原地改）。
	 */
	private static void publish() {
		ruleSnapshot = List.copyOf(RULES);
		recordSnapshot = List.copyOf(RECORDED);
		regionSnapshot = List.copyOf(REGIONS);
		presetSnapshot = List.copyOf(PRESETS);
	}

	/**
	 * 记住"用户输入的原始文本"。
	 *
	 * <p>作用：配置界面（MaLiLib 字符串列表）每次同步都会把文本回灌给
	 * {@link #setRuleSources} / {@link #setRegionSources}，
	 * 这两个方法靠跟这里记下的文本比对来判断"是不是我自己刚写进去的"，
	 * 从而避免"写回 → 触发回调 → 再写回"的死循环。
	 */
	private static void rememberSources() {
		ruleSourceInput = RULES.stream().map(BlockMatchRule::source).toList();
		regionSourceInput = REGIONS.stream().map(Region::toSource).toList();

		// 记录名单同样要记：少了这一行，"重置成空"会被 setRecordSources 当成没变化。
		recordSourceInput = RECORDED.stream().map(BlockMatchRule::source).toList();
	}

	/**
	 * 把运行时状态写回 {@link ModConfig} 并立刻落盘。
	 *
	 * <p>已知问题：调用点几乎都在 {@code synchronized} 块外面，
	 * 而这里读了 {@code RULES}/{@code REGIONS}/{@code PRESETS}/{@code corner1/2}，
	 * 属于无锁读可变集合；且每次调用都是一次完整的文件重写（非原子写）。
	 */
	private static void persist() {
		ModConfig config = ModConfig.get();
		config.mode = mode;
		config.transparency = transparency;
		config.recordMode = recordMode;
		config.recordApplyMode = recordApplyMode;
		config.invert = invert;
		config.wand = wand;
		config.fullBright = fullBright;
		config.rules = RULES.stream().map(BlockMatchRule::source).toList();
		config.recorded = RECORDED.stream().map(BlockMatchRule::source).toList();
		config.regionPos1 = toArray(corner1);
		config.regionPos2 = toArray(corner2);
		config.regions = REGIONS.stream().map(SelectiveRenderingManager::toArray).toList();
		config.presets = new ArrayList<>(PRESETS);
		ModConfig.save();
	}
}
