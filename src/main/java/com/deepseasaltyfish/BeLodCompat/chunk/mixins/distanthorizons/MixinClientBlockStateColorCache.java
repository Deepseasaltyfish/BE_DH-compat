package com.deepseasaltyfish.BeLodCompat.chunk.mixins.distanthorizons;

import com.deepseasaltyfish.BeLodCompat.cache.compat.LTBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos;
import loaderCommon.forge.com.seibel.distanthorizons.common.wrappers.block.BiomeWrapper;
import loaderCommon.forge.com.seibel.distanthorizons.common.wrappers.block.ClientBlockStateColorCache;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * This only works all properly in MERGE_SAME_BLOCKS!
 * @see com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2
 * @see com.seibel.distanthorizons.core.dataObjects.transformers.FullDataOcclusionCuller
 * @see com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode
 * */
@Deprecated
@Mixin(ClientBlockStateColorCache.class)
public abstract class MixinClientBlockStateColorCache {//TODO: we will remove this mixin after DH official API get ready
    @Unique
    private static final DebugLogger bE_LOD_compat$LOGGER = DebugLogger.getLogger(MixinClientBlockStateColorCache.class);

    @Inject(method = "getColor", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetColorReturn(BiomeWrapper biomeWrapper, FullDataSourceV2 fullDataSource, DhBlockPos blockPos, CallbackInfoReturnable<Integer> cir) {
        int originalColor = cir.getReturnValue();
        BlockPos mcPos = new BlockPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        int cachedColor = LTBlockDataCache.getColorAt(mcPos);
        if (cachedColor != 0 && cachedColor != 0xFFFFFFFF) {
            int blended = BlockDataUtil.multiplyArgb(originalColor, cachedColor);
            bE_LOD_compat$LOGGER.debug("origin: #{}, cached: #{}, blended: #{} at {}",
                    String.format("%08X", BlockDataUtil.argbToRgba(originalColor)),
                    String.format("%08X", BlockDataUtil.argbToRgba(cachedColor)),
                    String.format("%08X", BlockDataUtil.argbToRgba(blended)),
                    blockPos
            );
            cir.setReturnValue(blended);
        }
    }
}