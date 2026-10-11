package com.selectiverendering.config.region;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.selectiverendering.region.Area;

import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.IConfigValue;
import fi.dy.masa.malilib.config.options.ConfigBase;

public final class RegionAreaConfig extends ConfigBase<RegionAreaConfig> implements IConfigValue {
	private final RegionHandle handle;
	private final Area area;
	private final int index;

	RegionAreaConfig(RegionHandle handle, Area area, int index) {
		super(ConfigType.STRING, "region_area");

		this.handle = handle;
		this.area = area;
		this.index = index;
	}

	@Override
	public String getConfigGuiDisplayName() {
		return "";
	}

	RegionHandle handle() {
		return this.handle;
	}

	Area area() {
		return this.area;
	}

	int index() {
		return this.index;
	}

	@Override
	public String getStringValue() {
		return "";
	}

	@Override
	public String getDefaultStringValue() {
		return "";
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
		return new JsonPrimitive("");
	}
}
