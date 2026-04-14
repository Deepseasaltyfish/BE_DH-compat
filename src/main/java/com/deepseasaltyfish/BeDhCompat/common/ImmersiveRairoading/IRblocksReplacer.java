package com.deepseasaltyfish.BeDhCompat.common.ImmersiveRairoading;

import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkProcessingEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class IRblocksReplacer extends DhApiChunkProcessingEvent {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean DEAD = new AtomicBoolean(false);
    private static IDhApiBlockStateWrapper SOUL;

    private static String IRextractBaseId(String name) {
        int cut = name.indexOf(':');
        if (cut == -1) return name;
        int second = name.indexOf('_', cut + 1);
        if (second == -1) return name;
        int third  = name.indexOf('_', second + 1);
        return third == -1 ? name.substring(0, second)
                : name.substring(0, third);
    }

    @Override
    public void blockOrBiomeChangedDuringChunkProcessing(DhApiEventParam<EventParam> e) {
        if (DEAD.get()) return;

        if (SOUL == null) {
            try {
                SOUL = DhApi.Delayed.wrapperFactory
                        .getDefaultBlockStateWrapper("minecraft:soul_sand", e.value.levelWrapper);
            } catch (IOException ex) {
                LOGGER.error("fail to preload soul_sand, unbind", ex);
                if (!DEAD.getAndSet(true)) {
                    DhApi.events.unbind(DhApiChunkProcessingEvent.class, this.getClass());
                }
            }
        }

        //TODO:blocks will be optional in config

        String base = IRextractBaseId(e.value.currentBlock.getSerialString());
//        LOGGER.warn("base:"+base);

        if (!base.equals("immersiverailroading:block_rail")) return;

        if (SOUL != null) e.value.setBlockOverride(SOUL);
    }
}