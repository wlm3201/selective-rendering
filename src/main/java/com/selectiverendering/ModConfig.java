package com.selectiverendering;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.selectiverendering.SelectiveRenderingManager.Mode;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * State that survives a restart, saved as JSON in the config folder.
 *
 * <p>Gson writes every non static, non {@code transient} field. That gives us the rule for the
 * fields below: {@link #mode}, {@link #transparency} and {@link #rules} are backed by something
 * that actually works, and anything marked {@code transient} is a placeholder for a feature that is
 * not implemented yet, so it is deliberately kept out of the file. Implementing one of those
 * features means dropping the {@code transient} modifier and nothing else.</p>
 */
public final class ModConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger(SelectiveRendering.MOD_ID);
	private static final String FILE_NAME = SelectiveRendering.MOD_ID + ".json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * Written into every file, so a reader can tell what shape it is. Nothing reads it back yet:
	 * the one migration there has been, in {@link #migrate(ModConfig)}, is a rename that is safe to
	 * try on a file of any age, because a list that has already been renamed has nothing left under
	 * its old name to move across. It is here for the migration that will not be safe to try blind.
	 */
	public static final int VERSION = 3;

	private static ModConfig instance = new ModConfig();

	public int version = VERSION;

	public Mode mode = Mode.OFF;
	public int transparency = 50;
	public List<String> rules = new ArrayList<>();

	/**
	 * The two corners of the region being selected, three ints each, null until that corner is set.
	 */
	public int[] regionPos1 = null;
	public int[] regionPos2 = null;

	/**
	 * The regions that were added, six ints each: the low corner then the high one.
	 */
	public List<int[]> regions = new ArrayList<>();

	/**
	 * What {@link #rules} was called before it could hold tags and block states. Read only, so a
	 * file written by an older version keeps its list instead of losing it.
	 */
	public transient List<String> blocks = null;

	/**
	 * The item that counts as the wand, as its id. One the registry does not know falls back to the
	 * default where it is read, so a typo costs the wand rather than the controls.
	 */
	public String wand = "minecraft:breeze_rod";

	// --- Not implemented yet, so not persisted. Drop transient when the feature lands. ---
	public transient Mode entityMode = Mode.OFF;
	public transient int entityTransparency = 50;
	public transient Mode particleMode = Mode.OFF;
	public transient int particleTransparency = 50;

	private ModConfig() {
	}

	public static ModConfig get() {
		return instance;
	}

	/**
	 * Leaves the defaults in place when the file is missing or unreadable.
	 */
	public static void load() {
		Path path = path();
		if (!Files.isRegularFile(path)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
			if (loaded != null) {
				migrate(loaded);
				instance = loaded;
			}
		} catch (Exception e) {
			LOGGER.error("Could not read {}, keeping defaults", FILE_NAME, e);
		}
	}

	/**
	 * Moves the list over from the name it carried in version 1, and clears the old field so it is
	 * never written back out.
	 */
	private static void migrate(ModConfig config) {
		if (config.rules == null) {
			config.rules = config.blocks == null ? new ArrayList<>() : config.blocks;
		}

		config.blocks = null;
		config.version = VERSION;
	}

	public static void save() {
		try {
			Path path = path();
			Files.createDirectories(path.getParent());

			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			LOGGER.error("Could not write {}", FILE_NAME, e);
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}
}
