package com.selectiverendering.config.suggest;

import java.util.List;

import net.minecraft.network.chat.MutableComponent;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IDialogHandler;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;
import fi.dy.masa.malilib.util.GuiUtils;

/** 带补全的名单配置项的一行 */
public final class WidgetSuggestingList extends WidgetConfigOption {
	public WidgetSuggestingList(int x, int y, int width, int height, int labelWidth, int configWidth,
		ConfigOptionWrapper wrapper, int listIndex, IKeybindConfigGui host,
		WidgetListConfigOptionsBase<?, ?> parent) {
		super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);
	}

	@Override
	protected void addConfigOption(int x, int y, int labelWidth, int configWidth, IConfigBase config) {
		if (!(config instanceof SuggestingStringList list)) {
			return;
		}

		y += 1;

		this.addLabel(x, y + 6, labelWidth, 8, 0xFFFFFFFF, list.getConfigGuiDisplayName());

		// 没人调过 setCommentComponent，恒为 null；回退到 .comment.<name>，跟基类 WidgetConfigOption 一致。
		String text = list.getComment();
		MutableComponent comment = list.getCommentComponent();

		if (comment != null) {
			this.addConfigComment(x, y + 4, labelWidth, 12, comment);
		} else if (text != null && !text.isEmpty()) {
			this.addConfigComment(x, y + 4, labelWidth, 12, text);
		}

		int buttonX = x + labelWidth + 10;

		this.addButton(new ButtonGeneric(buttonX, y, configWidth, 20, this.summary(list.getStrings(), configWidth - 12)), (button, mouseButton) -> {
			IDialogHandler dialogs = this.host.getDialogHandler();
			GuiSuggestStringListEdit editor = new GuiSuggestStringListEdit(list, this.host, dialogs, dialogs == null ? GuiUtils.getCurrentScreen() : null, list.kind());

			if (dialogs != null) {
				dialogs.openDialog(editor);
			} else {
				GuiBase.openGui(editor);
			}
		});

		ButtonGeneric reset = this.createResetButton(buttonX + configWidth + 2, y, list);
		this.addButton(reset, (button, mouseButton) -> {
			list.resetToDefault();

			if (this.host instanceof GuiBase gui) {
				gui.initGui();
			}
		});
	}

	private String summary(List<String> values, int width) {
		String text = values.isEmpty() ? "-" : String.join(", ", values);

		if (this.getStringWidth(text) <= width) {
			return text;
		}

		while (text.length() > 1 && this.getStringWidth(text + "…") > width) {
			text = text.substring(0, text.length() - 1);
		}

		return text + "…";
	}
}
