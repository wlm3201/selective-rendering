package com.selectiverendering.util;

import com.selectiverendering.SelectiveRendering;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.server.commands.SetBlockCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 日志入口，可用 JVM 参数 -Dselective_rendering.debug=true 启用 debug 输出。 */
public final class Log {
	private static final Logger LOGGER = LoggerFactory.getLogger(SelectiveRendering.MOD_ID);
	private static final boolean LOG = "true".equalsIgnoreCase(System.getProperty("selective_rendering.debug"));

	public static void say(String message, Object... args) {
		LOGGER.info(message, args);
	}

	public static void error(String message, Object... args) {
		LOGGER.error(message, args);
	}

	public static void debug(String message, Object... args) {
		if (LOG)
			LOGGER.debug(message, args);
	}

	public static void warn(String message, Object... args) {
		LOGGER.warn(message, args);
	}
}
