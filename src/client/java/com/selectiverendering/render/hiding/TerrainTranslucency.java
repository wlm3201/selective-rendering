package com.selectiverendering.render.hiding;

import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class TerrainTranslucency {
	// alpha 是截流时的透明度，global 表示它等于当时的全局透明度（之后跟随配置变化，不用重建）。
	/** raw 为真：颜色里还没有烘焙阴影和 AO（投影的四边形），重算光照时按投影自己的光照场再加。 */
	public record Quad(float[] positions, float[] uvs, int[] colors, int[] lights, float normalX, float normalY, float normalZ, int alpha, boolean global, boolean translucent, java.util.UUID region, boolean raw) {
			public Quad withRegion(java.util.UUID region) {
			return new Quad(positions, uvs, colors, lights, normalX, normalY, normalZ, alpha, false, translucent, region, raw);
		}
	}

	/** 投影用的四边形按（范围, 区段）存放：坐标仍然是区段内坐标，投影后的位置就是区段原点加位移。 */
	public record ProjectionKey(String area, long section) {
	}

	private static final Map<ProjectionKey, List<Quad>> PROJECTED = new ConcurrentHashMap<>();
	private static final Map<ProjectionKey, Set<TextureAtlasSprite>> PROJECTED_SPRITES = new ConcurrentHashMap<>();

	private static final Map<Long, List<Quad>> SECTIONS = new ConcurrentHashMap<>();
	// 区段里用到的SPRITES，Sodium只让被渲染区段里用到的动画贴图播放，自己维护时要自己标记
	private static final Map<Long, Set<TextureAtlasSprite>> SPRITES = new ConcurrentHashMap<>();
	private static final ThreadLocal<Builder> BUILDER = new ThreadLocal<>();

	private static final class Builder {
		final long section;
		final boolean capturing;
		final List<Quad> quads = new ArrayList<>();
		final Set<TextureAtlasSprite> sprites = new HashSet<>();
		final List<ProjectionAreas.Active> areas = ProjectionAreas.current();
		final Map<String, List<Quad>> projections = new java.util.HashMap<>();

		Builder(long section, boolean capturing) {
			this.section = section;
			this.capturing = capturing;
		}
	}

	public static void begin(long section) {
		BUILDER.set(new Builder(section, true));
	}

	public static void finish(boolean completed) {
		Builder builder = BUILDER.get();
		BUILDER.remove();

		if (builder == null || !completed) {
			return;
		}

		for (ProjectionAreas.Active area : builder.areas) {
			ProjectionKey key = new ProjectionKey(area.key(), builder.section);
			List<Quad> projected = builder.projections.get(area.key());

			if (projected == null || projected.isEmpty()) {
				PROJECTED.remove(key);
				PROJECTED_SPRITES.remove(key);
			}
			else {
				PROJECTED_SPRITES.put(key, builder.sprites);
				PROJECTED.put(key, projected);
			}

			// 范围里有区段重新编译过，方块可能变了，投影的光照要重算。
			ProjectionLight.invalidate(area.key());
		}

		if (builder.quads.isEmpty()) {
			SECTIONS.remove(builder.section);
			SPRITES.remove(builder.section);
		}
		else {
			SPRITES.put(builder.section, builder.sprites);
			RetainedTerrain.prebuild(builder.section, builder.quads);
			SECTIONS.put(builder.section, builder.quads);
		}
	}

	public static boolean capturing() {
		Builder builder = BUILDER.get();
		return builder != null && builder.capturing;
	}

	/** 当前编译任务里，这个方块是否落在某个投影的源范围里。 */
	public static boolean projecting(BlockPos pos) {
		Builder builder = BUILDER.get();
		if (builder == null) {
			return false;
		}

		for (ProjectionAreas.Active area : builder.areas) {
			if (area.contains(pos)) {
				return true;
			}
		}

		return false;
	}

	public static net.minecraft.core.Direction directionOf(float x, float y, float z) {
		float ax = Math.abs(x);
		float ay = Math.abs(y);
		float az = Math.abs(z);

		if (ay >= ax && ay >= az) {
			return y >= 0 ? net.minecraft.core.Direction.UP : net.minecraft.core.Direction.DOWN;
		}

		if (ax >= az) {
			return x >= 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST;
		}

		return z >= 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH;
	}

	public static float directionalShade(net.minecraft.core.Direction direction) {
		if (direction == null) {
			return 1.0F;
		}

		return switch (direction) {
			case DOWN -> 0.5F;
			case UP -> 1.0F;
			case NORTH, SOUTH -> 0.8F;
			case WEST, EAST -> 0.6F;
		};
	}

	/** 方块在某个投影的源范围里，而邻居不在：这个面朝着范围外，投影时需要它，不能因为外面有方块就被剔除。 */
	public static boolean projectionBoundary(BlockPos pos, BlockPos neighbour) {
		Builder builder = BUILDER.get();
		if (builder == null) {
			return false;
		}

		for (ProjectionAreas.Active area : builder.areas) {
			if (area.contains(pos) && !area.contains(neighbour)) {
				return true;
			}
		}

		return false;
	}

	/** 把方块的一个四边形（区段内坐标）复制给包含它的每个投影范围。 */
	public static void addProjected(BlockPos pos, Quad quad) {
		Builder builder = BUILDER.get();
		if (builder == null) {
			return;
		}

		for (ProjectionAreas.Active area : builder.areas) {
			if (area.contains(pos)) {
				builder.projections.computeIfAbsent(area.key(), ignored -> new ArrayList<>()).add(quad);
			}
		}
	}

	static Map<ProjectionKey, List<Quad>> projected() {
		return PROJECTED;
	}

	static Set<TextureAtlasSprite> projectedSprites(ProjectionKey key) {
		Set<TextureAtlasSprite> sprites = PROJECTED_SPRITES.get(key);
		return sprites == null ? Set.of() : sprites;
	}

	static void removeProjected(ProjectionKey key) {
		PROJECTED.remove(key);
		PROJECTED_SPRITES.remove(key);
	}

	public static void addSprite(TextureAtlasSprite sprite) {
		Builder builder = BUILDER.get();
		if (builder != null && sprite != null) {
			builder.sprites.add(sprite);
		}
	}

	static Set<TextureAtlasSprite> sprites(long section) {
		Set<TextureAtlasSprite> sprites = SPRITES.get(section);
		return sprites == null ? Set.of() : sprites;
	}

	/** 半透明截流：如果这个方块的透明度是某个选区的覆盖决定的，记下是哪个选区。 */
	public static void addCaptured(BlockPos pos, Quad quad) {
		java.util.UUID region = com.selectiverendering.region.RegionTargets.blockOverrideRegion(pos);
		add(region == null ? quad : quad.withRegion(region));
	}

	public static void add(Quad quad) {
		Builder builder = BUILDER.get();
		if (builder != null) {
			builder.quads.add(quad);
		}
	}

	static Map<Long, List<Quad>> sections() {
		return SECTIONS;
	}

	static void remove(long section) {
		SECTIONS.remove(section);
		SPRITES.remove(section);
	}

	public static void clear() {
		SECTIONS.clear();
		SPRITES.clear();
		PROJECTED.clear();
		PROJECTED_SPRITES.clear();
	}

}
