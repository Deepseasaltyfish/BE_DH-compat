package com.deepseasaltyfish.BeLodCompat.common.ImmersiveRairoading;

import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkProcessingEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class IRblocksReplacer extends DhApiChunkProcessingEvent {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(IRblocksReplacer.class);
    private static final AtomicBoolean DEAD = new AtomicBoolean(false);

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

        IDhApiBlockStateWrapper current = e.value.currentBlock;
        String base = IRextractBaseId(current.getSerialString());
        if (!"immersiverailroading:block_rail".equals(base) && !"immersiverailroading:block_rail_gag".equals(base)) return;

        BlockPos pos = new BlockPos((e.value.chunkX << 4) + e.value.relativeBlockPosX,
                e.value.blockPosY,
                (e.value.chunkZ << 4) + e.value.relativeBlockPosZ);

//        BlockState state = IRColorCache.getColor(pos);
//        if (state == null) state = Blocks.SOUL_SAND.defaultBlockState();
        BlockState state = Blocks.SOUL_SAND.defaultBlockState();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        try {
            IDhApiBlockStateWrapper wrapper = DhApi.Delayed.wrapperFactory
                    .getDefaultBlockStateWrapper(id.toString(), e.value.levelWrapper);
            e.value.setBlockOverride(wrapper);
        } catch (IOException ex) {
            LOGGER.error("DH cant package {}", id, ex);
            if (!DEAD.getAndSet(true)) {
                DhApi.events.unbind(DhApiChunkProcessingEvent.class, this.getClass());
            }
        }
    }
}