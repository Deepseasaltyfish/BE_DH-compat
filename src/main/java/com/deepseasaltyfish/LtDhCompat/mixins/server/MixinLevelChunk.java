package com.deepseasaltyfish.LtDhCompat.mixins.server;

import com.deepseasaltyfish.LtDhCompat.common.LTColorCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public class MixinLevelChunk {
    @Inject(method = "setLoaded", at = @At("HEAD"))
    private void onChunkUnload(boolean loaded, CallbackInfo ci) {
        if (!loaded) {
            try{
                ChunkPos chunkPos = ((LevelChunk)(Object)this).getPos();
                LTColorCache.removeChunk(chunkPos);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
