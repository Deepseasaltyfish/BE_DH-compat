package com.deepseasaltyfish.BeLodCompat.common;

import com.deepseasaltyfish.BeLodCompat.common.cache.IRBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.common.cache.LTBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.util.BlockDataUtil;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkProcessingEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockStateReplacer extends DhApiChunkProcessingEvent {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(BlockStateReplacer.class);
    private static final AtomicBoolean DEAD = new AtomicBoolean(false);
    @Override
    public void blockOrBiomeChangedDuringChunkProcessing(DhApiEventParam<EventParam> e)
    {
        if (DEAD.get()) return;

        IDhApiBlockStateWrapper current = e.value.currentBlock;
        String LtBaseId = BlockDataUtil.extractLtBaseId(current.getSerialString());
        String IrBaseId = BlockDataUtil.extractIrBaseId(current.getSerialString());

        ResourceLocation id;
        BlockPos pos = new BlockPos(
                (e.value.chunkX << 4) + e.value.relativeBlockPosX,
                e.value.blockPosY,
                (e.value.chunkZ << 4) + e.value.relativeBlockPosZ
        );

        BlockState state;
        String dimName = e.value.levelWrapper.getDimensionName();

        if (LtBaseId.equals("littletiles:tiles")) {
            int cachedColor = LTBlockDataCache.getColorAt(pos, dimName);
            if (cachedColor != 0 && cachedColor != 0xFFFFFFFF) {//avoid missing AllowApiColorOverride for colored LT block!
                return;
            }
            state = LTBlockDataCache.getBlockStateAt(pos, dimName);
        } else if (IrBaseId.equals("immersiverailroading:block_rail") || IrBaseId.equals("immersiverailroading:block_rail_gag")) {
            state = IRBlockDataCache.getBlockStateAt(pos, dimName);
        } else {
            return;
        }

        if (state == null) {
            state = Blocks.BLACK_WOOL.defaultBlockState();//TODO: make this a debug config option
            LOGGER.debug("Found null state at {} in replacer", pos);
        }
        id = BuiltInRegistries.BLOCK.getKey(state.getBlock());

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