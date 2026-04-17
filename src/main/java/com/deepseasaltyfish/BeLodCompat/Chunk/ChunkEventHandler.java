package com.deepseasaltyfish.BeLodCompat.Chunk;

import com.deepseasaltyfish.BeLodCompat.BeLodCompat;
import com.deepseasaltyfish.BeLodCompat.common.ImmersiveRairoading.IRBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.common.LittleTiles.LTBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

    // Clear caches when a world unloads (both sides)
    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        IRBlockDataCache.clearAll();
        LTBlockDataCache.clearAll();
        LOGGER.info("World unloaded, Cleared all block data Cache");
    }
}