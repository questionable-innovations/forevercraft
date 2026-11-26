package nz.questionable.forevercraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import nz.questionable.forevercraft.config.ForevercraftConfig;

/**
 * 1:1 Nether travel. Only the Nether's own scale is replaced (identified by its sky effects), so modded
 * dimensions that use coordinate scaling keep it.
 */
@Mixin(DimensionType.class)
public abstract class DimensionTypeMixin {
    @Inject(method = "getTeleportationScale", at = @At("HEAD"), cancellable = true)
    private static void forevercraft$oneToOneNether(DimensionType from, DimensionType to,
                                                    CallbackInfoReturnable<Double> cir) {
        if (!ForevercraftConfig.ONE_TO_ONE_NETHER.get()) return;
        cir.setReturnValue(scale(from) / scale(to));
    }

    private static double scale(DimensionType type) {
        return BuiltinDimensionTypes.NETHER_EFFECTS.equals(type.effectsLocation()) ? 1.0 : type.coordinateScale();
    }
}
