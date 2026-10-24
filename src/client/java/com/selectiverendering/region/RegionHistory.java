package com.selectiverendering.region;

import com.selectiverendering.SelectiveRenderingManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/** 选区的撤销 / 重做：每次选区列表变化（添加、删除、裁剪、合并、属性修改）前后各存一份快照。 */
public final class RegionHistory {
	private static final int LIMIT = 64;
	private static final long COALESCE_MILLIS = 1200L;

	private record Snapshot(List<Region> regions, UUID selected) {
	}

	private static final Deque<Snapshot> UNDO = new ArrayDeque<>();
	private static final Deque<Snapshot> REDO = new ArrayDeque<>();

	private static Snapshot last;
	private static boolean restoring;
	private static UUID lastEditedId;
	private static long lastEditTime;

	private RegionHistory() {
	}

	/** 从磁盘加载完成后调用，丢弃旧历史；调用方须持有 LOCK。 */
	public static void reset() {
		UNDO.clear();
		REDO.clear();
		last = capture();
		lastEditedId = null;
	}

	/** 选区提交后调用；调用方须持有 LOCK。 */
	public static void record() {
		if (restoring) {
			return;
		}

		Snapshot now = capture();

		if (last == null) {
			last = now;
			return;
		}

		if (sameRegions(last.regions(), now.regions())) {
			last = new Snapshot(last.regions(), now.selected());
			return;
		}

		UUID edited = singleEditedId(last.regions(), now.regions());
		long time = System.currentTimeMillis();
		boolean coalesce = edited != null && edited.equals(lastEditedId) && time - lastEditTime < COALESCE_MILLIS && !UNDO.isEmpty();

		if (!coalesce) {
			UNDO.push(last);
			while (UNDO.size() > LIMIT) {
				UNDO.removeLast();
			}
		}

		REDO.clear();
		lastEditedId = edited;
		lastEditTime = time;
		last = now;
	}

	public static boolean undo() {
		return step(UNDO, REDO);
	}

	public static boolean redo() {
		return step(REDO, UNDO);
	}

	/*
	//Useless now?
	public static int undoSteps() {
		synchronized (SelectiveRenderingManager.LOCK) {
			return UNDO.size();
		}
	}

	public static int redoSteps() {
		synchronized (SelectiveRenderingManager.LOCK) {
			return REDO.size();
		}
	}*/

	private static boolean step(Deque<Snapshot> from, Deque<Snapshot> to) {
		List<Region> before;
		List<Region> after;

		synchronized (SelectiveRenderingManager.LOCK) {
			if (from.isEmpty()) {
				return false;
			}

			Snapshot target = from.pop();
			Snapshot now = capture();

			to.push(now);
			while (to.size() > LIMIT) {
				to.removeLast();
			}

			before = now.regions();
			after = target.regions();

			restoring = true;
			try {
				SelectiveRenderingManager.REGIONS.clear();
				SelectiveRenderingManager.REGIONS.addAll(target.regions());

				UUID selected = target.selected();
				boolean exists = selected != null && target.regions().stream().anyMatch(region -> region.id().equals(selected));
				SelectiveRenderingManager.selectedRegionId = exists ? selected : null;

				SelectiveRenderingManager.commitAndRemember();
			} finally {
				restoring = false;
			}

			last = capture();
			lastEditedId = null;
		}

		RegionEditor.refreshAfterRestore(before, after);
		return true;
	}

	private static Snapshot capture() {
		return new Snapshot(List.copyOf(SelectiveRenderingManager.REGIONS), SelectiveRenderingManager.selectedRegionId);
	}

	private static boolean sameRegions(List<Region> a, List<Region> b) {
		if (a.size() != b.size()) {
			return false;
		}

		for (int i = 0; i < a.size(); i++) {
			if (a.get(i) != b.get(i)) {
				return false;
			}
		}

		return true;
	}

	/** 两份快照只有一个选区被原地修改（数量和顺序不变，id 不变）时返回它的 id，否则返回 null。 */
	private static UUID singleEditedId(List<Region> before, List<Region> after) {
		if (before.size() != after.size()) {
			return null;
		}

		UUID edited = null;
		List<Region> differing = new ArrayList<>();

		for (int i = 0; i < before.size(); i++) {
			if (before.get(i) == after.get(i)) {
				continue;
			}

			if (!before.get(i).id().equals(after.get(i).id())) {
				return null;
			}

			differing.add(after.get(i));
			edited = after.get(i).id();
		}

		return differing.size() == 1 ? edited : null;
	}
}
