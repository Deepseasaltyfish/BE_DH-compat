package com.deepseasaltyfish.BeLodCompat.chunk.mixins;

import com.deepseasaltyfish.BeLodCompat.common.cache.IRBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.common.cache.LTBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.util.DebugLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class MixinLevel {

    @Unique
    private static final DebugLogger bE_LOD_compat$LOGGER = DebugLogger.getLogger(MixinLevel.class);

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void onSetBlock(BlockPos pos, BlockState newState, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        BlockState oldState = level.getBlockState(pos);
        if (oldState.hasBlockEntity()) {
            ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(oldState.getBlock());
            String id = rl.toString();
            if (id.equals("littletiles:tiles") || id.equals("immersiverailroading:block_rail") || id.equals("immersiverailroading:block_rail_gag")) {
                if (newState.isAir() || !newState.hasBlockEntity()) {
                    String dimName = level.dimension().location().toString();
                    bE_LOD_compat$LOGGER.debug("Removing cache at {} for dimension {} because oldState = {}, newState = {}", pos, dimName, oldState, newState);
                    LTBlockDataCache.removeAt(pos, dimName);
                    IRBlockDataCache.removeAt(pos, dimName);
                }
            }
        }
    }
}