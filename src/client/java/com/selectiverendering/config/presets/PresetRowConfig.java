package com.selectiverendering.config.presets;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.selectiverendering.SelectiveRenderingManager;

import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.IConfigValue;
import fi.dy.masa.malilib.config.options.ConfigBase;

/** 将预设包装为 MaLiLib 配置行，仅用于界面展示。 */
public final class PresetRowConfig extends ConfigBase<PresetRowConfig> implements IConfigValue {
	private final Preset preset;

	public PresetRowConfig(Preset preset) {
		super(ConfigType.STRING, preset == null ? "" : preset.name());

		this.preset = preset;
	}

	public Preset preset() {
		return this.preset;
	}

	@Override
	public String getConfigGuiDisplayName() {
		return "";
	}

	@Override
	public String getStringValue() {
		return this.preset.name();
	}

	@Override
	public String getDefaultStringValue() {
		return this.preset.name();
	}

	@Override
	public void setValueFromString(String value) {
		this.preset.name = value == null ? "" : value;
		SelectiveRenderingManager.savePresets();
	}

	public String summary() {
		return this.preset.summary();
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
		return new JsonPrimitive(this.preset.name());
	}
}
