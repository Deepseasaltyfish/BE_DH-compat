package com.deepseasaltyfish.BeLodCompat.common.cache;

import com.deepseasaltyfish.BeLodCompat.chunk.ChunkEventHandler;
import com.deepseasaltyfish.BeLodCompat.common.DataBaseCache;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

public class LTBlockDataCache {
    private static final String MOD_ID = "littletiles";
    private static final DebugLogger LOGGER = DebugLogger.getLogger(LTBlockDataCache.class);
    private static final long REMOVAL_DELAY_MS = 30_000;
    private static final ConcurrentHashMap<String, ChunkCache<LTBlockData>> cacheMap = new ConcurrentHashMap<>();

    private static ChunkCache<LTBlockData> getCache(String dimName) {
        if (dimName == null) return null;
        return cacheMap.computeIfAbsent(dimName, d -> new ChunkCache<>(REMOVAL_DELAY_MS));
    }

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
    public static boolean extractLTColor(BlockPos pos, CompoundTag contentTag, String dimName) {
        try {
            CompoundTag tilesTag = contentTag.getCompound("tiles");
            if (!tilesTag.isEmpty()) {
                String firstTileId = tilesTag.getAllKeys().iterator().next();
                Tag tileData = tilesTag.get(firstTileId);  // This is a ListTag
                int color = extractColorFromTileData(tileData);
                return put(pos, firstTileId, color, dimName);
            }

            ListTag childrenList = contentTag.getList("children", Tag.TAG_COMPOUND);
            for (int j = 0; j < childrenList.size(); j++) {
                CompoundTag wrapper = childrenList.getCompound(j);
                CompoundTag tiles = wrapper.getCompound("tiles");
                if (!tiles.isEmpty()) {
                    String firstTileId = tiles.getAllKeys().iterator().next();
                    Tag tileData = tiles.get(firstTileId);
                    int color = extractColorFromTileData(tileData);
                    return put(pos, firstTileId, color, dimName);
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

    public static boolean put(BlockPos pos, String blockStr, int color, String dimName) {
        Path dbFile = ChunkEventHandler.getDbFileForDimension(dimName);
        if (dbFile == null) {
            LOGGER.warn("LtPut: No database for dimension: {}", dimName);
            return false;
        }
        String blockName = BlockDataUtil.extractBlockName(blockStr);
        if (blockName == null) {
            LOGGER.error("Failed to extract block name from '{}' at {}", blockStr, pos);
            return false;
        }
        BlockState newState = BlockDataUtil.toDefaultBlockState(blockStr, pos, LOGGER, true);
        LTBlockData newData = new LTBlockData(newState, color);
        ChunkCache<LTBlockData> cache = getCache(dimName);
        if (cache == null) return false;
        boolean changed = cache.put(pos, newData, (old, fresh) ->
                old.getBlockState().equals(fresh.getBlockState()) && old.getColor() == fresh.getColor());

        if (changed && DatabaseManager.isReady(dbFile)) {
            if (color != 0xFFFFFFFF) {
                DataBaseCache.putBlockData(dbFile, MOD_ID, pos, blockName, color, DataBaseCache.CURRENT_VERSION);
            } else {
                DataBaseCache.removeBlockData(dbFile, MOD_ID, pos);
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
    public static BlockState getBlockStateAt(BlockPos pos, String dimName) {
        if (pos == null) {
            LOGGER.error("LTBlockData getBlockStateAt called with null pos");
            return null;
        }
        ChunkCache<LTBlockData> cache = getCache(dimName);
        if (cache == null) {
            LOGGER.warn("No cache for dimension: {}", dimName);
            return null;
        }
        LTBlockData data = cache.get(pos);
        if (data == null) {
            ChunkPos cp = new ChunkPos(pos);
            LOGGER.debug("No LTBlockData at chunk {} block {} for dimension {}", cp, pos, dimName);
            return null;
        }
        return data.getBlockState();
    }

    /**
     * Retrieve color (RGBA) at position, returns 0 if missing
     */
    public static int getColorAt(BlockPos pos, String dimName) {
        if (pos == null) {
            LOGGER.error("LTBlockData getColorAt called with null pos");
            return 0;
        }
        ChunkCache<LTBlockData> cache = getCache(dimName);
        if (cache == null) return 0;
        LTBlockData data = cache.get(pos);
        if (data == null) {
            Path dbFile = ChunkEventHandler.getDbFileForDimension(dimName);
            if (dbFile != null && DatabaseManager.isReady(dbFile)) {
                loadChunkFromDB(new ChunkPos(pos), dimName);
                data = cache.get(pos);
            }
        }
        return data != null ? data.getColor() : 0;
    }

    public static void removeChunkInMemory(ChunkPos chunkPos, String dimName) {
        ChunkCache<LTBlockData> cache = getCache(dimName);
        if (cache != null) {
            cache.removeChunkInMemory(chunkPos, (cp, removed) ->
                    LOGGER.debug("Delayed removal of chunk {} with {} entries for dimension {}", cp, removed.size(), dimName));
        }
    }

    public static void removeAt(BlockPos pos, String dimName) {
        ChunkCache<LTBlockData> cache = getCache(dimName);
        if (cache != null) cache.removeAt(pos);
        Path dbFile = ChunkEventHandler.getDbFileForDimension(dimName);
        if (dbFile != null && DatabaseManager.isReady(dbFile)) {
            DataBaseCache.removeBlockData(dbFile, MOD_ID, pos);
        }
    }

    public static void clearAll() {
        cacheMap.values().forEach(ChunkCache::clearAll);
        cacheMap.clear();
        LOGGER.debug("Cleared all LT memory caches for all dimensions");
    }

    public static void clearForDimension(String dimName) {
        ChunkCache<LTBlockData> cache = cacheMap.remove(dimName);
        if (cache != null) {
            cache.clearAll();
            LOGGER.debug("Cleared cache for dimension {}", dimName);
        }
    }

    public static boolean contains(BlockPos pos, String dimName) {
        ChunkCache<LTBlockData> cache = getCache(dimName);
        return cache != null && cache.contains(pos);
    }

    public static int getCacheSize(String dimName) {
        ChunkCache<LTBlockData> cache = getCache(dimName);
        return cache != null ? cache.getChunkCount() : 0;
    }

    //debug
    public static String dumpAllEntries(String dimName) {
        ChunkCache<LTBlockData> cache = getCache(dimName);
        if (cache == null) return "No cache available for dimension: " + dimName;
        return cache.dumpToString("LTBlockDataCache", data -> {
            ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(data.getBlockState().getBlock());
            return rl.toString() + " color: #" + String.format("%08X", BlockDataUtil.argbToRgba(data.getColor()));
        });
    }

    //database
    private static void loadChunkFromDB(ChunkPos chunkPos, String dimName) {
        Path dbFile = ChunkEventHandler.getDbFileForDimension(dimName);
        if (dbFile == null) return;
        ChunkCache<LTBlockData> cache = getCache(dimName);
        if (cache == null) return;
        cache.loadChunkFromDB(chunkPos, MOD_ID, entry -> {
            BlockState state = BlockDataUtil.toDefaultBlockState(entry.blockStateStr, null, LOGGER, true);
            return new LTBlockData(state, entry.color);
        }, dbFile, LOGGER);
    }
}