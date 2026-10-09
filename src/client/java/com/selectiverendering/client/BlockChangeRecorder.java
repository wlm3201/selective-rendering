package com.selectiverendering.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockChangeRecorder {
    private static final int PER_TICK = 2000;

    private static final int MAX_CHANGED = 1 << 19;

    private static final byte MAX_RETRY = 5;

    private static final Map<Long, Byte> pending = new ConcurrentHashMap<>();

    private static final Set<Long> changed = ConcurrentHashMap.newKeySet();

    private static volatile boolean recording;

    private BlockChangeRecorder() {}

    public static boolean isRecording() {
        return recording;
    }

    public static int count() {
        return changed.size();
    }

    public static boolean start() {
        if (recording) {
            return false;
        }

        if (SelectiveRenderingManager.selection() == null) {
            say("no_selection", false);
            return false;
        }

        changed.clear();
        pending.clear();
        recording = true;
        Log.say("[recorder] started in {}", SelectiveRenderingManager.selection().toSource());
        say("started", true);

        if (SelectiveRenderingManager.recordSources().isEmpty()) {
            say("empty_list", false);
        }

        return true;
    }

    public static boolean stop() {
        if (!recording) {
            return false;
        }

        recording = false;
        pending.clear();

        List<BlockPos> positions = new ArrayList<>(changed.size());
        for (long packed : changed) {
            positions.add(BlockPos.of(packed));
        }

        changed.clear();

        List<Region> boxes = RegionMerger.merge(positions);
        Log.say(
                "[recorder] stopped: {} position(s) -> {} region(s)",
                positions.size(),
                boxes.size());

        if (boxes.isEmpty()) {
            say("nothing", false);
            return true;
        }

        boolean subtract =
                SelectiveRenderingManager.getRecordApplyMode()
                        == SelectiveRenderingManager.RecordApplyMode.SUBTRACT;
        boolean applied =
                subtract
                        ? SelectiveRenderingManager.subtractRegions(boxes)
                        : SelectiveRenderingManager.addRegions(boxes) > 0;

        if (!applied) {
            say("nothing", false);
            return true;
        }

        say(subtract ? "clipped" : "recorded", true, String.valueOf(boxes.size()));

        return true;
    }

    public static void toggle() {
        if (recording) {
            stop();
        } else {
            start();
        }
    }

    public static void note(BlockPos pos, BlockState incoming) {
        if (!recording || !inside(pos)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        long packed = pos.asLong();
        if (changed.contains(packed)) {
            return;
        }

        BlockState current = level.getBlockState(pos);

        if (isBeingMoved(current)) {
            BlockState carried = carried(level, pos);
            if (carried == null) {
                defer(packed);
                return;
            }

            consider(packed, carried, true);
            return;
        }

        if (isBeingMoved(incoming)) {
            defer(packed);
            return;
        }

        if (wanted(current, false)) {
            consider(packed, current, false);
            return;
        }

        consider(packed, incoming, false);
    }

    public static void noteMoving(BlockPos pos) {
        if (!recording || !inside(pos)) {
            return;
        }

        long packed = pos.asLong();
        if (changed.contains(packed)) {
            return;
        }

        defer(packed);
    }

    public static void onTick(Minecraft minecraft) {
        if (!recording) {
            return;
        }

        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        int budget = PER_TICK;
        Iterator<Map.Entry<Long, Byte>> iterator = pending.entrySet().iterator();

        while (iterator.hasNext() && budget > 0) {
            Map.Entry<Long, Byte> entry = iterator.next();
            iterator.remove();
            budget--;

            long packed = entry.getKey();
            if (changed.contains(packed)) {
                continue;
            }

            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);
            boolean moving = false;

            if (isBeingMoved(state)) {
                BlockState carried = carried(level, pos);
                if (carried == null) {
                    byte tried = entry.getValue();
                    if (tried < MAX_RETRY) {
                        pending.put(packed, (byte) (tried + 1));
                    }

                    continue;
                }

                state = carried;
                moving = true;
            }

            consider(packed, state, moving);
        }
    }

    private static boolean inside(BlockPos pos) {
        Region selection = SelectiveRenderingManager.selection();
        return selection != null && selection.contains(pos);
    }

    private static void defer(long packed) {
        pending.putIfAbsent(packed, (byte) 0);
    }

    private static void consider(long packed, BlockState state, boolean moving) {
        if (changed.contains(packed) || !wanted(state, moving)) {
            return;
        }

        if (changed.size() >= MAX_CHANGED) {
            Log.say("[recorder] 已达容量上限 {}，自动停止记录", MAX_CHANGED);
            stop();
            say("full", false, String.valueOf(MAX_CHANGED));
            return;
        }

        changed.add(packed);
    }

    private static boolean isBeingMoved(BlockState state) {
        return state.getBlock() instanceof MovingPistonBlock;
    }

    private static BlockState carried(ClientLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof PistonMovingBlockEntity piston ? piston.getMovedState() : null;
    }

    private static boolean wanted(BlockState state, boolean moving) {
        return SelectiveRenderingManager.matchesRecorded(state, moving);
    }

    private static void say(String key, boolean good, Object... arguments) {
        String prefix = SelectiveRendering.MOD_ID + ".message.";
        BlockListMessage.show(Component.translatable(prefix + key, arguments).getString(), good);
    }
}
