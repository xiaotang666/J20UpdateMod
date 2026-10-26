package com.xiaotang.j20updatemod.mixin;

import com.xiaotang.j20updatemod.inject.PackInjector;

import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * PackRepository 构造期注入（Fabric 无 AddPackFindersEvent 的等价注册点）。
 * 仅当构造参数中出现 ClientPackSource（即 Minecraft 的客户端资源包仓库）时，
 * 把 PackInjector 的 RepositorySource 追加进 sources，
 * 避免影响世界数据包仓库等其他 PackRepository 实例。
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {

    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void j20updatemod$appendRepositorySource(RepositorySource[] sources, CallbackInfo ci) {
        for (RepositorySource source : sources) {
            if (source instanceof ClientPackSource) {
                Set<RepositorySource> merged = new LinkedHashSet<>(this.sources);
                merged.add(PackInjector.createSource());
                this.sources = Collections.unmodifiableSet(merged);
                return;
            }
        }
    }
}
