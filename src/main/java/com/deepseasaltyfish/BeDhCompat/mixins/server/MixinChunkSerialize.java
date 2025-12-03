package com.deepseasaltyfish.BeDhCompat.mixins.server;


import com.deepseasaltyfish.BeDhCompat.common.LTColorCache;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
public class MixinChunkSerialize
{

    private static final Logger LOGGER = LogManager.getLogger();



    @Inject(method = "write", at = @At("HEAD"))
    private static void onChunkWrite(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
        if (chunk instanceof LevelChunk levelChunk) {//&& Config.Common.LodBuilding.convertLTBlock.get()
            levelChunk.getBlockEntities().forEach((pos, be) -> {
                CompoundTag beTag = be.saveWithFullMetadata();
                if (beTag != null && "littletiles:tiles".equals(beTag.getString("id"))) {
                    CompoundTag contentTag = beTag.getCompound("content");
                    LTColorCache.extractLTColor(pos, contentTag);
                }
            });
        }
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void onChunkRead(ServerLevel level, PoiManager poiManager, ChunkPos chunkPos, CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir) {
        ChunkAccess chunk = cir.getReturnValue();
        if (chunk instanceof ProtoChunk) {//&& Config.Common.LodBuilding.convertLTBlock.get()
            ListTag beList = tag.getList("block_entities", 10);
            for (int i = 0; i < beList.size(); i++) {
                CompoundTag beTag = beList.getCompound(i);
                if (beTag != null && "littletiles:tiles".equals(beTag.getString("id"))) {
                    BlockPos pos = BlockEntity.getPosFromTag(beTag);
                    CompoundTag contentTag = beTag.getCompound("content");
                    LTColorCache.extractLTColor(pos, contentTag);
                }
            }
        }
    }



}
