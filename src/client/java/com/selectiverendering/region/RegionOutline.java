package com.selectiverendering.region;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 把一个选区的所有范围融合成一个整体线框：只保留融合后形状的真实棱，范围之间重叠、相邻的内部线和平整合并面上的线都不画。 */
public final class RegionOutline {
	/** 网格格子数超过这个值就放弃融合，逐个范围画线框。 */
	private static final long MAX_CELLS = 400_000L;

	private RegionOutline() {
	}

	public record Segment(double x1, double y1, double z1, double x2, double y2, double z2) {
	}

	public static List<Segment> of(List<Area> areas) {
		List<Segment> segments = new ArrayList<>();

		if (areas.size() > 1) {
			int[] xs = bounds(areas, 0);
			int[] ys = bounds(areas, 1);
			int[] zs = bounds(areas, 2);
			long cells = (long) (xs.length - 1) * (ys.length - 1) * (zs.length - 1);

			if (cells <= MAX_CELLS) {
				fuse(areas, xs, ys, zs, segments);
				return List.copyOf(segments);
			}
		}

		for (Area area : areas) {
			box(area, segments);
		}

		return List.copyOf(segments);
	}

	private static void fuse(List<Area> areas, int[] xs, int[] ys, int[] zs, List<Segment> segments) {
		int nx = xs.length - 1;
		int ny = ys.length - 1;
		int nz = zs.length - 1;
		boolean[] occupied = new boolean[nx * ny * nz];

		for (Area area : areas) {
			int x0 = Arrays.binarySearch(xs, area.min().getX());
			int x1 = Arrays.binarySearch(xs, area.max().getX() + 1);
			int y0 = Arrays.binarySearch(ys, area.min().getY());
			int y1 = Arrays.binarySearch(ys, area.max().getY() + 1);
			int z0 = Arrays.binarySearch(zs, area.min().getZ());
			int z1 = Arrays.binarySearch(zs, area.max().getZ() + 1);

			for (int x = x0; x < x1; x++) {
				for (int y = y0; y < y1; y++) {
					for (int z = z0; z < z1; z++) {
						occupied[(x * ny + y) * nz + z] = true;
					}
				}
			}
		}

		Grid grid = new Grid(occupied, nx, ny, nz);

		// 沿 X 的棱：固定网格点 (y, z)，沿 x 方向把连续的棱合成一条
		for (int y = 0; y <= ny; y++) {
			for (int z = 0; z <= nz; z++) {
				int start = -1;

				for (int x = 0; x <= nx; x++) {
					boolean edge = x < nx && grid.edge(x, y, z, 0);

					if (edge && start < 0) {
						start = x;
					} else if (!edge && start >= 0) {
						segments.add(new Segment(xs[start], ys[y], zs[z], xs[x], ys[y], zs[z]));
						start = -1;
					}
				}
			}
		}

		for (int x = 0; x <= nx; x++) {
			for (int z = 0; z <= nz; z++) {
				int start = -1;

				for (int y = 0; y <= ny; y++) {
					boolean edge = y < ny && grid.edge(x, y, z, 1);

					if (edge && start < 0) {
						start = y;
					} else if (!edge && start >= 0) {
						segments.add(new Segment(xs[x], ys[start], zs[z], xs[x], ys[y], zs[z]));
						start = -1;
					}
				}
			}
		}

		for (int x = 0; x <= nx; x++) {
			for (int y = 0; y <= ny; y++) {
				int start = -1;

				for (int z = 0; z <= nz; z++) {
					boolean edge = z < nz && grid.edge(x, y, z, 2);

					if (edge && start < 0) {
						start = z;
					} else if (!edge && start >= 0) {
						segments.add(new Segment(xs[x], ys[y], zs[start], xs[x], ys[y], zs[z]));
						start = -1;
					}
				}
			}
		}
	}

	private static void box(Area area, List<Segment> segments) {
		BlockPos min = area.min();
		BlockPos max = area.max();
		double x0 = min.getX();
		double y0 = min.getY();
		double z0 = min.getZ();
		double x1 = max.getX() + 1.0;
		double y1 = max.getY() + 1.0;
		double z1 = max.getZ() + 1.0;

		for (double y : new double[] {y0, y1}) {
			for (double z : new double[] {z0, z1}) {
				segments.add(new Segment(x0, y, z, x1, y, z));
			}
		}

		for (double x : new double[] {x0, x1}) {
			for (double z : new double[] {z0, z1}) {
				segments.add(new Segment(x, y0, z, x, y1, z));
			}
		}

		for (double x : new double[] {x0, x1}) {
			for (double y : new double[] {y0, y1}) {
				segments.add(new Segment(x, y, z0, x, y, z1));
			}
		}
	}

	/** 某个轴上所有范围的边界坐标，升序去重。 */
	private static int[] bounds(List<Area> areas, int axis) {
		return areas.stream()
			.flatMapToInt(area -> axis == 0
				? java.util.stream.IntStream.of(area.min().getX(), area.max().getX() + 1)
				: axis == 1
					? java.util.stream.IntStream.of(area.min().getY(), area.max().getY() + 1)
					: java.util.stream.IntStream.of(area.min().getZ(), area.max().getZ() + 1))
			.distinct()
			.sorted()
			.toArray();
	}

	private record Grid(boolean[] occupied, int nx, int ny, int nz) {
		private boolean at(int x, int y, int z) {
			return x >= 0 && y >= 0 && z >= 0 && x < nx && y < ny && z < nz && occupied[(x * ny + y) * nz + z];
		}

		/** 沿 axis 方向、起点在网格点 (x, y, z) 的这条棱是不是融合形状的真实棱。 */
		boolean edge(int x, int y, int z, int axis) {
			boolean a;
			boolean b;
			boolean c;
			boolean d;

			// a、b、c、d 是垂直于棱的平面里围着它的四个格子：a=(−,−) b=(−,+) c=(+,−) d=(+,+)
			if (axis == 0) {
				a = at(x, y - 1, z - 1);
				b = at(x, y - 1, z);
				c = at(x, y, z - 1);
				d = at(x, y, z);
			} else if (axis == 1) {
				a = at(x - 1, y, z - 1);
				b = at(x - 1, y, z);
				c = at(x, y, z - 1);
				d = at(x, y, z);
			} else {
				a = at(x - 1, y - 1, z);
				b = at(x - 1, y, z);
				c = at(x, y - 1, z);
				d = at(x, y, z);
			}

			int count = (a ? 1 : 0) + (b ? 1 : 0) + (c ? 1 : 0) + (d ? 1 : 0);

			return count == 1 || count == 3 || (count == 2 && ((a && d) || (b && c)));
		}
	}
}
