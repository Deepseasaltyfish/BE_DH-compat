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
        String serialString = param.getBlockStateWrapper().getSerialString();//something like littletiles:tiles_STATE_{waterlogged:false}
        if (serialString.startsWith("littletiles:tiles")) {
            param.setOpacity(LodUtil.BLOCK_FULLY_OPAQUE);
            param.setAllowApiColorOverride(true);
        } else {
            param.setAllowApiColorOverride(true);
        }
    }
}