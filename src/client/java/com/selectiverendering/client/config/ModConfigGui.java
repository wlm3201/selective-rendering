package com.selectiverendering.client.config;

import com.selectiverendering.client.Preset;
import com.selectiverendering.client.SelectiveRendering;
import com.selectiverendering.client.SelectiveRenderingManager;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.render.GuiContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;

public final class ModConfigGui extends GuiConfigsBase {
    private static final int LIST_X = 10;
    private static final int LIST_Y = 50;

    private static final String HEADING = ModConfigs.text("presets");

    public ModConfigGui(Screen parent) {
        super(
                LIST_X,
                LIST_Y,
                SelectiveRendering.MOD_ID,
                parent,
                SelectiveRendering.MOD_ID + ".config.title");

        this.setParent(parent);
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        List<ConfigOptionWrapper> configs = new ArrayList<>();

        configs.add(new ConfigOptionWrapper(ModConfigs.MODE));
        configs.add(new ConfigOptionWrapper(ModConfigs.INVERT));
        configs.add(new ConfigOptionWrapper(ModConfigs.TRANSPARENCY));
        configs.add(new ConfigOptionWrapper(ModConfigs.RULES));
        configs.add(new ConfigOptionWrapper(ModConfigs.REGIONS));
        configs.add(new ConfigOptionWrapper(ModConfigs.BLOCKS_KEY));
        configs.add(new ConfigOptionWrapper(ModConfigs.REGION_KEY));
        configs.add(new ConfigOptionWrapper(ModConfigs.OPEN_CONFIG_GUI));
        configs.add(new ConfigOptionWrapper(ModConfigs.WAND));
        configs.add(new ConfigOptionWrapper(ModConfigs.FULL_BRIGHT));
        configs.add(new ConfigOptionWrapper(ModConfigs.PASS_THROUGH));
        configs.add(new ConfigOptionWrapper(ModConfigs.RECORDED));
        configs.add(new ConfigOptionWrapper(ModConfigs.RECORD_MODE));
        configs.add(new ConfigOptionWrapper(ModConfigs.RECORD_APPLY_MODE));
        configs.add(new ConfigOptionWrapper(ModConfigs.RECORD_KEY));

        configs.add(new ConfigOptionWrapper(HEADING));

        for (Preset preset : SelectiveRenderingManager.presets()) {
            configs.add(new ConfigOptionWrapper(new PresetRowConfig(preset)));
        }

        return configs;
    }

    @Override
    protected WidgetListConfigOptions createListWidget(int listX, int listY) {
        return new WidgetListConfigOptions(
                listX,
                listY,
                this.getBrowserWidth(),
                this.getBrowserHeight(),
                this.getConfigWidth(),
                0.f,
                this.useKeybindSearch(),
                this) {
            @Override
            protected WidgetConfigOption createListEntryWidget(
                    int x, int y, int listIndex, boolean isOdd, ConfigOptionWrapper entry) {
                IConfigBase config = entry.getConfig();

                if (entry.getType() == ConfigOptionWrapper.Type.LABEL
                        && HEADING.equals(entry.getLabel())) {
                    return new WidgetPresetHeader(
                            x,
                            y,
                            this.browserEntryWidth,
                            this.browserEntryHeight,
                            this.maxLabelWidth,
                            this.configWidth,
                            entry,
                            listIndex,
                            this.parent,
                            this);
                }

                if (config instanceof PresetRowConfig) {
                    return new WidgetPresetRow(
                            x,
                            y,
                            this.browserEntryWidth,
                            this.browserEntryHeight,
                            this.maxLabelWidth,
                            this.configWidth,
                            entry,
                            listIndex,
                            this.parent,
                            this);
                }

                if (config instanceof ModConfigs.ModeConfig) {
                    return new WidgetModeRow(
                            x,
                            y,
                            this.browserEntryWidth,
                            this.browserEntryHeight,
                            this.maxLabelWidth,
                            this.configWidth,
                            entry,
                            listIndex,
                            this.parent,
                            this);
                }

                return super.createListEntryWidget(x, y, listIndex, isOdd, entry);
            }

            @Override
            public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
                super.drawContents(ctx, mouseX, mouseY, partialTicks);

                for (WidgetConfigOption widget : this.listWidgets) {
                    if (widget instanceof WidgetModeRow row) {
                        row.drawOpenDropdown(ctx, mouseX, mouseY);
                    }
                }
            }
        };
    }
}
