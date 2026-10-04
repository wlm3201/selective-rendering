package com.selectiverendering;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * 把"被淡化的方块"说成空气的 {@link BlockGetter} 外壳，只给准星射线用。
 *
 * <h2>为什么这样就能穿透</h2>
 * <p>原版的射线是 {@code BlockGetter.clip}（一个 default 方法，
 * 26.1.2 / 26.2 / 26.3 三版都只有这一处定义，<b>没有任何类覆写它</b>）。
 * 它关于"世界长什么样"只问两件事：
 * <pre>
 *   BlockState blockState = this.getBlockState(pos);
 *   FluidState fluidState = this.getFluidState(pos);
 * </pre>
 * 其余全是形状计算（{@code ClipContext#getBlockShape} / {@code getFluidShape} /
 * {@code BlockGetter#clipWithInteractionOverride}），都走 public 或 default 方法。
 * 所以只要把这两个入口改成"被淡化就答空气"，射线就当它不存在——
 * 而 DDA 遍历、OUTLINE 形状、交互形状覆盖、{@code inside} 标志、MISS 兜底
 * <b>全部由原版代码算</b>，一行形状逻辑都不用复制。
 *
 * <h2>为什么不用"命中隐藏方块就把起点推过去再打一次"</h2>
 * <p>那种写法在不完整方块上会翻车：{@code VoxelShape.clip} 在起点位于满格体素内部时
 * 直接返回 {@code inside=true} 的命中，用"命中点 + ε 推进"容易原地打转，
 * 还得额外加收敛保护。而"当空气"是一次遍历、零特例。
 *
 * <h2>代价</h2>
 * <p>本类只实现 {@link BlockGetter}（3 个抽象方法）+ {@code LevelHeightAccessor}
 * （2 个），全部委托给真实世界。仍然会传进 {@code state.getShape(level, pos, ctx)}
 * 当 {@code level} 用，但方块取形状基本只读自己的状态，
 * 少数查邻居的也由委托兜住（最坏情况是"栅栏不连向被淡化的栅栏"，可接受）。
 *
 * <p>调用方是 {@code mixin/ClientLevelClipMixin}（给 {@code ClientLevel} 补的
 * {@code clip} 覆写）。那里是原版 {@code Entity#pick}、OrbitCam 等等
 * 一切客户端射线的共同收口，详见那个类的注释。
 *
 * <h2>开关判定在这里做</h2>
 * <p>外壳<b>自己</b>查 {@code isPassThroughActive()}，所以调用方无条件套一层就行：
 * 关闭时它原样转发，走的还是同一个 {@code BlockGetter#clip} default 实现，
 * 结果与原版逐字一致。
 *
 * <p>判定用 {@link SelectiveRenderingManager#isFaded} 这条<b>不记账</b>的路径
 * （射线不属于渲染管线，不能往 {@code HiddenSections} 写）。
 */
public final class FadedBlockGetter implements BlockGetter {
	private final BlockGetter delegate;

	public FadedBlockGetter(BlockGetter delegate) {
		this.delegate = delegate;
	}

	/** 这一格要不要当空气。开关关闭时恒为 false。 */
	private boolean faded(BlockState state, BlockPos pos) {
		return SelectiveRenderingManager.isPassThroughActive() && SelectiveRenderingManager.isFaded(state, pos);
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		BlockState state = delegate.getBlockState(pos);
		return faded(state, pos) ? Blocks.AIR.defaultBlockState() : state;
	}

	/**
	 * 被淡化的流体同样当空气——否则淡化的水还是会挡住射线。
	 *
	 * <p>注意：这里重新取了一次 {@code getBlockState}（{@code clip} 刚取过同一个 pos）。
	 * 射线每帧只跑两次、每次遍历十几格，多一次查表可以忽略，
	 * 换来的是不用在本类里维护"上一次访问到哪格"的可变缓存。
	 */
	@Override
	public FluidState getFluidState(BlockPos pos) {
		BlockState state = delegate.getBlockState(pos);
		return faded(state, pos) ? Fluids.EMPTY.defaultFluidState() : delegate.getFluidState(pos);
	}

	@Override
	public BlockEntity getBlockEntity(BlockPos pos) {
		return delegate.getBlockEntity(pos);
	}

	@Override
	public int getHeight() {
		return delegate.getHeight();
	}

	@Override
	public int getMinY() {
		return delegate.getMinY();
	}
}
