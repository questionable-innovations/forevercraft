package nz.questionable.forevercraft.world;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * One "world" in the long-running save. Spawn is in true coordinates; the offset (chunk-aligned) is what
 * clients subtract so this era's spawn reads as roughly 0,0.
 */
public final class Era {
    public final int index;
    public BlockPos spawn;
    public int offsetX;
    public int offsetZ;
    public final long startedAt;
    public long endedAt;
    public final List<Stash> stashes = new ArrayList<>();

    public Era(int index, BlockPos spawn, int offsetX, int offsetZ, long startedAt) {
        this.index = index;
        this.spawn = spawn;
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
        this.startedAt = startedAt;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("index", index);
        tag.put("spawn", BlockPosTag.write(spawn));
        tag.putInt("offsetX", offsetX);
        tag.putInt("offsetZ", offsetZ);
        tag.putLong("startedAt", startedAt);
        tag.putLong("endedAt", endedAt);
        ListTag list = new ListTag();
        for (Stash s : stashes) list.add(s.save());
        tag.put("stashes", list);
        return tag;
    }

    static Era load(CompoundTag tag) {
        Era era = new Era(tag.getInt("index"), BlockPosTag.read(tag.getCompound("spawn")),
                tag.getInt("offsetX"), tag.getInt("offsetZ"), tag.getLong("startedAt"));
        era.endedAt = tag.getLong("endedAt");
        for (Tag t : tag.getList("stashes", Tag.TAG_COMPOUND)) era.stashes.add(Stash.load((CompoundTag) t));
        return era;
    }
}
