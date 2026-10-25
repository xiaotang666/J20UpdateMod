package com.xiaotang.j20updatemod.mixin;

import com.xiaotang.j20updatemod.inject.PackInjector;
import net.minecraft.client.resource.DefaultClientResourcePackProvider;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Fabric 没有 AddPackFindersEvent：在 ResourcePackManager（PackRepository 对应类）构造完成处
 * 追加自定义 ResourcePackProvider（RepositorySource 对应接口），镜像 NeoForge AddPackFindersEvent 的做法。
 * 仅当管理器是客户端资源包管理器（providers 含 DefaultClientResourcePackProvider）时注入，
 * 避免把语言包误注册进单人/集成服务端的数据包管理器。
 */
@Mixin(ResourcePackManager.class)
public abstract class ResourcePackManagerMixin {

    @Shadow
    @Final
    @Mutable
    private Set<ResourcePackProvider> providers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void j20updatemod$appendPackProvider(CallbackInfo ci) {
        boolean clientResources = false;
        for (ResourcePackProvider provider : this.providers) {
            if (provider instanceof DefaultClientResourcePackProvider) {
                clientResources = true;
                break;
            }
        }
        if (!clientResources) {
            return;
        }

        // providers 是 ImmutableSet.copyOf 构造的不可变集合，复制为可变集合后追加
        Set<ResourcePackProvider> copy = new LinkedHashSet<>(this.providers);
        if (PackInjector.onManagerCreated(copy)) {
            this.providers = copy;
        }
    }
}
