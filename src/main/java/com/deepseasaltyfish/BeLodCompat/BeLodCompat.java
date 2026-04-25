package com.deepseasaltyfish.BeLodCompat;

import com.deepseasaltyfish.BeLodCompat.common.BlockColorReplacer;
import com.deepseasaltyfish.BeLodCompat.common.BlockStateOpacityReplacer;
import com.deepseasaltyfish.BeLodCompat.common.BlockStateReplacer;
import com.deepseasaltyfish.BeLodCompat.config.ModConfigs;
import com.deepseasaltyfish.BeLodCompat.util.DatabaseManager;
import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBlockColorOverrideEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBlockStateWrapperCreatedEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkProcessingEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
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
        ModConfigs.register();
        DatabaseManager.init();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        BlockStateReplacer blockStateReplacer = new BlockStateReplacer();
        DhApi.events.bind(DhApiChunkProcessingEvent.class, blockStateReplacer);

        BlockStateOpacityReplacer blockStateOpacityReplacer = new BlockStateOpacityReplacer();
        DhApi.events.bind(DhApiBlockStateWrapperCreatedEvent.class, blockStateOpacityReplacer);

        //TODO: revert this until the api could be used...
//        BlockColorReplacer blockColorReplacer = new BlockColorReplacer();
//        DhApi.events.bind(DhApiBlockColorOverrideEvent.class, blockColorReplacer);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }
}