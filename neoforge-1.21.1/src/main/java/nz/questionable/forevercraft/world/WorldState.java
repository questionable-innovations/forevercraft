package nz.questionable.forevercraft.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Era history and pending work for one world. Kept in its own file (not SavedData) because the End seed
 * and pending End reset must be read before any level exists.
 */
public final class WorldState {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int VERSION = 1;

    private final Path file;
    public final List<Era> eras = new ArrayList<>();
    /** Players who were offline during a reset; they are stashed and moved on next login. */
    public final Set<UUID> pendingPlayers = new HashSet<>();
    /** Seed the End generates with, or null for the world seed. */
    @Nullable public Long endSeed;
    /** Seed for an End wipe that runs on the next server start. */
    @Nullable public Long pendingEndSeed;

    private WorldState(Path file) {
        this.file = file;
    }

    public static Path fileIn(Path worldDir) {
        return worldDir.resolve("forevercraft").resolve("state.dat");
    }

    public static WorldState load(Path file) throws IOException {
        WorldState state = new WorldState(file);
        if (!Files.exists(file)) return state;
        CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        for (Tag t : tag.getList("eras", Tag.TAG_COMPOUND)) state.eras.add(Era.load((CompoundTag) t));
        for (Tag t : tag.getList("pendingPlayers", Tag.TAG_STRING)) {
            state.pendingPlayers.add(UUID.fromString(t.getAsString()));
        }
        if (tag.contains("endSeed")) state.endSeed = tag.getLong("endSeed");
        if (tag.contains("pendingEndSeed")) state.pendingEndSeed = tag.getLong("pendingEndSeed");
        return state;
    }

    /** Written atomically so a crash mid-save cannot lose the era history. */
    public void save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", VERSION);
        ListTag erasTag = new ListTag();
        for (Era era : eras) erasTag.add(era.save());
        tag.put("eras", erasTag);
        ListTag pending = new ListTag();
        for (UUID id : pendingPlayers) pending.add(StringTag.valueOf(id.toString()));
        tag.put("pendingPlayers", pending);
        if (endSeed != null) tag.putLong("endSeed", endSeed);
        if (pendingEndSeed != null) tag.putLong("pendingEndSeed", pendingEndSeed);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            NbtIo.writeCompressed(tag, tmp);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.error("Failed to save Forevercraft state to {}", file, e);
        }
    }

    public boolean hasEras() {
        return !eras.isEmpty();
    }

    public Era currentEra() {
        return eras.get(eras.size() - 1);
    }

    public Era era(int index) {
        return eras.get(Math.max(0, Math.min(index, eras.size() - 1)));
    }
}
