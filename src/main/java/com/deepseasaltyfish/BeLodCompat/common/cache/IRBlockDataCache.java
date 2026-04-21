package com.deepseasaltyfish.BeLodCompat.common.cache;

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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class IRBlockDataCache {
    private static final String MOD_ID = "immersiverailroading";
    private static final DebugLogger LOGGER = DebugLogger.getLogger(IRBlockDataCache.class);
    private static final long REMOVAL_DELAY_MS = 30_000;
    private static final ChunkCache<IRBlockDataCache.IRBlockData> CACHE = new ChunkCache<>(REMOVAL_DELAY_MS);
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
    public static boolean extractIRColor(BlockPos pos, CompoundTag tag, boolean isParent) {
        // Check config override first
        if (ModConfigs.overrideIrRailBlock) {
            String overrideId = ModConfigs.getValidatedOverrideId();
            if (overrideId != null && !overrideId.isEmpty()) {
                LOGGER.debug("IR rail override applied at {}, using {}", pos, overrideId);
                return put(pos, overrideId, null, isParent);
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
                IRBlockData parentData = CACHE.get(parentPos);
                if(parentData == null && DatabaseManager.isReady()){
                    loadChunkFromDB(new ChunkPos(parentPos));
                    parentData = CACHE.get(parentPos);
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

            return put(pos, id, parentPos, isParent);
        } catch (Exception e) {
            LOGGER.error("Failed to extract IR color at {}", pos, e);
            return false;
        }
    }

    public static boolean put(BlockPos pos, String blockStr, BlockPos parentPos, boolean isParent) {
        String blockName = BlockDataUtil.extractBlockName(blockStr);
        if (blockName == null) {
            LOGGER.error("Failed to extract block name from '{}' at {}", blockStr, pos);
            return false;
        }

        BlockState newState = BlockDataUtil.toDefaultBlockState(blockStr, pos, LOGGER, false);
        IRBlockData newData = new IRBlockData(newState, parentPos);
        boolean changed = CACHE.put(pos, newData, (old, fresh) ->
                old.getBlockState().equals(fresh.getBlockState()) && java.util.Objects.equals(old.getParentPos(), fresh.getParentPos())
        );

        if (changed && DatabaseManager.isReady()) {
            if(isParent) {
                DataBaseCache.putBlockData(MOD_ID, pos, blockStr, 0, DataBaseCache.CURRENT_VERSION);
            }else {
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
            LOGGER.error("IRBlockData getBlockStateAt called with null pos");
            return null;
        }
        IRBlockData data = CACHE.get(pos);
        if (data == null) {
            ChunkPos cp = new ChunkPos(pos);
            LOGGER.warn("No IRBlockData at chunk {} block {}", cp, pos);
            return Blocks.BLACK_WOOL.defaultBlockState();
        }

        //we do not need this yet
//        if(data.getParentPos() != null && !data.getParentPos().equals(pos)) {
//            BlockPos parentPos = data.getParentPos();
//            IRBlockData parentData = CACHE.get(data.getParentPos());
//            if (parentData == null && DatabaseManager.isReady()) {
//                loadChunkFromDB(new ChunkPos(parentPos));
//                parentData = CACHE.get(parentPos);
//            }
//            if (parentData != null) {
//                return parentData.getBlockState();
//            } else {
//                LOGGER.debug("Parent rail at {} not found for child at {}", parentPos, pos);
//                return data.getBlockState();
//            }
//        }
        return data.getBlockState();
    }

    public static void removeChunkInMemory(ChunkPos chunkPos) {
        CACHE.removeChunkInMemory(chunkPos, (cp, removed) ->
                LOGGER.debug("Delayed removal of chunk {} with {} entries", cp, removed.size()));
    }
    public static void removeAt(BlockPos pos) { CACHE.removeAt(pos); }
    public static void clearAll() { CACHE.clearAll(); }
    public static boolean contains(BlockPos pos) { return CACHE.contains(pos); }
    public static int getCacheSize() { return CACHE.getChunkCount(); }

    //debug
    public static String dumpAllEntries() {
        return CACHE.dumpToString("IRBlockDataCache", data ->
                BuiltInRegistries.BLOCK.getKey(data.getBlockState().getBlock()).toString()
        );
    }

    private static void loadChunkFromDB(ChunkPos chunkPos) {
        if (!DatabaseManager.isReady()) return;
        CACHE.getRawCache().computeIfAbsent(chunkPos, cp -> {
            Map<BlockPos, DataBaseCache.BlockDataEntry> tempMap = new HashMap<>();
            DataBaseCache.loadChunk(cp, MOD_ID, tempMap);
            ConcurrentHashMap<BlockPos, IRBlockData> inner = new ConcurrentHashMap<>();
            for (Map.Entry<BlockPos, DataBaseCache.BlockDataEntry> entry : tempMap.entrySet()) {
                BlockPos pos = entry.getKey();
                DataBaseCache.BlockDataEntry dataEntry = entry.getValue();
                BlockState state = BlockDataUtil.toDefaultBlockState(dataEntry.blockStateStr, pos, LOGGER, false);
                // Loaded from DB are always parent blocks (children not stored), so parentPos = null
                inner.put(pos.immutable(), new IRBlockData(state, null));
            }
            return inner;
        });
    }
}