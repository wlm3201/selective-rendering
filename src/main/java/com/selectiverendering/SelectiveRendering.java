package com.selectiverendering;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Entry point. Everything this mod does is client side, so there is no main entry point.
 */
public class SelectiveRendering implements ClientModInitializer {
	public static final String MOD_ID = "selective_rendering";

	/**
	 * The other renderer the mod has a set of hooks for. Which of the two sets runs is decided by
	 * whether this is installed, so it is the first thing worth knowing about a session log.
	 */
	private static final String SODIUM_ID = "sodium";

	@Override
	public void onInitializeClient() {
		Log.say("[state] sodium {}", FabricLoader.getInstance().isModLoaded(SODIUM_ID) ? "present" : "absent");

		// First, because the keys have to be registered before the options are built: that is when
		// the controls screen takes the list it shows.
		ModKeyBindings.register();
		ModConfig.load();
		SelectiveRenderingManager.load();
	}

	/**
	 * Whether the wand is in either hand. Which item counts as one is the player's to say in the
	 * config screen; a breeze rod is only where it starts, which is the item Lucidity uses.
	 */
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
		Identifier id = Identifier.tryParse(source);
		if (id == null) {
			return Items.BREEZE_ROD;
		}

		return BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BREEZE_ROD);
	}
}
