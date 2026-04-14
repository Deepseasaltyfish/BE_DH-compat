package com.deepseasaltyfish.BeDhCompat.common.LittleTiles;

import com.deepseasaltyfish.BeDhCompat.util.DebugLogger;
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

public class LTColorCache {
    //TODO: we should store these cache in region instead of generate them frequently
    private static final DebugLogger LOGGER = DebugLogger.getLogger(LTColorCache.class);

    // Main cache: each chunk maps to a BlockPos -> LTBlockData (BlockState + color)
    private static final ConcurrentHashMap<ChunkPos, ConcurrentHashMap<BlockPos, LTBlockData>> chunkColorMap = new ConcurrentHashMap<>();

    /**
     * ARGB format
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

    public static void extractLTColor(BlockPos pos, CompoundTag contentTag) {
        try {
            CompoundTag tilesTag = contentTag.getCompound("tiles");
            if (!tilesTag.isEmpty()) {
                String firstTileId = tilesTag.getAllKeys().iterator().next();
                CompoundTag tileCompound = tilesTag.getCompound(firstTileId);
                int color = extractColorFromTile(tileCompound);
                put(pos, firstTileId, color);
                return;
            }

            ListTag childrenList = contentTag.getList("children", Tag.TAG_COMPOUND);
            for (int j = 0; j < childrenList.size(); j++) {
                CompoundTag wrapper = childrenList.getCompound(j);
                CompoundTag tiles = wrapper.getCompound("tiles");
                if (!tiles.isEmpty()) {
                    String firstTileId = tiles.getAllKeys().iterator().next();
                    CompoundTag tileCompound = tiles.getCompound(firstTileId);
                    int color = extractColorFromTile(tileCompound);
                    put(pos, firstTileId, color);
                    return;
                }
            }

            LOGGER.error("No tile found at {}", pos);

        } catch (Exception e) {
            LOGGER.error("Failed to extract LT color at {}", pos, e);
        }
    }

    /**
     * get color from CompoundTag in tile (format: 0xBBGGRRAA)
     */
    private static int extractColorFromTile(CompoundTag tileCompound) {//红色00 00 FF,绿色为00 FF 00,蓝色FF 00 00,黄色00 FF FF，FF 00 FF洋红,FF FF 00青绿色
        int packed;
        if (tileCompound.contains("color", Tag.TAG_INT)) {
            packed = tileCompound.getInt("color");
        } else if (tileCompound.contains("c", Tag.TAG_INT)) {
            packed = tileCompound.getInt("c");
        } else {
            LOGGER.debug("empty color tag:" + tileCompound);
            return 0; // no color, full transparent
        }
        return bgrAlphaToArgb(packed);
    }

    /**
     * Convert LittleTiles 0xBBGGRRAA format to ARGB (0xAARRGGBB)
     */
    private static int bgrAlphaToArgb(int bgra) {
        int b = (bgra >> 24) & 0xFF;
        int g = (bgra >> 16) & 0xFF;
        int r = (bgra >> 8) & 0xFF;
        int a = bgra & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Used for converting string to BlockState
     */
    public static BlockState parseBlockStateString(String blockStateStr, BlockPos pos) {
        try {
            if (blockStateStr == null || blockStateStr.isEmpty()) {
                LOGGER.error("null blockStateString at " + pos);
                return Blocks.AIR.defaultBlockState();
            }
            //sometimes the blockStateStr could be "littletiles:missing" (mostly caused by missing other mod), temporarily convert to stone
            if(blockStateStr.equals("littletiles:missing")){
                LOGGER.debug("find LT \"littletiles:missing\" value at " + pos + ", converted to stone");
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
            LOGGER.error("Failed to parse BlockState string: " + blockStateStr + " at " + pos, e);
            return Blocks.AIR.defaultBlockState(); // fallback
        }
    }
    // Helper: bypass generic restriction and safely set property
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState safeSetProperty(BlockState state, Property<?> property, Object value) {
        return state.setValue((Property<T>) property, (T) value);
    }

    /**
     * Insert a color mapping (with color extracted from tile NBT)
     */
    public static void put(BlockPos pos, String blockStr, int color) {
        ChunkPos chunkPos = new ChunkPos(pos);
        BlockState convertedState = parseBlockStateString(blockStr, pos);
        if(convertedState != null){
            chunkColorMap
                    .computeIfAbsent(chunkPos, cp -> new ConcurrentHashMap<>())
                    .put(pos.immutable(), new LTBlockData(convertedState, color));
        }else{
            LOGGER.error("Fail to convert to BlockState for LT at: " + pos);
        }
    }

    /**
     * Insert a color mapping (legacy, no color, default 0)
     */
    public static void put(BlockPos pos, String blockStr) {
        put(pos, blockStr, 0);
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
     * Retrieve block state (returns null if missing)
     */
    public static BlockState getBlockStateAt(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        Map<BlockPos, LTBlockData> innerMap = chunkColorMap.get(chunkPos);
        if (innerMap != null) {
            LTBlockData data = innerMap.get(pos);
            return data != null ? data.getBlockState() : null;
        }
        return null;
    }

    /**
     * Retrieve color (ARGB) at position, returns 0 if missing
     */
    public static int getColorAt(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        Map<BlockPos, LTBlockData> innerMap = chunkColorMap.get(chunkPos);
        if (innerMap != null) {
            LTBlockData data = innerMap.get(pos);
            return data != null ? data.getColor() : 0;
        }
        return 0;
    }

    /**
     * Check if a BlockPos has cached data
     */
    public static boolean contains(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        Map<BlockPos, LTBlockData> innerMap = chunkColorMap.get(chunkPos);
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