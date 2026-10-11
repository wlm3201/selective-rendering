package com.selectiverendering.config.region;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.IConfigValue;
import fi.dy.masa.malilib.config.options.ConfigBase;

public final class RegionMergeConfig extends ConfigBase<RegionMergeConfig> implements IConfigValue {
	private final RegionHandle handle;

	RegionMergeConfig(RegionHandle handle) {
		super(ConfigType.STRING, "region_merge");

		this.handle = handle;
	}

	@Override
	public String getConfigGuiDisplayName() {
		return "";
	}

	RegionHandle handle() {
		return this.handle;
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
