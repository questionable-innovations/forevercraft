package nz.questionable.forevercraft.world;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import nz.questionable.forevercraft.config.ForevercraftConfig;
import nz.questionable.forevercraft.net.ForevercraftNetwork;
import nz.questionable.forevercraft.net.ServerSettingsPacket;

/** Owns the loaded {@link WorldState} for the running server and keeps clients in sync with it. */
public final class ServerState {
    private static WorldState state;

    private ServerState() {}

    public static WorldState get() {
        if (state == null) throw new IllegalStateException("Forevercraft state is not loaded");
        return state;
    }

    public static boolean isLoaded() {
        return state != null;
    }

    /** Runs before any level is created, which is the only safe moment to swap out the End. */
    public static void beforeLevelsLoad(MinecraftServer server) {
        Path worldDir = worldDir(server);
        try {
            state = WorldState.load(WorldState.fileIn(worldDir));
        } catch (IOException e) {
            // Refuse to start rather than silently forget the era history (which would move everyone's offset).
            throw new UncheckedIOException("Could not read Forevercraft state for " + worldDir, e);
        }
        if (state.pendingEndSeed != null) EndRegeneration.atStartup(server, state, worldDir);
        EndSeed.set(state.endSeed);
    }

    public static void afterLevelsLoad(MinecraftServer server) {
        if (state.hasEras()) return;
        ServerLevel overworld = server.overworld();
        state.eras.add(new Era(0, overworld.getSharedSpawnPos(), 0, 0, System.currentTimeMillis()));
        state.save();
    }

    public static void stopped() {
        EndRegeneration.cancelLive();
        EndSeed.set(null);
        state = null;
    }

    public static Path worldDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }

    public static ServerSettingsPacket settingsFor(ServerPlayer player) {
        Era era = get().currentEra();
        boolean hide = ForevercraftConfig.HIDE_COORDINATES.get() && !PlayerMarks.revealsTrueCoordinates(player);
        return new ServerSettingsPacket(hide ? era.offsetX : 0, hide ? era.offsetZ : 0, offsetDimensions(),
                ForevercraftConfig.DISABLE_ELYTRA.get());
    }

    public static Set<ResourceLocation> offsetDimensions() {
        return ForevercraftConfig.OFFSET_DIMENSIONS.get().stream()
                .map(ResourceLocation::tryParse).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }

    public static void sync(ServerPlayer player) {
        ForevercraftNetwork.send(player, settingsFor(player));
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player);
    }
}
