package com.xiaotang.j20updatemod.mixin;

import com.xiaotang.j20updatemod.J20UpdateMod;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 进服/进档钩子（等效 Forge 的 ClientPlayerNetworkEvent.LoggingIn）：
 * handleLogin 是客户端进入世界（Join Game）的处理入口，用于发送 VP 安装提醒。
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void j20$afterLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        J20UpdateMod.onLocalPlayerReady();
    }
}
