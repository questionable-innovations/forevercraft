package nz.questionable.forevercraft.reset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.LevelResource;
import nz.questionable.forevercraft.config.ForevercraftConfig;
import nz.questionable.forevercraft.reset.SpawnPlanner.Point;
import nz.questionable.forevercraft.world.Era;
import nz.questionable.forevercraft.world.PlayerMarks;
import nz.questionable.forevercraft.world.ServerState;
import nz.questionable.forevercraft.world.Stash;
import nz.questionable.forevercraft.world.WorldState;

/** Ends the current era: new spawn, everyone stashed and moved, End queued for regeneration. */
public final class WorldResetService {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int BORDER_MARGIN = 1024;

    public record Result(Era era, int playersMoved, int stashesPlaced, int offlinePending) {}

    private WorldResetService() {}

    public static Result startNewEra(MinecraftServer server) {
        WorldState state = ServerState.get();
        ServerLevel overworld = server.overworld();
        long now = System.currentTimeMillis();

        Era ending = state.currentEra();
        ending.spawn = overworld.getSharedSpawnPos(); // picks up any manual /setworldspawn
        ending.endedAt = now;

        List<Point> previous = state.eras.stream().map(e -> new Point(e.spawn.getX(), e.spawn.getZ())).toList();
        Point center = SpawnPlanner.pickCenter(previous, new Point(ending.spawn.getX(), ending.spawn.getZ()),
                ForevercraftConfig.MIN_SPAWN_DISTANCE.get(), ForevercraftConfig.MAX_SPAWN_DISTANCE.get(),
                ForevercraftConfig.PREFERRED_MAX_RADIUS.get(), borderLimit(overworld.getWorldBorder()), new Random());
        BlockPos spawn = SpawnLocator.find(overworld, center.x(), center.z());
        // Chunk-aligned so F3's chunk-relative numbers and chunk borders agree with the shown coordinates.
        Era next = new Era(ending.index + 1, spawn, spawn.getX() & ~15, spawn.getZ() & ~15, now);
        state.eras.add(next);
        overworld.setDefaultSpawnPos(spawn, 0.0F);
        LOG.info("Forevercraft: era {} begins at {} (offset {}, {})", next.index, spawn, next.offsetX, next.offsetZ);

        if (ForevercraftConfig.RESET_END.get()) state.pendingEndSeed = new Random().nextLong();

        Set<UUID> offline = knownPlayers(server);
        int moved = 0;
        int stashes = 0;
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            offline.remove(player.getUUID());
            state.pendingPlayers.remove(player.getUUID());
            if (moveToEra(server, player, ending, next)) stashes++;
            moved++;
        }
        state.pendingPlayers.addAll(offline);
        state.save();
        ServerState.syncAll(server);
        server.saveEverything(true, false, true);
        return new Result(next, moved, stashes, state.pendingPlayers.size());
    }

    /**
     * Stashes the player's belongings where they are (or at {@code from}'s spawn if they are in an End that
     * is being wiped), resets them and sends them to {@code to}'s spawn.
     *
     * @return whether a stash was placed
     */
    public static boolean moveToEra(MinecraftServer server, ServerPlayer player, Era from, Era to) {
        ServerLevel stashLevel = player.serverLevel();
        BlockPos stashPos = player.blockPosition();
        if (ForevercraftConfig.RESET_END.get() && stashLevel.dimension() == Level.END) {
            stashLevel = server.overworld();
            stashPos = from.spawn;
        }

        List<ItemStack> items = PlayerStasher.takeBelongings(player, ForevercraftConfig.ENDER_CHEST_MODE.get());
        String name = player.getGameProfile().getName();
        Component label = Component.literal(name + "'s belongings (era " + from.index + ")");
        PlayerStasher.Placed placed = PlayerStasher.placeStash(stashLevel, stashPos,
                player.getDirection().getOpposite(), items, label);
        if (placed != null) {
            from.stashes.add(new Stash(player.getUUID(), name, stashLevel.dimension().location(), placed.bottom(),
                    placed.chests(), placed.stacks()));
            LOG.info("Forevercraft: stashed {} stacks for {} at {} {}", placed.stacks(), name,
                    stashLevel.dimension().location(), placed.bottom());
        }

        if (player.isAlive()) {
            PlayerStasher.resetVitals(player);
            BlockPos spawn = to.spawn;
            player.teleportTo(server.overworld(), spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
        } else {
            // On the death screen: they respawn at the (new) world spawn with fresh health anyway.
            player.setExperienceLevels(0);
            player.setExperiencePoints(0);
            player.totalExperience = 0;
            player.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
        }
        PlayerMarks.setEra(player, to.index);
        return placed != null;
    }

    /** Everyone who has ever joined, from the saved player files. */
    private static Set<UUID> knownPlayers(MinecraftServer server) {
        Set<UUID> ids = new HashSet<>();
        Path dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (!Files.isDirectory(dir)) return ids;
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".dat"))
                    .map(n -> n.substring(0, n.length() - 4))
                    .forEach(n -> {
                        try {
                            ids.add(UUID.fromString(n));
                        } catch (IllegalArgumentException ignored) {
                            // not a player file
                        }
                    });
        } catch (IOException e) {
            LOG.error("Forevercraft: could not list player data; offline players will not be reset", e);
        }
        return ids;
    }

    private static int borderLimit(WorldBorder border) {
        double limit = Math.min(Math.min(border.getMaxX(), -border.getMinX()),
                Math.min(border.getMaxZ(), -border.getMinZ()));
        return (int) Math.max(0, limit - BORDER_MARGIN);
    }
}
