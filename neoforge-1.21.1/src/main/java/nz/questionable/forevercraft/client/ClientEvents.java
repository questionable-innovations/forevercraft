package nz.questionable.forevercraft.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ElytraItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import nz.questionable.forevercraft.Forevercraft;
import nz.questionable.forevercraft.net.ServerSettingsPacket;
import nz.questionable.forevercraft.net.SyncedSettings;

@EventBusSubscriber(modid = Forevercraft.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    /** Lowest priority so lines other mods add are shifted too. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !SyncedSettings.offsetAppliesTo(mc.level.dimension().location())) return;
        ServerSettingsPacket s = SyncedSettings.client();
        DebugCoordinateRewriter.rewriteLeft(event.getLeft(), s.offsetX(), s.offsetZ());
        DebugCoordinateRewriter.rewriteRight(event.getRight(), s.offsetX(), s.offsetZ());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SyncedSettings.clearClient();
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().getItem() instanceof ElytraItem && SyncedSettings.client().elytraDisabled()) {
            event.getToolTip().add(Component.translatable("forevercraft.tooltip.elytra_disabled")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
