package com.deepseasaltyfish.BeLodCompat.chunk.mixins.client;

import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
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
        String dimName = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.dimension().location().toString() : "unknown";
        BlockDataUtil.tryExtractBlockData(id, tag, pos, dimName);
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void onChunkLoad(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        int chunkX = packet.getX();
        int chunkZ = packet.getZ();

        ClientboundLevelChunkPacketData data = packet.getChunkData();
        Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer = data.getBlockEntitiesTagsConsumer(chunkX, chunkZ);

        consumer.accept((blockPos, type, tag) -> {
            ResourceLocation rl = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            String id = rl != null ? rl.toString() : null;
            bE_LOD_compat$LOGGER.debug("handleLevelChunkWithLight id: {} tag: {} at pos: {}", id, tag, blockPos);
            String dimName = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.dimension().location().toString() : "unknown";
            BlockDataUtil.tryExtractBlockData(id, tag, blockPos, dimName);
        });
    }
}