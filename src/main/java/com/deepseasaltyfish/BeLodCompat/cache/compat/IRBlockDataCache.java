package com.deepseasaltyfish.BeLodCompat.cache.compat;

import com.deepseasaltyfish.BeLodCompat.cache.GenericBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.config.ModConfigs;
import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.ConcurrentHashMap;

public class IRBlockDataCache {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(IRBlockDataCache.class);
    private static final long REMOVAL_DELAY_MS = 30_000;
    private static final GenericBlockDataCache<IRBlockData> CACHE = new GenericBlockDataCache<>(REMOVAL_DELAY_MS) {
        @Override
        protected void onChunkUnloaded(ChunkPos chunkPos, ConcurrentHashMap<BlockPos, IRBlockData> removedData) {
            LOGGER.debug("IR chunk {} unloaded, {} entries", chunkPos, removedData.size());
        }
    };

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
            String overrideId = ModConfigs.overrideIrRailBlockId;
            if (overrideId != null && !overrideId.isEmpty()) {
                LOGGER.debug("IR rail override applied at {}, using {}", pos, overrideId);
                return put(pos, overrideId);
            }
        }

        try {
            CompoundTag bedItem;
            if (isParent) {
                CompoundTag info = tag.getCompound("info");
                CompoundTag settings = info.getCompound("settings");
                bedItem = settings.getCompound("bedItem");
            } else {
                if (!tag.contains("railBedCache", Tag.TAG_COMPOUND)) {
                    LOGGER.debug("No railBedCache found at {}, this is old version IR or railBedCache not stored", pos);
                }
                bedItem = tag.getCompound("railBedCache");
            }

            String id = bedItem.getString("id");
            if (id.isEmpty()) {
                LOGGER.debug("Empty bedItem id at {}, using override block", pos);
                return put(pos, ModConfigs.overrideIrRailBlockId);
            }
            return put(pos, id);
        } catch (Exception e) {
            LOGGER.error("Failed to extract IR color at {}", pos, e);
            return false;
        }
    }

    public static class IRBlockData {
        private final BlockState blockState;
        IRBlockData(BlockState blockState) {
            this.blockState = blockState;
        }
        BlockState getBlockState() { return blockState; }
    }

    public static boolean put(BlockPos pos, String blockStr) {
        BlockState newState = BlockDataUtil.toDefaultBlockState(blockStr, pos, LOGGER, false);
        IRBlockData newData = new IRBlockData(newState);
        CACHE.updateCache(pos, newData, (old, fresh) -> old.blockState.equals(fresh.blockState));
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
        ChunkPos chunkPos = new ChunkPos(pos);
        ConcurrentHashMap<BlockPos, IRBlockData> inner = CACHE.cache.get(chunkPos);
        if (inner == null) {
            LOGGER.debug("No IRBlockData at chunk {} block {} (No chunk data)", chunkPos, pos);
            return Blocks.BLACK_WOOL.defaultBlockState();
        }
        IRBlockData data = inner.get(pos);
        if (data == null) {
            LOGGER.debug("No IRBlockData at chunk {} block {}", chunkPos, pos);
            return Blocks.RED_WOOL.defaultBlockState();
        }
        return data.getBlockState();
    }

    public static void removeChunkInMemory(ChunkPos chunkPos) { CACHE.removeChunkInMemory(chunkPos); }

    public static void removeAt(BlockPos pos) { CACHE.removeAt(pos); }

    public static void clearAll() { CACHE.clearAll(); }

    public static boolean contains(BlockPos pos) { return CACHE.contains(pos); }

    public static int getCacheSize() { return CACHE.getChunkCount(); }

    //debug
    public static String dumpAllEntries() {
        return CACHE.dumpAllEntries(data -> {
            ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(data.getBlockState().getBlock());
            return rl.toString();
        });
    }
}