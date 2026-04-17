package com.deepseasaltyfish.BeLodCompat.common.ImmersiveRairoading;

import com.deepseasaltyfish.BeLodCompat.config.ModConfigs;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IRBlockDataCache {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(IRBlockDataCache.class);

    // Cache: chunk -> pos -> IRBlockData (BlockState only, color unused for now)
    private static final ConcurrentHashMap<ChunkPos, ConcurrentHashMap<BlockPos, IRBlockData>> chunkColorMap = new ConcurrentHashMap<>();

    public static class IRBlockData {
        private final BlockState blockState;

        IRBlockData(BlockState blockState) {
            this.blockState = blockState;
        }

        BlockState getBlockState() { return blockState; }
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
                LOGGER.debug("Empty bedItem id at {}, using soul sand (handled by put fallback)", pos);
            }
            return put(pos, id);
        } catch (Exception e) {
            LOGGER.error("Failed to extract IR color at {}", pos, e);
            return false;
        }
    }

    private static BlockState parseBlockStateString(String blockStateStr, BlockPos pos) {
        if (blockStateStr == null || blockStateStr.isEmpty()) {
            LOGGER.error("Found null blockStateString at {}", pos);//TODO:IR side cache issue
            return Blocks.AIR.defaultBlockState();
        }
        try {
            ResourceLocation rl = ResourceLocation.parse(blockStateStr);
            Block block = BuiltInRegistries.BLOCK.get(rl);
            return block.defaultBlockState();
        } catch (Exception e) {
            LOGGER.error("Failed to parse IR BlockState string {} at {}",blockStateStr, pos, e);
            return Blocks.AIR.defaultBlockState(); // fallback
        }
    }

    public static boolean put(BlockPos pos, String blockStr) {
        ChunkPos chunkPos = new ChunkPos(pos);
        BlockState convertedState = parseBlockStateString(blockStr, pos);

        if(convertedState != null){
            chunkColorMap
                    .computeIfAbsent(chunkPos, cp -> new ConcurrentHashMap<>())
                    .put(pos.immutable(), new IRBlockDataCache.IRBlockData(convertedState));
            return true;
        }else{
            LOGGER.error("Fail to convert to BlockState for IR at {}", pos);
            return false;
        }
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
        LOGGER.debug("test debug");
        LOGGER.info("test info");
        if (pos == null) {
            LOGGER.error("IRBlockData getBlockStateAt called with null pos");
            return null;
        }
        ChunkPos chunkPos = new ChunkPos(pos);
        Map<BlockPos, IRBlockData> innerMap = chunkColorMap.get(chunkPos);
        if (innerMap == null) {
            LOGGER.debug("No IRBlockData at chunk {} block {} (No chunk data)", chunkPos, pos);
            return null;
        }
        IRBlockData data = innerMap.get(pos);
        if (data == null) {
            LOGGER.debug("No IRBlockData at chunk {} block {}", chunkPos, pos);
            return null;
        }
        return data.getBlockState();
    }

    public static void removeChunk(ChunkPos chunkPos) {
        if (chunkPos == null) return;
        if(chunkColorMap.get(chunkPos) != null)LOGGER.debug("remove chunk at" + chunkPos);
        chunkColorMap.remove(chunkPos);
    }

    public static void clearAll() {
        chunkColorMap.clear();
    }

    public static boolean contains(BlockPos pos) {
        if (pos == null) return false;
        ChunkPos chunkPos = new ChunkPos(pos);
        Map<BlockPos, IRBlockData> innerMap = chunkColorMap.get(chunkPos);
        return innerMap != null && innerMap.containsKey(pos);
    }

    public static int getCacheSize() {
        return chunkColorMap.size();
    }

    //debug

    public static String dumpAllEntries() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== IRBlockDataCache Dump ===\n");
        int total = 0;
        for (Map.Entry<ChunkPos, ConcurrentHashMap<BlockPos, IRBlockData>> chunkEntry : chunkColorMap.entrySet()) {
            ChunkPos cp = chunkEntry.getKey();
            sb.append("Chunk ").append(cp.x).append(", ").append(cp.z).append(":\n");
            for (Map.Entry<BlockPos, IRBlockData> entry : chunkEntry.getValue().entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue().getBlockState();
                ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                sb.append("  ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ())
                        .append(" -> ").append(rl).append("\n");
                total++;
            }
        }
        if (chunkColorMap.isEmpty()) {
            sb.append("(empty)\n");
        } else {
            sb.append("Total entries: ").append(total).append("\n");
        }
        return sb.toString();
    }
}