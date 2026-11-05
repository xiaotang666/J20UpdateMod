package com.xiaotang.j20updatemod.universal;

import com.xiaotang.j20updatemod.J20Core;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * NeoForge 通用 dispatcher（跨 1.20.1–26.3）：把游戏目录/版本交给纯 Java 核心。
 */
@Mod(J20Core.MODID)
public final class NeoForgeDispatcher {

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");

    public NeoForgeDispatcher() {
        try {
            J20Core.start(FMLPaths.GAMEDIR.get(), J20Core.detectMcVersion(), "neoforge");
        } catch (Throwable t) {
            LOGGER.error("[j20] 通用装载器失败（neoforge）", t);
        }
    }
}
