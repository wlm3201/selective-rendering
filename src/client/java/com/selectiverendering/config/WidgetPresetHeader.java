package com.selectiverendering.config;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;

/**
 * 预设区的标题行，顺便承载"新建"按钮（因为 {@code GuiConfigsBase} 的
 * 普通标题行是不能放按钮的，这里用一个假的 config 行来顶替）。
 */
public final class WidgetPresetHeader extends WidgetConfigOption {
	public WidgetPresetHeader(int x, int y, int width, int height, int labelWidth, int configWidth,
			ConfigOptionWrapper wrapper, int listIndex, IKeybindConfigGui host,
			WidgetListConfigOptionsBase<?, ?> parent) {
		super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);

		String label = ModConfigs.text("presets.new_button");
		int buttonWidth = this.getStringWidth(label) + 10;

		ButtonGeneric button = new ButtonGeneric(
			WidgetPresetRow.resetButtonRight(this, x, labelWidth, configWidth) - buttonWidth, y + 1, buttonWidth, 20,
			label, ModConfigs.text("presets.new_button.description")
		);

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
