package com.selectiverendering;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志门面。
 *
 * <p>默认走 INFO。加 JVM 参数 {@code -Dselective_rendering.log=warn} 可以全部提到 WARN 级别
 * ——调试时很有用，因为很多玩家的日志收集工具默认过滤 INFO。
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
