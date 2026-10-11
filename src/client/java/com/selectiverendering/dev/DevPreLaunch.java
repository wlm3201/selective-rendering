package com.selectiverendering.dev;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.spongepowered.asm.mixin.Mixins;

/** 只在开发环境注册开发用的 mixin 配置，发布后的游戏里什么都不做。 */
public class DevPreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			Mixins.addConfiguration("selective-rendering.dev.mixins.json");
		}
	}
}
