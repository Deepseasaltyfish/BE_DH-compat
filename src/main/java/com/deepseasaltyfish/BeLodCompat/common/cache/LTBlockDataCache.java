package com.deepseasaltyfish.BeLodCompat.common.cache;

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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

public class LTBlockDataCache {
    private static final String MOD_ID = "littletiles";
    //TODO: we should store these cache in region instead of generate them frequently
    private static final DebugLogger LOGGER = DebugLogger.getLogger(IRBlockDataCache.class);
    private static final long REMOVAL_DELAY_MS = 30_000;
    private static final ChunkCache<LTBlockDataCache.LTBlockData> CACHE = new ChunkCache<>(REMOVAL_DELAY_MS);

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
        boolean changed = CACHE.put(pos, newData, (old, fresh) ->
                old.getBlockState().equals(fresh.getBlockState()) && old.getColor() == fresh.getColor());

        if (changed && DatabaseManager.isReady()) {
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
    public static BlockState getBlockStateAt(BlockPos pos) {//TODO: 有可能返回不正常的null？
        if (pos == null) {
            LOGGER.error("LTBlockData getBlockStateAt called with null pos");
            return null;
        }
        LTBlockData data = CACHE.get(pos);
        if (data == null) {
            ChunkPos cp = new ChunkPos(pos);
            LOGGER.warn("No LTBlockData at chunk {} block {}", cp, pos);
            return Blocks.BLACK_WOOL.defaultBlockState();
        }
        return data.getBlockState();
    }

    /**
     * Retrieve color (RGBA) at position, returns 0 if missing
     */
    public static int getColorAt(BlockPos pos) {
        if (pos == null) {
            LOGGER.error("LTBlockData getBlockStateAt called with null pos");
            return 0;
        }
        LTBlockData data = CACHE.get(pos);
        if (data == null && DatabaseManager.isReady()) {
            loadChunkFromDB(new ChunkPos(pos));
            data = CACHE.get(pos);
        }
        return data != null ? data.getColor() : 0;
    }

    public static void removeChunkInMemory(ChunkPos chunkPos) {
        CACHE.removeChunkInMemory(chunkPos, (cp, removed) ->
                LOGGER.debug("Delayed removal of chunk {} with {} entries", cp, removed.size()));
    }
    public static void removeAt(BlockPos pos) {
        CACHE.removeAt(pos);
        if (DatabaseManager.isReady()) {
            DataBaseCache.removeBlockData(MOD_ID, pos);
        }
    }
    public static void clearAll() { CACHE.clearAll(); }
    public static boolean contains(BlockPos pos) { return CACHE.contains(pos); }
    public static int getCacheSize() { return CACHE.getChunkCount(); }

    //debug
    public static String dumpAllEntries() {
        return CACHE.dumpToString("LTBlockDataCache", data -> {
            ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(data.getBlockState().getBlock());
            return rl.toString() + " color: #" + String.format("%08X", BlockDataUtil.argbToRgba(data.getColor()));
        });
    }

    //database
    private static void loadChunkFromDB(ChunkPos chunkPos) {
        CACHE.loadChunkFromDB(chunkPos, MOD_ID, entry -> {
            BlockState state = BlockDataUtil.toDefaultBlockState(entry.blockStateStr, null, LOGGER, true);
            return new LTBlockData(state, entry.color);
        }, LOGGER);
    }
}