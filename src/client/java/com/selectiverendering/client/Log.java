package com.selectiverendering.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Log {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectiveRendering.MOD_ID);
    private static final boolean WARN =
            "warn".equalsIgnoreCase(System.getProperty("selectiverendering.log"));

    private Log() {}

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
