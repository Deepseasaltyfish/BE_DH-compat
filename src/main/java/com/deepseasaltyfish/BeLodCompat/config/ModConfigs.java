package com.deepseasaltyfish.BeLodCompat.config;

public class ModConfigs {
    @CfgConfig.Comment("Enable debug logging")
    public static volatile boolean debugLogging = false;

    @CfgConfig.Comment("If true, replace all Immersive Railroading rails with the block specified in 'overrideBlockId'")
    public static volatile boolean overrideIrRailBlock = false;

    @CfgConfig.Comment("Block ID to replace IR rails with (e.g., 'minecraft:stone', 'minecraft:diamond_block')." +
            "If the ID is invalid or the block does not exist, falls back to 'minecraft:soul_sand'.")
    public static volatile String overrideIrRailBlockId = "minecraft:soul_sand";

    @CfgConfig.Comment("Enable database caching (requires restart)")
    public static boolean enableDatabase = true;

    @CfgConfig.Comment("Use asynchronous database writes (may improve performance but could lose data on crash)")
    public static volatile boolean useAsyncDbWrite = true;

    public static void register() {
        CfgConfig.register(ModConfigs.class, "belodcompat.cfg");
    }
}