package com.xiaotang.j20updatemod.mixin;

import com.xiaotang.j20updatemod.inject.PackInjector;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * PackRepository 钩子（Fabric 无 AddPackFindersEvent 的等效方案）：
 * 在 discoverAvailable（reload 时收集可用资源包）返回处并入汉化资源包；
 * 之后 vanilla 的 rebuildSelected 会把 required 包按默认位置（TOP）自动插回选中列表，
 * 从而实现"强制置顶、自动启用"。
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {

    @Inject(method = "discoverAvailable", at = @At("RETURN"), cancellable = true)
    private void j20$injectPack(CallbackInfoReturnable<Map<String, Pack>> cir) {
        cir.setReturnValue(PackInjector.mergePack(cir.getReturnValue()));
    }
}
