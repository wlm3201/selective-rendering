package com.selectiverendering;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Says what the last click did to the block list: the rule that went on, or came off, with a plus
 * or a minus in front of it.
 *
 * <p>Drawn by this mod rather than handed to the vanilla toast stack. Toasts are a setting, and one
 * plenty of players have turned off - Sodium hides them in some of its own presets - so a toast is
 * a message that part of the players it is meant for never see. This one goes straight onto the
 * GUI, which nothing can switch off from under it.</p>
 *
 * <p>It sits low on the screen rather than out in the middle of it. The middle is where the
 * player is looking when the wand is clicked, and a line there is in the way of the block the
 * click was about; down here it is a note at the edge of the view, above the hotbar and the
 * health, hunger and experience bars and clear of the line the held item's name gets.</p>
 *
 * <p>It is text on the world with nothing behind it, the way the HUD in the corner is: the shadow
 * under each line is what keeps it readable, and a box in the middle of the screen would cover
 * more of the world than the answer is worth. It fades on its own, and a second click replaces it
 * instead of queueing behind it. What the wand just did is the only thing worth saying, and a
 * stack of them would have the player reading the first one while the last is the one that is
 * true.</p>
 */
public final class BlockListMessage {
	private static final long LIFETIME_MS = 2000L;
	private static final long FADE_MS = 500L;

	/**
	 * How far up from the bottom of the screen. Above the hotbar and the bars that sit on top of
	 * it, and clear of the line the held item's name gets.
	 */
	private static final int BOTTOM_MARGIN = 74;

	private static final int ADDED = 0x0080FF80;
	private static final int REMOVED = 0x00FF8080;
	private static final int TEXT = 0x00E0E0E0;

	private static String sign = "";
	private static String rule = "";
	private static int signColor = TEXT;

	/** When it was shown, or {@link Long#MIN_VALUE} for never. */
	private static long shownAt = Long.MIN_VALUE;

	private BlockListMessage() {
	}

	/**
	 * Wall clock rather than the game's own: this is drawn from the render thread and only ever
	 * asked how long ago a click was, which is not something the level's tick count knows.
	 */
	private static long now() {
		return System.currentTimeMillis();
	}

	/**
	 * Puts the message up for a rule that was just added to, or dropped from, the list. One that is
	 * still up is replaced rather than queued behind.
	 */
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

		// Full until the last moment, then out over the fade rather than blinking off.
		int alpha = age <= LIFETIME_MS - FADE_MS ? 255 : (int) ((LIFETIME_MS - age) * 255 / FADE_MS);
		int width = font.width(sign) + font.width(rule);
		int x = (Minecraft.getInstance().getWindow().getGuiScaledWidth() - width) / 2;
		int y = Minecraft.getInstance().getWindow().getGuiScaledHeight() - BOTTOM_MARGIN - font.lineHeight;

		graphics.text(font, sign, x, y, (alpha << 24) | signColor, true);
		graphics.text(font, rule, x + font.width(sign), y, (alpha << 24) | TEXT, true);
	}
}
