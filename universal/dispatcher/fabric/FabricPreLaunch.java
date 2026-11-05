package com.xiaotang.j20updatemod.universal;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Fabric preLaunch 入口（实现真实接口——Fabric 的 entrypoint 不支持 Class#方法 写法）。
 */
public final class FabricPreLaunch implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        FabricDispatcher.onPreLaunch();
    }
}
