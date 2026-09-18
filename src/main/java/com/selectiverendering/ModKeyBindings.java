package com.selectiverendering;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * The mod's keys, registered with the game rather than with the config screen.
 *
 * <p>They live in the vanilla controls screen under their own category, so they are bound the same
 * way every other key is, saved in {@code options.txt} with the rest, and shown with the name of the
 * key the player actually bound. A dropdown of key names in the config was the wrong place for
 * them: it could not show what was already taken, and it had no idea what the player's keyboard
 * calls the key it was writing down.</p>
 *
 * <p>{@link KeyMapping} registers itself with the game as it is constructed, which is all that is
 * needed for it to be pressed and saved. The controls screen reads the array on
 * {@link net.minecraft.client.Options}, so {@code OptionsMixin} appends these to it.</p>
 */
public final class ModKeyBindings {
	/**
	 * The heading these sit under in the controls screen. Its name is read from
	 * {@code key.category.selective_rendering.main}, which is what {@code Category.label()} asks
	 * for.
	 */
	public static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SelectiveRendering.MOD_ID, "main"));

	/**
	 * Held while clicking to work on the regions: the left button adds the two corners as a region,
	 * or drops the region the crosshair is on when there is one. Left control by default.
	 *
	 * <p>The wheel is not this key's to give away. It switches the rendering mode while control is
	 * held and stays on control whatever this was rebound to: which two keys the wheel sits on is
	 * what makes the wand feel like Litematica's, and that is worth more than following a binding.
	 * The hints name control for the same reason rather than asking what this is bound to.</p>
	 */
	public static final KeyMapping REGION = register("region", GLFW.GLFW_KEY_LEFT_CONTROL);

	/**
	 * Held while clicking to work on the block list: the left button adds or drops the id of the
	 * block that was clicked, the right one the same block with all of its states written out. Left
	 * alt by default.
	 *
	 * <p>What the wheel does while alt is held - moving the corner that is picked - is bound the
	 * same way the mode above is: to alt itself, and not to this key. See {@link #REGION}.</p>
	 */
	public static final KeyMapping BLOCKS = register("blocks", GLFW.GLFW_KEY_LEFT_ALT);

	/**
	 * Opens the config screen. V by default, and any combination of keys the player likes works
	 * without the mod having to know about it: Amecs and the other key binding mods add multi key
	 * chords to every mapping the game has, this one included.
	 */
	public static final KeyMapping CONFIG = register("config", GLFW.GLFW_KEY_V);

	private static final KeyMapping[] ALL = {REGION, BLOCKS, CONFIG};

	private ModKeyBindings() {
	}

	private static KeyMapping register(String name, int code) {
		return new KeyMapping(
			SelectiveRendering.MOD_ID + ".key." + name,
			InputConstants.Type.KEYSYM,
			code,
			CATEGORY
		);
	}

	/**
	 * A copy, since the caller appends this to an array the game owns.
	 */
	public static KeyMapping[] all() {
		return ALL.clone();
	}

	/**
	 * Registers them, which is to say it runs the class initialiser: a {@link KeyMapping} puts
	 * itself on the game's own maps as it is built, so holding them here is all that is needed for
	 * them to be pressed and saved. Must run before the options are built, since that is when the
	 * controls screen takes its list.
	 */
	public static void register() {
	}
}
