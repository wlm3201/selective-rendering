package com.selectiverendering.render.information;

import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.SelectiveRenderingManager.Mode;
import com.selectiverendering.config.entrypoint.ModConfigScreen;
import com.selectiverendering.config.entrypoint.ModHotkeys;
import com.selectiverendering.interaction.OperationActions;
import com.selectiverendering.matchingrule.BlockListMessage;
import com.selectiverendering.recording.BlockChangeRecorder;
import com.selectiverendering.region.Region;
import com.selectiverendering.region.RegionEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** 显示魔杖状态、渲染模式、带图标的操作提示及规则修改消息，整体贴在左下角。 */
public final class WandHudRenderer {
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFD0D0D0;
	private static final int HINT = 0xFFC8C8C8;
	private static final int MARGIN = 6;
	private static final int LINE_GAP = 1;

	private static final int SOURCE_SIZE = 32;
	private static final int MODE_ICON = 24;
	private static final int ICON = 12;
	private static final int ICON_GAP = 2;
	private static final int ROW = ICON;
	private static final int TARGET_ICON = 18;
	private static final int SELECTED_ROW = TARGET_ICON;

	private static final Identifier HOTKEY = texture("hotkey");
	private static final Identifier MOUSE = texture("mouse");
	private static final Identifier MOUSE_LEFT = texture("mouse_left");
	private static final Identifier MOUSE_MIDDLE = texture("mouse_middle");
	private static final Identifier MOUSE_RIGHT = texture("mouse_right");

	private WandHudRenderer() {
	}

	private record Hint(List<Identifier> icons, Component text) {
		static Hint of(Component text, Identifier... icons) {
			return new Hint(List.of(icons), text);
		}
	}

	public static void render(GuiGraphicsExtractor graphics) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;

		BlockListMessage.render(graphics, font);

		if (!SelectiveRendering.isWandHeld()) {
			return;
		}

		Region selected = SelectiveRenderingManager.selectedRegion();
		List<Component> status = statusLines();
		List<Hint> hints = hints();

		int height = MODE_ICON + LINE_GAP
			+ (selected == null ? 0 : SELECTED_ROW + LINE_GAP)
			+ status.size() * (font.lineHeight + LINE_GAP)
			+ hints.size() * ROW;

		int x = MARGIN;
		int y = graphics.guiHeight() - height - MARGIN;

		y = modeRow(graphics, font, x, y);

		if (selected != null) {
			y = selectedRow(graphics, font, x, y, selected);
		}

		for (int index = 0; index < status.size(); index++) {
			graphics.text(font, status.get(index), x, y, index == 0 ? TEXT : TEXT_DIM, true);
			y += font.lineHeight + LINE_GAP;
		}

		for (Hint hint : hints) {
			int textX = x;

			for (Identifier icon : hint.icons()) {
				icon(graphics, icon, textX, y, ICON);
				textX += ICON + ICON_GAP;
			}

			graphics.text(font, hint.text(), textX + 2, y + (ICON - font.lineHeight) / 2 + 1, HINT, true);
			y += ROW;
		}
	}

	private static int modeRow(GuiGraphicsExtractor graphics, Font font, int x, int y) {
		Mode mode = SelectiveRenderingManager.getMode();

		Component name = mode.displayName();
		if (SelectiveRenderingManager.isInvert()) {
			name = name.copy().append(" · ").append(Component.translatable(SelectiveRendering.MOD_ID + ".hud.invert"));
		}

		icon(graphics, texture("rendering_mode/" + modeIcon(mode)), x, y, MODE_ICON);
		graphics.text(font, name, x + MODE_ICON + 4, y + (MODE_ICON - font.lineHeight) / 2, TEXT, true);

		return y + MODE_ICON + LINE_GAP;
	}

	private static String modeIcon(Mode mode) {
		return switch (mode) {
			case OFF -> "off";
			case REGION_INSIDE -> "inside_all";
			case REGION_INSIDE_LISTED -> "inside_specific";
			case REGION_INSIDE_UNLISTED -> "inside_non_specific";
			case REGION_OUTSIDE -> "outside_all";
			case REGION_OUTSIDE_LISTED -> "outside_specific";
			case REGION_OUTSIDE_UNLISTED -> "outside_non_specific";
			case BLACKLIST -> "any_specific";
			case WHITELIST -> "any_non_specific";
		};
	}

	private static int selectedRow(GuiGraphicsExtractor graphics, Font font, int x, int y, Region region) {
		int swatchTop = y + (SELECTED_ROW - 10) / 2;
		graphics.fill(x, swatchTop, x + 10, swatchTop + 10, 0xFF000000 | region.color());

		Component label = Component.translatable(
			SelectiveRendering.MOD_ID + ".hud.region_selected",
			String.valueOf(SelectiveRenderingManager.regions().indexOf(region) + 1),
			String.valueOf(region.areas().size()));

		int textX = x + 14;
		graphics.text(font, label, textX, y + (SELECTED_ROW - font.lineHeight) / 2, TEXT, true);

		int iconX = textX + font.width(label) + 6;
		int iconY = y + (SELECTED_ROW - TARGET_ICON) / 2;

		for (String target : List.of("blocks", "entities", "particles")) {
			boolean enabled = switch (target) {
				case "blocks" -> region.options().blocks();
				case "entities" -> region.options().entities();
				default -> region.options().particles();
			};

			if (enabled) {
				icon(graphics, texture("rendering_mode/wand_mode_" + target), iconX, iconY, TARGET_ICON);
				iconX += TARGET_ICON + ICON_GAP;
			}
		}

		return y + SELECTED_ROW + LINE_GAP;
	}

	private static List<Component> statusLines() {
		String prefix = SelectiveRendering.MOD_ID + ".hud.";

		List<Component> lines = new ArrayList<>();
		lines.add(regionLine(prefix));

		if (BlockChangeRecorder.isRecording()) {
			lines.add(Component.translatable(prefix + "recording", String.valueOf(BlockChangeRecorder.count())));
		} else {
			lines.add(Component.empty());
		}

		return lines;
	}

	private static Component regionLine(String prefix) {
		BlockPos first = RegionEditor.getCorner(0);
		BlockPos second = RegionEditor.getCorner(1);

		if (first == null && second == null) {
			return Component.translatable(prefix + "region.empty");
		}

		return Component.translatable(prefix + "region", corner(first), corner(second));
	}

	private static String corner(BlockPos pos) {
		return pos == null ? "?" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}

	/** Alt + 滚轮作用在唯一选中的目标上，提示按当前选中的东西换一行。 */
	private static String moveHint() {
		if (SelectiveRenderingManager.selectedRegion() != null) {
			return "move_projection";
		}

		return RegionEditor.isSelectionSelected() ? "move_selection" : "move_corner";
	}

	private static List<Hint> hints() {
		String prefix = SelectiveRendering.MOD_ID + ".hint.";
		List<Hint> hints = new ArrayList<>();

		String region = ModHotkeys.regionKeyName();
		String operation = ModHotkeys.operationKeyName();
		String config = ModConfigScreen.configKeyName();
		String record = ModConfigScreen.recordKeyName();

		if (ModHotkeys.regionHeld()) {
			hints.add(Hint.of(Component.translatable(prefix + "region"), HOTKEY, MOUSE_LEFT));
			hints.add(Hint.of(Component.translatable(prefix + "clip"), HOTKEY, MOUSE_RIGHT));
			hints.add(Hint.of(Component.translatable(prefix + "mode"), HOTKEY, MOUSE));
			return hints;
		}

		if (ModHotkeys.operationHeld()) {
			String scope = OperationActions.scope(SelectiveRenderingManager.selectedRegion());
			hints.add(Hint.of(Component.translatable(prefix + "operation_left", scope), HOTKEY, MOUSE_LEFT));
			hints.add(Hint.of(Component.translatable(prefix + "operation_right", scope), HOTKEY, MOUSE_RIGHT));
			hints.add(Hint.of(Component.translatable(prefix + moveHint()), HOTKEY, MOUSE));
			return hints;
		}

		hints.add(Hint.of(Component.translatable(prefix + "corner1"), MOUSE_LEFT));
		hints.add(Hint.of(Component.translatable(prefix + "corner2"), MOUSE_RIGHT));
		hints.add(Hint.of(Component.translatable(prefix + "select_corner"), MOUSE_MIDDLE));
		hints.add(Hint.of(Component.translatable(prefix + "region_key", region), HOTKEY));
		hints.add(Hint.of(Component.translatable(prefix + "operation_key", operation), HOTKEY));

		if (record != null) {
			hints.add(Hint.of(Component.translatable(prefix + "record", record), HOTKEY));
		}

		return hints;
	}

	/** 32×32 的贴图缩放到 size 画出。 */
	private static void icon(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int size) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, size, size, SOURCE_SIZE, SOURCE_SIZE, SOURCE_SIZE, SOURCE_SIZE);
	}

	private static Identifier texture(String name) {
		return Identifier.fromNamespaceAndPath(SelectiveRendering.MOD_ID, "textures/gui/" + name + ".png");
	}
}
