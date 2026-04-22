package com.deepseasaltyfish.BeLodCompat.util;

import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Path;

public class WorldPathUtil {
    /**
     * Returns the actual root directory path of the current world (save).
     * For singleplayer: returns .minecraft/saves/<save_folder>/
     * For multiplayer: returns .minecraft/config/bedhcompat/servers/<server_address>/
     * On server side: returns config/bedhcompat/server/
     *
     * @param level the current level (world)
     * @return the root path for world data storage
     */
    public static Path getWorldRootPath(Level level) {
        if (!level.isClientSide()) {
            // Server side fallback (should not be called normally)
            return FMLPaths.CONFIGDIR.get().resolve("bedhcompat").resolve("server");
        }
        // Delegate to client-only implementation
        return getClientWorldRootPath();
    }

    @OnlyIn(Dist.CLIENT)
    private static Path getClientWorldRootPath() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            // Singleplayer: use actual save folder path
            return mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        } else if (mc.getCurrentServer() != null) {
            // Multiplayer: use server address as identifier
            String serverIp = mc.getCurrentServer().ip.replace(':', '_').replace('/', '_');
            return FMLPaths.CONFIGDIR.get().resolve("bedhcompat").resolve("servers").resolve(serverIp);
        } else {
            // Unknown client scenario
            return FMLPaths.CONFIGDIR.get().resolve("bedhcompat").resolve("unknown");
        }
    }
}