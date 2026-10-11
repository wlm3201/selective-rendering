package com.selectiverendering.config.region;

import com.selectiverendering.config.presets.WidgetPresetRow;
import com.selectiverendering.config.entrypoint.ModConfigGui;
import com.selectiverendering.config.entrypoint.ModConfigs;
import com.selectiverendering.region.Region;
import com.selectiverendering.region.RegionEditor;

import java.util.List;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;

public final class WidgetRegionRow extends WidgetConfigOption {
	private static final int MIN_BUTTON_WIDTH = 36;
	private static final int GAP = 6;

	public WidgetRegionRow(int x, int y, int width, int height, int labelWidth, int configWidth,
			ConfigOptionWrapper wrapper, int listIndex, IKeybindConfigGui host,
			WidgetListConfigOptionsBase<?, ?> parent) {
		super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);
	}

	@Override
	protected void addConfigOption(int x, int y, int labelWidth, int configWidth, IConfigBase config) {
		if (!(config instanceof RegionRowConfig row)) {
			return;
		}

		Region region = row.region();

		String editText = ModConfigs.text("region_edit");
		String removeText = ModConfigs.text("region_remove");
		int editWidth = Math.max(MIN_BUTTON_WIDTH, this.getStringWidth(editText) + 12);
		int removeWidth = Math.max(MIN_BUTTON_WIDTH, this.getStringWidth(removeText) + 12);

		int right = WidgetPresetRow.resetButtonRight(this, x, labelWidth, configWidth);
		int removeX = right - removeWidth;
		int editX = removeX - GAP - editWidth;

		this.addLabel(x + 18, y + 6, editX - GAP - x - 18, 8, 0xFFFFFFFF, row.title());

		this.addButton(new ButtonGeneric(editX, y, editWidth, 20, editText), (button, mouseButton) -> {
			if (this.host instanceof ModConfigGui gui) {
				gui.openRegion(region, row.index());
			}
		});

		this.addButton(new ButtonGeneric(removeX, y, removeWidth, 20, removeText), (button, mouseButton) -> {
			RegionEditor.removeRegions(List.of(region));
			this.refresh();
		});
	}

	@Override
	public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
		super.render(ctx, mouseX, mouseY, selected);

		if (this.getEntry() != null && this.getEntry().getConfig() instanceof RegionRowConfig row) {
			RenderUtils.drawRect(ctx, this.getX() + 4, this.getY() + 4, 10, 12, 0xFF000000 | row.region().color());
		}
	}

	private void refresh() {
		if (this.host instanceof GuiBase gui) {
			gui.initGui();
		}
	}
}
