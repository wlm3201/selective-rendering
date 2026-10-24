package com.selectiverendering.config.region;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.selectiverendering.config.entrypoint.ModConfigs;
import com.selectiverendering.region.Region;

import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.IConfigValue;
import fi.dy.masa.malilib.config.options.ConfigBase;

public final class RegionRowConfig extends ConfigBase<RegionRowConfig> implements IConfigValue {
	private final Region region;
	private final int index;

	public RegionRowConfig(Region region, int index) {
		super(ConfigType.STRING, "region");

		this.region = region;
		this.index = index;
	}

	public int index() {
		return this.index;
	}

	@Override
	public String getConfigGuiDisplayName() {
		return "";
	}

	public Region region() {
		return this.region;
	}

	public String title() {
		return "#" + this.index + "  " + this.region.areas().size() + " " + ModConfigs.text("region_areas") + "  "
			+ RegionConfigs.format(this.region.bounds().min()) + " → " + RegionConfigs.format(this.region.bounds().max());
	}

	@Override
	public String getStringValue() {
		return this.region.toSource();
	}

	@Override
	public String getDefaultStringValue() {
		return this.region.toSource();
	}

	@Override
	public void setValueFromString(String value) {
	}

	@Override
	public boolean isModified() {
		return false;
	}

	@Override
	public boolean isModified(String newValue) {
		return false;
	}

	@Override
	public void resetToDefault() {
	}

	@Override
	public void setValueFromJsonElement(JsonElement element) {
	}

	@Override
	public JsonElement getAsJsonElement() {
		return new JsonPrimitive(this.region.toSource());
	}
}
