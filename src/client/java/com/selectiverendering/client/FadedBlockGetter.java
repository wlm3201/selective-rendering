package com.selectiverendering.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public final class FadedBlockGetter implements BlockGetter {
    private final BlockGetter delegate;

    public FadedBlockGetter(BlockGetter delegate) {
        this.delegate = delegate;
    }

    private boolean faded(BlockState state, BlockPos pos) {
        return SelectiveRenderingManager.isPassThroughActive()
                && SelectiveRenderingManager.isFaded(state, pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = delegate.getBlockState(pos);
        return faded(state, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

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
