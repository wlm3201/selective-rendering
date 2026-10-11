package com.selectiverendering.config.region;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.IConfigValue;
import fi.dy.masa.malilib.config.options.ConfigBase;

/** 「投影位移」一行，显示当前位移和重置按钮。 */
public final class RegionOffsetConfig extends ConfigBase<RegionOffsetConfig> implements IConfigValue {
	private final RegionHandle handle;

	RegionOffsetConfig(RegionHandle handle) {
		super(ConfigType.STRING, "region_offset");

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
