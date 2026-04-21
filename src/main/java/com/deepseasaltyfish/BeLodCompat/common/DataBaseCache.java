package com.deepseasaltyfish.BeLodCompat.common;

import com.deepseasaltyfish.BeLodCompat.config.ModConfigs;
import com.deepseasaltyfish.BeLodCompat.util.DatabaseManager;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Handles database caching for block data, including per-mod block_data tables and a shared state dictionary.
 */
public class DataBaseCache {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(DataBaseCache.class);

    private static final String TABLE_DICT = "block_state_dict";
    private static final String COL_X = "x";
    private static final String COL_Y = "y";
    private static final String COL_Z = "z";
    private static final String COL_CHUNK_X = "chunk_x";
    private static final String COL_CHUNK_Z = "chunk_z";
    private static final String COL_STATE_ID = "state_id";
    private static final String COL_COLOR = "color";
    private static final String COL_VERSION = "version";

    public static final int CURRENT_VERSION = 1;

    // Cache of tables that have already been created (to avoid repeated CREATE TABLE)
    private static final ConcurrentSkipListSet<String> createdTables = new ConcurrentSkipListSet<>();

    /**
     * Returns the table name for a given mod ID.
     *
     * @param modId the mod ID
     * @return the table name
     */
    private static String getTableName(String modId) {
        return modId + "_block_data";
    }

    /**
     * Ensures that the block_data table for the given mod exists.
     * If the table does not exist, it is created.
     *
     * @param modId the mod ID
     */
    private static void ensureTableExists(String modId) {
        if (!DatabaseManager.isReady()) return;
        if (createdTables.contains(modId)) return;

        String tableName = getTableName(modId);
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                COL_X + " INT NOT NULL, " +
                COL_Y + " INT NOT NULL, " +
                COL_Z + " INT NOT NULL, " +
                COL_CHUNK_X + " INT NOT NULL, " +
                COL_CHUNK_Z + " INT NOT NULL, " +
                COL_STATE_ID + " INT NOT NULL, " +
                COL_COLOR + " INT NOT NULL, " +
                COL_VERSION + " INT DEFAULT " + CURRENT_VERSION + ", " +
                "PRIMARY KEY (" + COL_X + ", " + COL_Y + ", " + COL_Z + "))";
        DatabaseManager.executeUpdate(sql);

        String idxChunk = "CREATE INDEX IF NOT EXISTS idx_" + tableName + "_chunk ON " + tableName + " (" + COL_CHUNK_X + ", " + COL_CHUNK_Z + ")";
        DatabaseManager.executeUpdate(idxChunk);

        createdTables.add(modId);
        LOGGER.debug("Table {} ensured for mod {}", tableName, modId);
    }

    /**
     * Initializes the global state dictionary table.
     * Should be called once when the database is ready.
     */
    public static void initTable() {
        if (!DatabaseManager.isReady()) return;
        String dictSQL = "CREATE TABLE IF NOT EXISTS " + TABLE_DICT + " (id INTEGER PRIMARY KEY, state TEXT UNIQUE NOT NULL)";
        DatabaseManager.executeUpdate(dictSQL);
        LOGGER.info("Table {} initialized", TABLE_DICT);
    }

    // Cache for state string -> ID, bound to current database path
    private static final ConcurrentHashMap<String, Integer> stateIdCache = new ConcurrentHashMap<>();
    private static volatile String currentDbPathForCache = null;

    private static void ensureCacheForCurrentDb() {
        java.nio.file.Path currentPath = DatabaseManager.getCurrentDbPath();
        String pathStr = currentPath != null ? currentPath.toString() : null;
        if (!pathStr.equals(currentDbPathForCache)) {
            stateIdCache.clear();
            currentDbPathForCache = pathStr;
        }
    }

    private static int getOrCreateStateId(String blockStateStr) {
        if (blockStateStr == null) return 0;
        ensureCacheForCurrentDb();

        Integer cached = stateIdCache.get(blockStateStr);
        if (cached != null) return cached;

        // First query
        String selectSQL = "SELECT id FROM " + TABLE_DICT + " WHERE state = ?";
        int[] id = {-1};
        DatabaseManager.executeQuery(selectSQL, (rs) -> {
            try { if (rs.next()) id[0] = rs.getInt("id"); } catch (SQLException e) {}
        }, blockStateStr);
        if (id[0] != -1) {
            stateIdCache.put(blockStateStr, id[0]);
            return id[0];
        }

        // Try insert using INSERT OR IGNORE to avoid concurrency conflicts
        DatabaseManager.executeUpdate("INSERT OR IGNORE INTO " + TABLE_DICT + " (state) VALUES (?)", blockStateStr);
        // Query again
        DatabaseManager.executeQuery(selectSQL, (rs) -> {
            try { if (rs.next()) id[0] = rs.getInt("id"); } catch (SQLException e) {}
        }, blockStateStr);
        if (id[0] != -1) {
            stateIdCache.put(blockStateStr, id[0]);
            return id[0];
        }
        LOGGER.error("Failed to get or create state id for {}", blockStateStr);
        return 0;
    }

    /**
     * Stores block data for a specific mod asynchronously or synchronously based on configuration.
     *
     * @param modId         the mod ID
     * @param pos           the block position
     * @param blockStateStr the block state string
     * @param color         the color (ARGB)
     * @param version       the data version
     */
    public static void putBlockData(String modId, BlockPos pos, String blockStateStr, int color, int version) {
        if (!DatabaseManager.isReady()) return;
        ensureTableExists(modId);

        int stateId = getOrCreateStateId(blockStateStr);
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        String tableName = getTableName(modId);
        String sql = "INSERT OR REPLACE INTO " + tableName + " (" +
                COL_X + ", " + COL_Y + ", " + COL_Z + ", " +
                COL_CHUNK_X + ", " + COL_CHUNK_Z + ", " +
                COL_STATE_ID + ", " + COL_COLOR + ", " + COL_VERSION +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        if (ModConfigs.useAsyncDbWrite) {
            DatabaseManager.executeUpdateAsync(sql, pos.getX(), pos.getY(), pos.getZ(), chunkX, chunkZ, stateId, color, version);
            LOGGER.debug("putBlockData async: mod={}, pos={}, stateId={}, color=0x{}", modId, pos, stateId, Integer.toHexString(color));
        } else {
            DatabaseManager.executeUpdate(sql, pos.getX(), pos.getY(), pos.getZ(), chunkX, chunkZ, stateId, color, version);
            LOGGER.debug("putBlockData sync: mod={}, pos={}, stateId={}, color=0x{}", modId, pos, stateId, Integer.toHexString(color));
        }
    }

    /**
     * Loads all block data for a given chunk and mod into the provided map.
     *
     * @param chunkPos   the chunk position
     * @param modId      the mod ID
     * @param targetMap  the map to populate (must accept BlockPos as key and BlockDataEntry as value)
     */
    public static void loadChunk(ChunkPos chunkPos, String modId, Map<BlockPos, ? super BlockDataEntry> targetMap) {
        if (!DatabaseManager.isReady()) return;
        ensureTableExists(modId);

        String tableName = getTableName(modId);
        String sql = "SELECT d." + COL_X + ", d." + COL_Y + ", d." + COL_Z + ", s.state, d." + COL_COLOR + ", d." + COL_VERSION +
                " FROM " + tableName + " d JOIN " + TABLE_DICT + " s ON d." + COL_STATE_ID + " = s.id " +
                "WHERE d." + COL_CHUNK_X + " = ? AND d." + COL_CHUNK_Z + " = ?";
        DatabaseManager.executeQuery(sql, (rs) -> {
            try {
                int count = 0;
                while (rs.next()) {
                    int x = rs.getInt(COL_X);
                    int y = rs.getInt(COL_Y);
                    int z = rs.getInt(COL_Z);
                    String blockStateStr = rs.getString("state");
                    int color = rs.getInt(COL_COLOR);
                    int version = rs.getInt(COL_VERSION);
                    targetMap.put(new BlockPos(x, y, z), new BlockDataEntry(blockStateStr, color, version));
                    count++;
                }
                LOGGER.debug("loadChunk: chunk={}, mod={}, loaded {} entries", chunkPos, modId, count);
            } catch (SQLException e) {
                LOGGER.error("Error processing result set", e);
            }
        }, chunkPos.x, chunkPos.z);
    }

    /**
     * Retrieves the color of a block from the database.
     *
     * @param modId the mod ID
     * @param pos   the block position
     * @return the color (ARGB), or null if not found
     */
    public static Integer getColor(String modId, BlockPos pos) {
        if (!DatabaseManager.isReady()) return null;
        ensureTableExists(modId);

        String tableName = getTableName(modId);
        String sql = "SELECT " + COL_COLOR + " FROM " + tableName + " WHERE " +
                COL_X + " = ? AND " + COL_Y + " = ? AND " + COL_Z + " = ?";
        final int[] result = {-1};
        DatabaseManager.executeQuery(sql, (rs) -> {
            try { if (rs.next()) result[0] = rs.getInt(COL_COLOR); } catch (SQLException e) {}
        }, pos.getX(), pos.getY(), pos.getZ());
        Integer ret = result[0] == -1 ? null : result[0];
        LOGGER.debug("getColor: mod={}, pos={}, color={}", modId, pos, ret != null ? "0x"+Integer.toHexString(ret) : "null");
        return ret;
    }

    /**
     * Removes block data for a specific position from the database.
     *
     * @param modId the mod ID
     * @param pos   the block position
     */
    public static void removeBlockData(String modId, BlockPos pos) {
        if (!DatabaseManager.isReady()) return;
        ensureTableExists(modId);

        String tableName = getTableName(modId);
        String sql = "DELETE FROM " + tableName + " WHERE " + COL_X + " = ? AND " + COL_Y + " = ? AND " + COL_Z + " = ?";
        DatabaseManager.executeUpdate(sql, pos.getX(), pos.getY(), pos.getZ());
        LOGGER.debug("removeBlockData: mod={}, pos={}", modId, pos);
    }

    /**
     * Removes all block data for a whole chunk from the database.
     *
     * @param modId    the mod ID
     * @param chunkPos the chunk position
     */
    public static void removeChunk(String modId, ChunkPos chunkPos) {
        if (!DatabaseManager.isReady()) return;
        ensureTableExists(modId);

        String tableName = getTableName(modId);
        String sql = "DELETE FROM " + tableName + " WHERE " + COL_CHUNK_X + " = ? AND " + COL_CHUNK_Z + " = ?";
        DatabaseManager.executeUpdate(sql, chunkPos.x, chunkPos.z);
        LOGGER.debug("removeChunk: mod={}, chunk={}", modId, chunkPos);
    }

    /**
     * Performs a VACUUM operation on the database to reclaim unused space.
     * Use with caution.
     */
    public static void vacuum() {
        if (!DatabaseManager.isReady()) return;
        DatabaseManager.executeUpdate("VACUUM");
        LOGGER.info("Database vacuumed");
    }

    /**
     * Simple data container for a block database entry.
     */
    public static class BlockDataEntry {
        public final String blockStateStr;
        public final int color;
        public final int version;

        public BlockDataEntry(String blockStateStr, int color, int version) {
            this.blockStateStr = blockStateStr;
            this.color = color;
            this.version = version;
        }
    }
}