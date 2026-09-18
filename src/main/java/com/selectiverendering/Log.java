package com.selectiverendering;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Says something in the log.
 *
 * <p>Info, unless the game was started with {@code -Dselective_rendering.log=warn}, which raises
 * these lines to warn instead. That switch is for launchers whose log configuration only shows
 * the game's own logger above warn: under one of those a mod that logs at info is silent, and
 * there is nothing in the log either way to tell that apart from the mod not running.</p>
 */
public final class Log {
	private static final Logger LOGGER = LoggerFactory.getLogger(SelectiveRendering.MOD_ID);
	private static final boolean WARN = "warn".equalsIgnoreCase(System.getProperty("selective_rendering.log"));

	private Log() {
	}

	public static void say(String message, Object... args) {
		if (WARN) {
			LOGGER.warn(message, args);
			return;
		}

		LOGGER.info(message, args);
	}

	public static void error(String message, Object... args) {
		LOGGER.error(message, args);
	}
}
