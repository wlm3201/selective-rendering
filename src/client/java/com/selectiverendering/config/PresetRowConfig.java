package com.selectiverendering.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.selectiverendering.Preset;
import com.selectiverendering.SelectiveRenderingManager;

import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.IConfigValue;
import fi.dy.masa.malilib.config.options.ConfigBase;

/**
 * 一个"假的"配置项：把一行 {@link Preset} 伪装成 MaLiLib 的配置项，
 * 这样它才能被塞进 {@code GuiConfigsBase} 的配置列表里显示出来。
 *
 * <p>它并不真的参与配置的读/写/重置（{@code isModified} 恒为 false，
 * {@code resetToDefault} 是空的），只是借壳显示；
 * 真正的预设增删改名在 {@link ModPresets} 和 {@link WidgetPresetRow} 里。
 */
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
