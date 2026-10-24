package com.selectiverendering.lighting;

import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.region.Area;
import com.selectiverendering.region.Region;
import com.selectiverendering.render.hiding.AlphaGetter;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 在客户端光照和 AO 计算中将隐藏方块视为空气，并调度受影响区块的光照重算。
 *
 * <p>重算只覆盖"选区（含刚被移除的选区）涉及的区块"，按帧分摊，随时可以被新的设置变化取消。
 * 名单类模式（BLACKLIST / WHITELIST）隐藏的方块遍布全世界，不为它重算光照：否则待重算的区块会
 * 随着区块加载无限增长，每帧的光照更新会被拖垮（整屏 FPS 掉到几十且关掉也回不来）。
 */
public final class LightSuppression {
	/** 每帧最多提交的 {@code checkBlock} 次数。 */
	private static final int BUDGET_PER_RUN = 1500;

	/** 每帧最多访问的方块数，防止大范围空气扫描本身耗时过长。 */
	private static final int VISIT_PER_RUN = 20000;

	private static final int MARGIN = 1;

	/** 待重算区块数量的硬上限，超大选区也不会把队列撑爆。 */
	private static final int MAX_PENDING_CHUNKS = 4096;

	/** 待还原的历史选区数量上限。 */
	private static final int MAX_RETIRED_AREAS = 64;

	private static final long NO_CHUNK = Long.MIN_VALUE;

	private static final int SOURCE_CACHE_LIMIT = 300_000;

	/** 列 (x, z) -> (真实源头高度, 隐藏方块当空气后的源头高度)，各占 32 位。 */
	private static final Long2LongOpenHashMap SOURCE_CACHE = new Long2LongOpenHashMap();

	private static final Object LOCK = new Object();

	/** 按插入顺序保存待重算区块并去重。 */
	private static final LongLinkedOpenHashSet PENDING = new LongLinkedOpenHashSet();

	/** 已删除或裁剪、仍需还原光照的历史选区。 */
	private static final List<Area> RETIRED_AREAS = new ArrayList<>();

	/** 每次设置变化自增，让还在队列里的旧重算任务自己退出。 */
	private static final AtomicLong GENERATION = new AtomicLong();

	/** 帧序号，由 {@code runLightUpdates} 推进；一帧只做一批重算。 */
	private static long frame;
	private static long lastWorkFrame = -1;

	/** 队列里是否已有一个有效的重算任务，以及它属于哪一代。 */
	private static boolean taskQueued;
	private static long taskGeneration = -1;

	/** 跨帧扫描的区块及列游标。 */
	private static long cursorChunk = NO_CHUNK;
	private static int cursorColumn;

	private LightSuppression() {
	}

	/** 返回光照和 AO 计算使用的状态，将隐藏方块视为空气。 */
	public static BlockState airIfHidden(BlockState state, BlockPos pos) {
		if (SelectiveRenderingManager.getMode() == SelectiveRenderingManager.Mode.OFF) {
			return state;
		}

		if (SelectiveRenderingManager.isFullBright()) {
			// 全亮时同时移除 AO 遮挡。
			return Blocks.AIR.defaultBlockState();
		}

		return AlphaGetter.alphaNow(state, pos, false) >= 0 ? Blocks.AIR.defaultBlockState() : state;
	}

	/** 设置变化后调用：重排受影响区块，并安排按帧分摊的重算。 */
	public static void relight() {
		synchronized (SOURCE_CACHE) {
			SOURCE_CACHE.clear();
		}

		List<Area> targets = collectTargets();

		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || targets.isEmpty()) {
			reset();
			return;
		}

		long generation = GENERATION.incrementAndGet();

		synchronized (LOCK) {
			for (Area area : targets) {
				enqueueArea(area);
			}
		}

		schedule(level, generation, targets);
	}

	/** 记录已移除、仍需还原光照的选区。 */
	public static void retire(List<Area> regions) {
		synchronized (LOCK) {
			for (Area region : regions) {
				if (!RETIRED_AREAS.contains(region)) {
					RETIRED_AREAS.add(region);
				}
			}

			while (RETIRED_AREAS.size() > MAX_RETIRED_AREAS) {
				RETIRED_AREAS.remove(0);
			}
		}
	}

	/**
	 * 区块光照数据落地后调用，保证客户端把"隐藏方块当空气"的结果重新算一遍。
	 * 只有与选区相交的区块才会入队；名单类模式不做重算。
	 */
	public static void markChunk(int chunkX, int chunkZ) {
		SelectiveRenderingManager.Mode mode = SelectiveRenderingManager.getMode();
		if (mode == SelectiveRenderingManager.Mode.OFF || SelectiveRenderingManager.isFullBright() || !mode.usesRegion()) {
			return;
		}

		if (!intersectsAreas(collectAreas(), chunkX, chunkZ)) {
			return;
		}

		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}

		synchronized (LOCK) {
			enqueue(chunkX, chunkZ);
		}

		schedule(level, GENERATION.get(), collectAreas());
	}

	/** 丢弃所有待重算区块。切换模式、关闭或没有选区时调用，保证不会留下持续的背景开销。 */
	public static void reset() {
		synchronized (LOCK) {
			PENDING.clear();
			RETIRED_AREAS.clear();
			cursorChunk = NO_CHUNK;
			cursorColumn = 0;
		}
	}

	/**
	 * 每次 {@code runLightUpdates} 结束时调用：推进帧号。
	 * 重算任务跑在它前面的 {@code pollLightUpdates} 里，因此一帧只会做一批。
	 */
	public static void endFrame() {
		frame++;
	}

	private static boolean isGlobal(SelectiveRenderingManager.Mode mode) {
		return mode == SelectiveRenderingManager.Mode.BLACKLIST || mode == SelectiveRenderingManager.Mode.WHITELIST;
	}

	private static void schedule(ClientLevel level, long generation, List<Area> areas) {
		if (level == null) {
			return;
		}

		synchronized (LOCK) {
			if (PENDING.isEmpty()) {
				return;
			}

			if (taskQueued && taskGeneration == generation) {
				// 同代的任务已经在队列里了，它自己会继续处理剩下的区块。
				return;
			}

			taskQueued = true;
			taskGeneration = generation;
		}

		level.queueLightUpdate(new Relight(generation, areas));
	}

	private static void finishChunk() {
		synchronized (LOCK) {
			PENDING.remove(cursorChunk);
			cursorChunk = NO_CHUNK;
			cursorColumn = 0;
		}
	}

	private static void enqueue(int chunkX, int chunkZ) {
		if (PENDING.size() >= MAX_PENDING_CHUNKS) {
			return;
		}

		long packed = ChunkPos.pack(chunkX, chunkZ);
		PENDING.add(packed);

		if (packed == cursorChunk) {
			// 区块再次变脏时，从首列重新扫描。
			cursorColumn = 0;
		}
	}

	private static void enqueueArea(Area area) {
		int minChunkX = (area.min().getX() - MARGIN) >> 4;
		int maxChunkX = (area.max().getX() + MARGIN) >> 4;
		int minChunkZ = (area.min().getZ() - MARGIN) >> 4;
		int maxChunkZ = (area.max().getZ() + MARGIN) >> 4;

		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				enqueue(chunkX, chunkZ);
			}
		}
	}

	/** 当前生效的选区范围。 */
	private static List<Area> collectAreas() {
		List<Region> regions = SelectiveRenderingManager.blockRegions();
		List<Area> areas = new ArrayList<>();

		for (Region region : regions) {
			areas.addAll(region.areas());
		}

		return areas;
	}

	/** 当前选区范围，并取出（清空）待还原的历史选区。 */
	private static List<Area> collectTargets() {
		List<Area> areas = collectAreas();

		synchronized (LOCK) {
			for (Area area : RETIRED_AREAS) {
				if (!areas.contains(area)) {
					areas.add(area);
				}
			}

			RETIRED_AREAS.clear();
		}

		return areas;
	}

	private static boolean intersectsAreas(List<Area> areas, int chunkX, int chunkZ) {
		int baseX = chunkX << 4;
		int baseZ = chunkZ << 4;

		for (Area area : areas) {
			if (area.max().getX() + MARGIN >= baseX && area.min().getX() - MARGIN <= baseX + 15
				&& area.max().getZ() + MARGIN >= baseZ && area.min().getZ() - MARGIN <= baseZ + 15) {
				return true;
			}
		}

		return false;
	}

	/**
	 * 天光源头高度：从真实源头往下穿过所有"当空气后不挡光"的方块，停在第一个仍然挡光的方块上方。
	 * 只对客户端引擎生效（调用方已核对），并且按列缓存；缓存在 {@link #relight()} 时整体失效。
	 * 名单类模式直接沿用原版结果：那种模式下要重算的范围是全世界，代价不可接受。
	 *
	 * @param real 原版按真实方块算出的结果
	 */
	public static int lowestSourceY(LightChunk chunk, int x, int z, int real) {
		SelectiveRenderingManager.Mode mode = SelectiveRenderingManager.getMode();
		if (real == Integer.MIN_VALUE || mode == SelectiveRenderingManager.Mode.OFF
			|| SelectiveRenderingManager.isFullBright() || isGlobal(mode) || hidesOutside(mode)) {
			return real;
		}

		long key = BlockPos.asLong(x, 0, z);

		synchronized (SOURCE_CACHE) {
			long cached = SOURCE_CACHE.getOrDefault(key, Long.MIN_VALUE);
			if (cached != Long.MIN_VALUE && (int) (cached >> 32) == real) {
				return (int) cached;
			}
		}

		int result = Integer.MIN_VALUE;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);

		for (int y = real - 1; y >= chunk.getMinY(); y--) {
			pos.setY(y);
			BlockState state = airIfHidden(chunk.getBlockState(pos), pos);

			if (state.getLightDampening() > 0 || state.useShapeForLightOcclusion()) {
				result = y + 1;
				break;
			}
		}

		synchronized (SOURCE_CACHE) {
			if (SOURCE_CACHE.size() > SOURCE_CACHE_LIMIT) {
				SOURCE_CACHE.clear();
			}

			SOURCE_CACHE.put(key, ((long) real << 32) | (result & 0xFFFFFFFFL));
		}

		return result;
	}

	private static boolean hidesOutside(SelectiveRenderingManager.Mode mode) {
		return mode == SelectiveRenderingManager.Mode.REGION_OUTSIDE
			|| mode == SelectiveRenderingManager.Mode.REGION_OUTSIDE_LISTED
			|| mode == SelectiveRenderingManager.Mode.REGION_OUTSIDE_UNLISTED;
	}

	/** 按帧分摊的光照重算任务；设置一旦变化就自行退出，由 {@link #relight()} 安排新的一批。 */
	private static final class Relight implements Runnable {
		private final long generation;
		private final List<Area> areas;
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		Relight(long generation, List<Area> areas) {
			this.generation = generation;
			this.areas = areas;
		}

		@Override
		public void run() {
			long generation = GENERATION.get();
			if (this.generation != generation) {
				synchronized (LOCK) {
					taskQueued = false;
				}

				return;
			}

			ClientLevel level = Minecraft.getInstance().level;
			if (level == null) {
				reset();

				synchronized (LOCK) {
					taskQueued = false;
				}

				return;
			}

			if (lastWorkFrame == frame) {
				// 这一帧已经做过一批了，下一帧再继续。
				level.queueLightUpdate(this);
				return;
			}

			if (PENDING.isEmpty()) {
				synchronized (LOCK) {
					taskQueued = false;
				}

				return;
			}

			lastWorkFrame = frame;

			int budget = BUDGET_PER_RUN;
			int visits = VISIT_PER_RUN;
			boolean remaining = true;

			while (budget > 0 && visits > 0) {
				if (cursorChunk == NO_CHUNK) {
					synchronized (LOCK) {
						if (PENDING.isEmpty()) {
							remaining = false;
							break;
						}

						cursorChunk = PENDING.firstLong();
						cursorColumn = 0;
					}
				}

				LevelChunk chunk = level.getChunkSource()
					.getChunk(ChunkPos.getX(cursorChunk), ChunkPos.getZ(cursorChunk), false);

				if (chunk == null) {
					finishChunk();
					continue;
				}

				int[] used = scanChunk(level, chunk, budget, visits);
				budget = used[0];
				visits -= used[1];

				if (cursorColumn >= 256) {
					finishChunk();
				}
			}

			if (!remaining) {
				synchronized (LOCK) {
					if (PENDING.isEmpty()) {
						taskQueued = false;
					}
				}

				return;
			}

			level.queueLightUpdate(this);
		}

		/** 扫描区块里与选区相交的列，返回 {剩余预算, 访问方块数}。 */
		private int[] scanChunk(ClientLevel level, LevelChunk chunk, int budget, int visits) {
			int baseX = chunk.getPos().getMinBlockX();
			int baseZ = chunk.getPos().getMinBlockZ();

			List<Area> local = new ArrayList<>();
			for (Area area : this.areas) {
				if (area.max().getX() + MARGIN >= baseX && area.min().getX() - MARGIN <= baseX + 15
					&& area.max().getZ() + MARGIN >= baseZ && area.min().getZ() - MARGIN <= baseZ + 15) {
					local.add(area);
				}
			}

			if (local.isEmpty()) {
				cursorColumn = 256;
				return new int[] {budget, 0};
			}

			int used = 0;

			while (cursorColumn < 256 && budget > 0 && visits > 0) {
				int x = baseX + (cursorColumn & 15);
				int z = baseZ + (cursorColumn >> 4);
				cursorColumn++;

				int top = Integer.MIN_VALUE;
				int bottom = Integer.MAX_VALUE;

				for (Area area : local) {
					if (x < area.min().getX() - MARGIN || x > area.max().getX() + MARGIN
						|| z < area.min().getZ() - MARGIN || z > area.max().getZ() + MARGIN) {
						continue;
					}

					top = Math.max(top, area.max().getY() + 1);
					bottom = Math.min(bottom, area.min().getY() - 1);
				}

				if (top == Integer.MIN_VALUE) {
					continue;
				}

				int source = chunk.getSkyLightSources().getLowestSourceY(x & 15, z & 15);
				bottom = Math.max(level.getMinY(), Math.min(bottom, source));
				top = Math.min(level.getMaxY(), top);

				for (int y = top; y >= bottom && budget > 0 && visits > 0; y--) {
					visits--;
					used++;

					this.pos.set(x, y, z);
					if (chunk.getBlockState(this.pos).isAir()) {
						// 空气换成空气不会改变任何光照结果。
						continue;
					}

					level.getLightEngine().checkBlock(this.pos);
					budget--;
				}
			}

			return new int[] {budget, used};
		}
	}
}
