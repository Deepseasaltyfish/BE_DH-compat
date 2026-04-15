package com.deepseasaltyfish.BeLodCompat.mixins.server;


import com.deepseasaltyfish.BeLodCompat.common.LittleTiles.LTColorCache;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
public class MixinChunkSerialize
{
    @Inject(method = "write", at = @At("HEAD"))
    private static void onChunkWrite(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
        if (chunk instanceof LevelChunk levelChunk) {
            levelChunk.getBlockEntities().forEach((pos, be) -> {
                CompoundTag beTag = be.saveWithFullMetadata();
                String id = beTag.getString("id");

                if ("littletiles:tiles".equals(id)) {
                    CompoundTag contentTag = beTag.getCompound("content");
                    LTColorCache.extractLTColor(pos, contentTag);
                }

//                if ("immersiverailroading:block_rail".equals(id) || "immersiverailroading:block_rail_gag".equals(id)) {
//                    boolean isParent = id.equals("immersiverailroading:block_rail");
//                    CompoundTag instanceDataTag = beTag.getCompound("instanceData");
//                    IRColorCache.extractIRColor(pos, instanceDataTag,isParent);
//                }
            });
        }
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void onChunkRead(ServerLevel level, PoiManager poiManager, ChunkPos chunkPos, CompoundTag tag, CallbackInfoReturnable<ProtoChunk> cir) {
        ChunkAccess chunk = cir.getReturnValue();
        if (chunk instanceof ProtoChunk) {
            ListTag beList = tag.getList("block_entities", 10);
            for (int i = 0; i < beList.size(); i++) {
                CompoundTag beTag = beList.getCompound(i);
                String id = beTag.getString("id");
                BlockPos pos = BlockEntity.getPosFromTag(beTag);

                if ("littletiles:tiles".equals(id)) {
                    CompoundTag contentTag = beTag.getCompound("content");
                    LTColorCache.extractLTColor(pos, contentTag);
                }

//                if ("immersiverailroading:block_rail".equals(id) || "immersiverailroading:block_rail_gag".equals(id)) {
//                    boolean isParent = id.equals("immersiverailroading:block_rail");
//                    CompoundTag instanceDataTag = beTag.getCompound("instanceData");
//                    IRColorCache.extractIRColor(pos, instanceDataTag,isParent);
//                }
            }
        }
    }
}
