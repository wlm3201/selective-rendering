package com.selectiverendering.config.region;

import java.util.List;

import com.selectiverendering.config.entrypoint.ModConfigGui;
import com.selectiverendering.config.entrypoint.ModConfigs;
import com.selectiverendering.config.suggest.SuggestingStringList;
import com.selectiverendering.config.suggest.WidgetSuggestingList;
import net.minecraft.client.gui.screens.Screen;

import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.region.Region;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.render.GuiContext;

/** 单个选区的设置界面 */
public final class GuiRegionConfigs extends GuiConfigsBase {
	private final RegionHandle handle;

	public GuiRegionConfigs(Screen listParent, Region region, int index) {
		super(10, 50, SelectiveRendering.MOD_ID, null, SelectiveRendering.MOD_ID + ".config.region_title", String.valueOf(index));

		this.handle = new RegionHandle(region);
		this.setParent(new ModConfigGui(listParent));
	}

	@Override
	public void initGui() {
		if (!this.handle.exists()) {
			this.closeGui(true);
			return;
		}

		super.initGui();

		String back = ModConfigs.text("region_back");
		int width = this.getStringWidth(back) + 12;

		this.addButton(new ButtonGeneric(10, 26, width, 20, back), (button, mouseButton) -> this.closeGui(true));
	}

	@Override
	protected void buildConfigSwitcher() {
		// 这是二级界面，MaLiLib 查不到它对应的登记项，会用 splitCamelCase(modId) 重新登记一条
		// （"Selective _rendering"），把主界面的正式名字覆盖掉。这里只刷新登记，不建切换框。
		ModConfigs.registerConfigScreen();
	}

	@Override
	public List<ConfigOptionWrapper> getConfigs() {
		return RegionConfigs.rows(this.handle);
	}

	@Override
	protected WidgetListConfigOptions createListWidget(int listX, int listY) {
		return new WidgetListConfigOptions(listX, listY,
			this.getBrowserWidth(), this.getBrowserHeight(), this.getConfigWidth(), 0.f, this.useKeybindSearch(), this) {
			@Override
			protected WidgetConfigOption createListEntryWidget(int x, int y, int listIndex, boolean isOdd, ConfigOptionWrapper entry) {
				if (entry.getConfig() instanceof SuggestingStringList) {
					return new WidgetSuggestingList(x, y, this.browserEntryWidth, this.browserEntryHeight,
						this.maxLabelWidth, this.configWidth, entry, listIndex, this.parent, this);
				}

				if (entry.getConfig() instanceof RegionAreaConfig) {
					return new WidgetRegionArea(x, y, this.browserEntryWidth, this.browserEntryHeight,
						this.maxLabelWidth, this.configWidth, entry, listIndex, this.parent, this);
				}

				if (entry.getConfig() instanceof RegionOffsetConfig) {
					return new WidgetRegionOffset(x, y, this.browserEntryWidth, this.browserEntryHeight,
						this.maxLabelWidth, this.configWidth, entry, listIndex, this.parent, this);
				}

				if (entry.getConfig() instanceof RegionMergeConfig) {
					return new WidgetRegionMerge(x, y, this.browserEntryWidth, this.browserEntryHeight,
						this.maxLabelWidth, this.configWidth, entry, listIndex, this.parent, this);
				}

				if (entry.getConfig() instanceof RegionCornerConfig) {
					return new WidgetRegionCorner(x, y, this.browserEntryWidth, this.browserEntryHeight,
						this.maxLabelWidth, this.configWidth, entry, listIndex, this.parent, this);
				}

				if (entry.getConfig() instanceof RegionTargetsConfig) {
					return new WidgetRegionTargets(x, y, this.browserEntryWidth, this.browserEntryHeight,
						this.maxLabelWidth, this.configWidth, entry, listIndex, this.parent, this);
				}

				return super.createListEntryWidget(x, y, listIndex, isOdd, entry);
			}

			@Override
			public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
				super.drawContents(ctx, mouseX, mouseY, partialTicks);

				for (WidgetConfigOption widget : this.listWidgets) {
					if (widget instanceof WidgetRegionMerge row) {
						row.drawOpenDropdown(ctx, mouseX, mouseY);
					}
				}
			}
		};
	}
}
