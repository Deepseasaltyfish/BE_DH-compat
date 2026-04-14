package com.deepseasaltyfish.BeDhCompat.common.LittleTiles;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

public class LTColorCache {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Main cache: each chunk maps to a BlockPos -> color (BlockState) mapping
    private static final ConcurrentHashMap<ChunkPos, ConcurrentHashMap<BlockPos, BlockState>> chunkColorMap = new ConcurrentHashMap<>();

    public static void extractLTColor(BlockPos pos, CompoundTag contentTag) {
        try {
            CompoundTag tilesTag = contentTag.getCompound("tiles");
            if (!tilesTag.isEmpty()) {
                String firstTileId = tilesTag.getAllKeys().iterator().next();
                put(pos, firstTileId);
                return;
            }

            ListTag childrenList = contentTag.getList("children", Tag.TAG_COMPOUND);
            for (int j = 0; j < childrenList.size(); j++) {
                CompoundTag wrapper = childrenList.getCompound(j);
                CompoundTag tiles = wrapper.getCompound("tiles");
                if (!tiles.isEmpty()) {
                    String firstTileId = tiles.getAllKeys().iterator().next();
                    put(pos, firstTileId);
                    return;
                }
            }

            LOGGER.warn("No tile found at {}", pos);

        } catch (Exception e) {
            LOGGER.error("Failed to extract LT color at {}", pos, e);
        }
    }

    //Used for converting string to BlockState
    public static BlockState parseBlockStateString(String blockStateStr) {
        try {
            if (blockStateStr == null || blockStateStr.isEmpty()) {
                LOGGER.warn("null blockStateStr");
                return Blocks.AIR.defaultBlockState();
            }
            //LOGGER.warn(blockStateStr);
            //sometimes the blockStateStr could be "littletiles:missing" (mostly caused by missing other mod), temporarily convert to stone
            if(blockStateStr.equals("littletiles:missing")){
                LOGGER.warn("find LT \"littletiles:missing\" value, converted to stone");
                return Blocks.STONE.defaultBlockState();
            }

            String blockName;
            String stateStr = null;

            // Determine whether there is a [state] section
            int stateStart = blockStateStr.indexOf('[');
            if (stateStart != -1) {
                blockName = blockStateStr.substring(0, stateStart);
                stateStr = blockStateStr.substring(stateStart + 1, blockStateStr.length() - 1);
            } else {
                blockName = blockStateStr;
            }
                    // Retrieve the Block
                    ResourceLocation blockId = new ResourceLocation(blockName);

                    Block block = BuiltInRegistries.BLOCK.get(blockId);
            if (block == null) {
                throw new IllegalArgumentException("Unknown block id: " + blockName);
            }

            BlockState state = block.defaultBlockState();

            // Parse properties and apply values
            if (stateStr != null && !stateStr.isEmpty()) {
                String[] properties = stateStr.split(",");
                for (String prop : properties) {
                    String[] kv = prop.split("=");
                    if (kv.length != 2) continue;

                    String key = kv[0];
                    String value = kv[1];

                    Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                    if (property != null) {
                        Optional<?> parsedValue = property.getValue(value);
                        if (parsedValue.isPresent()) {
                            // Note the generic cast: must be done safely
                            state = safeSetProperty(state, property, parsedValue.get());
                        }
                    }
                }
            }

            return state;

        } catch (Exception e) {
            LOGGER.error("Failed to parse BlockState string: " + blockStateStr, e);
            return Blocks.AIR.defaultBlockState(); // fallback
        }
    }
    // Helper: bypass generic restriction and safely set property
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState safeSetProperty(BlockState state, Property<?> property, Object value) {
        return state.setValue((Property<T>) property, (T) value);
    }
    /**
     * Insert a color mapping
     */
    public static void put(BlockPos pos, String blockStr) {
        ChunkPos chunkPos = new ChunkPos(pos);
        BlockState convertedState = parseBlockStateString(blockStr);
        if(convertedState != null){
            chunkColorMap
                    .computeIfAbsent(chunkPos, cp -> new ConcurrentHashMap<>())
                    .put(pos.immutable(), convertedState);
        }else{
            LOGGER.error("Fail to convert to BlockState for LT at: " + pos);
        }
    }

    /**
     * Get size of cache
     */
    public static int getCacheSize(){
        if(chunkColorMap != null){
            return chunkColorMap.size();
        }
        return 0;
    }


    /**
     * Retrieve color data (returns null if missing)
     */
    public static BlockState getBlockStateAt(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        Map<BlockPos, BlockState> innerMap = chunkColorMap.get(chunkPos);
        if (innerMap != null) {
            return innerMap.getOrDefault(pos,null);
        }
        return null;
    }

    /**
     * Check if a BlockPos has cached data
     */
    public static boolean contains(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        Map<BlockPos, BlockState> innerMap = chunkColorMap.get(chunkPos);
        return innerMap != null && innerMap.containsKey(pos);
    }

    /**
     * Clear cache for a specific chunk (called on chunk unload)
     */
    public static void removeChunk(ChunkPos chunkPos) {
        chunkColorMap.remove(chunkPos);
    }

    /**
     * Clear all cache (mostly when world closes)
     */
    public static void clearAll() {
        chunkColorMap.clear();
    }
}
