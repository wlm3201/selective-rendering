package com.selectiverendering.client;

import com.selectiverendering.client.compat.Platform;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;

public final class HiddenSections {
    private static final int TOO_MANY_TO_BE_WORTH_IT = 1500;

    private static final int MOST_REMEMBERED = 1 << 18;

    private static final Set<Long> SECTIONS = ConcurrentHashMap.newKeySet();

    private static final AtomicInteger COUNT = new AtomicInteger();

    private HiddenSections() {}

    public static void note(BlockPos pos) {
        if (COUNT.get() >= MOST_REMEMBERED) {
            return;
        }

        if (SECTIONS.add(SectionPos.asLong(pos))) {
            COUNT.incrementAndGet();
        }
    }

    public static boolean mark() {
        int count = COUNT.get();
        if (!Platform.ready() || count == 0 || count > TOO_MANY_TO_BE_WORTH_IT) {
            return false;
        }

        for (long section : SECTIONS) {
            Platform.markSectionWithNeighbors(
                    SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
        }

        return true;
    }

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
