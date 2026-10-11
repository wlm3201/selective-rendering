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
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** 在客户端光照和 AO 计算中将隐藏方块视为空气，并调度受影响区块的光照重算。 */
public final class LightSuppression {
	private static final int BUDGET_PER_FLUSH = 2000;

	private static final int MARGIN = 1;

	/** 全区块扫描的每帧时间预算（纳秒）及检查数量上限。 */
	private static final long GLOBAL_TIME_BUDGET = 2_000_000L;
	private static final int GLOBAL_CHECK_LIMIT = 6000;

	private static final Predicate<BlockState> LIGHT_RELEVANT = state -> state.getLightDampening() > 0 || state.getLightEmission() > 0;

	private static final long NO_CHUNK = Long.MIN_VALUE;

	private static final int SOURCE_CACHE_LIMIT = 300_000;

	/** 列 (x, z) -> (真实源头高度, 隐藏方块当空气后的源头高度)，各占 32 位。 */
	private static final Long2LongOpenHashMap SOURCE_CACHE = new Long2LongOpenHashMap();

	private static final Object LOCK = new Object();

	/** 按插入顺序保存待重算区块并去重。 */
	private static final LongLinkedOpenHashSet PENDING = new LongLinkedOpenHashSet();

	/** 已删除或裁剪、仍需还原光照的历史选区。 */
	private static final List<Area> RETIRED_AREAS = new ArrayList<>();

	/** 全局扫描队列：ALL 还原所有相关方块，FILTERED 仅检查当前隐藏方块。 */
	private static final LongLinkedOpenHashSet GLOBAL_ALL = new LongLinkedOpenHashSet();
	private static final LongLinkedOpenHashSet GLOBAL_FILTERED = new LongLinkedOpenHashSet();

	private static long globalChunk = NO_CHUNK;
	private static boolean globalAll;
	private static int globalIndex;

	/** 上次重算是否使用全局模式，用于切走后还原光照。 */
	private static boolean lastGlobal;

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


	/** 将当前及历史选区涉及的区块加入光照重算队列。 */
	public static void relight() {
		synchronized (SOURCE_CACHE) {
			SOURCE_CACHE.clear();
		}

		if (SelectiveRenderingManager.isFullBright()) {
			return;
		}

		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}

		SelectiveRenderingManager.Mode mode = SelectiveRenderingManager.getMode();
		boolean global = isGlobal(mode);

		if (global || lastGlobal) {
			enqueueAround(level);
		}

		lastGlobal = global;

		List<Area> targets = targets();
		if (targets.isEmpty()) {
			return;
		}

		synchronized (LOCK) {
			for (Area region : targets) {
				int minChunkX = (region.min().getX() - MARGIN) >> 4;
				int maxChunkX = (region.max().getX() + MARGIN) >> 4;
				int minChunkZ = (region.min().getZ() - MARGIN) >> 4;
				int maxChunkZ = (region.max().getZ() + MARGIN) >> 4;

				for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
					for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
						enqueue(chunkX, chunkZ);
					}
				}
			}
		}
	}

	/** 记录已移除、仍需还原光照的选区。 */
	public static void retire(List<Area> regions) {
		synchronized (LOCK) {
			for (Area region : regions) {
				if (!RETIRED_AREAS.contains(region)) {
					RETIRED_AREAS.add(region);
				}
			}
		}
	}

	/** 标记收到服务端光照的区块，待数据落地后重算。 */
	public static void markChunk(int chunkX, int chunkZ) {
		SelectiveRenderingManager.Mode mode = SelectiveRenderingManager.getMode();
		if (mode == SelectiveRenderingManager.Mode.OFF || SelectiveRenderingManager.isFullBright()) {
			return;
		}

		synchronized (LOCK) {
			if (isGlobal(mode)) {
				long packed = ChunkPos.pack(chunkX, chunkZ);
				if (!GLOBAL_ALL.contains(packed)) {
					GLOBAL_FILTERED.add(packed);
				}
			} else if (mode.usesRegion()) {
				enqueue(chunkX, chunkZ);
			}
		}
	}

	private static boolean isGlobal(SelectiveRenderingManager.Mode mode) {
		return mode == SelectiveRenderingManager.Mode.BLACKLIST || mode == SelectiveRenderingManager.Mode.WHITELIST;
	}

	private static void enqueueAround(ClientLevel level) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}

		int radius = minecraft.options.renderDistance().get();
		int centerX = minecraft.player.chunkPosition().x();
		int centerZ = minecraft.player.chunkPosition().z();

		List<long[]> ordered = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				ordered.add(new long[] {(long) dx * dx + (long) dz * dz, ChunkPos.pack(centerX + dx, centerZ + dz)});
			}
		}
		ordered.sort((a, b) -> Long.compare(a[0], b[0]));

		synchronized (LOCK) {
			for (long[] entry : ordered) {
				GLOBAL_ALL.add(entry[1]);
				GLOBAL_FILTERED.remove(entry[1]);
			}

			if (globalChunk != NO_CHUNK && !globalAll) {
				globalChunk = NO_CHUNK;
			}
		}
	}

	/**
	 * 天光源头高度：从真实源头往下穿过所有"当空气后不挡光"的方块，停在第一个仍然挡光的方块上方。
	 * 只对客户端引擎生效（调用方已核对），并且按列缓存；缓存在 {@link #relight()} 和该列
	 * {@code checkBlock} 时失效。
	 *
	 * @param real 原版按真实方块算出的结果
	 */
	public static int lowestSourceY(LightChunk chunk, int x, int z, int real) {
		SelectiveRenderingManager.Mode mode = SelectiveRenderingManager.getMode();
		if (real == Integer.MIN_VALUE || mode == SelectiveRenderingManager.Mode.OFF
			|| SelectiveRenderingManager.isFullBright() || hidesOutside(mode)) {
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

	public static void invalidateColumn(int x, int z) {
		synchronized (SOURCE_CACHE) {
			if (!SOURCE_CACHE.isEmpty()) {
				SOURCE_CACHE.remove(BlockPos.asLong(x, 0, z));
			}
		}
	}

	private static boolean hidesOutside(SelectiveRenderingManager.Mode mode) {
		return mode == SelectiveRenderingManager.Mode.REGION_OUTSIDE
			|| mode == SelectiveRenderingManager.Mode.REGION_OUTSIDE_LISTED
			|| mode == SelectiveRenderingManager.Mode.REGION_OUTSIDE_UNLISTED;
	}

	private static void enqueue(int chunkX, int chunkZ) {
		long packed = ChunkPos.pack(chunkX, chunkZ);
		PENDING.add(packed);

		if (packed == cursorChunk) {
			// 区块再次变脏时，从首列重新扫描。
			cursorColumn = 0;
		}
	}


	/** 在客户端光照更新后，按预算处理待重算区块。 */
	public static void flush(LevelLightEngine engine) {
		synchronized (LOCK) {
			if (PENDING.isEmpty() && GLOBAL_ALL.isEmpty() && GLOBAL_FILTERED.isEmpty()) {
				return;
			}
		}

		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			synchronized (LOCK) {
				PENDING.clear();
				GLOBAL_ALL.clear();
				GLOBAL_FILTERED.clear();
				cursorChunk = NO_CHUNK;
				globalChunk = NO_CHUNK;
			}
			return;
		}

		if (engine != level.getLightEngine()) {
			return;
		}

		flushGlobal(level);

		List<Area> targets = targets();

		synchronized (LOCK) {
			int budget = BUDGET_PER_FLUSH;

			while (budget > 0 && !PENDING.isEmpty()) {
				if (cursorChunk == NO_CHUNK) {
					cursorChunk = PENDING.firstLong();
					cursorColumn = 0;
				}

				LevelChunk chunk = level.getChunkSource().getChunk(ChunkPos.getX(cursorChunk), ChunkPos.getZ(cursorChunk), false);
				if (chunk == null || targets.isEmpty()) {
					finishChunk();
					continue;
				}

				budget = scanChunk(level, chunk, targets, budget);
				if (cursorColumn >= 256) {
					finishChunk();
				}
			}
		}
	}

	private static void flushGlobal(ClientLevel level) {
		long deadline = System.nanoTime() + GLOBAL_TIME_BUDGET;
		int checks = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		synchronized (LOCK) {
			while (checks < GLOBAL_CHECK_LIMIT && System.nanoTime() < deadline) {
				if (globalChunk == NO_CHUNK) {
					if (!GLOBAL_ALL.isEmpty()) {
						globalChunk = GLOBAL_ALL.firstLong();
						globalAll = true;
					} else if (!GLOBAL_FILTERED.isEmpty()) {
						globalChunk = GLOBAL_FILTERED.firstLong();
						globalAll = false;
					} else {
						return;
					}

					globalIndex = 0;
				}

				LevelChunk chunk = level.getChunkSource().getChunk(ChunkPos.getX(globalChunk), ChunkPos.getZ(globalChunk), false);
				if (chunk == null) {
					finishGlobal();
					continue;
				}

				LevelChunkSection[] sections = chunk.getSections();
				int total = sections.length * 4096;
				int baseX = chunk.getPos().getMinBlockX();
				int baseZ = chunk.getPos().getMinBlockZ();

				while (globalIndex < total) {
					int sectionIndex = globalIndex >> 12;
					LevelChunkSection section = sections[sectionIndex];

					if (section.hasOnlyAir() || !section.maybeHas(LIGHT_RELEVANT)) {
						globalIndex = (sectionIndex + 1) << 12;
						continue;
					}

					int local = globalIndex & 4095;
					globalIndex++;

					int localX = local & 15;
					int localZ = (local >> 4) & 15;
					int localY = local >> 8;

					BlockState state = section.getBlockState(localX, localY, localZ);
					if (LIGHT_RELEVANT.test(state)) {
						int y = (level.getSectionYFromSectionIndex(sectionIndex) << 4) + localY;
						pos.set(baseX + localX, y, baseZ + localZ);

						if (globalAll || airIfHidden(state, pos) != state) {
							level.getLightEngine().checkBlock(pos);
							checks++;
						}
					}

					if ((globalIndex & 511) == 0 && (checks >= GLOBAL_CHECK_LIMIT || System.nanoTime() >= deadline)) {
						return;
					}
				}

				finishGlobal();
			}
		}
	}

	private static void finishGlobal() {
		(globalAll ? GLOBAL_ALL : GLOBAL_FILTERED).remove(globalChunk);
		globalChunk = NO_CHUNK;
		globalIndex = 0;
	}

	private static void finishChunk() {
		PENDING.remove(cursorChunk);
		cursorChunk = NO_CHUNK;
		cursorColumn = 0;
	}

	/** 扫描区块中与选区相交的列并提交光照检查，返回剩余预算。 */
	private static int scanChunk(ClientLevel level, LevelChunk chunk, List<Area> targets, int budget) {
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();

		List<Area> local = new ArrayList<>();
		for (Area region : targets) {
			if (region.max().getX() + MARGIN >= baseX && region.min().getX() - MARGIN <= baseX + 15
				&& region.max().getZ() + MARGIN >= baseZ && region.min().getZ() - MARGIN <= baseZ + 15) {
				local.add(region);
			}
		}

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		while (cursorColumn < 256 && budget > 0) {
			int x = baseX + (cursorColumn & 15);
			int z = baseZ + (cursorColumn >> 4);
			cursorColumn++;

			int top = Integer.MIN_VALUE;
			int bottom = Integer.MAX_VALUE;

			for (Area region : local) {
				if (x < region.min().getX() - MARGIN || x > region.max().getX() + MARGIN
					|| z < region.min().getZ() - MARGIN || z > region.max().getZ() + MARGIN) {
					continue;
				}

				top = Math.max(top, region.max().getY() + 1);
				bottom = Math.min(bottom, region.min().getY() - 1);
			}

			if (top == Integer.MIN_VALUE) {
				continue;
			}

			int source = chunk.getSkyLightSources().getLowestSourceY(x & 15, z & 15);
			bottom = Math.max(level.getMinY(), Math.min(bottom, source));
			top = Math.min(level.getMaxY(), top);

			for (int y = top; y >= bottom; y--) {
				level.getLightEngine().checkBlock(pos.set(x, y, z));
				budget--;
			}
		}

		return budget;
	}

	private static List<Area> targets() {
		List<Region> regions = SelectiveRenderingManager.blockRegions();

		synchronized (LOCK) {
			List<Area> targets = new ArrayList<>(regions.size() + RETIRED_AREAS.size());

			for (Region region : regions) {
				targets.addAll(region.areas());
			}

			for (Area region : RETIRED_AREAS) {
				if (!targets.contains(region)) {
					targets.add(region);
				}
			}

			return targets;
		}
	}
}
