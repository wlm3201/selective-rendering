package com.selectiverendering.config.region;

import com.selectiverendering.config.entrypoint.ModConfigs;
import com.selectiverendering.region.Region;
import com.selectiverendering.region.RegionEditor;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;

/** 「投影位移」一行，当前位移 (x, y, z) 和一个重置按钮。 */
public final class WidgetRegionOffset extends WidgetConfigOption {
	public WidgetRegionOffset(int x, int y, int width, int height, int labelWidth, int configWidth,
		ConfigOptionWrapper wrapper, int listIndex, IKeybindConfigGui host,
		WidgetListConfigOptionsBase<?, ?> parent) {
		super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);
	}

	@Override
	protected void addConfigOption(int x, int y, int labelWidth, int configWidth, IConfigBase config) {
		if (!(config instanceof RegionOffsetConfig offset)) {
			return;
		}

		RegionHandle handle = offset.handle();
		Region region = handle.region();

		y += 1;

		this.addLabel(x + 4, y + 6, labelWidth, 8, 0xFFFFFFFF, ModConfigs.text("region_offset"));

		int valueX = x + labelWidth + 10;
		String value = region.options().hasOffset()
			? region.options().offsetX() + ", " + region.options().offsetY() + ", " + region.options().offsetZ()
			: ModConfigs.text("region_offset_none");

		this.addLabel(valueX, y + 6, configWidth, 8, region.options().hasOffset() ? 0xFFFFFFFF : 0xFFAAAAAA, value);

		String resetText = ModConfigs.text("region_offset_reset");
		int resetWidth = Math.max(36, this.getStringWidth(resetText) + 12);

		ButtonGeneric reset = new ButtonGeneric(valueX + configWidth + 2, y, resetWidth, 20, resetText, ModConfigs.text("region_offset_tip"));
		reset.setEnabled(region.options().hasOffset());

		this.addButton(reset, (button, mouseButton) -> {
			RegionEditor.resetProjection(handle.region());

			if (this.host instanceof GuiBase gui) {
				gui.initGui();
			}
		});
	}
}
