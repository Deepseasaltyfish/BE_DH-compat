package com.deepseasaltyfish.BeLodCompat.config;

import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

import java.io.IOException;
import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simple configuration file manager with hot-reload support.
 * Reads/writes .cfg files in the config directory and notifies listeners via Forge event bus.
 */
public class CfgConfig {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(CfgConfig.class);
    private static final Map<Path, Class<?>> registeredConfigs = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService watcherService = Executors.newSingleThreadScheduledExecutor();
    private static final Map<Path, Long> lastModifiedMap = new ConcurrentHashMap<>();
    private static volatile boolean watcherStarted = false;

    /**
     * Annotation for configuration field comments.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Comment {
        String value();
    }

    /**
     * Event fired when a configuration file is reloaded.
     * Posted to the Forge event bus.
     */
    public static class ReloadEvent extends Event {
        private final Path configFile;
        public ReloadEvent(Path configFile) { this.configFile = configFile; }
        public Path getConfigFile() { return configFile; }
    }

    /**
     * Registers a configuration class and starts file watching (asynchronous polling).
     *
     * @param configClass the class containing static fields annotated with @Comment
     * @param fileName    the file name (relative to the config directory)
     */
    public static void register(Class<?> configClass, String fileName) {
        Path path = Paths.get("config", fileName);
        registeredConfigs.put(path, configClass);
        loadConfig(configClass, path);
        if (!watcherStarted) {
            startWatcher();
            watcherStarted = true;
        }
    }

    /**
     * Loads or reloads the configuration from the given file.
     *
     * @param configClass the configuration class
     * @param path        the file path
     */
    private static void loadConfig(Class<?> configClass, Path path) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        Map<String, String> comments = new LinkedHashMap<>();
        collectFields(configClass, null, defaults, comments);

        Properties props = new Properties();
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path)) {
                props.load(reader);
            } catch (IOException e) {
                LOGGER.error("Failed to load config " + path, e);
            }
        }

        // Merge and write back if necessary
        boolean needSave = false;
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String key = entry.getKey();
            Object defaultValue = entry.getValue();
            if (props.containsKey(key)) {
                String str = props.getProperty(key);
                setFieldValue(configClass, key, str, defaultValue);
            } else {
                props.setProperty(key, defaultValue.toString());
                needSave = true;
            }
        }

        if (needSave) {
            saveConfig(path, defaults, comments, props);
        }

        // Fire reload event after loading
        MinecraftForge.EVENT_BUS.post(new ReloadEvent(path));
    }

    /**
     * Saves the configuration to a file with comments.
     *
     * @param path      the file path
     * @param defaults  map of keys to default values
     * @param comments  map of keys to comment strings
     * @param props     properties containing current values
     */
    private static void saveConfig(Path path, Map<String, Object> defaults, Map<String, String> comments, Properties props) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            String key = entry.getKey();
            String comment = comments.get(key);
            if (comment != null && !comment.isEmpty()) {
                lines.add("# " + comment);
            }
            lines.add(key + "=" + props.getProperty(key));
            lines.add("");
        }
        try {
            Files.write(path, lines);
        } catch (IOException e) {
            LOGGER.error("Failed to write config " + path, e);
        }
    }

    /**
     * Recursively collects static fields from a class and its inner classes.
     *
     * @param clazz    the class to scan
     * @param prefix   dot-separated prefix for nested keys
     * @param defaults map to store default values
     * @param comments map to store comments
     */
    private static void collectFields(Class<?> clazz, String prefix, Map<String, Object> defaults, Map<String, String> comments) {
        for (Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (Modifier.isFinal(field.getModifiers())) continue;
            field.setAccessible(true);

            String key = (prefix == null ? "" : prefix + ".") + field.getName();

            // Handle inner classes as groups
            if (Modifier.isStatic(field.getType().getModifiers()) && field.getType().getDeclaredFields().length > 0) {
                collectFields(field.getType(), key, defaults, comments);
                continue;
            }

            try {
                Object value = field.get(null);
                defaults.put(key, value);
                Comment comment = field.getAnnotation(Comment.class);
                if (comment != null) comments.put(key, comment.value());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Sets a static field value from a string.
     *
     * @param clazz        the class containing the field
     * @param fullKey      full dot-separated key
     * @param strValue     string value from config file
     * @param defaultValue default value to determine type
     */
    private static void setFieldValue(Class<?> clazz, String fullKey, String strValue, Object defaultValue) {
        String[] parts = fullKey.split("\\.");
        Class<?> targetClass = clazz;
        String fieldName = parts[parts.length - 1];
        for (int i = 0; i < parts.length - 1; i++) {
            try {
                Field innerField = targetClass.getDeclaredField(parts[i]);
                innerField.setAccessible(true);
                targetClass = innerField.getType();
            } catch (NoSuchFieldException e) {
                throw new RuntimeException("Invalid config key: " + fullKey, e);
            }
        }
        try {
            Field field = targetClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object parsed = parseValue(strValue, defaultValue);
            field.set(null, parsed);
        } catch (Exception e) {
            LOGGER.error("Failed to set field {} with value '{}'", fullKey, strValue, e);
        }
    }

    /**
     * Parses a string into the appropriate type based on the default value.
     *
     * @param str          the string to parse
     * @param defaultValue the default value (used for type inference)
     * @return parsed object
     * @throws UnsupportedOperationException if the type is not supported
     */
    private static Object parseValue(String str, Object defaultValue) {
        if (defaultValue instanceof Boolean) return Boolean.parseBoolean(str);
        if (defaultValue instanceof Integer) return Integer.parseInt(str);
        if (defaultValue instanceof String) return str;
        throw new UnsupportedOperationException("Unsupported type: " + defaultValue.getClass());
    }

    /**
     * Starts the file watcher that periodically checks for modifications.
     */
    private static void startWatcher() {
        watcherService.scheduleWithFixedDelay(() -> {
            for (Map.Entry<Path, Class<?>> entry : registeredConfigs.entrySet()) {
                Path path = entry.getKey();
                try {
                    if (!Files.exists(path)) continue;
                    long lastModified = Files.getLastModifiedTime(path).toMillis();
                    Long old = lastModifiedMap.put(path, lastModified);
                    if (old != null && lastModified != old) {
                        loadConfig(entry.getValue(), path);
                        LOGGER.info("Config reloaded: {}", path);
                    }
                } catch (Exception e) {
                    LOGGER.error("Error watching config file " + path, e);
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
}