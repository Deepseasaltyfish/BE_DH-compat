package com.deepseasaltyfish.BeLodCompat.cache.compat;

import com.deepseasaltyfish.BeLodCompat.cache.DataBaseCache;
import com.deepseasaltyfish.BeLodCompat.cache.GenericBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.util.DatabaseManager;
import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LTBlockDataCache {
    private static final String MOD_ID = "littletiles";
    private static final DebugLogger LOGGER = DebugLogger.getLogger(LTBlockDataCache.class);
    private static final long REMOVAL_DELAY_MS = 30_000;
    private static final GenericBlockDataCache<LTBlockData> CACHE = new GenericBlockDataCache<LTBlockData>(REMOVAL_DELAY_MS) {};

    /**
     * RGBA format
     */
    private static class LTBlockData {
        private final BlockState blockState;
        private final int color; // ARGB

        LTBlockData(BlockState blockState, int color) {
            this.blockState = blockState;
            this.color = color;
        }

        BlockState getBlockState() { return blockState; }
        int getColor() { return color; }
    }

    /**
     * Extract color from LT content tile NBT and cache it.
     * @param pos block position
     * @param contentTag the block entity NBT (content tag of tiles)
     * @return true if successfully cached, false if fallback used
     */
    public static boolean extractLTColor(BlockPos pos, CompoundTag contentTag) {
        try {
            CompoundTag tilesTag = contentTag.getCompound("tiles");
            if (!tilesTag.isEmpty()) {
                String firstTileId = tilesTag.getAllKeys().iterator().next();
                Tag tileData = tilesTag.get(firstTileId);  // This is a ListTag
                int color = extractColorFromTileData(tileData);
                return put(pos, firstTileId, color);
            }

            ListTag childrenList = contentTag.getList("children", Tag.TAG_COMPOUND);
            for (int j = 0; j < childrenList.size(); j++) {
                CompoundTag wrapper = childrenList.getCompound(j);
                CompoundTag tiles = wrapper.getCompound("tiles");
                if (!tiles.isEmpty()) {
                    String firstTileId = tiles.getAllKeys().iterator().next();
                    Tag tileData = tiles.get(firstTileId);
                    int color = extractColorFromTileData(tileData);
                    return put(pos, firstTileId, color);
                }
            }

            LOGGER.error("No tile found at {}", pos);
            return false;
        } catch (Exception e) {
            LOGGER.error("Failed to extract LT color at {}", pos, e);
            return false;
        }
    }

    /**
     * Extracts the color integer from LittleTiles tile data.
     * The tile data is a ListTag where the first element is an IntArrayTag containing a single integer (the color).
     */
    private static int extractColorFromTileData(Tag tileData) {
        if (tileData instanceof ListTag list && !list.isEmpty()) {
            Tag first = list.get(0);
            if (first instanceof IntArrayTag intArray && intArray.getAsIntArray().length > 0) {
                int packed = intArray.getAsIntArray()[0];
                return packed;
            }
        }
        LOGGER.debug("No valid color found in tile data: {}", tileData);
        return 0;
    }

    public static boolean put(BlockPos pos, String blockStr, int color) {
        String blockName = BlockDataUtil.extractBlockName(blockStr);
        if (blockName == null) {
            LOGGER.error("Failed to extract block name from '{}' at {}", blockStr, pos);
            return false;
        }
        BlockState newState = BlockDataUtil.toDefaultBlockState(blockStr, pos, LOGGER, true);
        LTBlockData newData = new LTBlockData(newState, color);
        CACHE.updateCache(pos, newData, (old, fresh) ->
                old.blockState.equals(fresh.blockState) && old.color == fresh.color
        );
        if (DatabaseManager.isReady()) {
            if (color != 0xFFFFFFFF) {
                DataBaseCache.putBlockData(MOD_ID, pos, blockName, color, DataBaseCache.CURRENT_VERSION);
            } else {
                DataBaseCache.removeBlockData(MOD_ID, pos);
            }
        }
        return true;
    }

    /**
     * Retrieves the cached BlockState for the given position.
     * <p>
     * This method is called concurrently from DH's LOD Builder threads
     * ("DH-LOD Builder Thread[x]"). The implementation must be thread-safe.
     * The underlying cache uses ConcurrentHashMap, which is safe for concurrent reads.
     *
     * @param pos the block position
     * @return the cached BlockState, or null if not found
     */
    public static BlockState getBlockStateAt(BlockPos pos) {
        if (pos == null) {
            LOGGER.error("LTBlockData getBlockStateAt called with null pos");
            return null;
        }
        ChunkPos chunkPos = new ChunkPos(pos);
        ConcurrentHashMap<BlockPos, LTBlockDataCache.LTBlockData> inner = CACHE.cache.get(chunkPos);
        if (inner == null) {
            LOGGER.debug("No LTBlockData at chunk {} block {} (No chunk data)", chunkPos, pos);
            return Blocks.BLACK_WOOL.defaultBlockState();
        }
        LTBlockData data = inner.get(pos);
        if(data == null) {
            LOGGER.debug("No LTBlockData at chunk {} block {}", chunkPos, pos);
            return Blocks.RED_WOOL.defaultBlockState();
        }
        return data.getBlockState();
    }

    /**
     * Retrieve color (RGBA) at position, returns 0 if missing
     */
    public static int getColorAt(BlockPos pos) {
        if (pos == null) return 0;
        LTBlockData data = CACHE.get(pos);
        if (data == null && DatabaseManager.isReady()) {
            loadChunkFromDB(new ChunkPos(pos));
            data = CACHE.get(pos);
        }
        return data != null ? data.getColor() : 0;
    }

    public static void removeChunkInMemory(ChunkPos chunkPos) { CACHE.removeChunkInMemory(chunkPos); }

    public static void removeAt(BlockPos pos) { CACHE.removeAt(pos); }

    public static void clearAll() { CACHE.clearAll(); }

    public static boolean contains(BlockPos pos) { return CACHE.contains(pos); }

    public static int getCacheSize() { return CACHE.getChunkCount(); }

    //debug

    public static String dumpAllEntries() {
        return CACHE.dumpAllEntries(data -> {
            ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(data.blockState.getBlock());
            return rl.toString() + " color: #" + String.format("%08X", BlockDataUtil.argbToRgba(data.color));
        });
    }

    //database
    /**
     * 从数据库加载指定区块的所有 LT 数据到内存缓存。
     * 如果数据库未就绪或区块无数据，则不做任何事。
     */
    private static void loadChunkFromDB(ChunkPos chunkPos) {
        if (!DatabaseManager.isReady()) return;
        CACHE.cache.computeIfAbsent(chunkPos, cp -> {
            Map<BlockPos, DataBaseCache.BlockDataEntry> tempMap = new HashMap<>();
            DataBaseCache.loadChunk(cp, MOD_ID, tempMap);
            ConcurrentHashMap<BlockPos, LTBlockData> inner = new ConcurrentHashMap<>();
            for (Map.Entry<BlockPos, DataBaseCache.BlockDataEntry> entry : tempMap.entrySet()) {
                BlockPos pos = entry.getKey();
                DataBaseCache.BlockDataEntry dataEntry = entry.getValue();
                BlockState state = BlockDataUtil.toDefaultBlockState(dataEntry.blockStateStr, pos, LOGGER, true);
                if (state != null) {
                    inner.put(pos.immutable(), new LTBlockData(state, dataEntry.color));
                }
            }
            return inner;
        });
    }
}