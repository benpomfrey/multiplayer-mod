package com.example;

import com.example.mixin.client.ExampleClientMixin;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Inject(method = "onPlayerConnect", at = @At("HEAD"), cancellable = true)
    private void onPlayerConnect(net.minecraft.network.ClientConnection connection, ServerPlayerEntity player, CallbackInfo ci) {
        String name = player.getGameProfile().getName();
        if (!InviteManager.isInvited(name)) {
            player.networkHandler.disconnect(Text.literal("You are not invited to this server."));
            ci.cancel();
        }
    }
}