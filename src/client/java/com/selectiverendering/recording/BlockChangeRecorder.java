package com.selectiverendering.recording;

import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.matchingrule.BlockListMessage;
import com.selectiverendering.region.Area;
import com.selectiverendering.region.RegionEditor;
import com.selectiverendering.region.RegionMerger;
import com.selectiverendering.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 记录当前选区内符合条件的方块变化，并将结果合并后添加到选区或用于裁剪。 */
public final class BlockChangeRecorder {
	private static final int PER_TICK = 2000;

	private static final int MAX_CHANGED = 1 << 19;

	/** 活塞方块实体尚未创建时，等待后续 tick 解析的最大重试次数。 */
	private static final byte MAX_RETRY = 5;

	/** 待解析位置及其重试次数。 */
	private static final Map<Long, Byte> pending = new ConcurrentHashMap<>();

	private static final Set<Long> changed = ConcurrentHashMap.newKeySet();

	private static volatile boolean recording;

	private BlockChangeRecorder() {
	}

	public static boolean isRecording() {
		return recording;
	}

	public static int count() {
		return changed.size();
	}

	/** 开始记录；要求当前选区的两个角点均已设置。 */
	public static void start() {
		if (recording) {
			return;
		}

		if (RegionEditor.selection() == null) {
			say("no_selection", false);
			return;
		}

		changed.clear();
		pending.clear();
		recording = true;
		Log.debug("[recorder] Recording started in {}", RegionEditor.selection().toSource());
		say("started", true);

		if (SelectiveRenderingManager.recordSources().isEmpty()) {
			say("empty_list", false);
		}

	}

	public static void stop() {
		if (!recording) {
			return;
		}

		recording = false;
		pending.clear();

		List<BlockPos> positions = new ArrayList<>(changed.size());
		for (long packed : changed) {
			positions.add(BlockPos.of(packed));
		}

		changed.clear();

		List<Area> boxes = RegionMerger.merge(positions);
		Log.debug("[recorder] Recording stopped: {} position(s) -> {} region(s)", positions.size(), boxes.size());

		if (boxes.isEmpty()) {
			say("nothing", false);
			return;
		}

		boolean subtract = SelectiveRenderingManager.getRecordApplyMode() == SelectiveRenderingManager.RecordApplyMode.SUBTRACT;
		boolean applied = subtract
			? RegionEditor.subtractAreas(boxes)
			: RegionEditor.addAreas(boxes) > 0;

		if (!applied) {
			say("nothing", false);
			return;
		}

		say(subtract ? "clipped" : "recorded", true, String.valueOf(boxes.size()));

	}

	public static void toggle() {
		if (recording) {
			stop();
		} else {
			start();
		}
	}

	/** 记录服务端确认的变化，新旧状态任一命中即可。 */
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

		// HEAD 注入读取到旧状态；移动方块按其携带状态匹配。
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

	/** 将本地活塞移动位置加入待解析队列。 */
	public static void noteMoving(BlockPos pos) {
		if (!recording || !inside(pos)) {
			return;
		}

		long packed = pos.asLong();
		if (changed.contains(packed)) {
			return;
		}

		// 方块实体在 setBlock 后创建，留到下一 tick 解析。
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
		Area selection = RegionEditor.selection();
		return selection != null && selection.contains(pos);
	}

	private static void defer(long packed) {
		pending.putIfAbsent(packed, (byte) 0);
	}

	/** 按记录条件收集位置并限制容量。 */
	private static void consider(long packed, BlockState state, boolean moving) {
		if (changed.contains(packed) || !wanted(state, moving)) {
			return;
		}

		if (changed.size() >= MAX_CHANGED) {
			Log.warn("[recorder] Hitting record limit {}, stopped.", MAX_CHANGED);
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
