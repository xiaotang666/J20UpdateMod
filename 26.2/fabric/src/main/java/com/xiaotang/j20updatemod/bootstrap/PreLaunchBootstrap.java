package com.xiaotang.j20updatemod.bootstrap;

import com.xiaotang.j20updatemod.J20UpdateMod;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Fabric preLaunch 入口：游戏主体启动前限时同步等下载（A 方案），
 * 使首次安装也当次生效；超时转后台继续并由登录提示兜底（B 方案）。
 */
public final class PreLaunchBootstrap implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        J20UpdateMod.runEarlyCheck();
    }
}
