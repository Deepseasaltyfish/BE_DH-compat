package com.deepseasaltyfish.BeLodCompat;

import com.deepseasaltyfish.BeLodCompat.common.ImmersiveRairoading.IRblocksReplacer;
import com.deepseasaltyfish.BeLodCompat.common.LittleTiles.LTblocksReplacer;
import com.deepseasaltyfish.BeLodCompat.config.ModConfigs;
import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkProcessingEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(BeLodCompat.MODID)
public class BeLodCompat {
    public static final String MODID = "be_lod_compat";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BeLodCompat(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onClientSetup);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, ModConfigs.COMMON_SPEC, ModConfigs.CONFIG_FILE_NAME);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LTblocksReplacer ltReplacer = new LTblocksReplacer();
        DhApi.events.bind(DhApiChunkProcessingEvent.class, ltReplacer);

        IRblocksReplacer irReplacer = new IRblocksReplacer();
        DhApi.events.bind(DhApiChunkProcessingEvent.class, irReplacer);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
    }

    // 服务器启动事件示例
    @net.neoforged.bus.api.SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }
}