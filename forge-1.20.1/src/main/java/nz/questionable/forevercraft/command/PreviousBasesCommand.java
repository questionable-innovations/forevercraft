package nz.questionable.forevercraft.command;

import java.text.NumberFormat;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import nz.questionable.forevercraft.config.ForevercraftConfig;
import nz.questionable.forevercraft.world.Dates;
import nz.questionable.forevercraft.world.Era;
import nz.questionable.forevercraft.world.ServerState;
import nz.questionable.forevercraft.world.Stash;
import nz.questionable.forevercraft.world.WorldState;

/** Lists earlier eras' spawns in the coordinates the caller currently sees. */
public final class PreviousBasesCommand {
    private PreviousBasesCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("previousbases")
                .requires(src -> src.hasPermission(ForevercraftConfig.PREVIOUS_BASES_PERMISSION_LEVEL.get()))
                .executes(ctx -> run(ctx.getSource())));
    }

    private static int run(CommandSourceStack src) {
        WorldState state = ServerState.get();
        Era current = state.currentEra();
        if (current.index == 0) {
            src.sendSuccess(() -> Component.literal("This is the first era; there are no previous bases yet."), false);
            return 0;
        }
        ServerPlayer player = src.getPlayer();
        CoordinateView view = player != null ? CoordinateView.of(player) : CoordinateView.ofEra(current);
        boolean showStashes = player != null
                && (ForevercraftConfig.PREVIOUS_BASES_SHOW_OWN_STASHES.get() || src.hasPermission(2));
        // Distances from where the caller stands (only meaningful in a dimension that shares overworld coords).
        BlockPos from = player != null && player.level().dimension() != Level.END
                ? player.blockPosition() : current.spawn;
        NumberFormat num = NumberFormat.getIntegerInstance(Locale.ROOT);

        MutableComponent msg = Component.literal("Previous eras (in the coordinates you see now):")
                .withStyle(ChatFormatting.GOLD);
        for (Era era : state.eras) {
            if (era.index == current.index) continue;
            msg.append(Component.literal("\nEra " + era.index).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" (ended " + Dates.day(era.endedAt) + ")")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(": spawn at " + view.formatXZ(Level.OVERWORLD.location(), era.spawn)
                            + ", " + num.format(CoordinateView.horizontalDistance(from, era.spawn)) + " blocks "
                            + CoordinateView.direction(from, era.spawn)));
            if (!showStashes) continue;
            for (Stash stash : era.stashes) {
                if (!stash.player().equals(player.getUUID())) continue;
                msg.append(Component.literal("\n   your belongings: " + view.format(stash.dimension(), stash.pos())
                        + " in " + CoordinateView.dimensionName(stash.dimension()) + " (" + stash.chests() + " chest"
                        + (stash.chests() == 1 ? "" : "s") + ")").withStyle(ChatFormatting.GRAY));
            }
        }
        src.sendSuccess(() -> msg, false);
        return state.eras.size() - 1;
    }
}
