package com.deepseasaltyfish.BeLodCompat.config;

import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public class ModConfigs {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(ModConfigs.class);
    @CfgConfig.Comment("Enable debug logging")
    public static volatile boolean debugLogging = false;

    @CfgConfig.Comment("If true, replace all Immersive Railroading rails with the block specified in 'overrideBlockId'")
    public static volatile boolean overrideIrRailBlock = false;

    @CfgConfig.Comment("Block ID to replace IR rails with (e.g., 'minecraft:stone', 'minecraft:diamond_block')." +
            "If the ID is invalid or the block does not exist, falls back to 'minecraft:soul_sand'.")
    private static volatile String overrideIrRailBlockId = "minecraft:soul_sand";

    @CfgConfig.Comment("bedFill color will be correct faster, may slow down speed.")
    public static volatile boolean getIrDataFromParentDirectly = false;

    @CfgConfig.Comment("Replace the color if IR bedFill is Air.")
    public static volatile boolean replaceIrIfAir = false;

    @CfgConfig.Comment("Enable database caching (requires restart)")
    public static boolean enableDatabase = true;

    @CfgConfig.Comment("Use asynchronous database writes (may improve performance but could lose data on crash)")
    public static volatile boolean useAsyncDbWrite = true;

    public static void register() {
        CfgConfig.register(ModConfigs.class, "belodcompat.cfg");
    }

    /**
     * Returns a validated block ID that is guaranteed to exist in the block registry.
     * If the current overrideIrRailBlockId is invalid, falls back to "minecraft:soul_sand".
     *
     * @return a valid block ID
     */
    public static String getValidatedOverrideId() {
        String id = overrideIrRailBlockId;
        if (id == null || id.isEmpty()) {
            return "minecraft:soul_sand";
        }
        try {
            ResourceLocation rl = ResourceLocation.parse(id);
            if (BuiltInRegistries.BLOCK.containsKey(rl)) {
                return id;
            } else {
                LOGGER.warn("Invalid override block ID '{}', using soul_sand", id);
                return "minecraft:soul_sand";
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse override block ID '{}', using soul_sand", id, e);
            return "minecraft:soul_sand";
        }
    }
}