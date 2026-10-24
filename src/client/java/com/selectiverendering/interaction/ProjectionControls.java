package com.selectiverendering.interaction;

import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.matchingrule.BlockListMessage;
import com.selectiverendering.region.Region;
import com.selectiverendering.region.RegionEditor;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/** 拿着魔杖时，用 Alt + 滚轮移动当前选中的目标：选区挪投影，角点框整体挪，单个角点挪自己。 */
public final class ProjectionControls {
	private ProjectionControls() {
	}

	/** 滚轮滚动时大翻滚！没有可移动的目标时返回 false，让滚轮照常切物品。 */
	public static boolean scroll(Direction looking, int amount) {
		Region selected = SelectiveRenderingManager.selectedRegion();
		if (selected != null) {
			shift(selected, looking, amount);
			return true;
		}

		if (RegionEditor.isSelectionSelected()) {
			return RegionEditor.moveSelection(looking, amount);
		}

		return RegionEditor.moveSelectedCorner(looking, amount);
	}

	private static void shift(Region region, Direction direction, int amount) {
		int[] offset = RegionEditor.moveProjection(
			region,
			direction.getStepX() * amount,
			direction.getStepY() * amount,
			direction.getStepZ() * amount);

		if (offset != null) {
			BlockListMessage.note(Component.translatable(
				SelectiveRendering.MOD_ID + ".message.projection",
				String.valueOf(offset[0]), String.valueOf(offset[1]), String.valueOf(offset[2])).getString());
		}
	}
}
