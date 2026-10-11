package com.selectiverendering.region;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 一个选区：一个或多个范围（{@link Area}）加上颜色和设置。身份由 id 决定，改范围或设置得到的是同一个选区的新版本；
 * 同一个选区的所有范围共用一份设置。
 */
public final class Region {
	private final UUID id;
	private final int color;
	private final List<Area> areas;
	private final RegionOptions options;
	private final Area bounds;

	private volatile List<RegionOutline.Segment> outline;

	public Region(UUID id, int color, List<Area> areas, RegionOptions options) {
		if (areas.isEmpty()) {
			throw new IllegalArgumentException("A region needs at least one area");
		}

		this.id = id;
		this.color = color & 0xFFFFFF;
		this.areas = List.copyOf(areas);
		this.options = options == null ? RegionOptions.DEFAULT : options;
		this.bounds = boundsOf(this.areas);
	}

	public static Region create(List<Area> areas) {
		return new Region(UUID.randomUUID(), randomColor(), areas, RegionOptions.DEFAULT);
	}

	public static Region create(Area area) {
		return create(List.of(area));
	}

	public static int randomColor() {
		return Mth.hsvToRgb(ThreadLocalRandom.current().nextFloat(), 0.65F, 1.0F);
	}

	public UUID id() {
		return id;
	}

	public int color() {
		return color;
	}

	public List<Area> areas() {
		return areas;
	}

	public RegionOptions options() {
		return options;
	}

	/** 所有范围的包围盒。 */
	public Area bounds() {
		return bounds;
	}

	public Region withAreas(List<Area> value) {
		return new Region(id, color, value, options);
	}

	public Region withOptions(RegionOptions value) {
		return new Region(id, color, areas, value);
	}

	public Region withColor(int value) {
		return new Region(id, value, areas, options);
	}

	public boolean contains(BlockPos pos) {
		if (!bounds.contains(pos)) {
			return false;
		}

		for (Area area : areas) {
			if (area.contains(pos)) {
				return true;
			}
		}

		return false;
	}

	public boolean contains(double x, double y, double z) {
		if (!bounds.contains(x, y, z)) {
			return false;
		}

		for (Area area : areas) {
			if (area.contains(x, y, z)) {
				return true;
			}
		}

		return false;
	}

	public boolean intersectsSection(int sx, int sy, int sz) {
		if (!bounds.intersectsSection(sx, sy, sz)) {
			return false;
		}

		for (Area area : areas) {
			if (area.intersectsSection(sx, sy, sz)) {
				return true;
			}
		}

		return false;
	}

	/** 融合后的整体线框，第一次用到时才计算。 */
	public List<RegionOutline.Segment> outline() {
		List<RegionOutline.Segment> cached = outline;

		if (cached == null) {
			cached = RegionOutline.of(areas);
			outline = cached;
		}

		return cached;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Region region && id.equals(region.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	/** 范围用 ; 分隔，设置和颜色在末尾的 {SNBT} 里：minX,minY,minZ:maxX,maxY,maxZ;…{color:…}。 */
	public String toSource() {
		StringBuilder source = new StringBuilder();

		for (Area area : areas) {
			if (!source.isEmpty()) {
				source.append(';');
			}

			source.append(area.toSource());
		}

		CompoundTag tag = options.toTag();
		tag.putInt("color", color);
		return source + tag.toString();
	}

	@Nullable
	public static Region parse(String input) {
		if (input == null) {
			return null;
		}

		String text = input.trim();
		int brace = text.indexOf('{');
		RegionOptions options = RegionOptions.DEFAULT;
		int color = randomColor();

		if (brace >= 0) {
			try {
				CompoundTag tag = TagParser.parseCompoundFully(text.substring(brace));
				options = RegionOptions.fromTag(tag);
				color = tag.getIntOr("color", color);
			}
			catch (CommandSyntaxException e) {
				return null;
			}

			text = text.substring(0, brace);
		}

		List<Area> areas = new ArrayList<>();

		for (String part : text.split(";")) {
			if (part.isBlank()) {
				continue;
			}

			Area area = Area.parse(part);
			if (area == null) {
				return null;
			}

			areas.add(area);
		}

		return areas.isEmpty() ? null : new Region(UUID.randomUUID(), color, areas, options);
	}

	/** 把 source 并入 target：范围合在一起；设置以 target 为准，名单取并集，勾选项丢弃 source 的。 */
	public static Region merge(Region target, Region source) {
		List<Area> areas = new ArrayList<>(target.areas);

		for (Area area : source.areas) {
			if (!areas.contains(area)) {
				areas.add(area);
			}
		}

		return target.withAreas(areas).withOptions(target.options.mergedWith(source.options));
	}

	private static Area boundsOf(List<Area> areas) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;

		for (Area area : areas) {
			minX = Math.min(minX, area.min().getX());
			minY = Math.min(minY, area.min().getY());
			minZ = Math.min(minZ, area.min().getZ());
			maxX = Math.max(maxX, area.max().getX());
			maxY = Math.max(maxY, area.max().getY());
			maxZ = Math.max(maxZ, area.max().getZ());
		}

		return new Area(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
	}
}
