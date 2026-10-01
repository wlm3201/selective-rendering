package com.selectiverendering;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 把一堆散落的方块坐标"贪心地"合并成尽量少的长方体 {@link Region}。
 *
 * <p>只有 {@link BlockChangeRecorder} 在用：记录器收集到的变化坐标可能有几万个，
 * 如果一格一格地加进选区列表，判定开销会爆炸，所以先压成若干个盒子。
 *
 * <h2>算法（贪心，不保证最优）</h2>
 * <ol>
 *   <li>按 Y 分层，每层存该层所有 (x,z) 组成的集合（用 long 打包成一个 key）；</li>
 *   <li>在某一层任取一个还没被吃掉的格子，向右扩成一条最长的行，再向前扩成最大的矩形；</li>
 *   <li>把这个矩形从当前层移除，然后试着<b>向上</b>长高：只要上一层的同一个矩形也完整存在，
 *       就把它也吃掉并继续往上一层；</li>
 *   <li>得到一个 3D 盒子，重复直到该层空了。</li>
 * </ol>
 *
 * <p>因为是贪心，结果可能远多于理论最小值（比如棋盘状分布会退化成很多小盒子），
 * 但对"记录一次建筑/挖矿过程"这种实际数据够用。
 */
public final class RegionMerger {
	private RegionMerger() {
	}

	public static List<Region> merge(Collection<BlockPos> positions) {
		if (positions.isEmpty()) {
			return List.of();
		}

		Map<Integer, Set<Long>> layers = new TreeMap<>();

		for (BlockPos pos : positions) {
			layers.computeIfAbsent(pos.getY(), y -> new HashSet<>()).add(pack(pos.getX(), pos.getZ()));
		}

		List<Region> boxes = new ArrayList<>();

		for (Map.Entry<Integer, Set<Long>> entry : layers.entrySet()) {
			int minY = entry.getKey();
			Set<Long> columns = entry.getValue();

			while (!columns.isEmpty()) {
				long start = columns.iterator().next();
				int minX = unpackX(start);
				int minZ = unpackZ(start);

				int maxX = minX;
				while (columns.contains(pack(maxX + 1, minZ))) {
					maxX++;
				}

				int maxZ = minZ;
				while (hasRow(columns, minX, maxX, maxZ + 1)) {
					maxZ++;
				}

				take(columns, minX, maxX, minZ, maxZ);

				int maxY = minY;
				Set<Long> above = layers.get(maxY + 1);

				while (above != null && hasRectangle(above, minX, maxX, minZ, maxZ)) {
					take(above, minX, maxX, minZ, maxZ);
					maxY++;
					above = layers.get(maxY + 1);
				}

				boxes.add(new Region(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ)));
			}
		}

		return boxes;
	}

	private static boolean hasRow(Set<Long> columns, int minX, int maxX, int z) {
		for (int x = minX; x <= maxX; x++) {
			if (!columns.contains(pack(x, z))) {
				return false;
			}
		}

		return true;
	}

	private static boolean hasRectangle(Set<Long> columns, int minX, int maxX, int minZ, int maxZ) {
		for (int z = minZ; z <= maxZ; z++) {
			if (!hasRow(columns, minX, maxX, z)) {
				return false;
			}
		}

		return true;
	}

	private static void take(Set<Long> columns, int minX, int maxX, int minZ, int maxZ) {
		for (int z = minZ; z <= maxZ; z++) {
			for (int x = minX; x <= maxX; x++) {
				columns.remove(pack(x, z));
			}
		}
	}

	/** 把 (x, z) 打包进一个 long：高 32 位是 x，低 32 位是 z（z 为负数时按补码存，{@link #unpackZ} 能还回来）。 */
	private static long pack(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	private static int unpackX(long packed) {
		return (int) (packed >>> 32);
	}

	private static int unpackZ(long packed) {
		return (int) packed;
	}
}
