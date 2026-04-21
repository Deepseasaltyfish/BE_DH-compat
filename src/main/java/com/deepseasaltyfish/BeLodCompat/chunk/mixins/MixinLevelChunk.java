package com.deepseasaltyfish.BeLodCompat.chunk.mixins;

import com.deepseasaltyfish.BeLodCompat.common.cache.IRBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.common.cache.LTBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public class MixinLevelChunk {
    @Unique
    private static final DebugLogger bE_LOD_compat$LOGGER = DebugLogger.getLogger(MixinLevelChunk.class);
    @Inject(method = "setLoaded", at = @At("HEAD"))
    private void onChunkUnload(boolean loaded, CallbackInfo ci) {
        if (!loaded) {
            try{
                ChunkPos chunkPos = ((LevelChunk)(Object)this).getPos();
                LTBlockDataCache.removeChunkInMemory(chunkPos);
                IRBlockDataCache.removeChunkInMemory(chunkPos);
            }catch (Exception e){
                bE_LOD_compat$LOGGER.error("Fail to remove cache at chunk " + this, e);
            }
        }
    }
}
