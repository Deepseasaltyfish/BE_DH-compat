package com.deepseasaltyfish.BeLodCompat.util;

import com.deepseasaltyfish.BeLodCompat.BeLodCompat;
import com.deepseasaltyfish.BeLodCompat.chunk.ChunkEventHandler;
import com.deepseasaltyfish.BeLodCompat.common.cache.IRBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.common.cache.LTBlockDataCache;
import com.deepseasaltyfish.BeLodCompat.config.ModConfigs;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = BeLodCompat.MODID)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("belodcompat")
                .then(Commands.literal("dumpIR")
                        .then(Commands.argument("dimension", StringArgumentType.string())
                                .executes(ctx -> dumpIRCache(ctx, StringArgumentType.getString(ctx, "dimension")))
                        )
                        .executes(ctx -> dumpIRCache(ctx, getDimension(ctx)))
                )
                .then(Commands.literal("dumpLT")
                        .then(Commands.argument("dimension", StringArgumentType.string())
                                .executes(ctx -> dumpLTCache(ctx, StringArgumentType.getString(ctx, "dimension")))
                        )
                        .executes(ctx -> dumpLTCache(ctx, getDimension(ctx)))
                )
                .then(Commands.literal("showConfig")
                        .executes(ctx -> showConfig(ctx))
                )
                .then(Commands.literal("getLtColor")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .then(Commands.argument("dimension", StringArgumentType.string())
                                                        .executes(ctx -> getLtColorAt(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z"),
                                                                StringArgumentType.getString(ctx, "dimension")))
                                                )
                                                .executes(ctx -> getLtColorAt(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                        IntegerArgumentType.getInteger(ctx, "z"),
                                                        getDimension(ctx)))
                                        )
                                )
                        )
                )
                .then(Commands.literal("getBlockStateOfLT")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .then(Commands.argument("dimension", StringArgumentType.string())
                                                        .executes(ctx -> getBlockStateOfLT(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z"),
                                                                StringArgumentType.getString(ctx, "dimension")))
                                                )
                                                .executes(ctx -> getBlockStateOfLT(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                        IntegerArgumentType.getInteger(ctx, "z"),
                                                        getDimension(ctx)))
                                        )
                                )
                        )
                )
                .then(Commands.literal("getBlockStateOfIR")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .then(Commands.argument("dimension", StringArgumentType.string())
                                                        .executes(ctx -> getBlockStateOfIR(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z"),
                                                                StringArgumentType.getString(ctx, "dimension")))
                                                )
                                                .executes(ctx -> getBlockStateOfIR(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                        IntegerArgumentType.getInteger(ctx, "z"),
                                                        getDimension(ctx)))
                                        )
                                )
                        )
                )
        );
    }

    private static String getDimension(CommandContext<CommandSourceStack> ctx) {
        try {
            Level level = ctx.getSource().getLevel();
            if (level != null) {
                return level.dimension().location().toString();
            }
        } catch (Exception ignored) {}
        return "minecraft:overworld";
    }

    private static boolean isDimensionValid(String dimName) {
        Path dbFile = ChunkEventHandler.getDbFileForDimension(dimName);
        return dbFile != null;
    }

    private static int getLtColorAt(CommandContext<CommandSourceStack> ctx, int x, int y, int z, String dimName) {
        if (!isDimensionValid(dimName)) {
            ctx.getSource().sendFailure(Component.literal("Dimension '" + dimName + "' is not loaded or does not exist."));
            return 0;
        }
        BlockPos pos = new BlockPos(x, y, z);
        int color = LTBlockDataCache.getColorAt(pos, dimName);
        if (color == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("No LT color cached at " + pos + " in dimension " + dimName), false);
        } else {
            String hex = String.format("%08X", BlockDataUtil.argbToRgba(color));
            ctx.getSource().sendSuccess(() -> Component.literal("LT color at " + pos + " in dimension " + dimName + " = #" + hex), false);
        }
        return 1;
    }

    private static int getBlockStateOfLT(CommandContext<CommandSourceStack> ctx, int x, int y, int z, String dimName) {
        if (!isDimensionValid(dimName)) {
            ctx.getSource().sendFailure(Component.literal("Dimension '" + dimName + "' is not loaded or does not exist."));
            return 0;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = LTBlockDataCache.getBlockStateAt(pos, dimName);
        if (state == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("No LT block state cached at " + pos + " in dimension " + dimName), false);
        } else {
            ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            ctx.getSource().sendSuccess(() -> Component.literal("LT block state at " + pos + " in dimension " + dimName + " = " + rl), false);
        }
        return 1;
    }

    private static int getBlockStateOfIR(CommandContext<CommandSourceStack> ctx, int x, int y, int z, String dimName) {
        if (!isDimensionValid(dimName)) {
            ctx.getSource().sendFailure(Component.literal("Dimension '" + dimName + "' is not loaded or does not exist."));
            return 0;
        }
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = IRBlockDataCache.getBlockStateAt(pos, dimName);
        if (state == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("No IR block state cached at " + pos + " in dimension " + dimName), false);
        } else {
            ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            ctx.getSource().sendSuccess(() -> Component.literal("IR block state at " + pos + " in dimension " + dimName + " = " + rl), false);
        }
        return 1;
    }

    private static int dumpIRCache(CommandContext<CommandSourceStack> ctx, String dimName) {
        if (!isDimensionValid(dimName)) {
            ctx.getSource().sendFailure(Component.literal("Dimension '" + dimName + "' is not loaded or does not exist."));
            return 0;
        }
        String dump = IRBlockDataCache.dumpAllEntries(dimName);
        ctx.getSource().sendSuccess(() -> Component.literal(dump), false);
        return 1;
    }

    private static int dumpLTCache(CommandContext<CommandSourceStack> ctx, String dimName) {
        if (!isDimensionValid(dimName)) {
            ctx.getSource().sendFailure(Component.literal("Dimension '" + dimName + "' is not loaded or does not exist."));
            return 0;
        }
        String dump = LTBlockDataCache.dumpAllEntries(dimName);
        ctx.getSource().sendSuccess(() -> Component.literal(dump), false);
        return 1;
    }

    private static int showConfig(CommandContext<CommandSourceStack> ctx) {
        StringBuilder sb = new StringBuilder();
        Field[] fields = ModConfigs.class.getDeclaredFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                field.setAccessible(true);
                String name = field.getName();
                Object value;
                try {
                    if ("overrideIrRailBlockId".equals(name)) {
                        value = ModConfigs.getValidatedOverrideId();
                    } else {
                        value = field.get(null);
                    }
                } catch (IllegalAccessException e) {
                    value = "<error>";
                }
                sb.append(name).append(": ").append(value).append("\n");
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
}