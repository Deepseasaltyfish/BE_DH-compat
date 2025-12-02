package com.deepseasaltyfish.LtDhCompat.common;

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
    private final AtomicBoolean setup = new AtomicBoolean(false);
    @Override
    public void blockOrBiomeChangedDuringChunkProcessing(DhApiEventParam<EventParam> e)
    {
        // 1. 一次性初始化（只跑一圈）
        if (setup.compareAndSet(false, true)) {
            // 这里可以预加载一些常量，比如默认石头包装器
        }

        // 2. 每方块都跑
        IDhApiBlockStateWrapper current = e.value.currentBlock;
        if (!current.getSerialString().contains("littletiles:tiles")) return;

        LOGGER.warn("114514:正常进入");


        BlockPos pos = new BlockPos((e.value.chunkX << 4) + e.value.relativeBlockPosX,
                e.value.blockPosY,
                (e.value.chunkZ << 4) + e.value.relativeBlockPosZ);

        LOGGER.warn("pos:"+pos);

        BlockState state = LTColorCache.getTrueColor(pos);   // 你自己的缓存
        if (state == null) state = Blocks.AIR.defaultBlockState();

        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        try {
            IDhApiBlockStateWrapper wrapper = DhApi.Delayed.wrapperFactory
                    .getDefaultBlockStateWrapper(id.toString(), e.value.levelWrapper);
            e.value.setBlockOverride(wrapper);
        } catch (IOException ex) {
            LOGGER.error("DH cant package {}", id, ex);
            DhApi.events.unbind(DhApiChunkProcessingEvent.class, this.getClass());
        }
    }
}
