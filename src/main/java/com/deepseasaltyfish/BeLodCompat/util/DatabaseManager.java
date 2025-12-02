package com.deepseasaltyfish.BeLodCompat.util;

import com.deepseasaltyfish.BeLodCompat.config.ModConfigs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

public class DatabaseManager {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(DatabaseManager.class);
    private static final String DEFAULT_DRIVER_CLASS = "dh_sqlite.JDBC";
    private static final String DEFAULT_JDBC_PREFIX = "jdbc:dh_sqlite:";
    private static final boolean DEFAULT_ENABLE_WAL = true;
    private static final boolean DEFAULT_ENABLE_SYNC_NORMAL = true;

    private static String driverClass = DEFAULT_DRIVER_CLASS;
    private static String jdbcPrefix = DEFAULT_JDBC_PREFIX;
    private static boolean enableWal = DEFAULT_ENABLE_WAL;
    private static boolean enableSyncNormal = DEFAULT_ENABLE_SYNC_NORMAL;

    private static boolean enabled = false;
    private static final ConcurrentHashMap<Path, Connection> connections = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Path, ScheduledExecutorService> perDbExecutors = new ConcurrentHashMap<>();

    public static void init() {
        if (!ModConfigs.enableDatabase) {
            LOGGER.info("Database caching disabled by config");
            return;
        }
        try {
            Class.forName(driverClass);
            try (Connection testConn = DriverManager.getConnection(jdbcPrefix + ":memory:")) {
                testConn.close();
            }
            enabled = true;
            LOGGER.info("SQLite JDBC available, database caching enabled");
        } catch (ClassNotFoundException e) {
            LOGGER.error("SQLite JDBC driver not found, database caching disabled", e);
            enabled = false;
        } catch (SQLException e) {
            LOGGER.error("SQLite JDBC test connection failed, database caching disabled", e);
            enabled = false;
        }
    }

    private static Connection getConnection(Path dbFile) throws SQLException {
        return connections.computeIfAbsent(dbFile, path -> {
            try {
                Files.createDirectories(path.getParent());
                Connection conn = DriverManager.getConnection(jdbcPrefix + path.toString());
                conn.setAutoCommit(true);
                try (Statement stmt = conn.createStatement()) {
                    if (enableWal) stmt.execute("PRAGMA journal_mode=WAL");
                    if (enableSyncNormal) stmt.execute("PRAGMA synchronous=NORMAL");
                    stmt.execute("PRAGMA cache_size=-20000");
                } catch (SQLException e) {
                    LOGGER.warn("Failed to set PRAGMA optimizations for {}", path, e);
                }
                LOGGER.info("Opened database at {}", path);
                return conn;
            } catch (SQLException | IOException e) {
                LOGGER.error("Failed to open database at {}", path, e);
                throw new RuntimeException(e);
            }
        });
    }

    public static void open(Path dbFile) {
        if (!enabled) {
            LOGGER.warn("Database not enabled, cannot open {}", dbFile);
            return;
        }
        try {
            getConnection(dbFile);
        } catch (Exception e) {
            LOGGER.error("Failed to open database at {}", dbFile, e);
        }
    }

    public static void close(Path dbFile) {
        Connection conn = connections.remove(dbFile);
        if (conn != null) {
            try { conn.close(); } catch (SQLException e) { LOGGER.error("Error closing connection", e); }
        }
        ScheduledExecutorService exec = perDbExecutors.remove(dbFile);
        if (exec != null) exec.shutdownNow();
    }

    public static void closeAll() {
        connections.forEach((path, conn) -> {
            try { conn.close(); } catch (SQLException e) {}
        });
        connections.clear();
        perDbExecutors.values().forEach(ExecutorService::shutdownNow);
        perDbExecutors.clear();
    }

    public static void executeUpdate(Path dbFile, String sql, Object... parameters) {
        if (!enabled) return;
        try (PreparedStatement pstmt = getConnection(dbFile).prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) pstmt.setObject(i + 1, parameters[i]);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to execute update: {}", sql, e);
        }
    }

    public static void executeUpdateAsync(Path dbFile, String sql, Object... parameters) {
        if (!enabled) return;
        ScheduledExecutorService executor = perDbExecutors.computeIfAbsent(dbFile, k -> Executors.newSingleThreadScheduledExecutor());
        executor.submit(() -> executeUpdate(dbFile, sql, parameters));
    }

    public static void executeQuery(Path dbFile, String sql, Consumer<ResultSet> resultHandler, Object... parameters) {
        if (!enabled) return;
        try (PreparedStatement pstmt = getConnection(dbFile).prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) pstmt.setObject(i + 1, parameters[i]);
            try (ResultSet rs = pstmt.executeQuery()) {
                resultHandler.accept(rs);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to execute query: {}", sql, e);
        }
    }

    public static boolean isReady(Path dbFile) {
        return enabled && connections.containsKey(dbFile);
    }
}