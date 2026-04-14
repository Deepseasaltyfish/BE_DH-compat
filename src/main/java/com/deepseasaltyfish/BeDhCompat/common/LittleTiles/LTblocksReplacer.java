package com.deepseasaltyfish.BeDhCompat.common.LittleTiles;

import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkProcessingEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class LTblocksReplacer extends DhApiChunkProcessingEvent {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean DEAD = new AtomicBoolean(false);
    private static String extractBaseId(String name) {
        int cut = name.indexOf(':');
        if (cut == -1) return name;
        int end = name.indexOf('_', cut + 1);
        return end == -1 ? name.substring(cut + 1)
                : name.substring(0, end);
    }

    @Override
    public void blockOrBiomeChangedDuringChunkProcessing(DhApiEventParam<EventParam> e)
    {
        if (DEAD.get()) return;

        IDhApiBlockStateWrapper current = e.value.currentBlock;
        String base = extractBaseId(current.getSerialString());
        if (!base.equals("littletiles:tiles")) return;

        BlockPos pos = new BlockPos((e.value.chunkX << 4) + e.value.relativeBlockPosX,
                e.value.blockPosY,
                (e.value.chunkZ << 4) + e.value.relativeBlockPosZ);


        //TODO:colorize and specially handel blocks containing glass/light/liquid tiles
        BlockState state = LTColorCache.getBlockStateAt(pos);
        if (state == null) state = Blocks.AIR.defaultBlockState();

        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
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