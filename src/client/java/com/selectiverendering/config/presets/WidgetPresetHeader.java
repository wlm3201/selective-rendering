package com.selectiverendering.config.presets;

import com.selectiverendering.config.entrypoint.ModConfigs;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;

/** 显示预设标题和新建按钮。 */
public final class WidgetPresetHeader extends WidgetConfigOption {
	public WidgetPresetHeader(int x, int y, int width, int height, int labelWidth, int configWidth,
		ConfigOptionWrapper wrapper, int listIndex, IKeybindConfigGui host,
		WidgetListConfigOptionsBase<?, ?> parent) {
		super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);

		String label = ModConfigs.text("presets.new_button");
		int buttonWidth = this.getStringWidth(label) + 10;

		ButtonGeneric button = new ButtonGeneric(
			WidgetPresetRow.resetButtonRight(this, x, labelWidth, configWidth) - buttonWidth, y + 1, buttonWidth, 20,
			label, ModConfigs.text("presets.new_button.description"));

		this.addButton(button, (pressed, mouseButton) -> {
			ModPresets.create();
			this.refresh();
		});
	}

	private void refresh() {
		if (this.host instanceof GuiBase gui) {
			gui.initGui();
		}
	}
}
