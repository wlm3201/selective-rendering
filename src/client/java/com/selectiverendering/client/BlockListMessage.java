package com.selectiverendering.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class BlockListMessage {
    private static final long LIFETIME_MS = 2000L;
    private static final long FADE_MS = 500L;

    private static final int BOTTOM_MARGIN = 74;

    private static final int ADDED = 0x0080FF80;
    private static final int REMOVED = 0x00FF8080;
    private static final int TEXT = 0x00FFFFFF;

    private static String sign = "";
    private static String rule = "";
    private static int signColor = TEXT;

    private static long shownAt = 0L;

    private BlockListMessage() {}

    private static long now() {
        return System.currentTimeMillis();
    }

    public static void show(String rule, boolean added) {
        sign = added ? "+ " : "- ";
        BlockListMessage.rule = rule;
        signColor = added ? ADDED : REMOVED;
        shownAt = now();
    }

    public static void render(GuiGraphicsExtractor graphics, Font font) {
        long age = now() - shownAt;
        if (rule.isEmpty() || age < 0 || age >= LIFETIME_MS) {
            return;
        }

        int alpha =
                age <= LIFETIME_MS - FADE_MS ? 255 : (int) ((LIFETIME_MS - age) * 255 / FADE_MS);
        int width = font.width(sign) + font.width(rule);
        int x = (Minecraft.getInstance().getWindow().getGuiScaledWidth() - width) / 2;
        int y =
                Minecraft.getInstance().getWindow().getGuiScaledHeight()
                        - BOTTOM_MARGIN
                        - font.lineHeight;

        graphics.text(font, sign, x, y, (alpha << 24) | signColor, true);
        graphics.text(font, rule, x + font.width(sign), y, (alpha << 24) | TEXT, true);
    }
}
