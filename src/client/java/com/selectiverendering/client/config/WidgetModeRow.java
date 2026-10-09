package com.selectiverendering.client.config;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetDropDownList;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;
import fi.dy.masa.malilib.render.GuiContext;
import java.util.List;

public final class WidgetModeRow extends WidgetConfigOption {
    private ModeDropdown dropdown;

    public WidgetModeRow(
            int x,
            int y,
            int width,
            int height,
            int labelWidth,
            int configWidth,
            ConfigOptionWrapper wrapper,
            int listIndex,
            IKeybindConfigGui host,
            WidgetListConfigOptionsBase<?, ?> parent) {
        super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);
    }

    @Override
    protected void addConfigOption(
            int x, int y, int labelWidth, int configWidth, IConfigBase config) {
        if (!(config instanceof ModConfigs.ModeConfig mode)) {
            return;
        }

        y += 1;

        this.addLabel(x, y + 6, labelWidth, 8, 0xFFFFFFFF, mode.getConfigGuiDisplayName());
        this.addConfigComment(x, y + 4, labelWidth, 12, mode.getCommentComponent());

        int dropdownX = x + labelWidth + 10;
        int resetX = dropdownX + configWidth + 2;

        this.dropdown = new ModeDropdown(dropdownX, y, configWidth, 20, ModeOption.all());
        this.dropdown.setSelectedEntry(ModeOption.of(mode.mode()));
        this.addWidget(this.dropdown);

        ButtonGeneric reset = this.createResetButton(resetX, y, mode);
        this.addButton(
                reset,
                (button, mouseButton) -> {
                    mode.resetToDefault();
                    this.dropdown.setSelectedEntry(ModeOption.of(mode.mode()));
                    this.refresh();
                });
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return super.isMouseOver(mouseX, mouseY)
                || (this.dropdown != null && this.dropdown.isMouseOver(mouseX, mouseY));
    }

    void drawOpenDropdown(GuiContext ctx, int mouseX, int mouseY) {
        if (this.dropdown != null && this.dropdown.isOpen()) {
            this.dropdown.postRenderHovered(ctx, mouseX, mouseY, false);
        }
    }

    private void refresh() {
        if (this.host instanceof GuiBase gui) {
            gui.initGui();
        }
    }

    private static final class ModeDropdown extends WidgetDropDownList<ModeOption> {
        private ModeDropdown(int x, int y, int width, int height, List<ModeOption> entries) {
            super(x, y, width, height, 200, 9, entries);
        }

        @Override
        protected void setSelectedEntry(int index) {
            super.setSelectedEntry(index);

            if (this.selectedEntry != null) {
                ModConfigs.MODE.setOptionListValue(this.selectedEntry);
            }
        }

        @Override
        protected String getDisplayString(ModeOption entry) {
            return entry.getDisplayName();
        }

        boolean isOpen() {
            return this.isOpen;
        }
    }
}
