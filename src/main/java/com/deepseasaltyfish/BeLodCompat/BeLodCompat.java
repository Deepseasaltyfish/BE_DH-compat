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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(BeLodCompat.MODID)
public class BeLodCompat
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "be_lod_compat";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public BeLodCompat(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        ModConfigs.register();
        DatabaseManager.init();
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        BlockStateReplacer blockStateReplacer = new BlockStateReplacer();
        DhApi.events.bind(DhApiChunkProcessingEvent.class, blockStateReplacer);

        BlockStateOpacityReplacer blockStateOpacityReplacer = new BlockStateOpacityReplacer();
        DhApi.events.bind(DhApiBlockStateWrapperCreatedEvent.class, blockStateOpacityReplacer);

        //TODO: wait DH for alpha support, still use mixin for now, also idk why it does not work yet
//        BlockColorReplacer blockColorReplacer = new BlockColorReplacer();
//        DhApi.events.bind(DhApiBlockColorOverrideEvent.class, blockColorReplacer);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
        }
    }
}
