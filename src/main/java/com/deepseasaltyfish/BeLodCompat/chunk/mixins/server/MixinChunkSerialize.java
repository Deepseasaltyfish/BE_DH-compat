package com.deepseasaltyfish.BeLodCompat.chunk.mixins.server;


import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
public class MixinChunkSerialize
{
    @Unique
    private static final DebugLogger bE_LOD_compat$LOGGER = DebugLogger.getLogger(MixinChunkSerialize.class);
    @Inject(method = "write", at = @At("HEAD"))
    private static void onChunkWrite(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
        if (chunk instanceof LevelChunk levelChunk) {
            levelChunk.getBlockEntities().forEach((pos, be) -> {
                CompoundTag beTag = be.saveWithFullMetadata();
                bE_LOD_compat$LOGGER.debug("onChunkWrite tag: {} at pos: {}", beTag, pos);
                BlockDataUtil.tryExtractBlockData(beTag, pos);
            });
        }
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void onChunkRead(ServerLevel level, PoiManager poiManager, ChunkPos chunkPos, CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir) {
        ChunkAccess chunk = cir.getReturnValue();
        if (chunk instanceof ProtoChunk) {
            ListTag beList = tag.getList("block_entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < beList.size(); i++) {
                CompoundTag beTag = beList.getCompound(i);
                BlockPos pos = BlockEntity.getPosFromTag(beTag);
                bE_LOD_compat$LOGGER.debug("onChunkRead tag: {} at pos: {}", beTag, pos);
                BlockDataUtil.tryExtractBlockData(beTag, pos);
            }
        }
    }
}
