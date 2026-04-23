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
import net.minecraft.world.level.ChunkPos;
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
        String dimName = level.dimension().location().toString(); // get dimension name
        chunk.getBlockEntities().forEach((pos, be) -> {
            CompoundTag beTag = be.saveWithFullMetadata();
            LOGGER.debug("onChunkWrite tag: {} at pos: {}", beTag, pos);
            BlockDataUtil.tryExtractBlockData(beTag, pos, dimName); // pass dimension name
        });
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        LevelChunk chunk = (LevelChunk) event.getChunk();
        Level level = chunk.getLevel();
        if (level.isClientSide()) return;
        String dimName = level.dimension().location().toString();
        ChunkPos pos = chunk.getPos();
        LTBlockDataCache.removeChunkInMemory(pos, dimName);
        IRBlockDataCache.removeChunkInMemory(pos, dimName);
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

    // replace pendingDbFile with pendingLevel and pendingDimName
    private static Level pendingLevel = null;
    private static String pendingDimName = null;
    // add to member variable area
    private static final ConcurrentHashMap<String, Path> levelDbMap = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        Level level = (Level) event.getLevel();
        if (!level.isClientSide() && level.getServer() == null) return;

        // For multiplayer client, delay opening until login
        if (level.isClientSide() && Minecraft.getInstance().getSingleplayerServer() == null) {
            pendingLevel = level;
            pendingDimName = level.dimension().location().getPath().replace('/', '_');
            LOGGER.debug("Database open delayed for multiplayer client");
            return;
        }

        // Singleplayer or server side, open immediately
        Path dbFile = getDbFileForLevel(level);
        if (dbFile == null) return;

        openDatabase(level, dbFile);   // pass level
        if (level.isClientSide()) checkDhCompressionMode();
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        String sanitizedIp;
        java.net.SocketAddress remoteAddr = event.getConnection().getRemoteAddress();
        if (remoteAddr instanceof java.net.InetSocketAddress) {
            java.net.InetSocketAddress addr = (java.net.InetSocketAddress) remoteAddr;
            String host = addr.getHostString();
            int port = addr.getPort();
            String ip = host + "_" + port;
            sanitizedIp = ip.replace(':', '_').replace('/', '_').replace('\\', '_');
        } else {
            sanitizedIp = "singleplayer_local";//will not be used truly
        }
        WorldPathUtil.setCachedServerIp(sanitizedIp);

        if (pendingLevel != null && pendingDimName != null) {
            Path dbFile = getDbFileForLevel(pendingLevel);
            if (dbFile != null) {
                openDatabase(pendingLevel, dbFile);
                LOGGER.info("Database opened after login");
                if (pendingLevel.isClientSide()) checkDhCompressionMode();
            }
            pendingLevel = null;
            pendingDimName = null;
        }
    }

    /**
     * Generates the database file path for a given level.
     *
     * @param level the level (world)
     * @return the database file path, or null if creation fails
     */
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

    /**
     * Retrieves the database file path for a given dimension name.
     *
     * @param dimName the dimension name (e.g. "minecraft:overworld")
     * @return the database file path, or null if not found
     */
    public static Path getDbFileForDimension(String dimName) {
        return levelDbMap.get(dimName);
    }

    /**
     * Opens a database and initializes related caches for the given level and file path.
     *
     * @param level  the level (world)
     * @param dbFile the database file path
     */
    private static void openDatabase(Level level, Path dbFile) {
        LOGGER.info("openDatabase called with dbFile: {}", dbFile);
        DatabaseManager.open(dbFile);
        DataBaseCache.initTable(dbFile);
        levelDbMap.put(level.dimension().location().toString(), dbFile);
    }
}