package com.xiaotang.j20updatemod.util;

import java.nio.file.Path;

/**
 * 游戏目录解析（纯 Java，无 MC 绑定）：
 * dispatcher 传入为主；本类仅作反射回退（FMLPaths.GAMEDIR，跨版本稳定）与 user.dir 兜底。
 */
public final class GameDirs {

    private GameDirs() {
    }

    public static Path resolveGameDir() {
        for (String fl : new String[]{"net.minecraftforge.fml.loading.FMLPaths",
                "net.neoforged.fml.loading.FMLPaths"}) {
            try {
                Object gamedir = Class.forName(fl).getField("GAMEDIR").get(null);
                Object path = gamedir.getClass().getMethod("get").invoke(gamedir);
                return ((Path) path).toAbsolutePath().normalize();
            } catch (Throwable ignored) {
            }
        }
        return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }
}
