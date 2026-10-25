package com.xiaotang.j20updatemod.util;

import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/**
 * 定位游戏根目录（.minecraft）。
 * 优先 Minecraft.gameDirectory，其次 FMLPaths.GAMEDIR（反射，兼容
 * net.neoforged / net.minecraftforge 两种包名），再次 FabricLoader.getGameDir()（反射），最后 user.dir。
 * 加载器相关路径全部走反射，保证这份类在 Forge / NeoForge / Fabric 三个工程原样通用。
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

        for (String fmlPathsName : new String[]{
                "net.neoforged.fml.loading.FMLPaths",
                "net.minecraftforge.fml.loading.FMLPaths"}) {
            try {
                Class<?> fmlPaths = Class.forName(fmlPathsName);
                Object gamedir = fmlPaths.getField("GAMEDIR").get(null);
                Object path = gamedir.getClass().getMethod("get").invoke(gamedir);
                if (path != null) {
                    return Path.of(path.toString());
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = fabricLoader.getMethod("getInstance").invoke(null);
            Object gameDir = instance.getClass().getMethod("getGameDir").invoke(instance);
            if (gameDir != null) {
                return Path.of(gameDir.toString());
            }
        } catch (Throwable ignored) {
        }

        return Path.of(System.getProperty("user.dir", "."));
    }
}
