package com.selectiverendering.config.region;

import com.selectiverendering.config.presets.WidgetPresetRow;
import com.selectiverendering.config.entrypoint.ModConfigs;
import com.selectiverendering.region.Area;
import com.selectiverendering.region.RegionEditor;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;

/** 某个范围的标题行：范围、独立按钮、删除按钮。 */
public final class WidgetRegionArea extends WidgetConfigOption {
	private static final int MIN_BUTTON_WIDTH = 36;
	private static final int GAP = 6;

	public WidgetRegionArea(int x, int y, int width, int height, int labelWidth, int configWidth,
			ConfigOptionWrapper wrapper, int listIndex, IKeybindConfigGui host,
			WidgetListConfigOptionsBase<?, ?> parent) {
		super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);
	}

	@Override
	protected void addConfigOption(int x, int y, int labelWidth, int configWidth, IConfigBase config) {
		if (!(config instanceof RegionAreaConfig row)) {
			return;
		}

		RegionHandle handle = row.handle();
		Area area = row.area();

		String detachText = ModConfigs.text("region_detach");
		String removeText = ModConfigs.text("region_remove");
		int detachWidth = Math.max(MIN_BUTTON_WIDTH, this.getStringWidth(detachText) + 12);
		int removeWidth = Math.max(MIN_BUTTON_WIDTH, this.getStringWidth(removeText) + 12);

		int right = WidgetPresetRow.resetButtonRight(this, x, labelWidth, configWidth);
		int removeX = right - removeWidth;
		int detachX = removeX - GAP - detachWidth;

		String title = ModConfigs.text("region_area") + " " + row.index() + "  " + RegionConfigs.format(area.min()) + " → " + RegionConfigs.format(area.max());
		this.addLabel(x + 4, y + 6, detachX - GAP - x - 4, 8, 0xFFFFFFFF, title);

		this.addButton(new ButtonGeneric(detachX, y, detachWidth, 20, detachText, ModConfigs.text("region_detach_tip")), (button, mouseButton) -> {
			if (!RegionEditor.detachArea(handle.region(), area)) {
				this.message(ModConfigs.text("region_detach_blocked"));
				return;
			}

			this.refresh();
		});

		this.addButton(new ButtonGeneric(removeX, y, removeWidth, 20, removeText), (button, mouseButton) -> {
			RegionEditor.removeArea(handle.region(), area);
			this.refresh();
		});
	}

	private void message(String text) {
		if (this.host instanceof GuiBase gui) {
			gui.addMessage(Message.MessageType.WARNING, 2000, text);
		}
	}

	private void refresh() {
		if (this.host instanceof GuiBase gui) {
			gui.initGui();
		}
	}
}
