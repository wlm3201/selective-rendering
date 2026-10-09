package com.selectiverendering.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public record Region(BlockPos min, BlockPos max) {
    public static Region of(BlockPos first, BlockPos second) {
        return new Region(
                new BlockPos(
                        Math.min(first.getX(), second.getX()),
                        Math.min(first.getY(), second.getY()),
                        Math.min(first.getZ(), second.getZ())),
                new BlockPos(
                        Math.max(first.getX(), second.getX()),
                        Math.max(first.getY(), second.getY()),
                        Math.max(first.getZ(), second.getZ())));
    }

    public AABB box() {
        return new AABB(
                min.getX(),
                min.getY(),
                min.getZ(),
                max.getX() + 1.0,
                max.getY() + 1.0,
                max.getZ() + 1.0);
    }

    public List<Region> subtract(Region cut) {
        Region overlap = overlap(cut);
        if (overlap == null) {
            return List.of(this);
        }

        List<Region> parts = new ArrayList<>();
        BlockPos low = overlap.min();
        BlockPos high = overlap.max();

        if (min.getX() < low.getX()) {
            parts.add(new Region(min, new BlockPos(low.getX() - 1, max.getY(), max.getZ())));
        }

        if (max.getX() > high.getX()) {
            parts.add(new Region(new BlockPos(high.getX() + 1, min.getY(), min.getZ()), max));
        }

        int x1 = Math.max(min.getX(), low.getX());
        int x2 = Math.min(max.getX(), high.getX());

        if (min.getY() < low.getY()) {
            parts.add(
                    new Region(
                            new BlockPos(x1, min.getY(), min.getZ()),
                            new BlockPos(x2, low.getY() - 1, max.getZ())));
        }

        if (max.getY() > high.getY()) {
            parts.add(
                    new Region(
                            new BlockPos(x1, high.getY() + 1, min.getZ()),
                            new BlockPos(x2, max.getY(), max.getZ())));
        }

        int y1 = Math.max(min.getY(), low.getY());
        int y2 = Math.min(max.getY(), high.getY());

        if (min.getZ() < low.getZ()) {
            parts.add(
                    new Region(
                            new BlockPos(x1, y1, min.getZ()),
                            new BlockPos(x2, y2, low.getZ() - 1)));
        }

        if (max.getZ() > high.getZ()) {
            parts.add(
                    new Region(
                            new BlockPos(x1, y1, high.getZ() + 1),
                            new BlockPos(x2, y2, max.getZ())));
        }

        return parts;
    }

    public boolean overlaps(Region other) {
        return min.getX() <= other.max.getX()
                && max.getX() >= other.min.getX()
                && min.getY() <= other.max.getY()
                && max.getY() >= other.min.getY()
                && min.getZ() <= other.max.getZ()
                && max.getZ() >= other.min.getZ();
    }

    @Nullable
    private Region overlap(Region other) {
        if (!overlaps(other)) {
            return null;
        }

        return new Region(
                new BlockPos(
                        Math.max(min.getX(), other.min.getX()),
                        Math.max(min.getY(), other.min.getY()),
                        Math.max(min.getZ(), other.min.getZ())),
                new BlockPos(
                        Math.min(max.getX(), other.max.getX()),
                        Math.min(max.getY(), other.max.getY()),
                        Math.min(max.getZ(), other.max.getZ())));
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX()
                && pos.getX() <= max.getX()
                && pos.getY() >= min.getY()
                && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ()
                && pos.getZ() <= max.getZ();
    }

    public String toSource() {
        return source(min) + ":" + source(max);
    }

    @Nullable
    public static Region parse(String input) {
        if (input == null) {
            return null;
        }

        String[] corners = input.trim().split(":", 2);
        if (corners.length != 2) {
            return null;
        }

        BlockPos first = corner(corners[0]);
        BlockPos second = corner(corners[1]);
        return first == null || second == null ? null : of(first, second);
    }

    @Nullable
    private static BlockPos corner(String text) {
        String[] parts = text.trim().split(",");
        if (parts.length != 3) {
            return null;
        }

        try {
            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String source(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
