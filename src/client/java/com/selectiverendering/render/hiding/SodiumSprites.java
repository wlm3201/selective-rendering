package com.selectiverendering.render.hiding;

import net.caffeinemc.mods.sodium.api.texture.SpriteUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

// Sodium 只让可见区段里用到的动画贴图推进；没装 Sodium 时原版每帧推进所有动画，不需要标记。
public final class SodiumSprites {
	private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("sodium");

	private SodiumSprites() {
	}

	public static void markActive(TextureAtlasSprite sprite) {
		if (PRESENT) {
			SpriteUtil.INSTANCE.markSpriteActive(sprite);
		}
	}
}
