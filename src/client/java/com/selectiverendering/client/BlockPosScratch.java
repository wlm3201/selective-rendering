package com.selectiverendering.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class BlockPosScratch {
    private static final ThreadLocal<BlockPos.MutableBlockPos> PRIMARY =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    private static final ThreadLocal<BlockPos.MutableBlockPos> ALTERNATE =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    private BlockPosScratch() {}

    public static BlockPos.MutableBlockPos get() {
        return PRIMARY.get();
    }

    public static BlockPos.MutableBlockPos alt() {
        return ALTERNATE.get();
    }

    public static BlockPos.MutableBlockPos offset(BlockPos from, Direction direction) {
        return PRIMARY.get().setWithOffset(from, direction);
    }

    public static BlockPos.MutableBlockPos offsetAlt(BlockPos from, Direction direction) {
        return ALTERNATE.get().setWithOffset(from, direction);
    }

    public static BlockPos.MutableBlockPos at(int x, int y, int z) {
        return PRIMARY.get().set(x, y, z);
    }

    public static BlockPos.MutableBlockPos atAlt(int x, int y, int z) {
        return ALTERNATE.get().set(x, y, z);
    }
}
