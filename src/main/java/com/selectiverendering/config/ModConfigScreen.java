package com.selectiverendering.config;

import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumDropdownControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The config screen, built with YACL.
 *
 * <p>Every mention of YACL lives in this package so the rest of the mod never names it. The mod has
 * to keep working when YACL is not installed, and the only way to get that is to keep the classes
 * that mention it from loading: {@link #open(Minecraft)} checks first and never reaches the screen
 * when YACL is missing.</p>
 *
 * <p>Everything sits in one category. Split into general, items and regions it read as three mods
 * bolted together, when the block list and the regions are the two halves of the one question the
 * mode asks and the rest of it is a number and an item; a single scrolling page puts what changes
 * together with what it changes.</p>
 *
 * <p>The bindings write through the manager rather than straight into the config, because a change
 * to any of these is only worth anything once the chunks are rebuilt, and that is the manager's
 * job. Nothing here reads the config file directly.</p>
 *
 * <p>Each binding is given the value the mod started from rather than the one it is at. YACL calls
 * that the default and both of its reset buttons put it back, so a reset that only reached what the
 * screen was opened with would have reset to the thing the player was trying to get away from.</p>
 *
 * <p>The keys are not here at all. They are bound in the controls screen like every other key, see
 * {@code ModKeyBindings}: a list of key names cannot know what is already taken, cannot show the key
 * the way the game writes it, and is a second place to keep in step with the first.</p>
 */
public final class ModConfigScreen {
	/**
	 * The mod id of the library this screen is built with, which the mod does without when it is
	 * not there.
	 */
	public static final String YACL_ID = "yet_another_config_lib_v3";

	private ModConfigScreen() {
	}

	/**
	 * Opens the screen from anywhere, which is what the config key does. Saying nothing when YACL is
	 * missing is worse than saying why, so the reason goes to chat.
	 */
	public static void open(Minecraft minecraft) {
		if (!FabricLoader.getInstance().isModLoaded(YACL_ID)) {
			if (minecraft.player != null) {
				minecraft.player.sendSystemMessage(
					Component.translatable(SelectiveRendering.MOD_ID + ".message.yacl_missing")
				);
			}

			return;
		}

		minecraft.setScreen(create(minecraft.screen));
	}

	public static Screen create(Screen parent) {
		return YetAnotherConfigLib.createBuilder()
			.title(text("title"))
			.category(ConfigCategory.createBuilder()
				.name(text("title"))
				.option(mode())
				.option(transparency())
				.option(items())
				.option(regions())
				.option(wand())
				.build())
			// The setters have already saved and rebuilt by the time this runs, so it is only here
			// to cover the case where nothing changed but the screen was saved anyway.
			.save(SelectiveRenderingManager::rebuildChunks)
			.build()
			.generateScreen(parent);
	}

	private static Option<SelectiveRenderingManager.Mode> mode() {
		return Option.<SelectiveRenderingManager.Mode>createBuilder()
			.name(text("mode"))
			.description(OptionDescription.of(text("mode.description")))
			.binding(
				SelectiveRenderingManager.DEFAULT_MODE,
				SelectiveRenderingManager::getMode,
				SelectiveRenderingManager::setMode
			)
			// Without this the dropdown would read REGION_INSIDE and friends, since the enum
			// constants are named in Java and translated by the lang file.
			.controller(option -> EnumDropdownControllerBuilder.create(option)
				.formatValue(SelectiveRenderingManager.Mode::displayName))
			.build();
	}

	private static Option<Integer> transparency() {
		return Option.<Integer>createBuilder()
			.name(text("transparency"))
			.description(OptionDescription.of(text("transparency.description")))
			.binding(
				SelectiveRenderingManager.DEFAULT_TRANSPARENCY,
				SelectiveRenderingManager::getTransparency,
				SelectiveRenderingManager::setTransparency
			)
			.controller(option -> IntegerSliderControllerBuilder.create(option)
				.range(0, SelectiveRenderingManager.MAX_TRANSPARENCY)
				.step(SelectiveRenderingManager.TRANSPARENCY_STEP)
				.formatValue(value -> Component.literal(value + "%")))
			.build();
	}

	/**
	 * The item list is an option of its own rather than a category: it is the block half of the same
	 * question the mode asks, so it belongs next to it.
	 */
	private static ListOption<String> items() {
		return ListOption.<String>createBuilder()
			.name(text("items"))
			.description(OptionDescription.of(text("items.description")))
			.binding(
				List.of(),
				SelectiveRenderingManager::ruleSources,
				SelectiveRenderingManager::setRuleSources
			)
			.controller(StringControllerBuilder::create)
			// A new entry starts empty rather than with example text, so there is nothing to
			// delete before typing.
			.initial("")
			.build();
	}

	/**
	 * The regions that were picked up in the world, as the same text the list is saved as, so what
	 * was clicked can be edited here and what is typed here can be seen in the world.
	 */
	private static ListOption<String> regions() {
		return ListOption.<String>createBuilder()
			.name(text("regions"))
			.description(OptionDescription.of(text("regions.description")))
			.binding(
				List.of(),
				SelectiveRenderingManager::regionSources,
				SelectiveRenderingManager::setRegionSources
			)
			.controller(StringControllerBuilder::create)
			.initial("")
			.build();
	}

	private static Option<String> wand() {
		return Option.<String>createBuilder()
			.name(text("wand"))
			.description(OptionDescription.of(text("wand.description")))
			.binding(
				SelectiveRenderingManager.DEFAULT_WAND,
				SelectiveRenderingManager::getWand,
				SelectiveRenderingManager::setWand
			)
			.controller(StringControllerBuilder::create)
			.build();
	}

	private static Component text(String key) {
		return Component.translatable(SelectiveRendering.MOD_ID + ".config." + key);
	}
}
