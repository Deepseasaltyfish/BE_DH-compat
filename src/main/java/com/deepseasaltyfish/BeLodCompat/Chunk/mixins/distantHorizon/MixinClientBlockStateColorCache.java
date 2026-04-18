package com.deepseasaltyfish.BeLodCompat.Chunk.mixins.distantHorizon;

import com.deepseasaltyfish.BeLodCompat.Chunk.mixins.client.MixinClientPacketListener;
import com.deepseasaltyfish.BeLodCompat.common.LittleTiles.LTBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import loaderCommon.neoforge.com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import loaderCommon.neoforge.com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateColorCache;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Deprecated
@Mixin(ClientBlockStateColorCache.class)
public abstract class MixinClientBlockStateColorCache {//TODO: this cant work
    private static final DebugLogger LOGGER = DebugLogger.getLogger(MixinClientBlockStateColorCache.class);
//    @Inject(method = "getColor", at = @At("RETURN"), cancellable = true, remap = false)
//    private void onGetColorReturn(BiomeWrapper biomeWrapper, FullDataSourceV2 fullDataSource, DhBlockPos blockPos, CallbackInfoReturnable<Integer> cir) {
//        int originalColor = cir.getReturnValue();
//        BlockPos mcPos = new BlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
//        int cachedColor = LTBlockDataCache.getColorAt(mcPos);
//        if (cachedColor != 0) {
//            int blended = multiplyArgb(originalColor, cachedColor);
//            LOGGER.debug("origin: #{}, cached: #{}, blended: #{} at {}",
//                    String.format("%08X", LTBlockDataCache.argbToRgba(originalColor)),
//                    String.format("%08X", LTBlockDataCache.argbToRgba(cachedColor)),
//                    String.format("%08X", LTBlockDataCache.argbToRgba(blended)),
//                    blockPos
//            );
//            cir.setReturnValue(blended);
//        }
//    }

    /**
     * Multiply two ARGB colors.
     * RGB channels are multiplied (component-wise) and normalized to 0-255.
     * Alpha uses the alpha from the overlay color (cached).
     */
    private static int multiplyArgb(int base, int overlay) {
        int baseA = (base >> 24) & 0xFF;
        int baseR = (base >> 16) & 0xFF;
        int baseG = (base >> 8) & 0xFF;
        int baseB = base & 0xFF;

        int overA = (overlay >> 24) & 0xFF;
        int overR = (overlay >> 16) & 0xFF;
        int overG = (overlay >> 8) & 0xFF;
        int overB = overlay & 0xFF;

        // Multiply RGB (normalized)
        int r = (baseR * overR) / 255;
        int g = (baseG * overG) / 255;
        int b = (baseB * overB) / 255;
        int a = (baseA * overA) / 255;

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}