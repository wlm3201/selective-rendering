package com.selectiverendering.config;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screens.Screen;

import com.selectiverendering.Preset;
import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;

/**
 * 配置界面本体（MaLiLib 的 {@code GuiConfigsBase}）。
 *
 * <p>两件事：
 * <ol>
 *   <li>{@link #getConfigs} 按顺序列出所有配置项（外加预设区的标题和每一行预设）；</li>
 *   <li>{@link #createListWidget} 覆写列表控件，为三种特殊行换上自定义 widget：
 *       预设标题行 / 预设行 / 模式行。模式行需要单独处理，
 *       因为它用下拉框代替了 MaLiLib 默认的"点击循环"控件。</li>
 * </ol>
 *
 * <p>预设行之所以要自定义：MaLiLib 没有"一行里放多个按钮 + 可编辑名字"的控件，
 * 所以自己拼了一个（{@link WidgetPresetRow}）。
 */
public final class ModConfigGui extends GuiConfigsBase {
	private static final int LIST_X = 10;
	private static final int LIST_Y = 50;

	private static final String HEADING = ModConfigs.text("presets");

	public ModConfigGui(Screen parent) {
		super(LIST_X, LIST_Y, SelectiveRendering.MOD_ID, parent, SelectiveRendering.MOD_ID + ".config.title");

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
		return new WidgetListConfigOptions(listX, listY,
				this.getBrowserWidth(), this.getBrowserHeight(), this.getConfigWidth(), 0.f, this.useKeybindSearch(), this) {
			@Override
			protected WidgetConfigOption createListEntryWidget(int x, int y, int listIndex, boolean isOdd, ConfigOptionWrapper entry) {
				IConfigBase config = entry.getConfig();

				if (entry.getType() == ConfigOptionWrapper.Type.LABEL && HEADING.equals(entry.getLabel())) {
					return new WidgetPresetHeader(x, y, this.browserEntryWidth, this.browserEntryHeight,
							this.maxLabelWidth, this.configWidth, entry, listIndex, this.parent, this);
				}

				if (config instanceof PresetRowConfig) {
					return new WidgetPresetRow(x, y, this.browserEntryWidth, this.browserEntryHeight,
							this.maxLabelWidth, this.configWidth, entry, listIndex, this.parent, this);
				}

				if (config instanceof ModConfigs.ModeConfig) {
					return new WidgetModeRow(x, y, this.browserEntryWidth, this.browserEntryHeight,
							this.maxLabelWidth, this.configWidth, entry, listIndex, this.parent, this);
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
