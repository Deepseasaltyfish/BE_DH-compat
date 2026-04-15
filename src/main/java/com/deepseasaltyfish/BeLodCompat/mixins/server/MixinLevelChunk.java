package com.deepseasaltyfish.BeLodCompat.mixins.server;

import com.deepseasaltyfish.BeLodCompat.common.LittleTiles.LTColorCache;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public class MixinLevelChunk {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(MixinLevelChunk.class);
    @Inject(method = "setLoaded", at = @At("HEAD"))
    private void onChunkUnload(boolean loaded, CallbackInfo ci) {
        if (!loaded) {
            try{
                ChunkPos chunkPos = ((LevelChunk)(Object)this).getPos();
                LTColorCache.removeChunk(chunkPos);
//                IRColorCache.removeChunk(chunkPos);
            }catch (Exception e){
                LOGGER.error("Fail to remove cache at chunk " + this, e);
            }
        }
    }
}
