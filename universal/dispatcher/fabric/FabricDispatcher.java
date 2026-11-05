package com.xiaotang.j20updatemod.universal;

import com.xiaotang.j20updatemod.J20Core;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/**
 * Fabric 通用 dispatcher（跨 1.20.1–26.3）：preLaunch 与 client 双入口都转发到核心（幂等）。
 */
public final class FabricDispatcher implements ClientModInitializer {

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");

    /** fabric.mod.json entrypoints.preLaunch 目标。 */
    public static void onPreLaunch() {
        try {
            J20Core.start(gameDir(), detectMcVersion(), "fabric");
        } catch (Throwable t) {
            LOGGER.error("[j20] 通用装载器 preLaunch 转发失败（fabric）", t);
        }
    }

    @Override
    public void onInitializeClient() {
        try {
            J20Core.start(gameDir(), detectMcVersion(), "fabric");
        } catch (Throwable t) {
            LOGGER.error("[j20] 通用装载器失败（fabric）", t);
        }
    }

    static Path gameDir() {
        return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
    }

    /** 运行时 MC 版本：FabricLoader 的 minecraft 容器版本。 */
    static String detectMcVersion() {
        try {
            return FabricLoader.getInstance().getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("");
        } catch (Throwable t) {
            return "";
        }
    }
}
