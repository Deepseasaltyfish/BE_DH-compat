package com.deepseasaltyfish.BeDhCompat.util;

import com.deepseasaltyfish.BeDhCompat.config.ModConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugLogger {
    private final Logger logger;

    private DebugLogger(Class<?> clazz) {
        logger = LoggerFactory.getLogger(clazz);
    }

    public static DebugLogger getLogger(Class<?> clazz) {
        return new DebugLogger(clazz);
    }

    public void debug(String msg) {
        if (ModConfigs.isDebugLogging()) {
            logger.debug(msg);
        }
    }

    public void debug(String format, Object arg) {
        if (ModConfigs.isDebugLogging()) {
            logger.debug(format, arg);
        }
    }

    public void debug(String format, Object... arguments) {
        if (ModConfigs.isDebugLogging()) {
            logger.debug(format, arguments);
        }
    }

    public void debug(String msg, Throwable t) {
        if (ModConfigs.isDebugLogging()) {
            logger.debug(msg, t);
        }
    }

    public void info(String msg) { logger.info(msg); }
    public void info(String format, Object arg) { logger.info(format, arg); }
    public void info(String format, Object... arguments) { logger.info(format, arguments); }
    public void info(String msg, Throwable t) { logger.info(msg, t); }

    public void warn(String msg) { logger.warn(msg); }
    public void warn(String format, Object arg) { logger.warn(format, arg); }
    public void warn(String format, Object... arguments) { logger.warn(format, arguments); }
    public void warn(String msg, Throwable t) { logger.warn(msg, t); }

    public void error(String msg) { logger.error(msg); }
    public void error(String format, Object arg) { logger.error(format, arg); }
    public void error(String format, Object... arguments) { logger.error(format, arguments); }
    public void error(String msg, Throwable t) { logger.error(msg, t); }
}