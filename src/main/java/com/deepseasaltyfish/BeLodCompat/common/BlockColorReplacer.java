package com.deepseasaltyfish.BeLodCompat.common;

import com.deepseasaltyfish.BeLodCompat.common.cache.LTBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBlockColorOverrideEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import net.minecraft.core.BlockPos;

public class BlockColorReplacer extends DhApiBlockColorOverrideEvent {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(BlockColorReplacer.class);
    @Override
    public void onBlockColorOverridden(DhApiEventParam<EventParam> eventParam) {
        EventParam param = eventParam.value;
        int originalColor = param.getColorAsInt();

        int x = param.getBlockPosX();
        int y = param.getBlockPosY();
        int z = param.getBlockPosZ();
        BlockPos pos = new BlockPos(x, y, z);

        String dimName = param.getLevelWrapper().getDimensionName();
        int cachedColor = LTBlockDataCache.getColorAt(pos, dimName);

        if (cachedColor != 0 && cachedColor != 0xFFFFFFFF) {
            int blended = BlockDataUtil.multiplyArgb(originalColor, cachedColor);

            LOGGER.debug("origin: #{}, cached: #{}, blended: #{} at {}",
                    String.format("%08X", BlockDataUtil.argbToRgba(originalColor)),
                    String.format("%08X", BlockDataUtil.argbToRgba(cachedColor)),
                    String.format("%08X", BlockDataUtil.argbToRgba(blended)),
                    pos
            );

            int alpha = (blended >> 24) & 0xFF;
            int red   = (blended >> 16) & 0xFF;
            int green = (blended >> 8) & 0xFF;
            int blue  = blended & 0xFF;
            param.setColor(alpha, red, green, blue);
        }
    }
}