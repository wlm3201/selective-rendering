package com.selectiverendering;

import com.selectiverendering.compat.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 记账本：记住"哪些 section 里出现过被隐藏的方块"。
 *
 * <h2>为什么需要它</h2>
 * <p>改<b>透明度</b>时几何没变（方块还在，只是 alpha 从 128 变成 64），
 * 理论上只需要重新烘焙那些"含隐藏方块"的 section，而不是整个世界。
 * 于是 {@code SelectiveRenderingManager.getAlpha} 每次判定为真时就往这里 {@link #note} 一笔，
 * 改透明度时 {@link #mark()} 只把这些 section（含邻居，因为面剔除受邻居影响）标脏。
 *
 * <h2>退出策略</h2>
 * <ul>
 *   <li>记账条数超过 {@link #TOO_MANY_TO_BE_WORTH_IT}（1500 个 section）时
 *       {@link #mark()} 返回 false，调用方退化成全量重建——精算已经不划算了。</li>
 *   <li>条目上限 {@link #MOST_REMEMBERED}，防止长时间游玩后无限膨胀。</li>
 * </ul>
 *
 * <h2>线程</h2>
 * <p>{@link #note} 会被区块构建线程和光照线程调用，所以用 {@link ConcurrentHashMap} 的 keySet。
 * 注意：它同时意味着"查询方法 {@code getAlpha} 会写全局状态"，是从渲染线程之外进来的副作用。
 *
 * <h2>为什么条数用 {@link AtomicInteger} 而不是 {@code SECTIONS.size()}</h2>
 * <p>这里是全 Mod 最热的写入点之一（每个被隐藏的方块、每帧成千上万次），
 * 而 {@code ConcurrentHashMap.size()} 内部要遍历 {@code counterCells} 求和，
 * 既不是 O(1)，又会让多个构建线程争抢同一批 cache line。
 * 改成"只在 {@code add} 真的新增了元素时才自增"的原子计数器后，
 * 常规路径只剩一次 {@code get()}（普通 volatile 读）。
 */
public final class HiddenSections {
	private static final int TOO_MANY_TO_BE_WORTH_IT = 1500;

	private static final int MOST_REMEMBERED = 1 << 18;

	private static final Set<Long> SECTIONS = ConcurrentHashMap.newKeySet();

	private static final AtomicInteger COUNT = new AtomicInteger();

	private HiddenSections() {
	}

	public static void note(BlockPos pos) {
		if (COUNT.get() >= MOST_REMEMBERED) {
			return;
		}

		if (SECTIONS.add(SectionPos.asLong(pos))) {
			COUNT.incrementAndGet();
		}
	}

	/**
	 * 把记过账的 section（含 26 个邻居方向）标脏。
	 *
	 * @return true = 已做增量重建；false = 没法/不值得做，调用方应该全量重建
	 */
	public static boolean mark() {
		int count = COUNT.get();
		if (!Platform.ready() || count == 0 || count > TOO_MANY_TO_BE_WORTH_IT) {
			return false;
		}

		for (long section : SECTIONS) {
			Platform.markSectionWithNeighbors(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
		}

		return true;
	}

	/**
	 * 按世界坐标的 {@link AABB} 标脏（新增/删除/裁剪选区时用）。
	 *
	 * <p>这里必须包含邻居：一个 section 的网格会因相邻 section 的内容不同而不同
	 * （面剔除、AO 都看邻居），只标自己会留下接缝。
	 */
	public static void mark(AABB box) {
		if (!Platform.ready()) {
			return;
		}

		int minX = SectionPos.blockToSectionCoord((int) Math.floor(box.minX));
		int minY = SectionPos.blockToSectionCoord((int) Math.floor(box.minY));
		int minZ = SectionPos.blockToSectionCoord((int) Math.floor(box.minZ));
		int maxX = SectionPos.blockToSectionCoord((int) Math.ceil(box.maxX) - 1);
		int maxY = SectionPos.blockToSectionCoord((int) Math.ceil(box.maxY) - 1);
		int maxZ = SectionPos.blockToSectionCoord((int) Math.ceil(box.maxZ) - 1);

		for (int y = minY; y <= maxY; y++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int x = minX; x <= maxX; x++) {
					Platform.markSectionWithNeighbors(x, y, z);
				}
			}
		}
	}

	public static void clear() {
		SECTIONS.clear();
		COUNT.set(0);
	}
}
