package com.deepseasaltyfish.BeLodCompat.chunk.mixins.client;

import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
    //init chunk early and unload chunk late, to make sure we don't lost any data
    @Unique
    private static final DebugLogger bE_LOD_compat$LOGGER = DebugLogger.getLogger(MixinClientPacketListener.class);
    @Inject(method = "handleBlockEntityData", at = @At("HEAD"))
    private void onReceiveBlockEntity(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
        CompoundTag tag = packet.getTag();
        if (tag == null) return;

        BlockEntityType<?> type = packet.getType();
        ResourceLocation rl = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        String id = rl != null ? rl.toString() : null;
        BlockPos pos = packet.getPos();
        bE_LOD_compat$LOGGER.debug("onReceiveBlockEntity id: {} tag: {} at pos: {}", id, tag, pos);
        BlockDataUtil.tryExtractBlockData(id, tag, pos);
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void onChunkLoad(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        int chunkX = packet.getX();
        int chunkZ = packet.getZ();

        ClientboundLevelChunkPacketData data = packet.getChunkData();
        Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer = data.getBlockEntitiesTagsConsumer(chunkX, chunkZ);

        consumer.accept((blockPos, type, tag) -> {
            ResourceLocation rl = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            String id = rl != null ? rl.toString() : null;
            bE_LOD_compat$LOGGER.debug("handleLevelChunkWithLight id: {} tag: {} at pos: {}", id, tag, blockPos);
            BlockDataUtil.tryExtractBlockData(id, tag, blockPos);
        });
    }
}
