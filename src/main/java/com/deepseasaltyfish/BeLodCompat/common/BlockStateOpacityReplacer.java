package com.deepseasaltyfish.BeLodCompat.common;

import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBlockStateWrapperCreatedEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.core.util.LodUtil;

public class BlockStateOpacityReplacer extends DhApiBlockStateWrapperCreatedEvent {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(BlockStateOpacityReplacer.class);
    @Override
    public void blockStateWrapperCreated(DhApiEventParam<EventParam> eventParam) {
        EventParam param = eventParam.value;
        String serialString = param.getBlockStateWrapper().getSerialString();
        if (serialString.contains("littletiles:tiles")) {
            LOGGER.info(serialString);
            param.setOpacity(LodUtil.BLOCK_FULLY_OPAQUE);
        }
    }
}