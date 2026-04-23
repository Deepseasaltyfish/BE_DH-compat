package com.deepseasaltyfish.BeLodCompat.chunk.mixins.distanthorizons;

import com.seibel.distanthorizons.common.wrappers.block.BlockStateWrapper_forge;
import com.seibel.distanthorizons.core.util.LodUtil;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/**
 * This mixin class will never be used!
 * */
@Deprecated
@Mixin(BlockStateWrapper_forge.class)
public abstract class MixinBlockStateWrapper_forge {

    @Final
    @Shadow(remap = false)
    @Nullable
    public BlockState blockState;

    @Inject(method = "getOpacity", at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetOpacity(CallbackInfoReturnable<Integer> cir) {
        if (this.blockState != null) {
            String blockName = this.blockState.getBlock().toString();
            if (blockName.equals("Block{littletiles:tiles}")) {
                cir.setReturnValue(LodUtil.BLOCK_FULLY_OPAQUE);
            }
        }
    }
}