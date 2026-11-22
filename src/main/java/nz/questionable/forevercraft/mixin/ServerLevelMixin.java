package nz.questionable.forevercraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldOptions;
import nz.questionable.forevercraft.world.EndSeed;

/**
 * Gives the End its own seed after a reset. getSeed() covers noise, biomes, structures and spikes;
 * the constructor redirect covers the structure check and the dragon fight's gateway order.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void forevercraft$endSeed(CallbackInfoReturnable<Long> cir) {
        Long seed = EndSeed.forLevel(((Level) (Object) this).dimension());
        if (seed != null) cir.setReturnValue(seed);
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J"))
    private long forevercraft$endSeedInCtor(WorldOptions options) {
        Long seed = EndSeed.forLevel(((Level) (Object) this).dimension());
        return seed != null ? seed : options.seed();
    }
}
