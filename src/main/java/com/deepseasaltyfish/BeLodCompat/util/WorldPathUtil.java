package com.deepseasaltyfish.BeLodCompat.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Path;

public class WorldPathUtil {
    /**
     * 获取当前世界（存档）的实际根目录路径。
     * 对于单机：返回 .minecraft/saves/存档文件夹名/
     * 对于联机：返回 .minecraft/config/bedhcompat/servers/服务器地址/
     * 注意：服务端不会调用此方法（已在事件中过滤）。
     */
    public static Path getWorldRootPath(Level level) {
        if (!level.isClientSide()) {
            // 服务端不应调用，但保留回退
            return FMLPaths.CONFIGDIR.get().resolve("bedhcompat").resolve("server");
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            // 单机：使用原版 MinecraftServer.getWorldPath(LevelResource.ROOT) 获取存档文件夹实际路径
            return mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
        } else if (mc.getCurrentServer() != null) {
            // 联机：使用服务器地址作为标识，存放到 config/bedhcompat/servers/ 下
            String serverIp = mc.getCurrentServer().ip.replace(':', '_').replace('/', '_');
            return FMLPaths.CONFIGDIR.get().resolve("bedhcompat").resolve("servers").resolve(serverIp);
        } else {
            // 未知情况，回退（通常不会发生）
            return FMLPaths.CONFIGDIR.get().resolve("bedhcompat").resolve("unknown");
        }
    }
}