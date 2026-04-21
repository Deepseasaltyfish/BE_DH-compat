package com.deepseasaltyfish.BeLodCompat.util;

import com.deepseasaltyfish.BeLodCompat.config.ModConfigs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Manages the SQLite database connection and provides low-level execution methods.
 * Borrows DH's SQLite JDBC driver to avoid repackaging.
 */
public class DatabaseManager {
    // Database driver configuration (can be changed to read from config)
    private static final String DEFAULT_DRIVER_CLASS = "dh_sqlite.JDBC"; // borrow DH for sqlite support instead of packing it yet
    private static final String DEFAULT_JDBC_PREFIX = "jdbc:dh_sqlite:"; // borrow DH for sqlite support
    private static final boolean DEFAULT_ENABLE_WAL = true;      // SQLite specific
    private static final boolean DEFAULT_ENABLE_SYNC_NORMAL = true;
    private static String driverClass = DEFAULT_DRIVER_CLASS;
    private static String jdbcPrefix = DEFAULT_JDBC_PREFIX;
    private static boolean enableWal = DEFAULT_ENABLE_WAL;
    private static boolean enableSyncNormal = DEFAULT_ENABLE_SYNC_NORMAL;

    private static final DebugLogger LOGGER = DebugLogger.getLogger(DatabaseManager.class);
    private static boolean enabled = false;
    private static Connection currentConnection = null;
    private static Path currentDbPath = null;
    private static final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();

    /**
     * Initializes the database driver and tests the connection.
     * Should be called once during mod initialization.
     */
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

    /**
     * Opens a database connection to the specified file.
     * If the database is already open and unchanged, does nothing.
     *
     * @param dbFile the database file path
     */
    public static synchronized void open(Path dbFile) {
        if (!enabled) {
            LOGGER.warn("Database not enabled, cannot open {}", dbFile);
            return;
        }
        if (dbFile.equals(currentDbPath) && currentConnection != null && !isConnectionClosed()) {
            LOGGER.debug("Database already open at {}", dbFile);
            return;
        }
        // Only close connection, do not change the enabled flag
        closeConnection();

        try {
            Files.createDirectories(dbFile.getParent());
            currentDbPath = dbFile;
            currentConnection = DriverManager.getConnection(jdbcPrefix + currentDbPath.toString());
            currentConnection.setAutoCommit(true);
            try (Statement stmt = currentConnection.createStatement()) {
                if (enableWal) stmt.execute("PRAGMA journal_mode=WAL");
                if (enableSyncNormal) stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=-20000");
            } catch (SQLException e) {
                LOGGER.warn("Failed to set PRAGMA optimizations", e);
            }
            // Ensure enabled is true (in case it was previously mis-set)
            enabled = true;
            LOGGER.info("Opened database at {}", currentDbPath);
        } catch (SQLException | IOException e) {
            LOGGER.error("Failed to open database at {}", dbFile, e);
            enabled = false;
            currentConnection = null;
            currentDbPath = null;
        }
    }

    private static void closeConnection() {
        if (currentConnection != null) {
            try {
                currentConnection.close();
                LOGGER.info("Database connection closed");
            } catch (SQLException e) {
                LOGGER.error("Error closing database connection", e);
            }
            currentConnection = null;
        }
        currentDbPath = null;
    }

    /**
     * Closes the current database connection.
     */
    public static synchronized void close() {
        closeConnection();
    }

    /**
     * Executes an SQL update synchronously.
     *
     * @param sql        the SQL statement
     * @param parameters the parameters to set
     */
    public static void executeUpdate(String sql, Object... parameters) {
        if (!isReady()) return;
        try (PreparedStatement pstmt = currentConnection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                pstmt.setObject(i + 1, parameters[i]);
            }
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to execute update: {}", sql, e);
        }
    }

    /**
     * Executes an SQL update asynchronously.
     *
     * @param sql        the SQL statement
     * @param parameters the parameters to set
     */
    public static void executeUpdateAsync(String sql, Object... parameters) {
        if (!isReady()) return;
        try {
            writeExecutor.submit(() -> executeUpdate(sql, parameters));
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Async update rejected (database closing), sql: {}", sql);
        }
    }

    /**
     * Executes an SQL query and processes the result set with the given handler.
     *
     * @param sql           the SQL statement
     * @param resultHandler consumer to handle the ResultSet
     * @param parameters    the parameters to set
     */
    public static void executeQuery(String sql, Consumer<ResultSet> resultHandler, Object... parameters) {
        if (!isReady()) return;
        try (PreparedStatement pstmt = currentConnection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                pstmt.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                resultHandler.accept(rs);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to execute query: {}", sql, e);
        }
    }

    /**
     * Creates a table if it does not exist.
     *
     * @param createTableSQL the CREATE TABLE SQL statement
     */
    public static void createTableIfNotExists(String createTableSQL) {
        executeUpdate(createTableSQL);
    }

    /**
     * Checks whether the database is ready for operations.
     *
     * @return true if enabled and connection is open and not closed
     */
    public static boolean isReady() {
        boolean connOk = currentConnection != null && !isConnectionClosed();
        LOGGER.debug("isReady: enabled={}, connOk={}", enabled, connOk);
        return enabled && connOk;
    }

    /**
     * Returns the current database file path.
     *
     * @return the path, or null if no database is open
     */
    public static Path getCurrentDbPath() {
        return currentDbPath;
    }

    private static boolean isConnectionClosed() {
        try {
            return currentConnection == null || currentConnection.isClosed();
        } catch (SQLException e) {
            return true;
        }
    }
}