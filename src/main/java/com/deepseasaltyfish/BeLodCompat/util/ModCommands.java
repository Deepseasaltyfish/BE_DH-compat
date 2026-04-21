package com.deepseasaltyfish.BeLodCompat.util;

import com.deepseasaltyfish.BeLodCompat.BeLodCompat;
import com.deepseasaltyfish.BeLodCompat.common.cache.IRBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.common.cache.LTBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.config.ModConfigs;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

@Mod.EventBusSubscriber(modid = BeLodCompat.MODID)
public class ModCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("belodcompat")
                .then(Commands.literal("dumpIR")
                        .executes(ctx -> dumpIRCache(ctx))
                )
                .then(Commands.literal("dumpLT")
                        .executes(ctx -> dumpLTCache(ctx))
                )
                .then(Commands.literal("showConfig")
                        .executes(ctx -> showConfig(ctx))
                )
                .then(Commands.literal("getColor")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> getColorAt(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                        IntegerArgumentType.getInteger(ctx, "z")))
                                        )
                                )
                        )
                )
        );
    }

    private static int getColorAt(CommandContext<CommandSourceStack> ctx, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        int color = LTBlockDataCache.getColorAt(pos);
        if (color == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("No LT color cached at " + pos), false);
        } else {
            String hex = String.format("%08X", BlockDataUtil.argbToRgba(color));
            ctx.getSource().sendSuccess(() -> Component.literal("LT color at " + pos + " = #" + hex), false);
        }
        return 1;
    }

    // 其余方法保持不变...
    private static int dumpIRCache(CommandContext<CommandSourceStack> ctx) {
        String dump = IRBlockDataCache.dumpAllEntries();
        ctx.getSource().sendSuccess(() -> Component.literal(dump), false);
        return 1;
    }

    private static int dumpLTCache(CommandContext<CommandSourceStack> ctx) {
        String dump = LTBlockDataCache.dumpAllEntries();
        ctx.getSource().sendSuccess(() -> Component.literal(dump), false);
        return 1;
    }

    private static int showConfig(CommandContext<CommandSourceStack> ctx) {
        String dump = "debugLogging: " + ModConfigs.debugLogging + "\n" +
                "overrideIrRailBlock: " + ModConfigs.overrideIrRailBlock + "\n" +
                "overrideIrRailBlockId: " + ModConfigs.getValidatedOverrideId();
        ctx.getSource().sendSuccess(() -> Component.literal(dump), false);
        return 1;
    }
}