package com.deepseasaltyfish.BeDhCompat.mixins.test;

import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.util.LodUtil;
import loaderCommon.forge.com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper;//emm should not need to mixin fabric side
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(BlockStateWrapper.class)
public abstract class MixinBlockStateWrapper {

    @Shadow(remap = false)
    @Nullable
    public BlockState blockState;

    @Shadow @Final private static DhLogger LOGGER;

    @Inject(method = "calculateOpacity", at = @At("HEAD"), cancellable = true, remap = false)
    private void onCalculateOpacity(CallbackInfoReturnable<Integer> cir) {
        if (this.blockState != null) {
            String blockName = this.blockState.getBlock().toString();
            if (blockName.equals("Block{littletiles:tiles}")) {
//                LOGGER.warn("changed Opacity for LT block");
                cir.setReturnValue(LodUtil.BLOCK_FULLY_OPAQUE);
            }
        }
    }
}
