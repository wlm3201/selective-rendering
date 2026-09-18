package com.selectiverendering.mixin;

import com.selectiverendering.BlockListMessage;
import com.selectiverendering.ModKeyBindings;
import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the state, and below it what the wand will currently do, in the bottom left corner.
 *
 * <p>The second half follows Lucidity: the wand does different things depending on what is held
 * down, so rather than listing every combination at once the hints change to match what is pressed.
 * Holding a wand key shows what that key does and nothing else, which is both of the things it
 * does - a click and the wheel - and with nothing held it shows every combination at once. It is
 * the same information, just never more of it than is relevant to the hand that is on the keyboard
 * right now.</p>
 */
@Mixin(Gui.class)
public class GuiMixin {
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFAAAAAA;
	private static final int HINT = 0xFFE0E0E0;
	private static final int MARGIN = 6;
	private static final int LINE_GAP = 2;
	private static final int HINT_GAP = 6;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void selectiveRendering$renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		if (font == null) {
			return;
		}

		// Drawn whatever is in the hand. The click that raised it is over by then, and a message
		// that disappeared because the wand was put away would say the opposite of what happened.
		BlockListMessage.render(graphics, font);

		if (!SelectiveRendering.isWandHeld()) {
			return;
		}

		List<Component> status = statusLines();
		List<Component> hints = hintLines();

		int lineCount = status.size() + hints.size();
		int contentHeight = lineCount * font.lineHeight + (lineCount - 1) * LINE_GAP + HINT_GAP;

		// No panel behind the text. The shadow under each line already makes it readable against the
		// world, and a box in the corner hides whatever the player was trying to look at.
		int x = MARGIN;
		int textY = graphics.guiHeight() - contentHeight - MARGIN;

		for (int index = 0; index < status.size(); index++) {
			graphics.text(font, status.get(index), x, textY, index == 0 ? TEXT : TEXT_DIM, true);
			textY += font.lineHeight + LINE_GAP;
		}

		textY += HINT_GAP;
		for (Component line : hints) {
			graphics.text(font, line, x, textY, HINT, true);
			textY += font.lineHeight + LINE_GAP;
		}
	}

	private static List<Component> statusLines() {
		String prefix = SelectiveRendering.MOD_ID + ".hud.";
		SelectiveRenderingManager.Mode mode = SelectiveRenderingManager.getMode();

		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(prefix + "mode", mode.displayName()));

		// The corners being clicked right now. Only the region modes read them, so the line goes
		// with them; the regions that were added are left to the config screen, which lists them
		// all, rather than to a count that says nothing about where any of them is.
		if (mode.usesRegion()) {
			lines.add(regionLine(prefix));
		}

		return lines;
	}

	/**
	 * The two corners, or a note that they are missing. One that has not been placed yet reads as a
	 * question mark instead of dropping the line, so it stays obvious what is left to click.
	 */
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

		// The wand keys are whatever the player bound them to, so the hints ask the game what to
		// call them rather than guessing.
		Component region = ModKeyBindings.REGION.getTranslatedKeyMessage();
		Component blocks = ModKeyBindings.BLOCKS.getTranslatedKeyMessage();
		Component config = ModKeyBindings.CONFIG.getTranslatedKeyMessage();

		// Holding one of the two shows what that one does and nothing else: the corners are already
		// known by then, and the hand is on the key that changes what a click means.
		// The two wheel hints name control and alt outright rather than asking what the wand keys
		// are bound to: the wheel is on control and alt whatever those are bound to, so naming a
		// key the player moved somewhere else would say the wrong thing here.
		if (ModKeyBindings.REGION.isDown()) {
			lines.add(Component.translatable(prefix + "region", region));
			lines.add(Component.translatable(prefix + "region_single", region));
			lines.add(Component.translatable(prefix + "mode"));
			return lines;
		}

		if (ModKeyBindings.BLOCKS.isDown()) {
			lines.add(Component.translatable(prefix + "block_id", blocks));
			lines.add(Component.translatable(prefix + "block_state", blocks));
			lines.add(Component.translatable(prefix + "move_corner"));
			return lines;
		}

		lines.add(Component.translatable(prefix + "corner1"));
		lines.add(Component.translatable(prefix + "corner2"));
		lines.add(Component.translatable(prefix + "region", region));
		lines.add(Component.translatable(prefix + "region_single", region));
		lines.add(Component.translatable(prefix + "block_id", blocks));
		lines.add(Component.translatable(prefix + "block_state", blocks));
		lines.add(Component.translatable(prefix + "mode"));
		lines.add(Component.translatable(prefix + "move_corner"));
		lines.add(Component.translatable(prefix + "config", config));
		return lines;
	}
}
