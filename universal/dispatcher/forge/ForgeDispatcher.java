package com.xiaotang.j20updatemod.universal;

import com.xiaotang.j20updatemod.J20Core;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Forge 通用 dispatcher（跨 1.20.1–26.3）：把游戏目录/版本交给纯 Java 核心。
 */
@Mod(J20Core.MODID)
public final class ForgeDispatcher {

    private static final Logger LOGGER = LogManager.getLogger("j20UpdateMod");

    public ForgeDispatcher() {
        try {
            J20Core.start(FMLPaths.GAMEDIR.get(), J20Core.detectMcVersion(), "forge");
        } catch (Throwable t) {
            LOGGER.error("[j20] 通用装载器失败（forge）", t);
        }
    }
}
