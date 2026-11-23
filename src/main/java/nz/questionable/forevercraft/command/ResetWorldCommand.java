package nz.questionable.forevercraft.command;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import nz.questionable.forevercraft.config.ForevercraftConfig;
import nz.questionable.forevercraft.reset.WorldResetService;
import nz.questionable.forevercraft.world.Era;
import nz.questionable.forevercraft.world.ServerState;
import nz.questionable.forevercraft.world.WorldState;

/** {@code /resetworld} shows what will happen and a one-time code; {@code /resetworld confirm <code>} runs it. */
public final class ResetWorldCommand {
    private static final long CONFIRM_WINDOW_MS = 60_000;
    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();

    private record Pending(String code, long expiresAt) {}

    private ResetWorldCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("resetworld")
                .requires(src -> src.hasPermission(ForevercraftConfig.RESET_PERMISSION_LEVEL.get()))
                .executes(ctx -> preview(ctx.getSource()))
                .then(Commands.literal("confirm")
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(ctx -> confirm(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "code"))))));
    }

    private static int preview(CommandSourceStack src) {
        WorldState state = ServerState.get();
        Era current = state.currentEra();
        String code = String.format(Locale.ROOT, "%04d", ThreadLocalRandom.current().nextInt(10_000));
        PENDING.put(src.getTextName(), new Pending(code, System.currentTimeMillis() + CONFIRM_WINDOW_MS));

        int online = src.getServer().getPlayerCount();
        MutableComponent msg = Component.literal("")
                .append(Component.literal("/resetworld ends era " + current.index + " and starts era "
                        + (current.index + 1) + ":").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(line("A new spawn is picked " + ForevercraftConfig.MIN_SPAWN_DISTANCE.get() + "–"
                        + ForevercraftConfig.MAX_SPAWN_DISTANCE.get()
                        + " blocks away from every old one; coordinates there read ~0, 0."))
                .append(line(belongingsLine()))
                .append(line("Levels, effects and respawn points are wiped, and "
                        + (online == 1 ? "the 1 online player is" : "all " + online + " online players are")
                        + " sent to the new spawn."))
                .append(line("Offline players are handled the next time they join."));
        if (ForevercraftConfig.RESET_END.get()) {
            msg.append(line(ForevercraftConfig.REGENERATE_END_LIVE.get()
                    ? "The End is regenerated (new seed, new dragon) right after, once nobody is left in it."
                    : "The End is regenerated (new seed, new dragon) on the next server restart"
                            + (ForevercraftConfig.BLOCK_END_UNTIL_RESTART.get()
                                    ? "; End portals are closed until then." : ".")));
        }
        String command = "/resetworld confirm " + code;
        msg.append(Component.literal("\nThis cannot be undone. ").withStyle(ChatFormatting.RED))
                .append(Component.literal("[Confirm reset]").withStyle(Style.EMPTY
                        .withColor(ChatFormatting.DARK_RED).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command)))))
                .append(Component.literal(" or type " + command + " within 60s.").withStyle(ChatFormatting.GRAY));
        src.sendSuccess(() -> msg, false);
        return 1;
    }

    private static String belongingsLine() {
        return switch (ForevercraftConfig.ENDER_CHEST_MODE.get()) {
            case STASH -> "Every player's inventory, armor and ender chest go into chests stacked where they stand.";
            case CLEAR -> "Every player's inventory and armor go into chests stacked where they stand; ender chests are emptied.";
            case KEEP -> "Every player's inventory and armor go into chests stacked where they stand; ender chests are kept.";
        };
    }

    private static String plural(int n, String noun) {
        if (n == 1) return "1 " + noun;
        return n + " " + noun + (noun.endsWith("sh") ? "es" : "s");
    }

    private static Component line(String text) {
        return Component.literal("\n • " + text).withStyle(ChatFormatting.YELLOW);
    }

    private static int confirm(CommandSourceStack src, String code) {
        Pending pending = PENDING.get(src.getTextName());
        if (pending == null || pending.expiresAt() < System.currentTimeMillis()) {
            PENDING.remove(src.getTextName());
            src.sendFailure(Component.literal("No reset is waiting for confirmation. Run /resetworld first."));
            return 0;
        }
        if (!pending.code().equals(code)) {
            src.sendFailure(Component.literal("That code does not match. Run /resetworld again for a new one."));
            return 0;
        }
        PENDING.remove(src.getTextName());

        src.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("A new era is beginning...").withStyle(ChatFormatting.GOLD), false);
        WorldResetService.Result result = WorldResetService.startNewEra(src.getServer());
        Era era = result.era();
        src.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                "Era " + era.index + " has begun. Your belongings were left in chests where you stood.")
                .withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Era %d started at true %d %d %d (shown as %d, %d). Moved %s and placed %s; %s will be handled "
                        + "when they next join.",
                era.index, era.spawn.getX(), era.spawn.getY(), era.spawn.getZ(),
                era.spawn.getX() - era.offsetX, era.spawn.getZ() - era.offsetZ,
                plural(result.playersMoved(), "player"), plural(result.stashesPlaced(), "stash"),
                plural(result.offlinePending(), "offline player"))), true);
        return 1;
    }
}
