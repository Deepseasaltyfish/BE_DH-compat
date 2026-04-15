package com.deepseasaltyfish.BeLodCompat.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ModConfigs {
    public static final String CONFIG_FILE_NAME = "belodcompat-common.toml";

    public static class Common {
        public final ModConfigSpec.BooleanValue debugLogging;

        Common(ModConfigSpec.Builder builder) {
            builder.comment("Common settings")
                    .push("common");
            debugLogging = builder
                    .comment("Enable debug logging")
                    .define("debugLogging", false);
            builder.pop();
        }
    }

    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    static {
        final Pair<Common, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static boolean isDebugLogging() {
        return COMMON.debugLogging.get();
    }
}