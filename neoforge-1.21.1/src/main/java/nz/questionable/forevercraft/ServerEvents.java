package nz.questionable.forevercraft;

import java.util.OptionalInt;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import nz.questionable.forevercraft.command.ForevercraftCommand;
import nz.questionable.forevercraft.command.PreviousBasesCommand;
import nz.questionable.forevercraft.command.ResetWorldCommand;
import nz.questionable.forevercraft.config.ForevercraftConfig;
import nz.questionable.forevercraft.reset.WorldResetService;
import nz.questionable.forevercraft.world.EndRegeneration;
import nz.questionable.forevercraft.world.Era;
import nz.questionable.forevercraft.world.PlayerMarks;
import nz.questionable.forevercraft.world.ServerState;
import nz.questionable.forevercraft.world.WorldState;

@EventBusSubscriber(modid = Forevercraft.MOD_ID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        ServerState.beforeLevelsLoad(event.getServer());
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        ServerState.afterLevelsLoad(event.getServer());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        ServerState.stopped();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ResetWorldCommand.register(event.getDispatcher());
        PreviousBasesCommand.register(event.getDispatcher());
        ForevercraftCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ServerState.isLoaded()) return;
        WorldState state = ServerState.get();
        Era current = state.currentEra();
        if (state.pendingPlayers.remove(player.getUUID())) {
            // Missed one or more resets while offline: stash them where they logged out, as if they had been online.
            OptionalInt last = PlayerMarks.era(player);
            int fromIndex = Math.min(last.orElse(current.index - 1), current.index - 1);
            WorldResetService.moveToEra(player.server, player, state.era(fromIndex), current);
            state.save();
            player.sendSystemMessage(Component.literal("Era " + current.index
                    + " began while you were away. Your belongings were left in chests where you logged out.")
                    .withStyle(ChatFormatting.GOLD));
        } else if (PlayerMarks.era(player).isEmpty()) {
            PlayerMarks.setEra(player, current.index);
        }
        ServerState.sync(player);
    }

    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getDimension() != Level.END || !ServerState.isLoaded()) return;
        if (ServerState.get().pendingEndSeed == null) return;
        boolean live = EndRegeneration.liveQueued();
        if (!live && !ForevercraftConfig.BLOCK_END_UNTIL_RESTART.get()) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal(live
                    ? "The End is reforming. Try again in a moment."
                    : "The End is reforming. It opens again after the next server restart.")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Catches glides already in progress when the ban was switched on, and any other way of starting one.
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isFallFlying() && ForevercraftConfig.DISABLE_ELYTRA.get()) player.stopFallFlying();
    }
}
