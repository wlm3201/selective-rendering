package com.selectiverendering;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 热路径上复用的可变 {@link BlockPos}（每线程一个）。
 *
 * <h2>为什么需要</h2>
 * <p>区块网格构建时"看一眼邻居"这类操作以前都写成 {@code pos.relative(dir)}，
 * 每调一次就新建一个 {@link BlockPos}。原版自己就是靠
 * {@code ModelBlockRenderer.scratchPos} / {@code AbstractBlockRenderContext.cachedPositionObject}
 * 这种复用对象来避免的，我们注入的代码不该反过来往热路径里塞分配。
 * 一个 16³ 的 section 有几千个方块、每个方块要问 6 个方向的邻居，
 * 累积起来是每秒几十万次分配。
 *
 * <h2>⚠ 使用约束</h2>
 * <p>同一个槽位在同一时刻只能被一个"逻辑坐标"占用，<b>用完即弃</b>：
 * <pre>
 *   // 正确：拿到之后立刻用掉，中间不调用任何可能再用 scratch 的方法
 *   BlockPos n = BlockPosScratch.offset(pos, dir);
 *   if (SelectiveRenderingManager.isHidden(level.getBlockState(n), n)) { ... }
 *
 *   // 错误：跨调用持有
 *   BlockPos n = BlockPosScratch.offset(pos, dir);
 *   someMethod(n);              // 里面又调了 offset() → n 被改掉
 *   use(n);
 * </pre>
 * 换句话说：<b>不要把它存进字段、不要跨方法边界传递</b>。
 * 需要长期持有的坐标请照旧用 {@code relative()} / {@code immutable()}。
 *
 * <p>两个槽位 {@link #get()} / {@link #alt()} 是为了允许"一个坐标还没用完，
 * 又要临时算另一个"的情况（例如同时持有自己和邻居）。
 */
public final class BlockPosScratch {
	private static final ThreadLocal<BlockPos.MutableBlockPos> PRIMARY = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
	private static final ThreadLocal<BlockPos.MutableBlockPos> ALTERNATE = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

	private BlockPosScratch() {
	}

	/** 主槽位。返回的对象会被下一次 {@code get()/offset()/at()} 覆盖。 */
	public static BlockPos.MutableBlockPos get() {
		return PRIMARY.get();
	}

	/** 副槽位，与主槽位互不干扰。 */
	public static BlockPos.MutableBlockPos alt() {
		return ALTERNATE.get();
	}

	/** 主槽位设为 {@code from} 沿 {@code direction} 偏移一格。 */
	public static BlockPos.MutableBlockPos offset(BlockPos from, Direction direction) {
		return PRIMARY.get().setWithOffset(from, direction);
	}

	/** 副槽位设为 {@code from} 沿 {@code direction} 偏移一格。 */
	public static BlockPos.MutableBlockPos offsetAlt(BlockPos from, Direction direction) {
		return ALTERNATE.get().setWithOffset(from, direction);
	}

	/** 主槽位设为 {@code (x, y, z)}。 */
	public static BlockPos.MutableBlockPos at(int x, int y, int z) {
		return PRIMARY.get().set(x, y, z);
	}

	/** 副槽位设为 {@code (x, y, z)}。 */
	public static BlockPos.MutableBlockPos atAlt(int x, int y, int z) {
		return ALTERNATE.get().set(x, y, z);
	}
}
