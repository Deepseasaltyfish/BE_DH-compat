package com.deepseasaltyfish.BeLodCompat.Chunk;

import com.deepseasaltyfish.BeLodCompat.BeLodCompat;
import com.deepseasaltyfish.BeLodCompat.common.ImmersiveRairoading.IRBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.common.LittleTiles.LTBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = BeLodCompat.MODID)
public class ChunkEventHandler {

    private static final DebugLogger LOGGER = DebugLogger.getLogger(ChunkEventHandler.class);

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        LevelChunk chunk = (LevelChunk) event.getChunk();
        Level level = chunk.getLevel();
        chunk.getBlockEntities().forEach((pos, be) -> {
            CompoundTag beTag = be.saveWithFullMetadata(level.registryAccess());
            LOGGER.debug("onChunkLoad tag: {} at pos: {}", beTag, pos);
            BlockDataUtil.tryExtractBlockData(beTag, pos);
        });
    }

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        IRBlockDataCache.clearAll();
        LTBlockDataCache.clearAll();
        LOGGER.info("Disconnected, cleared all block data Cache");
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        IRBlockDataCache.clearAll();
        LTBlockDataCache.clearAll();
        LOGGER.info("World unloaded, cleared all block data Cache");
    }
}