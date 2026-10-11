package com.selectiverendering.config.region;

import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.region.Area;
import com.selectiverendering.region.Region;
import com.selectiverendering.region.RegionEditor;
import com.selectiverendering.region.RegionOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** 设置界面里的一个选区：每次都按 id 取最新版本，合并、独立范围之后界面仍指向同一个选区。 */
final class RegionHandle {
	private final UUID id;
	private Region last;

	RegionHandle(Region region) {
		this.id = region.id();
		this.last = region;
	}

	Region region() {
		for (Region region : SelectiveRenderingManager.regions()) {
			if (region.id().equals(this.id)) {
				this.last = region;
				break;
			}
		}

		return this.last;
	}

	/** 选区是否还在；被删除或合并到别的选区后为 false。 */
	boolean exists() {
		for (Region region : SelectiveRenderingManager.regions()) {
			if (region.id().equals(this.id)) {
				return true;
			}
		}

		return false;
	}

	boolean replace(Region next) {
		if (RegionEditor.updateRegion(this.region(), next)) {
			this.last = next;
			return true;
		}

		return false;
	}

	void update(UnaryOperator<RegionOptions> change) {
		Region current = this.region();
		this.replace(current.withOptions(change.apply(current.options())));
	}

	/** 把一个范围换成另一个；换出的范围和别的范围重复时拒绝。 */
	boolean replaceArea(Area old, Area next) {
		Region current = this.region();
		List<Area> areas = new ArrayList<>(current.areas());
		int index = areas.indexOf(old);

		if (index < 0 || areas.contains(next) && !old.equals(next)) {
			return false;
		}

		areas.set(index, next);
		return this.replace(current.withAreas(areas));
	}
}
