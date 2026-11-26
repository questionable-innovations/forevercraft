package nz.questionable.forevercraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * BlockPos as {X, Y, Z}: the layout 1.20.1's NbtUtils wrote, kept so a world's state.dat carries over between
 * Minecraft versions (1.20.5+ NbtUtils writes an int array instead).
 */
final class BlockPosTag {
    private BlockPosTag() {}

    static CompoundTag write(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        return tag;
    }

    static BlockPos read(CompoundTag tag) {
        return new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }
}
