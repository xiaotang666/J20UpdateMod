package com.xiaotang.j20updatemod.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/**
 * 定位游戏根目录（.minecraft）。
 * 优先 FabricLoader.getGameDir()，其次 Minecraft.gameDirectory，最后 user.dir。
 */
public final class GameDirs {

    private GameDirs() {
    }

    public static Path resolveGameDir() {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            if (gameDir != null) {
                return gameDir;
            }
        } catch (Throwable ignored) {
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                return mc.gameDirectory.toPath();
            }
        } catch (Throwable ignored) {
        }
        return Path.of(System.getProperty("user.dir", "."));
    }
}
