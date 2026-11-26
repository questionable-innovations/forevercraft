package nz.questionable.forevercraft.world;

import java.util.OptionalInt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Per-player flags, stored under Forge's PlayerPersisted tag so they survive death and leaving the End. */
public final class PlayerMarks {
    private static final String ERA = "forevercraft:era";
    private static final String REVEAL = "forevercraft:reveal";

    private PlayerMarks() {}

    /** The era this player was last active in. */
    public static OptionalInt era(Player player) {
        CompoundTag tag = persisted(player);
        return tag.contains(ERA) ? OptionalInt.of(tag.getInt(ERA)) : OptionalInt.empty();
    }

    public static void setEra(Player player, int era) {
        CompoundTag tag = persisted(player);
        tag.putInt(ERA, era);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
    }

    public static boolean revealsTrueCoordinates(Player player) {
        return persisted(player).getBoolean(REVEAL);
    }

    public static void setRevealsTrueCoordinates(Player player, boolean reveal) {
        CompoundTag tag = persisted(player);
        tag.putBoolean(REVEAL, reveal);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
    }

    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }
}
