package com.deepseasaltyfish.BeDhCompat.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

public class ModConfigs {
    public static class Common {
        public final ForgeConfigSpec.BooleanValue debugLogging;

        Common(ForgeConfigSpec.Builder builder) {
            builder.comment("Common settings")
                    .push("common");
            debugLogging = builder
                    .comment("Enable debug logging")
                    .define("debugLogging", false);
            builder.pop();
        }
    }

    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        final Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "bedhcompat-common.toml");
    }

    public static boolean isDebugLogging() {
        return COMMON.debugLogging.get();
    }
}