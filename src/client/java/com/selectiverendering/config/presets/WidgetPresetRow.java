package com.selectiverendering.config.presets;

import com.selectiverendering.SelectiveRenderingManager;

import com.selectiverendering.config.entrypoint.ModConfigs;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;
import fi.dy.masa.malilib.gui.wrappers.TextFieldWrapper;

/** 显示预设名称及切换、覆盖、删除按钮。 */
public final class WidgetPresetRow extends WidgetConfigOption {
	private static final int MIN_BUTTON_WIDTH = 36;
	private static final int BUTTON_GAP = 4;

	public WidgetPresetRow(int x, int y, int width, int height, int labelWidth, int configWidth,
			ConfigOptionWrapper wrapper, int listIndex, IKeybindConfigGui host,
			WidgetListConfigOptionsBase<?, ?> parent) {
		super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);
	}

	@Override
	protected void addConfigOption(int x, int y, int labelWidth, int configWidth, IConfigBase config) {
		if (!(config instanceof PresetRowConfig row)) {
			return;
		}

		Preset preset = row.preset();

		int applyWidth = this.buttonWidth("presets.apply");
		int overwriteWidth = this.buttonWidth("presets.overwrite");
		int removeWidth = this.buttonWidth("presets.remove");
		int buttons = applyWidth + overwriteWidth + removeWidth + 2 * BUTTON_GAP;

		int right = resetButtonRight(this, x, labelWidth, configWidth);
		int buttonX = right - buttons;
		int fieldWidth = Math.max(60, buttonX - BUTTON_GAP - x);

		GuiTextFieldGeneric field = this.createTextField(x, y + 1, fieldWidth - 4, 17);
		field.setMaxLength(64);
		field.setValue(row.getStringValue());
		field.setHoverTooltip("%s", row.summary());

		this.textField = new TextFieldWrapper<>(field, new NameListener(row.preset()));
		this.parent.addTextField(this.textField);

		this.addButton(this.button(buttonX, y, "presets.apply", applyWidth), (button, mouseButton) -> {
			ModPresets.apply(preset);
			this.refresh();
		});

		buttonX += applyWidth + BUTTON_GAP;

		this.addButton(this.button(buttonX, y, "presets.overwrite", overwriteWidth), (button, mouseButton) -> {
			ModPresets.overwrite(preset);
			this.refresh();
		});

		buttonX += overwriteWidth + BUTTON_GAP;

		this.addButton(this.button(buttonX, y, "presets.remove", removeWidth), (button, mouseButton) -> {
			ModPresets.remove(preset);
			this.refresh();
		});
	}

	public static int resetButtonRight(WidgetConfigOption widget, int x, int labelWidth, int configWidth) {
		int resetWidth = widget.getStringWidth(StringUtils.translate("malilib.gui.button.reset.caps")) + 10;

		return x + labelWidth + 10 + configWidth + 2 + resetWidth;
	}

	private int buttonWidth(String key) {
		return Math.max(MIN_BUTTON_WIDTH, this.getStringWidth(ModConfigs.text(key)) + 12);
	}

	private ButtonGeneric button(int x, int y, String key, int width) {
		return new ButtonGeneric(x, y, width, 20, ModConfigs.text(key));
	}

	private void refresh() {
		if (this.host instanceof GuiBase gui) {
			gui.initGui();
		}
	}

	/** 修改预设名称并保存。 */
	private static final class NameListener implements ITextFieldListener<GuiTextFieldGeneric> {
		private final Preset preset;

		NameListener(Preset preset) {
			this.preset = preset;
		}

		@Override
		public boolean onTextChange(GuiTextFieldGeneric textField) {
			this.preset.name = textField.getValue();
			SelectiveRenderingManager.savePresets();
			return false;
		}
	}
}
