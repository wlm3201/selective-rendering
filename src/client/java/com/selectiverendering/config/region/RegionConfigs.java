package com.selectiverendering.config.region;

import com.selectiverendering.config.suggest.SuggestKind;
import com.selectiverendering.config.suggest.SuggestingStringList;
import com.selectiverendering.config.entrypoint.ModConfigs;
import com.selectiverendering.region.Area;
import com.selectiverendering.region.Region;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;

final class RegionConfigs {
	private RegionConfigs() {
	}

	static List<ConfigOptionWrapper> rows(RegionHandle handle) {
		Region region = handle.region();
		List<ConfigOptionWrapper> rows = new ArrayList<>();

		int index = 1;
		for (Area area : region.areas()) {
			rows.add(new ConfigOptionWrapper(new RegionAreaConfig(handle, area, index)));
			rows.add(new ConfigOptionWrapper(new RegionCornerConfig(handle, area, index, true)));
			rows.add(new ConfigOptionWrapper(new RegionCornerConfig(handle, area, index, false)));
			index++;
		}

		rows.add(new ConfigOptionWrapper(new RegionTargetsConfig(handle)));

		ConfigInteger alpha = new ConfigInteger("region_alpha", -1, -1, 255, true).apply(ModConfigs.KEY);
		alpha.setIntegerValue(region.options().alphaOverride());
		alpha.setValueChangeCallback(config -> handle.update(options -> options.withAlphaOverride(config.getIntegerValue())));

		SuggestingStringList rules = new SuggestingStringList("region_rules", SuggestKind.BLOCK).apply(ModConfigs.KEY);
		rules.setStrings(region.options().ruleSources());
		rules.setValueChangeCallback(config -> handle.update(options -> options.withRuleSources(config.getStrings())));

		SuggestingStringList entities = new SuggestingStringList("region_entity_types", SuggestKind.ENTITY).apply(ModConfigs.KEY);
		entities.setStrings(region.options().entityTypes());
		entities.setValueChangeCallback(config -> handle.update(options -> options.withEntityTypes(config.getStrings())));

		SuggestingStringList particles = new SuggestingStringList("region_particle_types", SuggestKind.PARTICLE).apply(ModConfigs.KEY);
		particles.setStrings(region.options().particleTypes());
		particles.setValueChangeCallback(config -> handle.update(options -> options.withParticleTypes(config.getStrings())));

		rows.addAll(ConfigOptionWrapper.createFor(List.of(alpha, rules, entities, particles)));
		rows.add(new ConfigOptionWrapper(new RegionOffsetConfig(handle)));
		rows.add(new ConfigOptionWrapper(new RegionMergeConfig(handle)));
		return rows;
	}

	static String format(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}
}
