package com.xiaotang.j20updatemod.util;

import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/**
 * 定位游戏根目录（.minecraft）。
 * 优先 Minecraft.gameDirectory，其次 FMLPaths.GAMEDIR（反射，避免编译期绑定加载器内部 API），最后 user.dir。
 */
public final class GameDirs {

    private GameDirs() {
    }

    public static Path resolveGameDir() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                return mc.gameDirectory.toPath();
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> fmlPaths = Class.forName("net.neoforged.fml.loading.FMLPaths");
            Object gamedir = fmlPaths.getField("GAMEDIR").get(null);
            Object path = gamedir.getClass().getMethod("get").invoke(gamedir);
            if (path != null) {
                return Path.of(path.toString());
            }
        } catch (Throwable ignored) {
        }

        return Path.of(System.getProperty("user.dir", "."));
    }
}
