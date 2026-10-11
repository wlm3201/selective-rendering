package com.selectiverendering.render.hiding;

import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.config.region.RenderConfig;
import com.selectiverendering.region.Area;
import com.selectiverendering.region.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前有投影位移的范围。区块编译、实体、粒子和方块实体的投影都靠它判断「这个东西在不在某个投影的源范围里」；
 * 随配置快照缓存，选区或位移一变就重新计算。
 */
public final class ProjectionAreas {
	/** key 在选区内唯一标识这个范围；offset 是投影位移。 */
	public record Active(String key, Area area, int offsetX, int offsetY, int offsetZ) {
		public boolean contains(BlockPos pos) {
			return pos.getX() >= area.min().getX() && pos.getX() <= area.max().getX()
				&& pos.getY() >= area.min().getY() && pos.getY() <= area.max().getY()
				&& pos.getZ() >= area.min().getZ() && pos.getZ() <= area.max().getZ();
		}

		public boolean contains(double x, double y, double z) {
			AABB box = area.box();
			return x >= box.minX && x < box.maxX && y >= box.minY && y < box.maxY && z >= box.minZ && z < box.maxZ;
		}
	}

	private record State(RenderConfig config, List<Active> list) {
	}

	private static volatile State state = new State(null, List.of());

	private ProjectionAreas() {
	}

	public static List<Active> current() {
		RenderConfig config = SelectiveRenderingManager.config();
		State known = state;

		if (known.config() == config) {
			return known.list();
		}

		List<Active> list = new ArrayList<>();

		if (config.mode() != SelectiveRenderingManager.Mode.OFF) {
			for (Region region : config.regions()) {
				if (!region.options().hasOffset()) {
					continue;
				}

				int index = 0;
				for (Area area : region.areas()) {
					list.add(new Active(region.id() + "#" + index++, area, region.options().offsetX(), region.options().offsetY(), region.options().offsetZ()));
				}
			}
		}

		List<Active> result = List.copyOf(list);
		state = new State(config, result);
		return result;
	}

	public static boolean isEmpty() {
		return current().isEmpty();
	}
}
