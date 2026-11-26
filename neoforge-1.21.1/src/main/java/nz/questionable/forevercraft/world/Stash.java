package nz.questionable.forevercraft.world;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Where a player's belongings were left when an era ended. {@code pos} is the bottom chest. */
public record Stash(UUID player, String playerName, ResourceLocation dimension, BlockPos pos, int chests,
                    int stacks) {
    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", player);
        tag.putString("name", playerName);
        tag.putString("dimension", dimension.toString());
        tag.put("pos", BlockPosTag.write(pos));
        tag.putInt("chests", chests);
        tag.putInt("stacks", stacks);
        return tag;
    }

    static Stash load(CompoundTag tag) {
        return new Stash(tag.getUUID("player"), tag.getString("name"),
                ResourceLocation.parse(tag.getString("dimension")), BlockPosTag.read(tag.getCompound("pos")),
                tag.getInt("chests"), tag.getInt("stacks"));
    }
}
