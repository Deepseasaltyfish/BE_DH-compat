package com.deepseasaltyfish.BeLodCompat.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Path;

public class WorldPathUtil {
    private static String cachedServerIp = null;

    public static void setCachedServerIp(String ip) {
        cachedServerIp = ip;
    }

    /**
     * Returns the root directory for world-specific data (database files).
     * For singleplayer: returns the save folder path (e.g. .minecraft/saves/WorldName/)
     * For multiplayer (client): returns .minecraft/belodcompat_servers/<server_ip>/ (without extra subfolder)
     * For dedicated server: returns the server's world save root (via Level.getServer().getWorldPath)
     *
     * @param level the current level (world)
     * @return the root path for storing per-world data
     */
    public static Path getWorldRootPath(Level level) {
        if (!level.isClientSide()) {
            // Dedicated server side
            return level.getServer().getWorldPath(LevelResource.ROOT);
        }
        return getClientWorldRootPath();
    }

    @OnlyIn(Dist.CLIENT)
    private static Path getClientWorldRootPath() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
        }
        String serverIp = cachedServerIp;
        if (serverIp == null && mc.getCurrentServer() != null) {
            serverIp = mc.getCurrentServer().ip;
        }
        if (serverIp != null) {
            String sanitizedIp = serverIp.replace(':', '_').replace('/', '_').replace('\\', '_');
            return FMLPaths.GAMEDIR.get().resolve("belodcompat_servers").resolve(sanitizedIp);
        }
        // Final fallback
        return FMLPaths.GAMEDIR.get().resolve("belodcompat_servers").resolve("can_not_resolve");
    }

}