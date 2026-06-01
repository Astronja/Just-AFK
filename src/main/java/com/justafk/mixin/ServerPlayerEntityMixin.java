package com.justafk.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides getPlayerListName() to return the grey/italic AFK name
 * when the player is in AFK mode.
 */
@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    @Inject(method = "getPlayerListName", at = @At("HEAD"), cancellable = true)
    private void onGetPlayerListName(CallbackInfoReturnable<Text> cir) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        Text afkName = com.justafk.AfkPlayerHandler.INSTANCE.getAfkDisplayName(self);
        if (afkName != null) {
            cir.setReturnValue(afkName);
        }
    }
}
