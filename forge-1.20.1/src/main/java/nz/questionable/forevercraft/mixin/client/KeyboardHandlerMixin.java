package nz.questionable.forevercraft.mixin.client;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import nz.questionable.forevercraft.net.SyncedSettings;

/** F3+C and F3+I copy commands containing true coordinates to the clipboard; block them while hidden. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Shadow @Final private Minecraft minecraft;

    @Shadow
    private void debugFeedbackComponent(Component message) {
        throw new AssertionError();
    }

    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void forevercraft$blockCoordinateCopy(int key, CallbackInfoReturnable<Boolean> cir) {
        if (key != GLFW.GLFW_KEY_C && key != GLFW.GLFW_KEY_I) return;
        if (minecraft.level == null || !SyncedSettings.offsetAppliesTo(minecraft.level.dimension().location())) return;
        debugFeedbackComponent(Component.translatable("forevercraft.debug.copy_blocked"));
        cir.setReturnValue(true);
    }
}
