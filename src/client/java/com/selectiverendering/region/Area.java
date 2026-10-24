package com.selectiverendering.region;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 轴对齐的整数方块长方体，包含 min 和 max 两端 */
public record Area(BlockPos min, BlockPos max) {
	public static Area of(BlockPos first, BlockPos second) {
		return new Area(
			new BlockPos(
				Math.min(first.getX(), second.getX()),
				Math.min(first.getY(), second.getY()),
				Math.min(first.getZ(), second.getZ())),
			new BlockPos(
				Math.max(first.getX(), second.getX()),
				Math.max(first.getY(), second.getY()),
				Math.max(first.getZ(), second.getZ())));
	}

	public AABB box() {
		return new AABB(
			min.getX(), min.getY(), min.getZ(),
			max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
	}

	/** 减去，返回剩余的至多六个长方体。 */
	public List<Area> subtract(Area cut) {
		Area overlap = overlap(cut);
		if (overlap == null) {
			return List.of(this);
		}

		List<Area> parts = new ArrayList<>();
		BlockPos low = overlap.min();
		BlockPos high = overlap.max();

		if (min.getX() < low.getX()) {
			parts.add(new Area(min, new BlockPos(low.getX() - 1, max.getY(), max.getZ())));
		}

		if (max.getX() > high.getX()) {
			parts.add(new Area(new BlockPos(high.getX() + 1, min.getY(), min.getZ()), max));
		}

		int x1 = Math.max(min.getX(), low.getX());
		int x2 = Math.min(max.getX(), high.getX());

		if (min.getY() < low.getY()) {
			parts.add(new Area(new BlockPos(x1, min.getY(), min.getZ()), new BlockPos(x2, low.getY() - 1, max.getZ())));
		}

		if (max.getY() > high.getY()) {
			parts.add(new Area(new BlockPos(x1, high.getY() + 1, min.getZ()), new BlockPos(x2, max.getY(), max.getZ())));
		}

		int y1 = Math.max(min.getY(), low.getY());
		int y2 = Math.min(max.getY(), high.getY());

		if (min.getZ() < low.getZ()) {
			parts.add(new Area(new BlockPos(x1, y1, min.getZ()), new BlockPos(x2, y2, low.getZ() - 1)));
		}

		if (max.getZ() > high.getZ()) {
			parts.add(new Area(new BlockPos(x1, y1, high.getZ() + 1), new BlockPos(x2, y2, max.getZ())));
		}

		return parts;
	}

	public boolean overlaps(Area other) {
		return min.getX() <= other.max.getX() && max.getX() >= other.min.getX()
			&& min.getY() <= other.max.getY() && max.getY() >= other.min.getY()
			&& min.getZ() <= other.max.getZ() && max.getZ() >= other.min.getZ();
	}

	@Nullable
	private Area overlap(Area other) {
		if (!overlaps(other)) {
			return null;
		}

		return new Area(
			new BlockPos(
				Math.max(min.getX(), other.min.getX()),
				Math.max(min.getY(), other.min.getY()),
				Math.max(min.getZ(), other.min.getZ())),
			new BlockPos(
				Math.min(max.getX(), other.max.getX()),
				Math.min(max.getY(), other.max.getY()),
				Math.min(max.getZ(), other.max.getZ())));
	}

	public boolean contains(BlockPos pos) {
		return pos.getX() >= min.getX() && pos.getX() <= max.getX()
			&& pos.getY() >= min.getY() && pos.getY() <= max.getY()
			&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
	}

	/** 连续坐标是否在这个长方体内（含 min 一侧，不含 max + 1 一侧）。 */
	public boolean contains(double x, double y, double z) {
		return x >= min.getX() && x < max.getX() + 1.0
			&& y >= min.getY() && y < max.getY() + 1.0
			&& z >= min.getZ() && z < max.getZ() + 1.0;
	}

	/** 是否与 [sx, sx+15] × [sy, sy+15] × [sz, sz+15] 这个 16 格方块相交。 */
	public boolean intersectsSection(int sx, int sy, int sz) {
		return max.getX() >= sx && min.getX() <= sx + 15
			&& max.getY() >= sy && min.getY() <= sy + 15
			&& max.getZ() >= sz && min.getZ() <= sz + 15;
	}

	/** 序列化为 minX,minY,minZ:maxX,maxY,maxZ。 */
	public String toSource() {
		return source(min) + ":" + source(max);
	}

	@Nullable
	public static Area parse(String input) {
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
				Integer.parseInt(parts[2].trim()));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String source(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}
}
