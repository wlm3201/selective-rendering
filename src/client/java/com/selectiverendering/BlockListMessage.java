package com.selectiverendering;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 屏幕下方居中的一条临时提示（"+ minecraft:stone" / "- #minecraft:planks"），
 * 2 秒后淡出。用于反馈"刚加了/删了一条规则或区域"。
 *
 * <p>它是<b>全静态</b>的单例样式：同一时刻只有一条消息，新的会盖掉旧的。
 * 只在客户端（渲染）线程读写，因此没有做同步。
 *
 * <p>渲染入口在 {@code GuiMixin}（注入 {@code Hud.extractRenderState}）。
 */
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

	private BlockListMessage() {
	}

	private static long now() {
		return System.currentTimeMillis();
	}

	public static void show(String rule, boolean added) {
		sign = added ? "+ " : "- ";
		BlockListMessage.rule = rule;
		signColor = added ? ADDED : REMOVED;
		shownAt = now();
	}

	/**
	 * 画出当前消息（如果有且未过期）。
	 *
	 * <p>{@code shownAt} 初值为 0，此时 {@code age} 会是一个巨大的正数，
	 * 直接被 {@code age >= LIFETIME_MS} 判掉（早年的实现用 {@code Long.MIN_VALUE}，
	 * 靠 long 溢出成负数来判，属于"碰巧对"的写法）。
	 */
	public static void render(GuiGraphicsExtractor graphics, Font font) {
		long age = now() - shownAt;
		if (rule.isEmpty() || age < 0 || age >= LIFETIME_MS) {
			return;
		}

		int alpha = age <= LIFETIME_MS - FADE_MS ? 255 : (int) ((LIFETIME_MS - age) * 255 / FADE_MS);
		int width = font.width(sign) + font.width(rule);
		int x = (Minecraft.getInstance().getWindow().getGuiScaledWidth() - width) / 2;
		int y = Minecraft.getInstance().getWindow().getGuiScaledHeight() - BOTTOM_MARGIN - font.lineHeight;

		graphics.text(font, sign, x, y, (alpha << 24) | signColor, true);
		graphics.text(font, rule, x + font.width(sign), y, (alpha << 24) | TEXT, true);
	}
}
