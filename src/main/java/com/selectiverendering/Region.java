package com.selectiverendering;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * One added region, kept as the two corners normalised so the low one always comes first.
 *
 * <p>Immutable and made of immutable parts, which is what lets the section build threads read a
 * region while the wand is still adding and removing them.</p>
 *
 * @param min the low corner, every coordinate less than or equal to {@link #max}.
 * @param max the high corner, which is inside the region rather than just outside it.
 */
public record Region(BlockPos min, BlockPos max) {
	/**
	 * Builds a region from two corners clicked in any order.
	 */
	public static Region of(BlockPos first, BlockPos second) {
		return new Region(
			new BlockPos(
				Math.min(first.getX(), second.getX()),
				Math.min(first.getY(), second.getY()),
				Math.min(first.getZ(), second.getZ())
			),
			new BlockPos(
				Math.max(first.getX(), second.getX()),
				Math.max(first.getY(), second.getY()),
				Math.max(first.getZ(), second.getZ())
			)
		);
	}

	/**
	 * The region as a box covering whole blocks, so the far corner block is inside it rather than
	 * only touching it. Used for the outline, the same way the single selection box is.
	 */
	public AABB box() {
		return new AABB(
			min.getX(), min.getY(), min.getZ(),
			max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0
		);
	}

	public boolean contains(BlockPos pos) {
		return pos.getX() >= min.getX() && pos.getX() <= max.getX()
			&& pos.getY() >= min.getY() && pos.getY() <= max.getY()
			&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
	}

	/**
	 * The region as the text a config screen edits it as: the low corner then the high one, three
	 * numbers each, {@code x1,y1,z1:x2,y2,z2}. The same shape Lucidity writes its areas in, so a
	 * list of regions can be copied between the two.
	 */
	public String toSource() {
		return source(min) + ":" + source(max);
	}

	/**
	 * Reads a region back from that text, or returns null when it does not describe one. The two
	 * corners can come in any order, they are sorted the same way here as they are when they are
	 * clicked.
	 */
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
				Integer.parseInt(parts[2].trim())
			);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private static String source(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}
}
