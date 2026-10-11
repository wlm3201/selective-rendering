package com.selectiverendering;

import com.selectiverendering.config.entrypoint.ModConfig;
import com.selectiverendering.config.entrypoint.ModConfigScreen;
import com.selectiverendering.util.ModCompat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SelectiveRendering implements ClientModInitializer {
	public static final String MOD_ID = "selective_rendering";

	private static volatile Wand cachedWand;

	@Override
	public void onInitializeClient() {
		FabricLoader loader = FabricLoader.getInstance();
		ModConfig.load();
		SelectiveRenderingManager.load();

		ModConfigScreen.register();
		ModCompat.registerMouseClaim();
	}

	private record Wand(String source, Item item) {
	}

	public static boolean isWandHeld() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return false;
		}

		return isWand(minecraft.player.getMainHandItem()) || isWand(minecraft.player.getOffhandItem());
	}

	private static boolean isWand(ItemStack stack) {
		Item item = resolveWand();
		return item != null && stack.is(item);
	}

	private static Item resolveWand() {
		String source = SelectiveRenderingManager.getWand();

		Wand cached = cachedWand;
		if (cached != null && cached.source.equals(source)) {
			return cached.item;
		}

		Identifier id = Identifier.tryParse(source);
		Item resolved = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);

		cachedWand = new Wand(source, resolved == null ? Items.BREEZE_ROD : resolved);
		return cachedWand.item;
	}
}
