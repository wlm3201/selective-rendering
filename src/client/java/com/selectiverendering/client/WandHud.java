package com.selectiverendering.client;

import com.selectiverendering.client.config.ModConfigScreen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class WandHud {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFD0D0D0;
    private static final int HINT = 0xFFB0B0B0;
    private static final int MARGIN = 6;
    private static final int LINE_GAP = 2;

    private WandHud() {}

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        if (font == null) {
            return;
        }

        BlockListMessage.render(graphics, font);

        if (!SelectiveRendering.isWandHeld()) {
            return;
        }

        List<Component> status = statusLines();
        List<Component> hints = hintLines();

        int lineCount = status.size() + hints.size();
        int contentHeight = lineCount * font.lineHeight + (lineCount - 1) * LINE_GAP;

        int x = MARGIN;
        int textY = graphics.guiHeight() - contentHeight - MARGIN;

        for (int index = 0; index < status.size(); index++) {
            graphics.text(font, status.get(index), x, textY, index == 0 ? TEXT : TEXT_DIM, true);
            textY += font.lineHeight + LINE_GAP;
        }

        for (Component line : hints) {
            graphics.text(font, line, x, textY, HINT, true);
            textY += font.lineHeight + LINE_GAP;
        }
    }

    private static List<Component> statusLines() {
        String prefix = SelectiveRendering.MOD_ID + ".hud.";
        SelectiveRenderingManager.Mode mode = SelectiveRenderingManager.getMode();

        List<Component> lines = new ArrayList<>();

        Component shown = mode.displayName();
        if (SelectiveRenderingManager.isInvert()) {
            shown = shown.copy().append(" · ").append(Component.translatable(prefix + "invert"));
        }

        lines.add(Component.translatable(prefix + "mode", shown));

        lines.add(regionLine(prefix));

        if (BlockChangeRecorder.isRecording()) {
            lines.add(
                    Component.translatable(
                            prefix + "recording", String.valueOf(BlockChangeRecorder.count())));
        } else {
            lines.add(Component.empty());
        }

        return lines;
    }

    private static Component regionLine(String prefix) {
        BlockPos first = SelectiveRenderingManager.getCorner(0);
        BlockPos second = SelectiveRenderingManager.getCorner(1);

        if (first == null && second == null) {
            return Component.translatable(prefix + "region.empty");
        }

        return Component.translatable(prefix + "region", corner(first), corner(second));
    }

    private static String corner(BlockPos pos) {
        return pos == null ? "?" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static List<Component> hintLines() {
        String prefix = SelectiveRendering.MOD_ID + ".hint.";
        List<Component> lines = new ArrayList<>();

        String region = ModHotkeys.regionKeyName();
        String blocks = ModHotkeys.blocksKeyName();

        String config = ModConfigScreen.configKeyName();

        String record = ModConfigScreen.recordKeyName();

        if (ModHotkeys.regionHeld()) {
            lines.add(Component.translatable(prefix + "region", region));
            lines.add(Component.translatable(prefix + "clip", region));
            lines.add(Component.translatable(prefix + "mode"));
            return lines;
        }

        if (ModHotkeys.blocksHeld()) {
            lines.add(Component.translatable(prefix + "block_id", blocks));
            lines.add(Component.translatable(prefix + "block_state", blocks));
            lines.add(Component.translatable(prefix + "select_corner"));
            lines.add(Component.translatable(prefix + "move_corner"));
            return lines;
        }

        lines.add(Component.translatable(prefix + "corner1"));
        lines.add(Component.translatable(prefix + "corner2"));
        lines.add(Component.translatable(prefix + "select_corner"));
        lines.add(Component.translatable(prefix + "region", region));
        lines.add(Component.translatable(prefix + "clip", region));
        lines.add(Component.translatable(prefix + "block_id", blocks));
        lines.add(Component.translatable(prefix + "block_state", blocks));
        lines.add(Component.translatable(prefix + "mode"));
        lines.add(Component.translatable(prefix + "move_corner"));

        if (record != null) {
            lines.add(Component.translatable(prefix + "record", record));
        }

        if (config != null) {
            lines.add(Component.translatable(prefix + "config", config));
        }

        return lines;
    }
}
