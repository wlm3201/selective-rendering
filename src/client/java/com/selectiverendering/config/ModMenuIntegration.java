package com.selectiverendering.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu 入口（在 {@code fabric.mod.json} 里以 {@code modmenu} entrypoint 注册）。
 *
 * <p>只在 ModMenu 存在时才会被加载；ModMenu 是 compileOnly 依赖，
 * 缺席时这个类不会被实例化，所以这里可以放心直接引用它的 API。
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ModConfigScreen::create;
	}
}
