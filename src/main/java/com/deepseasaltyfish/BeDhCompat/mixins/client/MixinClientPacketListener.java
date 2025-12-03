package com.deepseasaltyfish.BeDhCompat.mixins.client;
import com.deepseasaltyfish.BeDhCompat.common.LittleTiles.LTColorCache;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
    //init chunk early and unload chunk late, to make sure we don't lost any data
    @Inject(method = "handleBlockEntityData", at = @At("HEAD"))
    private void onReceiveBlockEntity(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
//        if (Config.Common.LodBuilding.convertLTBlock.get()){
            CompoundTag tag = packet.getTag();
            if (tag != null && "littletiles:tiles".equals(tag.getString("id"))) {
                BlockPos pos = packet.getPos();
                CompoundTag contentTag = tag.getCompound("content");
                LTColorCache.extractLTColor(pos, contentTag);
            }
//        }
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void onChunkLoad(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
//        if (Config.Common.LodBuilding.convertLTBlock.get()){
            int chunkX = packet.getX();
            int chunkZ = packet.getZ();

            ClientboundLevelChunkPacketData data = packet.getChunkData();
            Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer = data.getBlockEntitiesTagsConsumer(chunkX, chunkZ);

            consumer.accept((blockPos, type, tag) -> {
                if (tag != null && "littletiles:tiles".equals(tag.getString("id"))) {
                    CompoundTag content = tag.getCompound("content");
                    LTColorCache.extractLTColor(blockPos, content);
                }
            });
//        }
    }
}
