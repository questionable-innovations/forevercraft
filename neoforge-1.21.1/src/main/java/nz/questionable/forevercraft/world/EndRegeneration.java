package nz.questionable.forevercraft.world;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import nz.questionable.forevercraft.Forevercraft;
import nz.questionable.forevercraft.mixin.MinecraftServerAccessor;

/**
 * Replaces the End with a fresh one under {@link WorldState#pendingEndSeed}: live, once nobody is left in the
 * old one, or at the next startup if that did not happen. The old End is closed the way a server shutdown closes
 * it and the new one is built the way startup builds it.
 */
@EventBusSubscriber(modid = Forevercraft.MOD_ID)
public final class EndRegeneration {
    private static final Logger LOG = LogUtils.getLogger();
    private static final ChunkProgressListener QUIET = new ChunkProgressListener() {
        @Override public void updateSpawnPos(ChunkPos center) {}
        @Override public void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status) {}
        @Override public void start() {}
        @Override public void stop() {}
    };

    private static volatile boolean liveQueued;

    private EndRegeneration() {}

    public static void queueLive() {
        liveQueued = true;
    }

    /** End travel stays closed while this is true. */
    public static boolean liveQueued() {
        return liveQueued;
    }

    static void cancelLive() {
        liveQueued = false;
    }

    /** Before any level exists, so only the files need to move. */
    static void atStartup(MinecraftServer server, WorldState state, Path worldDir) {
        retireOldEnd(server, state, worldDir);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // At the end of a tick nothing is iterating the level map.
        if (!liveQueued || !ServerState.isLoaded()) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        WorldState state = ServerState.get();
        ServerLevel old = server.getLevel(Level.END);
        if (state.pendingEndSeed == null || old == null) {
            liveQueued = false;
            return;
        }
        // A reset moves everyone out, but a player on the death screen there still belongs to the old level.
        if (!old.players().isEmpty()) return;
        liveQueued = false;
        try {
            swap(server, old, state);
        } catch (IOException | RuntimeException e) {
            LOG.error("Forevercraft: could not replace the End live; it will regenerate on the next restart", e);
        }
    }

    private static void swap(MinecraftServer server, ServerLevel old, WorldState state) throws IOException {
        EndDragonFight fight = old.getDragonFight();
        if (fight != null) {
            // Its boss bar only drops players on its next scan, which a closed level never runs.
            for (ServerPlayer player : server.getPlayerList().getPlayers()) fight.removePlayer(player);
        }
        Map<ResourceKey<Level>, ServerLevel> levels = server.forgeGetWorldMap();
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(old));
        ServerLevel fresh;
        try {
            old.noSave = false;
            old.save(null, true, false);
            old.close();
            retireOldEnd(server, state, ServerState.worldDir(server));
            EndSeed.set(state.endSeed);
            fresh = create(server);
            levels.put(Level.END, fresh); // replaces in place, so level order is unchanged
        } catch (IOException | RuntimeException e) {
            // Better no End until a restart than one that ticks after being closed.
            levels.remove(Level.END);
            throw e;
        } finally {
            server.markWorldsDirty();
        }
        // The old End's border listener stays on the overworld border; it only forwards to a detached border.
        server.overworld().getWorldBorder()
                .addListener(new BorderChangeListener.DelegateBorderChangeListener(fresh.getWorldBorder()));
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(fresh));
        LOG.info("Forevercraft: the End was replaced live (seed {})", state.endSeed);
    }

    /** Mirrors MinecraftServer.createLevels for one non-overworld dimension. */
    private static ServerLevel create(MinecraftServer server) {
        MinecraftServerAccessor access = (MinecraftServerAccessor) server;
        WorldData data = server.getWorldData();
        LevelStem stem = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).getOrThrow(LevelStem.END);
        return new ServerLevel(server, access.forevercraft$executor(), access.forevercraft$storageSource(),
                new DerivedLevelData(data, data.overworldData()), Level.END, stem, QUIET, data.isDebugWorld(),
                BiomeManager.obfuscateSeed(data.worldGenOptions().seed()), List.of(), false,
                server.overworld().getRandomSequences());
    }

    private static void retireOldEnd(MinecraftServer server, WorldState state, Path worldDir) {
        Path endDir = DimensionType.getStorageFolder(Level.END, worldDir);
        if (Files.exists(endDir)) {
            // Kept, not deleted, so an admin can roll back by moving it back to DIM1.
            Path backup = worldDir.resolve("forevercraft").resolve("old_ends")
                    .resolve("DIM1-era" + (state.eras.size() - 2) + "-" + System.currentTimeMillis());
            try {
                Files.createDirectories(backup.getParent());
                Files.move(endDir, backup);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not move the old End out of the way", e);
            }
            LOG.info("Forevercraft: moved the old End to {}", backup);
        }
        server.getWorldData().setEndDragonFightData(EndDragonFight.Data.DEFAULT);
        state.endSeed = state.pendingEndSeed;
        state.pendingEndSeed = null;
        state.save();
        LOG.info("Forevercraft: the End will regenerate with seed {}", state.endSeed);
    }
}
