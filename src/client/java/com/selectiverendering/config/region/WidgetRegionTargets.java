package com.selectiverendering.config.region;

import com.selectiverendering.config.entrypoint.ModConfigs;
import com.selectiverendering.region.RegionOptions;

import java.util.function.BiFunction;
import java.util.function.Function;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;

public final class WidgetRegionTargets extends WidgetConfigOption {
	private static final int BOX = 20;
	private static final int GAP = 14;
	private static final int LABEL_GAP = 4;

	public WidgetRegionTargets(int x, int y, int width, int height, int labelWidth, int configWidth,
		ConfigOptionWrapper wrapper, int listIndex, IKeybindConfigGui host,
		WidgetListConfigOptionsBase<?, ?> parent) {
		super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);
	}

	@Override
	protected void addConfigOption(int x, int y, int labelWidth, int configWidth, IConfigBase config) {
		if (!(config instanceof RegionTargetsConfig targets)) {
			return;
		}

		RegionHandle handle = targets.handle();

		x = this.addToggle(x, y, "region_blocks", handle, RegionOptions::blocks, RegionOptions::withBlocks);
		x = this.addToggle(x, y, "region_entities", handle, RegionOptions::entities, RegionOptions::withEntities);
		this.addToggle(x, y, "region_particles", handle, RegionOptions::particles, RegionOptions::withParticles);
	}

	private int addToggle(int x, int y, String key, RegionHandle handle,
		Function<RegionOptions, Boolean> read, BiFunction<RegionOptions, Boolean, RegionOptions> write) {
		String text = ModConfigs.text(key);
		int textWidth = this.getStringWidth(text);

		this.addLabel(x, y + 6, textWidth, 8, 0xFFFFFFFF, text);

		int boxX = x + textWidth + LABEL_GAP;
		ButtonGeneric box = new ButtonGeneric(boxX, y, BOX, BOX, mark(read.apply(handle.region().options())));

		this.addButton(box, (button, mouseButton) -> {
			boolean next = !read.apply(handle.region().options());

			handle.update(options -> write.apply(options, next));
			box.setDisplayString(mark(read.apply(handle.region().options())));
		});

		return boxX + BOX + GAP;
	}

	private static String mark(boolean on) {
		return on ? "✔" : " ";
	}
}
