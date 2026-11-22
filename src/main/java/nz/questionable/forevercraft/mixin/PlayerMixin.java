package nz.questionable.forevercraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.player.Player;
import nz.questionable.forevercraft.net.SyncedSettings;

/** Blocks starting elytra flight on both sides, so the client does not predict a glide the server rejects. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void forevercraft$noElytra(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (SyncedSettings.elytraDisabled(self.level())) cir.setReturnValue(false);
    }
}
