package com.example.mixin.client;

import com.example.InviteManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ConnectedClientData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Inject(method = "onPlayerConnect", at = @At("HEAD"), cancellable = true)
    public void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData data, CallbackInfo ci) {
        PlayerManager manager = (PlayerManager)(Object)this;

        if (manager.getPlayerList().isEmpty()) {
            return;
        }

        String name = player.getGameProfile().getName();
        if (!InviteManager.isInvited(name)) {
            player.networkHandler.disconnect(Text.literal("You are not invited to this server."));
            ci.cancel();
        }
    }
}
