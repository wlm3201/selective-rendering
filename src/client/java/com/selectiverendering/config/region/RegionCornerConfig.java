package com.selectiverendering.config.region;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.selectiverendering.region.Area;

import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.IConfigValue;
import fi.dy.masa.malilib.config.options.ConfigBase;

/** 某个范围的一个角点输入行；只承载数据，不参与读写和重置。 */
public final class RegionCornerConfig extends ConfigBase<RegionCornerConfig> implements IConfigValue {
	private final RegionHandle handle;
	private final Area area;
	private final int index;
	private final boolean min;

	RegionCornerConfig(RegionHandle handle, Area area, int index, boolean min) {
		super(ConfigType.STRING, min ? "region_min" : "region_max");

		this.handle = handle;
		this.area = area;
		this.index = index;
		this.min = min;
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

	/** 范围在选区里的序号，从 1 开始。 */
	int index() {
		return this.index;
	}

	boolean isMin() {
		return this.min;
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
