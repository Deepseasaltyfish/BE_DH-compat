package com.deepseasaltyfish.BeLodCompat.common.cache;

import com.deepseasaltyfish.BeLodCompat.chunk.ChunkEventHandler;
import com.deepseasaltyfish.BeLodCompat.common.DataBaseCache;
import com.deepseasaltyfish.BeLodCompat.config.ModConfigs;
import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DatabaseManager;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.tuple.Pair;
import org.checkerframework.checker.units.qual.C;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class IRBlockDataCache {
    private static final String MOD_ID = "immersiverailroading";
    private static final DebugLogger LOGGER = DebugLogger.getLogger(IRBlockDataCache.class);
    private static final long REMOVAL_DELAY_MS = 30_000;
    private static final ConcurrentHashMap<String, ChunkCache<IRBlockData>> cacheMap = new ConcurrentHashMap<>();
    private static ChunkCache<IRBlockData> getCache(String dimName) {
        if (dimName == null) return null;
        return cacheMap.computeIfAbsent(dimName, d -> new ChunkCache<>(REMOVAL_DELAY_MS));
    }
    public static class IRBlockData {
        private final BlockState blockState;
        private final BlockPos parentPos;
        IRBlockData(BlockState blockState, BlockPos parentPos) {
            this.blockState = blockState;
            this.parentPos = parentPos;
        }
        BlockState getBlockState() { return blockState; }
        BlockPos getParentPos() { return parentPos; }
    }

    /**
     * Extract color from IR rail NBT and cache it.
     * @param pos block position
     * @param tag the block entity NBT (instanceData for rail, or full BE tag for parent)
     * @param isParent true for block_rail (parent), false for block_rail_gag (child)
     * @return true if successfully cached (or overridden), false if fallback used (put handled by caller)
     */
    public static boolean extractIRColor(BlockPos pos, CompoundTag tag, boolean isParent, String dimName) {
        Path dbFile = ChunkEventHandler.getDbFileForDimension(dimName);
        if (dbFile == null) {
            LOGGER.error("No database for dimension: {}", dimName);
            return false;
        }

        // Check config override first
        if (ModConfigs.overrideIrRailBlock) {
            String overrideId = ModConfigs.getValidatedOverrideId();
            if (overrideId != null && !overrideId.isEmpty()) {
                LOGGER.debug("IR rail override applied at {}, using {}", pos, overrideId);
                return put(pos, overrideId, null, isParent, dimName);
            }
        }

        try {
            CompoundTag bedItem = null;
            BlockPos parentPos = null;
            if (isParent) {
                CompoundTag info = tag.getCompound("info");
                CompoundTag settings = info.getCompound("settings");
                bedItem = settings.getCompound("bedItem");
//                parentPos = pos;
            } else {
                CompoundTag parentTag = tag.getCompound("parent");
                parentPos = pos.offset(parentTag.getInt("X"), parentTag.getInt("Y"), parentTag.getInt("Z"));
            }

            String parentId = "";
            if(!isParent){
                ChunkCache<IRBlockData> cache = getCache(dimName);
                if (cache == null) return false;
                IRBlockData parentData = cache.get(parentPos);
                if (parentData == null && DatabaseManager.isReady(dbFile)) {
                    loadChunkFromDB(new ChunkPos(parentPos), dimName);
                    parentData = cache.get(parentPos);
                }

                if(parentData != null){
                    ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(parentData.getBlockState().getBlock());
                    parentId = rl.toString();
                } else {
                    LOGGER.debug("Parent rail at {} not loading for child at {}", parentPos, pos);
                    parentId = ModConfigs.getValidatedOverrideId();//fallback when not loaded
                }
            }
            String id = bedItem != null ? bedItem.getString("id") : parentId;
            if (id.isEmpty()) { LOGGER.debug("Empty bedItem id at {}, using soul sand (handled by put fallback)", pos); }

            return put(pos, id, parentPos, isParent, dimName);
        } catch (Exception e) {
            LOGGER.error("Failed to extract IR color at {}", pos, e);
            return false;
        }
    }

    public static boolean put(BlockPos pos, String blockStr, BlockPos parentPos, boolean isParent, String dimName) {
        Path dbFile = ChunkEventHandler.getDbFileForDimension(dimName);
        if (dbFile == null) {
            LOGGER.error("No database for dimension: {}", dimName);
            return false;
        }
        String blockName = BlockDataUtil.extractBlockName(blockStr);
        if (blockName == null) {
            LOGGER.error("Failed to extract block name from '{}' at {}", blockStr, pos);
            return false;
        }
        BlockState newState = BlockDataUtil.toDefaultBlockState(blockStr, pos, LOGGER, false);
        IRBlockData newData = new IRBlockData(newState, parentPos);
        ChunkCache<IRBlockData> cache = getCache(dimName);
        if (cache == null) return false;
        boolean changed = cache.put(pos, newData, (old, fresh) ->
                old.getBlockState().equals(fresh.getBlockState()) && java.util.Objects.equals(old.getParentPos(), fresh.getParentPos())
        );
        if (changed && DatabaseManager.isReady(dbFile)) {
            if (isParent) {
                DataBaseCache.putBlockData(dbFile, MOD_ID, pos, blockStr, 0, DataBaseCache.CURRENT_VERSION);
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
            LOGGER.error("IRBlockData getBlockStateAt called with null pos");
            return null;
        }
        ChunkCache<IRBlockData> cache = getCache(dimName);
        if (cache == null) {
            LOGGER.warn("No cache for dimension: {}", dimName);
            return Blocks.BLACK_WOOL.defaultBlockState();
        }
        IRBlockData data = cache.get(pos);
        if (data == null) {
            ChunkPos cp = new ChunkPos(pos);
            LOGGER.debug("No IRBlockData at chunk {} block {} for dimension {}", cp, pos, dimName);
            return Blocks.BLACK_WOOL.defaultBlockState();
        }
        return data.getBlockState();
    }

    public static void removeChunkInMemory(ChunkPos chunkPos, String dimName) {
        ChunkCache<IRBlockData> cache = getCache(dimName);
        if (cache != null) {
            cache.removeChunkInMemory(chunkPos, (cp, removed) ->
                    LOGGER.debug("Delayed removal of chunk {} with {} entries for dimension {}", cp, removed.size(), dimName));
        }
    }
    public static void removeAt(BlockPos pos, String dimName) {
        ChunkCache<IRBlockData> cache = getCache(dimName);
        if (cache != null) cache.removeAt(pos);
        Path dbFile = ChunkEventHandler.getDbFileForDimension(dimName);
        if (dbFile != null && DatabaseManager.isReady(dbFile)) {
            DataBaseCache.removeBlockData(dbFile, MOD_ID, pos);
        }
    }
    public static void clearAll() {
        cacheMap.values().forEach(ChunkCache::clearAll);
        cacheMap.clear();
        LOGGER.debug("Cleared all IR memory caches for all dimensions");
    }
    public static void clearForDimension(String dimName) {
        ChunkCache<IRBlockData> cache = cacheMap.remove(dimName);
        if (cache != null) {
            cache.clearAll();
            LOGGER.debug("Cleared cache for dimension {}", dimName);
        }
    }
    public static boolean contains(BlockPos pos, String dimName) {
        ChunkCache<IRBlockData> cache = getCache(dimName);
        return cache != null && cache.contains(pos);
    }
    public static int getCacheSize(String dimName) {
        ChunkCache<IRBlockData> cache = getCache(dimName);
        return cache != null ? cache.getChunkCount() : 0;
    }

    //debug
    public static String dumpAllEntries(String dimName) {
        ChunkCache<IRBlockData> cache = getCache(dimName);
        if (cache == null) return "No cache available for dimension: " + dimName;
        return cache.dumpToString("IRBlockDataCache", data ->
                BuiltInRegistries.BLOCK.getKey(data.getBlockState().getBlock()).toString()
        );
    }

    //database
    private static void loadChunkFromDB(ChunkPos chunkPos, String dimName) {
        Path dbFile = ChunkEventHandler.getDbFileForDimension(dimName);
        if (dbFile == null) return;
        ChunkCache<IRBlockData> cache = getCache(dimName);
        if (cache == null) return;
        cache.loadChunkFromDB(chunkPos, MOD_ID, entry -> {
            BlockState state = BlockDataUtil.toDefaultBlockState(entry.blockStateStr, null, LOGGER, false);
            return new IRBlockData(state, null);
        }, dbFile, LOGGER);
    }
}