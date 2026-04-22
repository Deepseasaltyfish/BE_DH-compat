package com.deepseasaltyfish.BeLodCompat.common;

import com.deepseasaltyfish.BeLodCompat.config.ModConfigs;
import com.deepseasaltyfish.BeLodCompat.util.DatabaseManager;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

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

    // 每个数据库文件独立的表创建记录
    private static final ConcurrentHashMap<Path, ConcurrentSkipListSet<String>> createdTablesMap = new ConcurrentHashMap<>();
    // 每个数据库文件独立的状态ID缓存
    private static final ConcurrentHashMap<Path, ConcurrentHashMap<String, Integer>> stateIdCacheMap = new ConcurrentHashMap<>();

    private static String getTableName(String modId) {
        return modId + "_block_data";
    }

    private static ConcurrentSkipListSet<String> getCreatedTables(Path dbFile) {
        return createdTablesMap.computeIfAbsent(dbFile, k -> new ConcurrentSkipListSet<>());
    }

    private static ConcurrentHashMap<String, Integer> getStateIdCache(Path dbFile) {
        return stateIdCacheMap.computeIfAbsent(dbFile, k -> new ConcurrentHashMap<>());
    }

    private static void ensureTableExists(Path dbFile, String modId) {
        if (!DatabaseManager.isReady(dbFile)) return;
        ConcurrentSkipListSet<String> created = getCreatedTables(dbFile);
        if (created.contains(modId)) return;

        String tableName = getTableName(modId);
        String checkSQL = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
        boolean[] exists = {false};
        DatabaseManager.executeQuery(dbFile, checkSQL, rs -> {
            try { if (rs.next()) exists[0] = true; } catch (SQLException e) {}
        }, tableName);
        if (exists[0]) {
            created.add(modId);
            return;
        }

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
        DatabaseManager.executeUpdate(dbFile, sql);
        String idxChunk = "CREATE INDEX IF NOT EXISTS idx_" + tableName + "_chunk ON " + tableName + " (" + COL_CHUNK_X + ", " + COL_CHUNK_Z + ")";
        DatabaseManager.executeUpdate(dbFile, idxChunk);

        exists[0] = false;
        DatabaseManager.executeQuery(dbFile, checkSQL, rs -> {
            try { if (rs.next()) exists[0] = true; } catch (SQLException e) {}
        }, tableName);
        if (exists[0]) {
            created.add(modId);
            LOGGER.debug("Table {} created for {}", tableName, dbFile);
        } else {
            LOGGER.error("Failed to create table {} for {}", tableName, dbFile);
        }
    }

    public static void initTable(Path dbFile) {
        if (!DatabaseManager.isReady(dbFile)) return;
        String dictSQL = "CREATE TABLE IF NOT EXISTS " + TABLE_DICT + " (id INTEGER PRIMARY KEY, state TEXT UNIQUE NOT NULL)";
        DatabaseManager.executeUpdate(dbFile, dictSQL);
        LOGGER.info("Table {} initialized for {}", TABLE_DICT, dbFile);
    }

    private static int getOrCreateStateId(Path dbFile, String blockStateStr) {
        if (blockStateStr == null) return 0;
        ConcurrentHashMap<String, Integer> cache = getStateIdCache(dbFile);
        Integer cached = cache.get(blockStateStr);
        if (cached != null) return cached;

        String selectSQL = "SELECT id FROM " + TABLE_DICT + " WHERE state = ?";
        int[] id = {-1};
        DatabaseManager.executeQuery(dbFile, selectSQL, rs -> {
            try { if (rs.next()) id[0] = rs.getInt("id"); } catch (SQLException e) {}
        }, blockStateStr);
        if (id[0] != -1) {
            cache.put(blockStateStr, id[0]);
            return id[0];
        }

        DatabaseManager.executeUpdate(dbFile, "INSERT OR IGNORE INTO " + TABLE_DICT + " (state) VALUES (?)", blockStateStr);
        DatabaseManager.executeQuery(dbFile, selectSQL, rs -> {
            try { if (rs.next()) id[0] = rs.getInt("id"); } catch (SQLException e) {}
        }, blockStateStr);
        if (id[0] != -1) {
            cache.put(blockStateStr, id[0]);
            return id[0];
        }
        LOGGER.error("Failed to get or create state id for {} in {}", blockStateStr, dbFile);
        return 0;
    }

    public static void putBlockData(Path dbFile, String modId, BlockPos pos, String blockStateStr, int color, int version) {
        if (!DatabaseManager.isReady(dbFile)) return;
        ensureTableExists(dbFile, modId);
        int stateId = getOrCreateStateId(dbFile, blockStateStr);
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        String tableName = getTableName(modId);
        String sql = "INSERT OR REPLACE INTO " + tableName + " (" +
                COL_X + ", " + COL_Y + ", " + COL_Z + ", " +
                COL_CHUNK_X + ", " + COL_CHUNK_Z + ", " +
                COL_STATE_ID + ", " + COL_COLOR + ", " + COL_VERSION +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        if (ModConfigs.useAsyncDbWrite) {
            DatabaseManager.executeUpdateAsync(dbFile, sql, pos.getX(), pos.getY(), pos.getZ(), chunkX, chunkZ, stateId, color, version);
        } else {
            DatabaseManager.executeUpdate(dbFile, sql, pos.getX(), pos.getY(), pos.getZ(), chunkX, chunkZ, stateId, color, version);
        }
        LOGGER.debug("putBlockData: mod={}, pos={}, color=0x{}", modId, pos, Integer.toHexString(color));
    }

    public static void loadChunk(Path dbFile, ChunkPos chunkPos, String modId, Map<BlockPos, ? super BlockDataEntry> targetMap) {
        if (!DatabaseManager.isReady(dbFile)) return;
        ensureTableExists(dbFile, modId);
        String tableName = getTableName(modId);
        String sql = "SELECT d." + COL_X + ", d." + COL_Y + ", d." + COL_Z + ", s.state, d." + COL_COLOR + ", d." + COL_VERSION +
                " FROM " + tableName + " d JOIN " + TABLE_DICT + " s ON d." + COL_STATE_ID + " = s.id " +
                "WHERE d." + COL_CHUNK_X + " = ? AND d." + COL_CHUNK_Z + " = ?";
        DatabaseManager.executeQuery(dbFile, sql, (rs) -> {
            try {
                while (rs.next()) {
                    int x = rs.getInt(COL_X);
                    int y = rs.getInt(COL_Y);
                    int z = rs.getInt(COL_Z);
                    String blockStateStr = rs.getString("state");
                    int color = rs.getInt(COL_COLOR);
                    int version = rs.getInt(COL_VERSION);
                    targetMap.put(new BlockPos(x, y, z), new BlockDataEntry(blockStateStr, color, version));
                }
            } catch (SQLException e) {
                LOGGER.error("Error processing result set", e);
            }
        }, chunkPos.x, chunkPos.z);
    }

    public static Integer getColor(Path dbFile, String modId, BlockPos pos) {
        if (!DatabaseManager.isReady(dbFile)) return null;
        ensureTableExists(dbFile, modId);
        String tableName = getTableName(modId);
        String sql = "SELECT " + COL_COLOR + " FROM " + tableName + " WHERE " +
                COL_X + " = ? AND " + COL_Y + " = ? AND " + COL_Z + " = ?";
        final int[] result = {-1};
        DatabaseManager.executeQuery(dbFile, sql, (rs) -> {
            try { if (rs.next()) result[0] = rs.getInt(COL_COLOR); } catch (SQLException e) {}
        }, pos.getX(), pos.getY(), pos.getZ());
        return result[0] == -1 ? null : result[0];
    }

    public static void removeBlockData(Path dbFile, String modId, BlockPos pos) {
        if (!DatabaseManager.isReady(dbFile)) return;
        ensureTableExists(dbFile, modId);
        String tableName = getTableName(modId);
        String sql = "DELETE FROM " + tableName + " WHERE " + COL_X + " = ? AND " + COL_Y + " = ? AND " + COL_Z + " = ?";
        DatabaseManager.executeUpdate(dbFile, sql, pos.getX(), pos.getY(), pos.getZ());
    }

    public static void removeChunk(Path dbFile, String modId, ChunkPos chunkPos) {
        if (!DatabaseManager.isReady(dbFile)) return;
        ensureTableExists(dbFile, modId);
        String tableName = getTableName(modId);
        String sql = "DELETE FROM " + tableName + " WHERE " + COL_CHUNK_X + " = ? AND " + COL_CHUNK_Z + " = ?";
        DatabaseManager.executeUpdate(dbFile, sql, chunkPos.x, chunkPos.z);
    }

    public static void vacuum(Path dbFile) {
        if (!DatabaseManager.isReady(dbFile)) return;
        DatabaseManager.executeUpdate(dbFile, "VACUUM");
        LOGGER.info("Database vacuumed for {}", dbFile);
    }

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