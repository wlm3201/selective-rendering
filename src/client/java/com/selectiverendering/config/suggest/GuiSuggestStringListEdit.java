package com.selectiverendering.config.suggest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiStringListEdit;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.interfaces.IConfigGui;
import fi.dy.masa.malilib.gui.interfaces.IDialogHandler;
import fi.dy.masa.malilib.gui.widgets.WidgetListStringListEdit;
import fi.dy.masa.malilib.gui.widgets.WidgetStringListEditEntry;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;

/** 带注册名补全的 malilib 字符串列表编辑界面*/
public final class GuiSuggestStringListEdit extends GuiStringListEdit {
	private static final int VISIBLE = 8;
	private static final int INVALID_COLOR = 0xFFFF8080;
	private static final int ROW = 11;

	private final SuggestKind kind;
	private final Map<String, Boolean> validity = new HashMap<>();

	private List<Suggestion> suggestions = List.of();
	private String lastText = "";
	private int lastCursor = -1;
	private int selected;
	private int top;

	private int boxX;
	private int boxY;
	private int boxWidth;

	public GuiSuggestStringListEdit(IConfigStringList config, IConfigGui configGui, IDialogHandler dialogHandler, Screen parent, SuggestKind kind) {
		super(config, configGui, dialogHandler, parent);

		this.kind = kind;
	}

	@Override
	protected WidgetListStringListEdit createListWidget(int listX, int listY) {
		return new SuggestList(this.dialogLeft + 10, this.dialogTop + 20, this.getBrowserWidth(), this.getBrowserHeight(), this.dialogWidth - 100, this);
	}

	private boolean isValid(String entry) {
		if (this.validity.size() > 512) {
			this.validity.clear();
		}

		return this.validity.computeIfAbsent(entry, this.kind::isValid);
	}

	private GuiTextFieldGeneric focusedField() {
		return this.getListWidget() instanceof SuggestList list ? list.focusedField() : null;
	}

	private void update(GuiTextFieldGeneric field) {
		if (field == null) {
			this.suggestions = List.of();
			this.lastCursor = -1;
			return;
		}

		String text = field.getValue();
		int cursor = field.getCursorPosition();

		if (cursor != this.lastCursor || !text.equals(this.lastText)) {
			this.suggestions = this.kind.suggest(text, cursor);
			this.lastText = text;
			this.lastCursor = cursor;
			this.selected = 0;
			this.top = 0;
		}
	}

	private void accept(GuiTextFieldGeneric field, int index) {
		Suggestion suggestion = this.suggestions.get(index);
		String text = field.getValue();

		field.setValue(suggestion.apply(text));
		field.setCursorPosition(suggestion.getRange().getStart() + suggestion.getText().length());
		field.setHighlightPos(field.getCursorPosition());

		this.lastCursor = -1;
	}

	@Override
	public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
		if (this.getListWidget() instanceof SuggestList list) {
			list.markInvalid(this::isValid);
		}

		super.drawContents(ctx, mouseX, mouseY, partialTicks);

		GuiTextFieldGeneric field = this.focusedField();
		this.update(field);

		if (field == null || this.suggestions.isEmpty()) {
			return;
		}

		int rows = Math.min(VISIBLE, this.suggestions.size());
		int width = 0;

		for (Suggestion suggestion : this.suggestions) {
			width = Math.max(width, this.getStringWidth(suggestion.getText()));
		}

		this.boxWidth = Math.max(width + 8, field.getWidth());
		this.boxX = field.getX();
		this.boxY = field.getY() + field.getHeight() + 1;

		if (this.boxY + rows * ROW + 4 > this.height) {
			this.boxY = field.getY() - rows * ROW - 5;
		}

		RenderUtils.drawRect(ctx, this.boxX - 1, this.boxY - 1, this.boxWidth + 2, rows * ROW + 4, 0xFF808080);
		RenderUtils.drawRect(ctx, this.boxX, this.boxY, this.boxWidth, rows * ROW + 2, 0xF0101010);

		for (int row = 0; row < rows; row++) {
			int index = this.top + row;
			int y = this.boxY + 1 + row * ROW;

			if (index == this.selected) {
				RenderUtils.drawRect(ctx, this.boxX, y, this.boxWidth, ROW, 0xFF3A3A3A);
			}

			this.drawString(ctx, this.suggestions.get(index).getText(), this.boxX + 4, y + 2, index == this.selected ? 0xFFFFFF55 : 0xFFAAAAAA);
		}
	}

	@Override
	public boolean onKeyTyped(KeyEvent event) {
		GuiTextFieldGeneric field = this.focusedField();

		if (field != null && !this.suggestions.isEmpty()) {
			int key = event.key();

			if (key == InputConstants.KEY_TAB) {
				this.accept(field, this.selected);
				return true;
			}

			if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
				int size = this.suggestions.size();

				this.selected = Math.floorMod(this.selected + (key == InputConstants.KEY_DOWN ? 1 : -1), size);

				if (this.selected < this.top) {
					this.top = this.selected;
				}
				else if (this.selected >= this.top + VISIBLE) {
					this.top = this.selected - VISIBLE + 1;
				}

				return true;
			}
		}

		return super.onKeyTyped(event);
	}

	@Override
	public boolean onMouseClicked(MouseButtonEvent event, boolean doubleClick) {
		GuiTextFieldGeneric field = this.focusedField();

		if (field != null && !this.suggestions.isEmpty()) {
			int rows = Math.min(VISIBLE, this.suggestions.size());
			double x = event.x();
			double y = event.y();

			if (x >= this.boxX && x < this.boxX + this.boxWidth && y >= this.boxY + 1 && y < this.boxY + 1 + rows * ROW) {
				this.accept(field, this.top + (int) ((y - this.boxY - 1) / ROW));
				return true;
			}
		}

		return super.onMouseClicked(event, doubleClick);
	}

	/** 把每一行换成能让外面找到输入框的版本*/
	private static final class SuggestList extends WidgetListStringListEdit {
		SuggestList(int x, int y, int width, int height, int labelWidth, GuiStringListEdit gui) {
			super(x, y, width, height, labelWidth, gui);
		}

		void markInvalid(Predicate<String> valid) {
			for (WidgetStringListEditEntry entry : this.listWidgets) {
				if (entry instanceof SuggestEntry suggest && suggest.field() != null) {
					String text = Objects.requireNonNull(suggest.field()).getValue().trim();

					Objects.requireNonNull(suggest.field()).setTextColor(text.isEmpty() || valid.test(text) ? EditBox.DEFAULT_TEXT_COLOR : INVALID_COLOR);
				}
			}
		}

		GuiTextFieldGeneric focusedField() {
			for (WidgetStringListEditEntry entry : this.listWidgets) {
				if (entry instanceof SuggestEntry suggest && suggest.field() != null && Objects.requireNonNull(suggest.field()).isFocused()) {
					return suggest.field();
				}
			}

			return null;
		}

		@Override
		protected WidgetStringListEditEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, String entry) {
			List<String> values = this.config.getStrings();
			List<String> defaults = this.config.getDefaultStrings();

			boolean existing = listIndex >= 0 && listIndex < values.size();

			return new SuggestEntry(x, y, this.browserEntryWidth, this.browserEntryHeight, listIndex, isOdd,
				existing ? values.get(listIndex) : "",
				existing && listIndex < defaults.size() ? defaults.get(listIndex) : "",
				this);
		}
	}

	private static final class SuggestEntry extends WidgetStringListEditEntry {
		SuggestEntry(int x, int y, int width, int height, int listIndex, boolean isOdd, String value, String defaultValue, WidgetListStringListEdit parent) {
			super(x, y, width, height, listIndex, isOdd, value, defaultValue, parent);
		}

		GuiTextFieldGeneric field() {
			return this.textField == null ? null : this.textField.textField();
		}
	}
}
