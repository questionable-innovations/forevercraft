package nz.questionable.forevercraft.command;

import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import nz.questionable.forevercraft.world.Era;
import nz.questionable.forevercraft.world.PlayerMarks;
import nz.questionable.forevercraft.world.ServerState;
import nz.questionable.forevercraft.world.Stash;
import nz.questionable.forevercraft.world.WorldState;

/** Admin tools: status, every stash, toggling true coordinates for yourself, re-centring the offset. */
public final class ForevercraftCommand {
    private ForevercraftCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("forevercraft")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("stashes").executes(ctx -> stashes(ctx.getSource())))
                .then(Commands.literal("truecoords").executes(ctx -> toggleTrueCoords(ctx.getSource())))
                .then(Commands.literal("recenter").executes(ctx -> recenter(ctx.getSource()))));
    }

    private static int status(CommandSourceStack src) {
        WorldState state = ServerState.get();
        Era era = state.currentEra();
        String text = String.format(Locale.ROOT,
                "Era %d. True spawn %s; coordinate offset %d, %d. End seed: %s%s. Offline players awaiting reset: %d.",
                era.index, era.spawn.toShortString(), era.offsetX, era.offsetZ,
                state.endSeed == null ? "world seed" : state.endSeed,
                state.pendingEndSeed == null ? "" : " (regenerates on next restart)",
                state.pendingPlayers.size());
        src.sendSuccess(() -> Component.literal(text), false);
        return era.index;
    }

    private static int stashes(CommandSourceStack src) {
        WorldState state = ServerState.get();
        CoordinateView view = CoordinateView.ofEra(state.currentEra());
        MutableComponent msg = Component.literal("Stashes (true coordinates / as players see them):")
                .withStyle(ChatFormatting.GOLD);
        int count = 0;
        for (Era era : state.eras) {
            for (Stash s : era.stashes) {
                BlockPos p = s.pos();
                msg.append(Component.literal(String.format(Locale.ROOT, "\nEra %d %s: %s %s / %s (%d stacks)",
                        era.index, s.playerName(), s.dimension(), p.toShortString(), view.format(s.dimension(), p),
                        s.stacks())));
                count++;
            }
        }
        if (count == 0) msg.append(Component.literal("\nNone yet."));
        src.sendSuccess(() -> msg, false);
        return count;
    }

    private static int toggleTrueCoords(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        boolean reveal = !PlayerMarks.revealsTrueCoordinates(player);
        PlayerMarks.setRevealsTrueCoordinates(player, reveal);
        ServerState.sync(player);
        src.sendSuccess(() -> Component.literal(reveal
                ? "Your F3 screen now shows true coordinates."
                : "Your F3 screen now shows era coordinates like everyone else."), false);
        return 1;
    }

    /** After a manual /setworldspawn, make the new spawn read as ~0,0. */
    private static int recenter(CommandSourceStack src) {
        WorldState state = ServerState.get();
        Era era = state.currentEra();
        era.spawn = src.getServer().overworld().getSharedSpawnPos();
        era.offsetX = era.spawn.getX() & ~15;
        era.offsetZ = era.spawn.getZ() & ~15;
        state.save();
        ServerState.syncAll(src.getServer());
        src.sendSuccess(() -> Component.literal("Offset re-centred on spawn " + era.spawn.toShortString()
                + " (offset " + era.offsetX + ", " + era.offsetZ + ")."), true);
        return 1;
    }
}
