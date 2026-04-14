package com.deepseasaltyfish.BeDhCompat;

import com.deepseasaltyfish.BeDhCompat.common.ImmersiveRairoading.IRblocksReplacer;
import com.deepseasaltyfish.BeDhCompat.common.LittleTiles.LTblocksReplacer;
import com.deepseasaltyfish.BeDhCompat.config.ModConfigs;
import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.DhApi;
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
@Mod(BeDhCompat.MODID)
public class BeDhCompat
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "be_dh_compat";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public BeDhCompat(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        ModConfigs.register();
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {

        LTblocksReplacer LTReplacer = new LTblocksReplacer();
        DhApi.events.bind(DhApiChunkProcessingEvent.class, LTReplacer);

        IRblocksReplacer IRReplacer = new IRblocksReplacer();
        DhApi.events.bind(DhApiChunkProcessingEvent.class, IRReplacer);
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
