package com.selectiverendering.config.region;

import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.SelectiveRenderingManager.Mode;
import com.selectiverendering.SelectiveRenderingManager.RecordApplyMode;
import com.selectiverendering.SelectiveRenderingManager.RecordMode;
import com.selectiverendering.config.presets.Preset;
import com.selectiverendering.matchingrule.BlockMatchRule;
import com.selectiverendering.region.Area;
import com.selectiverendering.region.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 不可变的渲染配置快照，供渲染侧统一读取。新增字段：Builder 里加一个、这里加字段和访问器。 */
public final class RenderConfig {
	private final Mode mode;
	private final int transparency;
	private final boolean invert;
	private final List<BlockMatchRule> rules;
	private final List<BlockMatchRule> recorded;
	private final List<String> ruleSources;
	private final List<String> recordSources;
	private final List<Region> regions;
	private final List<Preset> presets;
	private final RecordMode recordMode;
	private final RecordApplyMode recordApplyMode;
	private final String wand;
	private final boolean fullBright;
	private final boolean passThrough;
	private final BlockPos corner1;
	private final BlockPos corner2;
	private final AABB region;

	private final int alpha;
	private final List<Region> blockRegions;
	private final List<Region> entityRegions;
	private final List<Region> particleRegions;
	private final Area union;
	private final boolean hasLocalRules;
	private final boolean hasAlphaOverride;

	private RenderConfig(Builder builder) {
		this.mode = builder.mode;
		this.transparency = builder.transparency;
		this.invert = builder.invert;
		this.rules = List.copyOf(builder.rules);
		this.recorded = List.copyOf(builder.recorded);
		this.ruleSources = List.copyOf(builder.ruleSources);
		this.recordSources = List.copyOf(builder.recordSources);
		this.regions = List.copyOf(builder.regions);
		this.presets = List.copyOf(builder.presets);
		this.recordMode = builder.recordMode;
		this.recordApplyMode = builder.recordApplyMode;
		this.wand = builder.wand;
		this.fullBright = builder.fullBright;
		this.passThrough = builder.passThrough;
		this.corner1 = builder.corner1;
		this.corner2 = builder.corner2;
		this.region = builder.region;

		this.alpha = Math.round(255F * (SelectiveRenderingManager.MAX_TRANSPARENCY - transparency) / SelectiveRenderingManager.MAX_TRANSPARENCY);
		this.blockRegions = regions.stream().filter(added -> added.options().blocks()).toList();
		this.entityRegions = regions.stream().filter(added -> added.options().entities()).toList();
		this.particleRegions = regions.stream().filter(added -> added.options().particles()).toList();
		this.union = unionOf(blockRegions);
		this.hasLocalRules = blockRegions.stream().anyMatch(added -> !added.options().rules().isEmpty());
		this.hasAlphaOverride = regions.stream().anyMatch(added -> added.options().alphaOverride() >= 0);
	}

	public Mode mode() {
		return mode;
	}

	public int transparency() {
		return transparency;
	}

	public boolean invert() {
		return invert;
	}

	public List<BlockMatchRule> rules() {
		return rules;
	}

	public List<BlockMatchRule> recorded() {
		return recorded;
	}

	public List<String> ruleSources() {
		return ruleSources;
	}

	public List<String> recordSources() {
		return recordSources;
	}

	public List<Region> regions() {
		return regions;
	}

	public List<Preset> presets() {
		return presets;
	}

	public RecordMode recordMode() {
		return recordMode;
	}

	public RecordApplyMode recordApplyMode() {
		return recordApplyMode;
	}

	public String wand() {
		return wand;
	}

	public boolean fullBright() {
		return fullBright;
	}

	public boolean passThrough() {
		return passThrough;
	}

	@Nullable
	public BlockPos corner1() {
		return corner1;
	}

	@Nullable
	public BlockPos corner2() {
		return corner2;
	}

	@Nullable
	public AABB region() {
		return region;
	}

	/** 隐藏方块使用的顶点 alpha，由透明度换算。 */
	public int alpha() {
		return alpha;
	}

	/** 参与方块判定的选区。 */
	public List<Region> blockRegions() {
		return blockRegions;
	}

	public List<Region> entityRegions() {
		return entityRegions;
	}

	public List<Region> particleRegions() {
		return particleRegions;
	}

	/** {@link #blockRegions()} 的包围盒；没有时为 null。 */
	@Nullable
	public Area union() {
		return union;
	}

	public boolean hasLocalRules() {
		return hasLocalRules;
	}

	public boolean hasAlphaOverride() {
		return hasAlphaOverride;
	}

	@Nullable
	private static Area unionOf(List<Region> regions) {
		if (regions.isEmpty()) {
			return null;
		}

		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;

		for (Region region : regions) {
			BlockPos min = region.bounds().min();
			BlockPos max = region.bounds().max();

			minX = Math.min(minX, min.getX());
			minY = Math.min(minY, min.getY());
			minZ = Math.min(minZ, min.getZ());
			maxX = Math.max(maxX, max.getX());
			maxY = Math.max(maxY, max.getY());
			maxZ = Math.max(maxZ, max.getZ());
		}

		return new Area(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
	}

	public static final class Builder {
		public Mode mode = SelectiveRenderingManager.DEFAULT_MODE;
		public int transparency = SelectiveRenderingManager.DEFAULT_TRANSPARENCY;
		public boolean invert = SelectiveRenderingManager.DEFAULT_INVERT;
		public List<BlockMatchRule> rules = List.of();
		public List<BlockMatchRule> recorded = List.of();
		public List<String> ruleSources = List.of();
		public List<String> recordSources = List.of();
		public List<Region> regions = List.of();
		public List<Preset> presets = List.of();
		public RecordMode recordMode = SelectiveRenderingManager.DEFAULT_RECORD_MODE;
		public RecordApplyMode recordApplyMode = SelectiveRenderingManager.DEFAULT_RECORD_APPLY_MODE;
		public String wand = SelectiveRenderingManager.DEFAULT_WAND;
		public boolean fullBright = SelectiveRenderingManager.DEFAULT_FULL_BRIGHT;
		public boolean passThrough = SelectiveRenderingManager.DEFAULT_PASS_THROUGH;
		public BlockPos corner1;
		public BlockPos corner2;
		public AABB region;

		public RenderConfig build() {
			return new RenderConfig(this);
		}
	}
}
