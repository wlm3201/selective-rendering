package com.selectiverendering;

import com.selectiverendering.config.ModConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>手持魔杖时左下角的 HUD</b>：状态行（模式 / 选区 / 记录中）+ 操作提示行，
 * 外加 {@link BlockListMessage} 的浮动提示。
 *
 * <h2>为什么单独成一个类</h2>
 * <p>这段逻辑在三个 MC 版本上逐字相同，但承载它的 mixin 目标类不一样：
 * <pre>
 *   26.1.2 → net.minecraft.client.gui.Gui
 *   26.2+  → net.minecraft.client.gui.Hud   （26.1.2 里根本没有 Hud）
 * </pre>
 * mixin 一次只能挂一个类，所以注入点必须按版本各留一份；
 * 但正文抽到这里之后，三个 {@code GuiMixin} 都只剩几行转发。
 *
 * <h2>提示行的显示策略</h2>
 * <p>随"当前按住哪个修饰键"变化：按住选区键只显示选区相关的 3 条，
 * 按住方块键只显示方块相关的 3 条，都不按才显示全部——
 * 避免一次刷 10 行把屏幕糊住。
 */
public final class WandHud {
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFD0D0D0;
	private static final int HINT = 0xFFB0B0B0;
	private static final int MARGIN = 6;
	private static final int LINE_GAP = 2;

	private WandHud() {
	}

	/**
	 * 画 HUD。由各版本的 {@code GuiMixin} 在 {@code extractRenderState} 末尾调用。
	 *
	 * @param graphics 26.1+ 的 HUD 改成"抽取渲染状态"而不是直接画，所以拿到的是这个而不是 PoseStack
	 */
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

		// 反转是"把命中的那批反过来淡化"，必须在模式名旁边标出来，否则只看模式名会完全误解
		Component shown = mode.displayName();
		if (SelectiveRenderingManager.isInvert()) {
			shown = shown.copy().append(" · ").append(Component.translatable(prefix + "invert"));
		}

		lines.add(Component.translatable(prefix + "mode", shown));

		lines.add(regionLine(prefix));

		if (BlockChangeRecorder.isRecording()) {
			lines.add(Component.translatable(prefix + "recording", String.valueOf(BlockChangeRecorder.count())));
		}
		else {
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
