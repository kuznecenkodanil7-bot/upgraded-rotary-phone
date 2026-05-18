package ru.vgoqcnss4002.staffjoin.mixin;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.vgoqcnss4002.staffjoin.StaffJoinNotifierClient;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onPlayerList", at = @At("RETURN"))
    private void staffJoinNotifier$onPlayerList(PlayerListS2CPacket packet, CallbackInfo ci) {
        StaffJoinNotifierClient.onPlayerListPacket(packet);
    }

    @Inject(method = "onPlayerRemove", at = @At("RETURN"))
    private void staffJoinNotifier$onPlayerRemove(PlayerRemoveS2CPacket packet, CallbackInfo ci) {
        StaffJoinNotifierClient.onPlayerRemovePacket(packet);
    }
}
