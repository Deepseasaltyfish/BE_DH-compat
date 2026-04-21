package com.deepseasaltyfish.BeLodCompat.util;

import com.deepseasaltyfish.BeLodCompat.common.ImmersiveRairoading.IRBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.common.LittleTiles.LTBlockDataCache;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

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
}
