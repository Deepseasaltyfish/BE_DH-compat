package com.deepseasaltyfish.BeLodCompat.chunk;

import com.deepseasaltyfish.BeLodCompat.BeLodCompat;
import com.deepseasaltyfish.BeLodCompat.common.DataBaseCache;
import com.deepseasaltyfish.BeLodCompat.common.cache.IRBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.common.cache.LTBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DatabaseManager;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import com.deepseasaltyfish.BeLodCompat.util.WorldPathUtil;
import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.core.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = BeLodCompat.MODID)
public class ChunkEventHandler {

    private static final DebugLogger LOGGER = DebugLogger.getLogger(ChunkEventHandler.class);

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        Level level = chunk.getLevel();
        String dimName = level.dimension().location().toString(); // 获取维度名称
        chunk.getBlockEntities().forEach((pos, be) -> {
            CompoundTag beTag = be.saveWithFullMetadata();
            LOGGER.debug("onChunkWrite tag: {} at pos: {}", beTag, pos);
            BlockDataUtil.tryExtractBlockData(beTag, pos, dimName); // 传入 dimName
        });
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        IRBlockDataCache.clearAll();
        LTBlockDataCache.clearAll();
        levelDbMap.values().forEach(DatabaseManager::close);
        levelDbMap.clear();
        LOGGER.info("Disconnected, cleared all block data Cache and closed all DB connections");
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        Level level = (Level) event.getLevel();
        String dimName = level.dimension().location().toString();
        Path dbFile = levelDbMap.remove(dimName);
        if (dbFile != null) {
            DatabaseManager.close(dbFile);
            IRBlockDataCache.clearForDimension(dimName);
            LTBlockDataCache.clearForDimension(dimName);
            LOGGER.info("Closed database and cleared cache for dimension: {}", dimName);
        } else {
            LOGGER.info("No stored dbFile for dimension: {}", dimName);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        levelDbMap.values().forEach(DatabaseManager::close);
        levelDbMap.clear();
        LOGGER.info("Server stopped, closed all remaining DB connections");
    }
    @OnlyIn(Dist.CLIENT)
    private static void checkDhCompressionMode() {
        EDhApiWorldCompressionMode mode = Config.Common.LodBuilding.worldCompression.get();
        if (mode == EDhApiWorldCompressionMode.VISUALLY_EQUAL && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal(
                            "[BeLodCompat] Warning: Distant Horizons compression mode is set to VISUALLY_EQUAL. " +
                                    "For correct LittleTiles colors, please change it to MERGE_SAME_BLOCKS in DH config."
                    )
            );
        }
    }

    // 替换 pendingDbFile 为 pendingLevel 和 pendingDimName
    private static Level pendingLevel = null;
    private static String pendingDimName = null;
    // 添加在成员变量区域
    private static final ConcurrentHashMap<String, Path> levelDbMap = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        Level level = (Level) event.getLevel();
        if (!level.isClientSide() && level.getServer() == null) return;

        // 如果是客户端多人游戏，延迟到登录后再打开
        if (level.isClientSide() && Minecraft.getInstance().getSingleplayerServer() == null) {
            pendingLevel = level;
            pendingDimName = level.dimension().location().getPath().replace('/', '_');
            LOGGER.debug("Database open delayed for multiplayer client");
            return;
        }

        // 单机或服务端立即打开
        Path dbFile = getDbFileForLevel(level);
        if (dbFile == null) return;

        openDatabase(level, dbFile);   // 传入 level
        if (level.isClientSide()) checkDhCompressionMode();
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        String remoteAddr = event.getConnection().getRemoteAddress().toString();
        String ip = remoteAddr.substring(1).replace('/', '_').replace(':', '_');
        WorldPathUtil.setCachedServerIp(ip);

        if (pendingLevel != null && pendingDimName != null) {
            Path dbFile = getDbFileForLevel(pendingLevel);
            if (dbFile != null) {
                openDatabase(pendingLevel, dbFile);   // 传入 pendingLevel
                LOGGER.info("Database opened after login");
                if (pendingLevel.isClientSide()) checkDhCompressionMode();
            }
            pendingLevel = null;
            pendingDimName = null;
        }
    }

    private static Path getDbFileForLevel(Level level) {
        Path worldRoot = WorldPathUtil.getWorldRootPath(level);
        String dimName = level.dimension().location().getPath().replace('/', '_');
        Path dbFile;
        boolean isMultiplayerClient = worldRoot.toString().contains("belodcompat_servers");
        if (isMultiplayerClient) {
            dbFile = worldRoot.resolve(dimName + ".db");
        } else {
            Path dbRoot = worldRoot.resolve("belodcompat");
            dbFile = dbRoot.resolve(dimName + ".db");
            try {
                java.nio.file.Files.createDirectories(dbRoot);
            } catch (IOException e) {
                LOGGER.error("Failed to create database directory", e);
                return null;
            }
        }
        LOGGER.info("getDbFileForLevel: level={}, worldRoot={}, dimName={}, result={}", level, worldRoot, dimName, dbFile);
        return dbFile;
    }

    public static Path getDbFileForDimension(String dimName) {
        return levelDbMap.get(dimName);
    }

    // 打开数据库并初始化相关缓存
    private static void openDatabase(Level level, Path dbFile) {
        LOGGER.info("openDatabase called with dbFile: {}", dbFile);
        DatabaseManager.open(dbFile);
        DataBaseCache.initTable(dbFile);
        levelDbMap.put(level.dimension().location().toString(), dbFile);
    }
}