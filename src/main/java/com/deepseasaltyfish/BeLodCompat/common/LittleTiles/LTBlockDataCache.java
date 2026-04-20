package com.deepseasaltyfish.BeLodCompat.common.LittleTiles;

import com.deepseasaltyfish.BeLodCompat.common.DataBase.DataBaseCache;
import com.deepseasaltyfish.BeLodCompat.dataBase.DatabaseManager;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class LTBlockDataCache {
    private static final String MOD_ID = "littletiles";
    //TODO: we should store these cache in region instead of generate them frequently
    private static final DebugLogger LOGGER = DebugLogger.getLogger(LTBlockDataCache.class);

    // Main cache: each chunk maps to a BlockPos -> LTBlockData (BlockState + color)
    private static final ConcurrentHashMap<ChunkPos, ConcurrentHashMap<BlockPos, LTBlockData>> chunkColorMap = new ConcurrentHashMap<>();

    /**
     * Multiply two ARGB colors.
     * RGB channels are multiplied (component-wise) and normalized to 0-255.
     * Alpha uses the alpha from the overlay color (cached).
     */
    public static int multiplyArgb(int base, int overlay) {
        int baseA = (base >> 24) & 0xFF;
        int baseR = (base >> 16) & 0xFF;
        int baseG = (base >> 8) & 0xFF;
        int baseB = base & 0xFF;

        int overA = (overlay >> 24) & 0xFF;
        int overR = (overlay >> 16) & 0xFF;
        int overG = (overlay >> 8) & 0xFF;
        int overB = overlay & 0xFF;

        // Multiply RGB (normalized)
        int r = (baseR * overR) / 255;
        int g = (baseG * overG) / 255;
        int b = (baseB * overB) / 255;
        // Use overlay alpha (or optionally combine)
        int a = overA;

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

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

    /**
     * 将 ARGB 格式的颜色值转换为 RGBA 格式。
     *
     * <p>输入格式为 0xAARRGGBB（Android/Windows 标准），输出格式为 0xRRGGBBAA（OpenGL/RGBA 标准）。
     *
     * @param argbColor ARGB 格式的颜色值，其中：
     * <ul>
     *  <li>bits 31-24: Alpha（透明度）</li>
     *  <li>bits 23-16: Red（红色）</li>
     *  <li>bits 15-8:  Green（绿色）</li>
     *  <li>bits 7-0:   Blue（蓝色）</li>
     * </ul>
     * @return RGBA 格式的颜色值，其中：
     * <ul>
     *  <li>bits 31-24: Red（红色）</li>
     *  <li>bits 23-16: Green（绿色）</li>
     *  <li>bits 15-8:  Blue（蓝色）</li>
     *  <li>bits 7-0:   Alpha（透明度）</li>
     * </ul>
     *
     */
    public static int argbToRgba(int argbColor) {
        int alpha = (argbColor >> 24) & 0xFF;
        int red   = (argbColor >> 16) & 0xFF;
        int green = (argbColor >> 8)  & 0xFF;
        int blue  = argbColor & 0xFF;

        return (red << 24) | (green << 16) | (blue << 8) | alpha;
    }

    public static BlockState parseBlockStateString(String blockStateStr, BlockPos pos) {
        if (blockStateStr == null || blockStateStr.isEmpty()) {
            LOGGER.error("null blockStateString at {}", pos);
            return Blocks.AIR.defaultBlockState();
        }
        try {
            //sometimes the blockStateStr could be "littletiles:missing" (mostly caused by missing other mod), temporarily convert to stone
            if(blockStateStr.equals("littletiles:missing")){
                LOGGER.debug("Found LT \"littletiles:missing\" value at {}, converted to stone", pos);
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
            ResourceLocation blockId = ResourceLocation.parse(blockName);
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
            LOGGER.error("Failed to parse LT BlockState string {} at {}",blockStateStr, pos, e);
            return Blocks.AIR.defaultBlockState(); // fallback
        }
    }
    // Helper: bypass generic restriction and safely set property
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState safeSetProperty(BlockState state, Property<?> property, Object value) {
        return state.setValue((Property<T>) property, (T) value);
    }

    public static boolean put(BlockPos pos, String blockStr, int color) {
        ChunkPos chunkPos = new ChunkPos(pos);
        BlockState convertedState = parseBlockStateString(blockStr, pos);
        if (convertedState == null) {
            LOGGER.error("Fail to convert to BlockState for LT at {}", pos);
            return false;
        }

        // 获取当前内存中的旧数据
        ConcurrentHashMap<BlockPos, LTBlockData> innerMap = chunkColorMap.get(chunkPos);
        LTBlockData oldData = innerMap != null ? innerMap.get(pos) : null;
        // 比较新旧数据是否完全相同
        if (oldData != null && oldData.getBlockState().equals(convertedState) && oldData.getColor() == color) {
            // 数据相同，无需更新
            return true;
        }

        // 更新内存缓存
        chunkColorMap.computeIfAbsent(chunkPos, cp -> new ConcurrentHashMap<>())
                .put(pos.immutable(), new LTBlockData(convertedState, color));

        // 数据库操作：仅当颜色不是 0xFFFFFFFF（默认无叠加）时才写入；如果是默认色则删除已有记录
        if (DatabaseManager.isReady()) {
            if (color != 0xFFFFFFFF) {
                DataBaseCache.putBlockData(MOD_ID, pos, blockStr, color, DataBaseCache.CURRENT_VERSION);
            } else {
                // 如果新颜色是默认色，且数据库中有旧记录，则删除
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
        ChunkPos chunkPos = new ChunkPos(pos);
        Map<BlockPos, LTBlockData> innerMap = chunkColorMap.get(chunkPos);
        if (innerMap == null) {
            LOGGER.debug("No LTBlockData at chunk {} block {} (No chunk data)", chunkPos, pos);
            return Blocks.BLACK_WOOL.defaultBlockState();
        }
        LTBlockData data = innerMap.get(pos);
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
        ChunkPos chunkPos = new ChunkPos(pos);
        Map<BlockPos, LTBlockData> innerMap = chunkColorMap.get(chunkPos);
        LTBlockData data = null;
        if (innerMap != null) {
            data = innerMap.get(pos);
        }
        if (data == null && DatabaseManager.isReady()) {
            loadChunkFromDB(chunkPos);
            innerMap = chunkColorMap.get(chunkPos);
            if (innerMap != null) {
                data = innerMap.get(pos);
            }
        }
        return data != null ? data.getColor() : 0;
    }

    public static void removeChunkInMemory(ChunkPos chunkPos) {
        if (chunkPos == null) return;
        if (chunkColorMap.get(chunkPos) != null) {
            LOGGER.debug("remove chunk at" + chunkPos);
            chunkColorMap.remove(chunkPos);
        }
    }
    public static void removeAt(BlockPos pos) {
        if (pos == null) return;
        ChunkPos chunkPos = new ChunkPos(pos);
        Map<BlockPos, LTBlockData> inner = chunkColorMap.get(chunkPos);
        if (inner != null) {
            inner.remove(pos);
            if (inner.isEmpty()) {
                chunkColorMap.remove(chunkPos);
            }
        }
        if (DatabaseManager.isReady()) {//we have to call this to make sure color cleaned completely
            DataBaseCache.removeBlockData(MOD_ID, pos);
        }
    }

    public static void clearAll() {
        chunkColorMap.clear();
    }

    public static boolean contains(BlockPos pos) {
        if (pos == null) return false;
        ChunkPos chunkPos = new ChunkPos(pos);
        Map<BlockPos, LTBlockData> innerMap = chunkColorMap.get(chunkPos);
        return innerMap != null && innerMap.containsKey(pos);
    }

    public static int getCacheSize() {
        return chunkColorMap.size();
    }

    //debug

    public static String dumpAllEntries() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== LTBlockDataCache Dump ===\n");

        // 第一次遍历：统计总数
        int total = 0;
        for (ConcurrentHashMap<BlockPos, LTBlockData> innerMap : chunkColorMap.values()) {
            total += innerMap.size();
        }

        if (total == 0) {
            sb.append("(empty)\n");
            return sb.toString();
        }

        if (total > 100) {
            sb.append("Total entries: ").append(total).append("\n");
            return sb.toString();
        }

        // 第二次遍历：输出详细信息（当总数 ≤ 100 时）
        for (Map.Entry<ChunkPos, ConcurrentHashMap<BlockPos, LTBlockData>> chunkEntry : chunkColorMap.entrySet()) {
            ConcurrentHashMap<BlockPos, LTBlockData> innerMap = chunkEntry.getValue();
            if (innerMap.isEmpty()) continue;
            ChunkPos cp = chunkEntry.getKey();
            sb.append("Chunk ").append(cp.x).append(", ").append(cp.z).append(":\n");
            for (Map.Entry<BlockPos, LTBlockData> entry : innerMap.entrySet()) {
                BlockPos pos = entry.getKey();
                LTBlockData data = entry.getValue();
                BlockState state = data.getBlockState();
                int color = data.getColor();
                ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                sb.append("  ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ())
                        .append(" -> ").append(rl).append(" color: #").append(String.format("%08X", argbToRgba(color))).append("\n");
            }
        }
        sb.append("Total entries: ").append(total).append("\n");
        return sb.toString();
    }

    //database
    /**
     * 从数据库加载指定区块的所有 LT 数据到内存缓存。
     * 如果数据库未就绪或区块无数据，则不做任何事。
     */
    private static void loadChunkFromDB(ChunkPos chunkPos) {
        if (!DatabaseManager.isReady()) return;
        // 使用 computeIfAbsent 确保只在区块不存在时才加载
        chunkColorMap.computeIfAbsent(chunkPos, cp -> {
            Map<BlockPos, DataBaseCache.BlockDataEntry> tempMap = new HashMap<>();
            DataBaseCache.loadChunk(cp, MOD_ID, tempMap);
            ConcurrentHashMap<BlockPos, LTBlockData> inner = new ConcurrentHashMap<>();
            for (Map.Entry<BlockPos, DataBaseCache.BlockDataEntry> entry : tempMap.entrySet()) {
                BlockPos pos = entry.getKey();
                DataBaseCache.BlockDataEntry dataEntry = entry.getValue();
                BlockState state = parseBlockStateString(dataEntry.blockStateStr, pos);
                if (state != null) {
                    inner.put(pos.immutable(), new LTBlockData(state, dataEntry.color));
                }
            }
            return inner;
        });
    }
}