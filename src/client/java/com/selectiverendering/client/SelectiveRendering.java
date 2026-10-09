package com.selectiverendering.client;

import com.selectiverendering.client.config.ModConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;

public class SelectiveRendering implements ClientModInitializer {
    public static final String MOD_ID = "selectiverendering";

    private static final String SODIUM_ID = "sodium";

    private static final String FABRIC_RENDERER_API_ID = "fabric-renderer-indigo";

    private static volatile Wand cachedWand;

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        Log.say(
                "[state] sodium {}, fabric renderer api {}",
                loader.isModLoaded(SODIUM_ID) ? "present" : "absent",
                loader.isModLoaded(FABRIC_RENDERER_API_ID) ? "present" : "absent");

        ModConfig.load();
        SelectiveRenderingManager.load();

        ModConfigScreen.register();
        reportClipHook();
    }

    private static void reportClipHook() {
        boolean installed = false;

        try {
            ClientLevel.class.getDeclaredMethod("clip", ClipContext.class);
            installed = true;
        } catch (NoSuchMethodException e) {
            installed = false;
        }

        Log.say(
                "[state] pass through clip hook {}",
                installed ? "installed" : "MISSING (mixin failed to apply)");
    }

    private record Wand(String source, Item item) {}

    public static boolean isWandHeld() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }

        return isWand(minecraft.player.getMainHandItem())
                || isWand(minecraft.player.getOffhandItem());
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
