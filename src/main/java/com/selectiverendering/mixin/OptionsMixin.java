package com.selectiverendering.mixin;

import com.selectiverendering.ModKeyBindings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Puts the mod's keys on the controls screen.
 *
 * <p>A {@link KeyMapping} adds itself to the game's own maps as it is made, which is enough for it
 * to be pressed and to be saved. The controls screen does not read those maps though, it reads the
 * array on {@link Options}, and that array is a fixed list of the vanilla keys. Nothing but adding
 * to it here will make a mod's key show up, and the field is final, so {@link Mutable} is what lets
 * it be swapped for a longer one.</p>
 */
@Mixin(Options.class)
public class OptionsMixin {
	@Mutable
	@Shadow
	public KeyMapping[] keyMappings;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void selectiveRendering$addKeyBinds(CallbackInfo ci) {
		KeyMapping[] added = ModKeyBindings.all();
		int length = keyMappings.length;

		keyMappings = Arrays.copyOf(keyMappings, length + added.length);
		System.arraycopy(added, 0, keyMappings, length, added.length);
	}
}
