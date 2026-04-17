package com.deepseasaltyfish.BeLodCompat.common;

import com.deepseasaltyfish.BeLodCompat.common.ImmersiveRairoading.IRBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.common.LittleTiles.LTBlockDataCache;
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
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockReplacer extends DhApiChunkProcessingEvent {
    private static final DebugLogger LOGGER = DebugLogger.getLogger(BlockReplacer.class);
    private static final AtomicBoolean DEAD = new AtomicBoolean(false);

    private static volatile boolean currentOverride = false;
    private static volatile String currentBlockId = "minecraft:soul_sand";
    public static void updateConfig(boolean override, String blockId) {
        currentOverride = override;
        currentBlockId = blockId;
    }

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
        BlockState state = null;

        if (LtBaseId.equals("littletiles:tiles")) {
            state = LTBlockDataCache.getBlockStateAt(pos);
        } else if (IrBaseId.equals("immersiverailroading:block_rail") || IrBaseId.equals("immersiverailroading:block_rail_gag")) {
            state = IRBlockDataCache.getBlockStateAt(pos);
        }else {
            return;
        }

        if (state == null) {
            state = Blocks.AIR.defaultBlockState();
            LOGGER.debug("Found null state at {} in replacer", pos);
        }
        id = ForgeRegistries.BLOCKS.getKey(state.getBlock());

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
