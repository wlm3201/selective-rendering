package com.selectiverendering.util;

import com.selectiverendering.SelectiveRendering;
import net.fabricmc.loader.api.FabricLoader;

import java.util.function.BooleanSupplier;

public class ModCompat {

	private static final String SODIUM_ID = "sodium";
	private static final String FABRIC_RENDERER_API_ID = "fabric-renderer-indigo";
	private static final String ORBITCAM_ID = "orbitcam";

	public static void registerMouseClaim() {
		if (!FabricLoader.getInstance().isModLoaded(ORBITCAM_ID)) {
			return;
		}

		try {
			Class.forName("com.example.orbitcam.client.OrbitCam")
				.getMethod("setMouseClaimProvider", BooleanSupplier.class)
				.invoke(null, (BooleanSupplier) SelectiveRendering::isWandHeld);
			Log.debug("[state] Orbitcam mouse claim registered");
		} catch (Throwable t) {
			Log.debug("[state] Orbitcam mouse claim NOT registered ({})", t);
		}
	}
}
