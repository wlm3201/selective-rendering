package com.selectiverendering.interaction;

import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.matchingrule.BlockListMessage;
import com.selectiverendering.region.Region;
import com.selectiverendering.region.RegionEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** 拿着魔杖并选中选区时，用 Ctrl + 方向键和 Ctrl + 滚轮移动选区的投影。 */
public final class ProjectionControls {
	public enum Move {
		LEFT, RIGHT, FORWARD, BACK
	}

	private ProjectionControls() {
	}

	/** 方向键按下时相对视线所朝的水平方向移动一格。 */
	public static boolean move(Move move) {
		Region region = SelectiveRenderingManager.selectedRegion();
		if (region == null || !SelectiveRendering.isWandHeld()) {
			return false;
		}

		Entity camera = Minecraft.getInstance().getCameraEntity();
		if (camera == null) {
			return false;
		}

		Direction facing = camera.getDirection();
		Direction direction = switch (move) {
			case FORWARD -> facing;
			case BACK -> facing.getOpposite();
			case LEFT -> facing.getCounterClockWise();
			case RIGHT -> facing.getClockWise();
		};

		shift(region, direction, 1);
		return true;
	}

	/** 滚轮滚动时大翻滚！ */
	public static void scroll(Region region, Direction looking, int amount) {
		shift(region, looking, amount);
	}

	private static void shift(Region region, Direction direction, int amount) {
		int[] offset = RegionEditor.moveProjection(
			region,
			direction.getStepX() * amount,
			direction.getStepY() * amount,
			direction.getStepZ() * amount
		);

		if (offset != null) {
			BlockListMessage.note(Component.translatable(
				SelectiveRendering.MOD_ID + ".message.projection",
				String.valueOf(offset[0]), String.valueOf(offset[1]), String.valueOf(offset[2])
			).getString());
		}
	}
}
