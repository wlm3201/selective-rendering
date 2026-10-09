package com.selectiverendering.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;

public final class RegionMerger {
    private RegionMerger() {}

    public static List<Region> merge(Collection<BlockPos> positions) {
        if (positions.isEmpty()) {
            return List.of();
        }

        Map<Integer, Set<Long>> layers = new TreeMap<>();

        for (BlockPos pos : positions) {
            layers.computeIfAbsent(pos.getY(), y -> new HashSet<>())
                    .add(pack(pos.getX(), pos.getZ()));
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

                boxes.add(
                        new Region(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ)));
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
