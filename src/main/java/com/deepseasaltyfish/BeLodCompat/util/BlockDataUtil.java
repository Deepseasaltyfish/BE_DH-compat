package com.deepseasaltyfish.BeLodCompat.util;

import com.deepseasaltyfish.BeLodCompat.cache.compat.IRBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.cache.compat.LTBlockDataCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class BlockDataUtil {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(BlockDataUtil.class);

    public static boolean tryExtractBlockData(CompoundTag tag, BlockPos pos) {
        if (tag != null) {
            String id = tag.getString("id");
            return tryExtractBlockData(id, tag, pos);
        }
        LOGGER.debug("null tag at {}", pos);
        return false;
    }


    public static boolean tryExtractBlockData(String id, CompoundTag tag, BlockPos pos) {
        if (id == null || tag == null) return false;
        if ("littletiles:tiles".equals(id)) {
            CompoundTag contentTag = tag.getCompound("content");
            return LTBlockDataCache.extractLTColor(pos, contentTag);
        } else if ("immersiverailroading:block_rail".equals(id) || "immersiverailroading:block_rail_gag".equals(id)) {
            CompoundTag instanceDataTag = tag.getCompound("instanceData");
            boolean isParent = "immersiverailroading:block_rail".equals(id);
            return IRBlockDataCache.extractIRColor(pos, instanceDataTag, isParent);
        }
        return false;
    }

    public static String extractIrBaseId(String name) {
        int cut = name.indexOf(':');
        if (cut == -1) return name;
        int second = name.indexOf('_', cut + 1);
        if (second == -1) return name;
        int third  = name.indexOf('_', second + 1);
        return third == -1 ? name.substring(0, second)
                : name.substring(0, third);
    }

    public static String extractLtBaseId(String name) {
        int cut = name.indexOf(':');
        if (cut == -1) return name;
        int end = name.indexOf('_', cut + 1);
        return end == -1 ? name.substring(cut + 1)
                : name.substring(0, end);
    }

    /**
     * Extracts the pure block ID from a block state string that may contain properties in square brackets.
     * Example: "minecraft:oak_leaves[distance=7,persistent=false]" -> "minecraft:oak_leaves"
     *
     * @param blockStateStr the full block state string (may be null or empty)
     * @return the extracted block ID, or null if input is invalid
     */
    public static String extractBlockName(String blockStateStr) {
        if (blockStateStr == null || blockStateStr.isEmpty()) {
            return null;
        }
        int stateStart = blockStateStr.indexOf('[');
        if (stateStart != -1) {
            return blockStateStr.substring(0, stateStart);
        }
        return blockStateStr;
    }

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

    /**
     * Converts a block state string (may contain properties in square brackets) to the block's default BlockState.
     *
     * @param input             raw string from cache (may be null or empty, or contain "[...]" suffixes)
     * @param pos               block position for logging
     * @param logger            logger instance to use for error/warning messages
     * @param handleMissingTile if true, input "littletiles:missing" will be converted to STONE default state;
     *                          otherwise it will be treated as a normal block ID (likely resulting in AIR)
     * @return the default BlockState of the block, or Blocks.AIR if parsing fails
     */
    public static BlockState toDefaultBlockState(String input, BlockPos pos, DebugLogger logger, boolean handleMissingTile) {
        if (input == null || input.isEmpty()) {
            logger.error("null or empty block state string at {}", pos);
            return Blocks.AIR.defaultBlockState();
        }

        // Special handling for LittleTiles missing tile
        if (handleMissingTile && "littletiles:missing".equals(input)) {
            logger.debug("Found \"littletiles:missing\" value at {}, converted to stone", pos);
            return Blocks.STONE.defaultBlockState();
        }

        String blockName = extractBlockName(input);
        if (blockName == null) {
            logger.error("Failed to extract block name from '{}' at {}", input, pos);
            return Blocks.AIR.defaultBlockState();
        }

        try {
            ResourceLocation blockId = ResourceLocation.parse(blockName);
            Block block = BuiltInRegistries.BLOCK.get(blockId);
            if (block == Blocks.AIR) {
                logger.warn("Unknown block ID '{}' at {}, using air", blockName, pos);
            }
            return block.defaultBlockState();
        } catch (Exception e) {
            logger.error("Failed to parse block ID '{}' from string {} at {}", blockName, input, pos, e);
            return Blocks.AIR.defaultBlockState();
        }
    }

}
