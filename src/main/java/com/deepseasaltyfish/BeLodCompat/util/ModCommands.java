package com.deepseasaltyfish.BeLodCompat.util;

import com.deepseasaltyfish.BeLodCompat.BeLodCompat;
import com.deepseasaltyfish.BeLodCompat.common.ImmersiveRairoading.IRBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.common.LittleTiles.LTBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.config.ModConfigs;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

@Mod.EventBusSubscriber(modid = BeLodCompat.MODID)
public class ModCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("belodcompat")
                .then(Commands.literal("dumpIR")
                        .executes(ctx -> dumpIRCache(ctx))
                )
        );
        dispatcher.register(Commands.literal("belodcompat")
                .then(Commands.literal("dumpLT")
                        .executes(ctx -> dumpLTCache(ctx))
                )
        );
        dispatcher.register(Commands.literal("belodcompat")
                .then(Commands.literal("showConfig")
                        .executes(ctx -> showConfig(ctx))
                )
        );
    }

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
        String dump = new String(
                "debugLogging: " + ModConfigs.debugLogging + "\n" +
                "overrideIrRailBlock: " + ModConfigs.overrideIrRailBlock + "\n" +
                "overrideIrRailBlockId: " + ModConfigs.overrideIrRailBlockId
        );
        ctx.getSource().sendSuccess(() -> Component.literal(dump), false);
        return 1;
    }
}
