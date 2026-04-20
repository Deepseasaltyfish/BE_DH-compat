package com.deepseasaltyfish.BeLodCompat.common.DataBase;

import com.deepseasaltyfish.BeLodCompat.dataBase.DatabaseManager;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负责处理 block_data 表的持久化操作。
 * 使用 DatabaseManager 提供底层连接和 SQL 执行。
 * 每个维度/存档的数据库文件应在调用前通过 DatabaseManager.open() 打开。
 */
public class DataBaseCache {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(DataBaseCache.class);

    // 表名和字段常量
    private static final String TABLE_NAME = "block_data";
    private static final String COL_X = "x";
    private static final String COL_Y = "y";
    private static final String COL_Z = "z";
    private static final String COL_CHUNK_X = "chunk_x";
    private static final String COL_CHUNK_Z = "chunk_z";
    private static final String COL_MOD_ID = "mod_id";
    private static final String COL_BLOCK_STATE = "block_state";
    private static final String COL_COLOR = "color";
    private static final String COL_VERSION = "version";
    private static final String COL_EXTRA = "extra";

    // 当前数据格式版本号（可递增，用于未来迁移）
    public static final int CURRENT_VERSION = 1;

    /**
     * 初始化表结构（如果不存在）。
     * 应在数据库打开后调用一次。
     */
    public static void initTable() {
        if (!DatabaseManager.isReady()) {
            LOGGER.info("DataBase not ready, will not init");
            return;
        }

        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                COL_X + " INT NOT NULL, " +
                COL_Y + " INT NOT NULL, " +
                COL_Z + " INT NOT NULL, " +
                COL_CHUNK_X + " INT NOT NULL, " +
                COL_CHUNK_Z + " INT NOT NULL, " +
                COL_MOD_ID + " TEXT NOT NULL, " +
                COL_BLOCK_STATE + " TEXT NOT NULL, " +
                COL_COLOR + " INT NOT NULL, " +
                COL_VERSION + " INT DEFAULT " + CURRENT_VERSION + ", " +
                COL_EXTRA + " TEXT, " +
                "PRIMARY KEY (" + COL_X + ", " + COL_Y + ", " + COL_Z + "))";
        DatabaseManager.executeUpdate(createTableSQL);

        // 创建索引（如果不存在）
        String idxChunk = "CREATE INDEX IF NOT EXISTS idx_" + TABLE_NAME + "_chunk ON " + TABLE_NAME + " (" + COL_CHUNK_X + ", " + COL_CHUNK_Z + ")";
        String idxMod = "CREATE INDEX IF NOT EXISTS idx_" + TABLE_NAME + "_mod ON " + TABLE_NAME + " (" + COL_MOD_ID + ")";
        DatabaseManager.executeUpdate(idxChunk);
        DatabaseManager.executeUpdate(idxMod);

        LOGGER.info("Table {} initialized", TABLE_NAME);
    }

    /**
     * 异步插入或替换一个方块数据。
     * @param modId 模组标识（如 "lt", "ir"）
     * @param pos 方块位置
     * @param blockStateStr 序列化的 BlockState
     * @param color ARGB 颜色值
     * @param version 数据格式版本（建议使用 CURRENT_VERSION）
     */
    public static void putBlockData(String modId, BlockPos pos, String blockStateStr, int color, int version) {
        if (!DatabaseManager.isReady()) return;
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        String sql = "INSERT OR REPLACE INTO " + TABLE_NAME + " (" +
                COL_X + ", " + COL_Y + ", " + COL_Z + ", " +
                COL_CHUNK_X + ", " + COL_CHUNK_Z + ", " +
                COL_MOD_ID + ", " + COL_BLOCK_STATE + ", " + COL_COLOR + ", " + COL_VERSION +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        DatabaseManager.executeUpdate(sql, pos.getX(), pos.getY(), pos.getZ(), chunkX, chunkZ, modId, blockStateStr, color, version);
        LOGGER.debug("putBlockData async: mod={}, pos={}, blockState={}, color=0x{}", modId, pos, blockStateStr, Integer.toHexString(color));
    }

    public static void putBlockDataAsync(String modId, BlockPos pos, String blockStateStr, int color, int version) {
        if (!DatabaseManager.isReady()) return;
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        String sql = "INSERT OR REPLACE INTO " + TABLE_NAME + " (" +
                COL_X + ", " + COL_Y + ", " + COL_Z + ", " +
                COL_CHUNK_X + ", " + COL_CHUNK_Z + ", " +
                COL_MOD_ID + ", " + COL_BLOCK_STATE + ", " + COL_COLOR + ", " + COL_VERSION +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        DatabaseManager.executeUpdateAsync(sql, pos.getX(), pos.getY(), pos.getZ(), chunkX, chunkZ, modId, blockStateStr, color, version);
        LOGGER.debug("putBlockData async: mod={}, pos={}, blockState={}, color=0x{}", modId, pos, blockStateStr, Integer.toHexString(color));
    }

    /**
     * 将指定区块的所有数据加载到传入的 Map 中。
     * @param chunkPos 区块坐标
     * @param modId 模组标识（如果为 null 则加载所有模组）
     * @param targetMap 目标 Map（BlockPos -> BlockData 或类似结构，需要调用者提供填充逻辑）
     */
    public static void loadChunk(ChunkPos chunkPos, String modId, Map<BlockPos, ? super BlockDataEntry> targetMap) {
        if (!DatabaseManager.isReady()) return;
        String sql;
        if (modId == null) {
            sql = "SELECT " + COL_X + ", " + COL_Y + ", " + COL_Z + ", " + COL_BLOCK_STATE + ", " + COL_COLOR + ", " + COL_VERSION +
                    " FROM " + TABLE_NAME + " WHERE " + COL_CHUNK_X + " = ? AND " + COL_CHUNK_Z + " = ?";
        } else {
            sql = "SELECT " + COL_X + ", " + COL_Y + ", " + COL_Z + ", " + COL_BLOCK_STATE + ", " + COL_COLOR + ", " + COL_VERSION +
                    " FROM " + TABLE_NAME + " WHERE " + COL_CHUNK_X + " = ? AND " + COL_CHUNK_Z + " = ? AND " + COL_MOD_ID + " = ?";
        }
        DatabaseManager.executeQuery(sql, (rs) -> {
            try {
                int count = 0; // TEMPORARY
                while (rs.next()) {
                    int x = rs.getInt(COL_X);
                    int y = rs.getInt(COL_Y);
                    int z = rs.getInt(COL_Z);
                    String blockStateStr = rs.getString(COL_BLOCK_STATE);
                    int color = rs.getInt(COL_COLOR);
                    int version = rs.getInt(COL_VERSION);
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockDataEntry entry = new BlockDataEntry(blockStateStr, color, version);
                    targetMap.put(pos, entry);
                    count++; // TEMPORARY
                }
                LOGGER.debug("loadChunk: chunk={}, mod={}, loaded {} entries", chunkPos, modId, count);
            } catch (SQLException e) {
                LOGGER.error("Error processing result set", e);
            }
        }, chunkPos.x, chunkPos.z, modId);
    }

    /**
     * 获取单个方块的颜色（同步）。
     * @param modId 模组标识
     * @param pos 方块位置
     * @return 颜色值，如果不存在则返回 null
     */
    public static Integer getColor(String modId, BlockPos pos) {
        if (!DatabaseManager.isReady()) return null;
        String sql = "SELECT " + COL_COLOR + " FROM " + TABLE_NAME + " WHERE " +
                COL_MOD_ID + " = ? AND " + COL_X + " = ? AND " + COL_Y + " = ? AND " + COL_Z + " = ?";
        final int[] result = { -1 };
        DatabaseManager.executeQuery(sql, (rs) -> {
            try {
                if (rs.next()) {
                    result[0] = rs.getInt(COL_COLOR);
                } else {
                    result[0] = -1;
                }
            } catch (SQLException e) {
                LOGGER.error("Error getting color", e);
            }
        }, modId, pos.getX(), pos.getY(), pos.getZ());
        Integer ret = result[0] == -1 ? null : result[0];
        LOGGER.debug("getColor: mod={}, pos={}, color={}", modId, pos, ret != null ? "0x"+Integer.toHexString(ret) : "null");
        return ret;
    }

    /**
     * 移除指定方块的数据（同步）。
     */
    public static void removeBlockData(String modId, BlockPos pos) {
        if (!DatabaseManager.isReady()) return;
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE " + COL_MOD_ID + " = ? AND " +
                COL_X + " = ? AND " + COL_Y + " = ? AND " + COL_Z + " = ?";
        DatabaseManager.executeUpdate(sql, modId, pos.getX(), pos.getY(), pos.getZ());
        LOGGER.debug("removeBlockData: mod={}, pos={}", modId, pos);
    }

    /**
     * 移除整个区块的数据（同步）。
     */
    public static void removeChunk(String modId, ChunkPos chunkPos) {
        if (!DatabaseManager.isReady()) return;
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE " + COL_MOD_ID + " = ? AND " +
                COL_CHUNK_X + " = ? AND " + COL_CHUNK_Z + " = ?";
        DatabaseManager.executeUpdate(sql, modId, chunkPos.x, chunkPos.z);
        LOGGER.debug("removeChunk: mod={}, chunk={}", modId, chunkPos);
    }

    /**
     * 简单数据容器，用于 loadChunk 回调。
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