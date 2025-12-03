package com.deepseasaltyfish.BeDhCompat.mixins.client;
import com.deepseasaltyfish.BeDhCompat.common.LittleTiles.LTColorCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientLevel.class)
public class MixinClientLevel {
    @Inject(method = "unload", at = @At("HEAD"))
    private void onChunkUnload(LevelChunk chunk, CallbackInfo ci) {
        ChunkPos pos = chunk.getPos();
        LTColorCache.removeChunk(pos);
    }
}
