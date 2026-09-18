package com.selectiverendering.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Puts a config button in the Mod Menu list.
 *
 * <p>This class is only ever loaded through the {@code modmenu} entrypoint, so Mod Menu being
 * absent is not a problem. YACL is a different matter, because it is what actually builds the
 * screen, so it gets a check before anything else happens in the method. Naming a YACL type in a
 * class that gets loaded would take the whole mod down when YACL is missing, which is the one
 * thing this indirection exists to avoid.</p>
 *
 * <p>Returning null leaves Mod Menu to decide there is no screen to show, which is the honest
 * answer when the library is not installed.</p>
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		if (!FabricLoader.getInstance().isModLoaded(ModConfigScreen.YACL_ID)) {
			return null;
		}

		return ModConfigScreen::create;
	}
}
