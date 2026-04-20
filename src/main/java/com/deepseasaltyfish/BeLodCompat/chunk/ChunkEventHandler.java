package com.deepseasaltyfish.BeLodCompat.chunk;

import com.deepseasaltyfish.BeLodCompat.BeLodCompat;
import com.deepseasaltyfish.BeLodCompat.common.DataBase.DataBaseCache;
import com.deepseasaltyfish.BeLodCompat.common.ImmersiveRairoading.IRBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.common.LittleTiles.LTBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.dataBase.DatabaseManager;
import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import com.deepseasaltyfish.BeLodCompat.util.WorldPathUtil;
import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.core.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = BeLodCompat.MODID)
public class ChunkEventHandler {

    private static final DebugLogger LOGGER = DebugLogger.getLogger(ChunkEventHandler.class);
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        LevelChunk chunk = (LevelChunk) event.getChunk();
        chunk.getBlockEntities().forEach((pos, be) -> {
            CompoundTag beTag = be.saveWithFullMetadata();
            LOGGER.debug("onChunkWrite tag: {} at pos: {}", beTag, pos);
            BlockDataUtil.tryExtractBlockData(beTag, pos);
        });
    }

    // Clear caches on client disconnect (only client side)
    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        IRBlockDataCache.clearAll();
        LTBlockDataCache.clearAll();
        LOGGER.info("Disconnected, cleared all block data Cache");
    }

    // Clear caches and close DB when a world unloads (both sides)
    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        IRBlockDataCache.clearAll();
        LTBlockDataCache.clearAll();
        LOGGER.info("World unloaded, Cleared all block data Cache");
        if (event.getLevel().isClientSide()) {
            DatabaseManager.close();
            LOGGER.info("Closed database for client level");
        }
    }

    // 世界加载时打开数据库
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        Level level = (Level) event.getLevel();
        if (!level.isClientSide()) return; // 只处理客户端

        Path worldRoot = WorldPathUtil.getWorldRootPath(level);
        String dimName = level.dimension().location().getPath().replace('/', '_');
        Path dbFile = worldRoot.resolve("bedhcompat").resolve(dimName + ".db");
        LOGGER.info("World root path: {}", worldRoot);

        EDhApiWorldCompressionMode mode = Config.Common.LodBuilding.worldCompression.get();
        if (mode == EDhApiWorldCompressionMode.VISUALLY_EQUAL) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal(
                                "[BeLodCompat] Warning: Distant Horizons compression mode is set to VISUALLY_EQUAL. " +
                                "For correct LittleTiles colors, please change it to MERGE_SAME_BLOCKS in DH config."
                        )
                );
            }
        }

        DatabaseManager.open(dbFile);
        DataBaseCache.initTable();
    }
}