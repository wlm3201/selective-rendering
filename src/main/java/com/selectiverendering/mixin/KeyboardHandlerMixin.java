package com.selectiverendering.mixin;

import com.selectiverendering.ModKeyBindings;
import com.selectiverendering.config.ModConfigScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The way into the config screen without going through the mod list: the key that was bound to it
 * in the controls screen, V unless the player says otherwise.
 *
 * <p>It is a {@code KeyMapping} like any other, so it is listed with the rest of them and saved
 * with the rest of them, and whatever a key binding mod such as Amecs lets a player bind - a
 * chord, a mouse button - reaches it without this class knowing anything about it. There used to
 * be an X+V chord hard coded alongside, and it went because it was a second way of doing the same
 * thing that could not be rebound and could not be seen anywhere.</p>
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	private static final int ACTION_PRESS = 1;

	@Inject(method = "keyPress", at = @At("HEAD"))
	private void selectiveRendering$keyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (action != ACTION_PRESS) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		// A screen of its own is what a chat box turns letters into, so this only fires in game.
		if (minecraft.screen != null) {
			return;
		}

		if (ModKeyBindings.CONFIG.matches(event)) {
			ModConfigScreen.open(minecraft);
		}
	}
}
