package com.selectiverendering.config.region;

import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.config.entrypoint.ModConfigs;
import com.selectiverendering.region.Region;
import com.selectiverendering.region.RegionEditor;

import java.util.ArrayList;
import java.util.List;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetDropDownList;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;
import fi.dy.masa.malilib.render.GuiContext;

/** "合并到"一行：下拉框选目标选区，后面的按钮执行合并。 */
public final class WidgetRegionMerge extends WidgetConfigOption {
	private RegionDropdown dropdown;

	public WidgetRegionMerge(int x, int y, int width, int height, int labelWidth, int configWidth,
			ConfigOptionWrapper wrapper, int listIndex, IKeybindConfigGui host,
			WidgetListConfigOptionsBase<?, ?> parent) {
		super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);
	}

	@Override
	protected void addConfigOption(int x, int y, int labelWidth, int configWidth, IConfigBase config) {
		if (!(config instanceof RegionMergeConfig merge)) {
			return;
		}

		RegionHandle handle = merge.handle();
		Region self = handle.region();

		List<Region> others = new ArrayList<>();
		for (Region region : SelectiveRenderingManager.regions()) {
			if (!region.equals(self)) {
				others.add(region);
			}
		}

		y += 1;

		this.addLabel(x + 4, y + 6, labelWidth, 8, 0xFFFFFFFF, ModConfigs.text("region_merge"));

		int pickX = x + labelWidth + 10;

		if (others.isEmpty()) {
			ButtonGeneric none = new ButtonGeneric(pickX, y, configWidth, 20, ModConfigs.text("region_merge_none"));
			none.setEnabled(false);
			this.addButton(none, (button, mouseButton) -> { });
			return;
		}

		this.dropdown = new RegionDropdown(pickX, y, configWidth, 20, others);
		this.dropdown.setSelectedEntry(others.get(0));
		this.addWidget(this.dropdown);

		String applyText = ModConfigs.text("region_merge_apply");
		int applyWidth = Math.max(36, this.getStringWidth(applyText) + 12);

		this.addButton(new ButtonGeneric(pickX + configWidth + 2, y, applyWidth, 20, applyText, ModConfigs.text("region_merge_tip")), (button, mouseButton) -> {
			Region target = this.dropdown.selected();

			if (target != null) {
				RegionEditor.mergeRegion(handle.region(), target);
			}

			if (this.host instanceof GuiBase gui) {
				gui.initGui();
			}
		});
	}

	@Override
	public boolean isMouseOver(int mouseX, int mouseY) {
		return super.isMouseOver(mouseX, mouseY) || (this.dropdown != null && this.dropdown.isMouseOver(mouseX, mouseY));
	}

	public void drawOpenDropdown(GuiContext ctx, int mouseX, int mouseY) {
		if (this.dropdown != null && this.dropdown.isOpen()) {
			this.dropdown.postRenderHovered(ctx, mouseX, mouseY, false);
		}
	}

	private static final class RegionDropdown extends WidgetDropDownList<Region> {
		private RegionDropdown(int x, int y, int width, int height, List<Region> entries) {
			super(x, y, width, height, 200, 9, entries);
		}

		@Override
		protected String getDisplayString(Region entry) {
			return "#" + (SelectiveRenderingManager.regions().indexOf(entry) + 1) + "  " + entry.areas().size() + " " + ModConfigs.text("region_areas");
		}

		Region selected() {
			return this.selectedEntry;
		}

		boolean isOpen() {
			return this.isOpen;
		}
	}
}
