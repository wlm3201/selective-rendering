package com.selectiverendering.interaction;

import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.render.hiding.AlphaGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/** 为交互射线提供世界视图，将淡化的方块和流体视为空气。 */
public final class FadedBlockGetter implements BlockGetter {
	private final BlockGetter delegate;

	public FadedBlockGetter(BlockGetter delegate) {
		this.delegate = delegate;
	}

	private boolean faded(BlockState state, BlockPos pos) {
		return SelectiveRenderingManager.isPassThroughActive() && AlphaGetter.isFaded(state, pos);
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		BlockState state = delegate.getBlockState(pos);
		return faded(state, pos) ? Blocks.AIR.defaultBlockState() : state;
	}

	/** 淡化流体不阻挡射线。 */
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
