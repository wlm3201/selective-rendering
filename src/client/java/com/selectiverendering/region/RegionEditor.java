package com.selectiverendering.region;

import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.lighting.LightSuppression;
import com.selectiverendering.render.hiding.HiddenSections;
import com.selectiverendering.render.hiding.RebuildScheduler;
import com.selectiverendering.util.Log;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 编辑魔杖的双角点、选区和选区里的范围，并同步配置、区块重建和光照。 */
public final class RegionEditor {
	private static final int MAX_SUBTRACT_FRAGMENTS = 4096;

	private RegionEditor() {
	}

	/** 射线命中的范围，以及它所属的选区。 */
	public record Hit(Region region, Area area) {
	}

	public static BlockPos getCorner(int index) {
		return index == 0 ? SelectiveRenderingManager.config().corner1() : SelectiveRenderingManager.config().corner2();
	}

	public static AABB regionBox() {
		return SelectiveRenderingManager.config().region();
	}

	public static void setCorner(int index, BlockPos pos) {
		synchronized (SelectiveRenderingManager.LOCK) {
			if (index == 0) {
				SelectiveRenderingManager.corner1 = pos.immutable();
			}
			else {
				SelectiveRenderingManager.corner2 = pos.immutable();
			}

			SelectiveRenderingManager.selectedCorner = index;
			SelectiveRenderingManager.updateRegion();
			SelectiveRenderingManager.commit();
		}
	}

	public static void clearCorners() {
		synchronized (SelectiveRenderingManager.LOCK) {
			SelectiveRenderingManager.corner1 = null;
			SelectiveRenderingManager.corner2 = null;
			SelectiveRenderingManager.selectedCorner = -1;
			SelectiveRenderingManager.updateRegion();
			SelectiveRenderingManager.commit();
		}
	}

	public static boolean isCornerSelected(int index) {
		return SelectiveRenderingManager.selectedCorner == index;
	}

	/** 中键：对着角点选角点，对着选区选选区，对着空气清空选择。 */
	public static void selectAlongRay(Vec3 start, Vec3 end) {
		synchronized (SelectiveRenderingManager.LOCK) {
			int corner = closestCorner(start, end);
			if (corner >= 0) {
				SelectiveRenderingManager.selectedCorner = corner;
				return;
			}

			Hit hit = pickArea(start, end);
			if (hit != null) {
				SelectiveRenderingManager.selectedRegionId = hit.region().id();
				return;
			}

			SelectiveRenderingManager.selectedCorner = -1;
			SelectiveRenderingManager.selectedRegionId = null;
		}
	}

	private static int closestCorner(Vec3 start, Vec3 end) {
		int closest = -1;
		double closestDistance = Double.MAX_VALUE;

		for (int index = 0; index < 2; index++) {
			BlockPos pos = getCorner(index);
			if (pos == null) {
				continue;
			}

			Optional<Vec3> hit = cornerBox(pos).clip(start, end);
			if (hit.isEmpty()) {
				continue;
			}

			double distance = start.distanceToSqr(hit.get());
			if (distance < closestDistance) {
				closestDistance = distance;
				closest = index;
			}
		}

		return closest;
	}

	private static AABB cornerBox(BlockPos pos) {
		return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
	}

	/** 沿指定方向移动当前选中的角点。 */
	public static void moveSelectedCorner(Direction direction, int amount) {
		synchronized (SelectiveRenderingManager.LOCK) {
			int index = SelectiveRenderingManager.selectedCorner;
			if (index < 0) {
				return;
			}

			BlockPos pos = index == 0 ? SelectiveRenderingManager.corner1 : SelectiveRenderingManager.corner2;
			if (pos == null) {
				return;
			}

			BlockPos moved = pos.relative(direction, amount).immutable();
			if (index == 0) {
				SelectiveRenderingManager.corner1 = moved;
			}
			else {
				SelectiveRenderingManager.corner2 = moved;
			}

			SelectiveRenderingManager.updateRegion();
			SelectiveRenderingManager.commit();
		}
	}

	@Nullable
	public static Area selection() {
		BlockPos first = SelectiveRenderingManager.corner1;
		BlockPos second = SelectiveRenderingManager.corner2;

		return first == null || second == null ? null : Area.of(first, second);
	}

	public static void addAreaFromSelection() {
		Area selection = selection();
		if (selection != null) {
			addAreas(List.of(selection));
		}
	}

	/** 有选中的选区就并入它，否则建一个新选区。返回新增的范围数。 */
	public static int addAreas(List<Area> areas) {
		List<Area> added = new ArrayList<>();

		synchronized (SelectiveRenderingManager.LOCK) {
			Region selected = SelectiveRenderingManager.selectedRegion();
			List<Area> existing = selected == null ? List.of() : selected.areas();

			for (Area area : areas) {
				if (!existing.contains(area) && !added.contains(area)) {
					added.add(area);
				}
			}

			if (added.isEmpty()) {
				return 0;
			}

			if (selected == null) {
				SelectiveRenderingManager.REGIONS.add(Region.create(added));
			}
			else {
				List<Area> merged = new ArrayList<>(existing);
				merged.addAll(added);
				SelectiveRenderingManager.REGIONS.set(SelectiveRenderingManager.REGIONS.indexOf(selected), selected.withAreas(merged));
			}

			SelectiveRenderingManager.commitAndRemember();
		}

		Log.debug("[state] {} area(s) added", added.size());
		changed(added, List.of(), false);
		return added.size();
	}

	/** 用 updated 替换 old（按 id 定位）；范围、方块开关或本地方块名单变了才会重建并重算光照。 */
	public static boolean updateRegion(Region old, Region updated) {
		synchronized (SelectiveRenderingManager.LOCK) {
			int index = SelectiveRenderingManager.REGIONS.indexOf(old);
			if (index < 0) {
				return false;
			}

			SelectiveRenderingManager.REGIONS.set(index, updated);
			SelectiveRenderingManager.commitAndRemember();
		}

		boolean blocksChanged = !old.areas().equals(updated.areas())
			|| old.options().blocks() != updated.options().blocks()
			|| !old.options().ruleSources().equals(updated.options().ruleSources())
			|| alphaCategory(old.options().alphaOverride()) != alphaCategory(updated.options().alphaOverride());

		if (blocksChanged) {
			List<Area> touched = new ArrayList<>(old.areas());
			touched.addAll(updated.areas());
			changed(touched, old.areas(), false);
		}

		return true;
	}

	private static int alphaCategory(int override) {
		int alpha = override < 0 ? SelectiveRenderingManager.config().alpha() : override;

		if (alpha >= 255) {
			return 2;
		}

		if (alpha <= 0 && !SelectiveRenderingManager.isBuildHiddenMesh()) {
			return 0;
		}

		return 1;
	}

	/** 把选区的投影位移改变 (dx, dy, dz)，返回改变后的位移（x, y, z）；选区已不存在时返回 null。 */
	@Nullable
	public static int[] moveProjection(Region region, int dx, int dy, int dz) {
		Region current = latest(region);
		if (current == null) {
			return null;
		}

		RegionOptions options = current.options();
		int x = options.offsetX() + dx;
		int y = options.offsetY() + dy;
		int z = options.offsetZ() + dz;

		updateRegion(current, current.withOptions(options.withOffset(x, y, z)));

		// 第一次出现位移时，区块编译才开始为它收集四边形，范围内的区段要重建一次。
		if (!options.hasOffset()) {
			changed(current.areas(), List.of(), false);
		}

		return new int[]{x, y, z};
	}

	public static void resetProjection(Region region) {
		Region current = latest(region);
		if (current != null && current.options().hasOffset()) {
			updateRegion(current, current.withOptions(current.options().withOffset(0, 0, 0)));
		}
	}

	@Nullable
	private static Region latest(Region region) {
		synchronized (SelectiveRenderingManager.LOCK) {
			for (Region candidate : SelectiveRenderingManager.REGIONS) {
				if (candidate.equals(region)) {
					return candidate;
				}
			}
		}

		return null;
	}

	/** 增删选区本地方块名单里的一项，返回是否是加入。 */
	public static boolean toggleLocalRule(Region region, String source) {
		return toggleLocal(region, source, RegionOptions::ruleSources, RegionOptions::withRuleSources);
	}

	/** 增删选区本地实体名单里的一项，返回是否是加入。 */
	public static boolean toggleLocalEntityType(Region region, String source) {
		return toggleLocal(region, source, RegionOptions::entityTypes, RegionOptions::withEntityTypes);
	}

	private static boolean toggleLocal(
		Region region,
		String source,
		java.util.function.Function<RegionOptions, List<String>> read,
		java.util.function.BiFunction<RegionOptions, List<String>, RegionOptions> write
	) {
		Region current = null;
		List<String> entries;
		boolean removed;

		synchronized (SelectiveRenderingManager.LOCK) {
			for (Region candidate : SelectiveRenderingManager.REGIONS) {
				if (candidate.equals(region)) {
					current = candidate;
					break;
				}
			}

			if (current == null) {
				return false;
			}

			entries = new ArrayList<>(read.apply(current.options()));
			removed = entries.removeIf(entry -> entry.equalsIgnoreCase(source));

			if (!removed) {
				entries.add(source);
			}
		}

		updateRegion(current, current.withOptions(write.apply(current.options(), entries)));
		return !removed;
	}

	/** 把一个范围从选区里独立出去，成为新的选区，保留原选区的设置。只剩一个范围时不能独立。 */
	public static boolean detachArea(Region region, Area area) {
		synchronized (SelectiveRenderingManager.LOCK) {
			int index = SelectiveRenderingManager.REGIONS.indexOf(region);
			if (index < 0 || region.areas().size() < 2 || !region.areas().contains(area)) {
				return false;
			}

			List<Area> rest = new ArrayList<>(region.areas());
			rest.remove(area);

			Region detached = new Region(java.util.UUID.randomUUID(), Region.randomColor(), List.of(area), region.options());

			SelectiveRenderingManager.REGIONS.set(index, region.withAreas(rest));
			SelectiveRenderingManager.REGIONS.add(index + 1, detached);
			SelectiveRenderingManager.commitAndRemember();
		}

		return true;
	}

	/**
	 * 把 source 的所有范围并入 target，设置以 target 为准，名单取并集；source 随即消失。
	 */
	public static void mergeRegion(Region source, Region target) {
		if (source.equals(target)) {
			return;
		}

		synchronized (SelectiveRenderingManager.LOCK) {
			int sourceIndex = SelectiveRenderingManager.REGIONS.indexOf(source);
			int targetIndex = SelectiveRenderingManager.REGIONS.indexOf(target);
			if (sourceIndex < 0 || targetIndex < 0) {
				return;
			}

			SelectiveRenderingManager.REGIONS.set(targetIndex, Region.merge(target, source));
			SelectiveRenderingManager.REGIONS.remove(source);

			if (source.id().equals(SelectiveRenderingManager.selectedRegionId)) {
				SelectiveRenderingManager.selectedRegionId = target.id();
			}

			SelectiveRenderingManager.commitAndRemember();
		}

		changed(source.areas(), source.areas(), false);
	}

	/**
	 * 删除指定选区并保存、重建及还原光照。
	 */
	public static void removeRegions(List<Region> targets) {
		List<Area> dropped = new ArrayList<>();

		synchronized (SelectiveRenderingManager.LOCK) {
			boolean any = false;

			for (Region region : targets) {
				if (SelectiveRenderingManager.REGIONS.remove(region)) {
					dropped.addAll(region.areas());
					any = true;
				}
			}

			if (!any) {
				return;
			}

			dropSelectionIfGone();
			SelectiveRenderingManager.commitAndRemember();
		}

		Log.debug("[state] {} area(s) removed with their regions", dropped.size());
		changed(dropped, dropped, false);
	}

	/** 返回射线命中的最近范围，未命中时返回 null。 */
	@Nullable
	public static Hit pickArea(Vec3 start, Vec3 end) {
		synchronized (SelectiveRenderingManager.LOCK) {
			Hit closest = null;
			double closestDistance = Double.MAX_VALUE;

			for (Region region : SelectiveRenderingManager.REGIONS) {
				for (Area area : region.areas()) {
					Optional<Vec3> hit = area.box().clip(start, end);
					if (hit.isEmpty()) {
						continue;
					}

					double distance = start.distanceToSqr(hit.get());
					if (distance < closestDistance) {
						closestDistance = distance;
						closest = new Hit(region, area);
					}
				}
			}

			return closest;
		}
	}

	/**
	 * 删除射线命中的最近范围（选区的最后一个范围被删掉时选区也消失），未命中则添加当前选区。
	 */
	public static void toggleAreaAlongRay(Vec3 start, Vec3 end) {
		Hit hit = pickArea(start, end);
		if (hit != null) {
			removeArea(hit.region(), hit.area());
		} else {
			addAreaFromSelection();
		}
	}

	public static void removeArea(Region region, Area area) {
		synchronized (SelectiveRenderingManager.LOCK) {
			int index = SelectiveRenderingManager.REGIONS.indexOf(region);
			if (index < 0 || !region.areas().contains(area)) {
				return;
			}

			List<Area> rest = new ArrayList<>(region.areas());
			rest.remove(area);

			if (rest.isEmpty()) {
				SelectiveRenderingManager.REGIONS.remove(index);
				dropSelectionIfGone();
			}
			else {
				SelectiveRenderingManager.REGIONS.set(index, region.withAreas(rest));
			}

			SelectiveRenderingManager.commitAndRemember();
		}

		changed(List.of(area), List.of(area), false);
	}

	/**
	 * 用当前双角点选区裁剪所有选区，裁出的新部分仍留在原来的选区里，保留当前角点。
	 */
	public static void clipRegions() {
		Area cut = selection();
		if (cut != null) {
			cut(List.of(cut), true);
		}
	}

	/** 记录器的裁剪模式：用记录到的范围裁剪所有选区。 */
	public static boolean subtractAreas(List<Area> cuts) {
		return !cuts.isEmpty() && cut(cuts, false);
	}

	/** local 为真时只重建被裁剪的范围，否则整体重建。 */
	private static boolean cut(List<Area> cuts, boolean local) {
		List<Area> before = new ArrayList<>();

		synchronized (SelectiveRenderingManager.LOCK) {
			if (SelectiveRenderingManager.REGIONS.isEmpty()) {
				return false;
			}

			List<Region> next = new ArrayList<>();
			int fragments = 0;

			for (Region region : SelectiveRenderingManager.REGIONS) {
				before.addAll(region.areas());

				List<Area> current = region.areas();

				for (Area cut : cuts) {
					List<Area> after = new ArrayList<>(current.size() + 4);

					for (Area area : current) {
						after.addAll(area.subtract(cut));
					}

					current = after;
				}

				fragments += current.size();

				if (!current.isEmpty()) {
					next.add(region.withAreas(current));
				}
			}

			if (fragments > MAX_SUBTRACT_FRAGMENTS) {
				Log.warn("[state] 裁剪产生的碎片过多（>{}），已放弃：选区形状过于破碎", MAX_SUBTRACT_FRAGMENTS);
				return false;
			}

			SelectiveRenderingManager.REGIONS.clear();
			SelectiveRenderingManager.REGIONS.addAll(next);
			dropSelectionIfGone();
			SelectiveRenderingManager.commitAndRemember();
		}

		Log.debug("[state] regions clipped by {} area(s): {} area(s) before", cuts.size(), before.size());
		changed(before, before, !local);
		return true;
	}

	/** 撤销 / 重做后整体刷新 */
	static void refreshAfterRestore(List<Region> before, List<Region> after) {
		List<Area> areas = new ArrayList<>();
		before.forEach(region -> areas.addAll(region.areas()));
		after.forEach(region -> areas.addAll(region.areas()));

		changed(areas, areas, true);
	}

	private static void dropSelectionIfGone() {
		java.util.UUID id = SelectiveRenderingManager.selectedRegionId;

		if (id != null && SelectiveRenderingManager.REGIONS.stream().noneMatch(region -> region.id().equals(id))) {
			SelectiveRenderingManager.selectedRegionId = null;
		}
	}

	/** 受影响的范围重建，被移走的范围还原光照；fullRebuild 时整体重建。 */
	static void changed(List<Area> touched, List<Area> retired, boolean fullRebuild) {
		if (!SelectiveRenderingManager.getMode().usesRegion()) {
			return;
		}

		if (fullRebuild) {
			if (!retired.isEmpty()) {
				LightSuppression.retire(retired);
			}

			RebuildScheduler.rebuildChunks();
			return;
		}

		for (Area area : touched) {
			HiddenSections.mark(area.box());
		}

		if (!retired.isEmpty()) {
			LightSuppression.retire(retired);
		}

		LightSuppression.relight();
	}
}
