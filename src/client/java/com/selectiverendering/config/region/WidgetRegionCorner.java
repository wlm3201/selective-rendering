package com.selectiverendering.config.region;

import com.selectiverendering.config.entrypoint.ModConfigs;
import com.selectiverendering.region.Area;
import net.minecraft.core.BlockPos;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;
import fi.dy.masa.malilib.gui.wrappers.TextFieldWrapper;

/** 范围角点的坐标输入框，点后面的按钮才应用，避免边输入边重建。 */
public final class WidgetRegionCorner extends WidgetConfigOption {
	private static final Pattern NUMBER = Pattern.compile("-?\\d+");

	public WidgetRegionCorner(int x, int y, int width, int height, int labelWidth, int configWidth,
		ConfigOptionWrapper wrapper, int listIndex, IKeybindConfigGui host,
		WidgetListConfigOptionsBase<?, ?> parent) {
		super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);
	}

	@Override
	protected void addConfigOption(int x, int y, int labelWidth, int configWidth, IConfigBase config) {
		if (!(config instanceof RegionCornerConfig corner)) {
			return;
		}

		RegionHandle handle = corner.handle();
		Area area = corner.area();
		BlockPos shown = corner.isMin() ? area.min() : area.max();

		String label = ModConfigs.text(corner.isMin() ? "region_min" : "region_max");
		this.addLabel(x + 12, y + 6, labelWidth, 8, 0xFFFFFFFF, label);

		int fieldX = x + labelWidth + 10;
		GuiTextFieldGeneric field = this.createTextField(fieldX, y + 1, configWidth - 4, 17);
		field.setMaxLength(48);
		field.setValue(RegionConfigs.format(shown));

		this.textField = new TextFieldWrapper<>(field, new Ignore());
		this.parent.addTextField(this.textField);

		ButtonGeneric apply = new ButtonGeneric(fieldX + configWidth + 2, y, 20, 20, "✔", ModConfigs.text("region_apply"));
		this.addButton(apply, (button, mouseButton) -> {
			BlockPos parsed = parse(field.getValue());

			if (parsed == null) {
				this.message(ModConfigs.text("region_invalid"));
				return;
			}

			BlockPos other = corner.isMin() ? area.max() : area.min();

			if (!handle.replaceArea(area, Area.of(parsed, other))) {
				this.message(ModConfigs.text("region_invalid"));
				return;
			}

			this.refresh();
		});
	}

	private static BlockPos parse(String text) {
		Matcher matcher = NUMBER.matcher(text);
		int[] values = new int[3];

		for (int index = 0; index < 3; index++) {
			if (!matcher.find()) {
				return null;
			}

			try {
				values[index] = Integer.parseInt(matcher.group());
			} catch (NumberFormatException e) {
				return null;
			}
		}

		return matcher.find() ? null : new BlockPos(values[0], values[1], values[2]);
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

	private static final class Ignore implements ITextFieldListener<GuiTextFieldGeneric> {
		@Override
		public boolean onTextChange(GuiTextFieldGeneric textField) {
			return false;
		}
	}
}
