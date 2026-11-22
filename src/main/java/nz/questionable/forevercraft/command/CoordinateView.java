package nz.questionable.forevercraft.command;

import java.util.Locale;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import nz.questionable.forevercraft.net.ServerSettingsPacket;
import nz.questionable.forevercraft.world.Era;
import nz.questionable.forevercraft.world.ServerState;

/** Converts true coordinates into what a given viewer sees on their F3 screen. */
record CoordinateView(int offsetX, int offsetZ, Set<ResourceLocation> dimensions) {
    private static final String[] DIRECTIONS =
            {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

    static CoordinateView of(ServerPlayer player) {
        ServerSettingsPacket s = ServerState.settingsFor(player);
        return new CoordinateView(s.offsetX(), s.offsetZ(), s.offsetDimensions());
    }

    /** What players currently see, regardless of any per-player reveal. */
    static CoordinateView ofEra(Era era) {
        return new CoordinateView(era.offsetX, era.offsetZ, ServerState.offsetDimensions());
    }

    String format(ResourceLocation dimension, BlockPos pos) {
        boolean shifted = dimensions.contains(dimension);
        int x = shifted ? pos.getX() - offsetX : pos.getX();
        int z = shifted ? pos.getZ() - offsetZ : pos.getZ();
        return String.format(Locale.ROOT, "%d %d %d", x, pos.getY(), z);
    }

    String formatXZ(ResourceLocation dimension, BlockPos pos) {
        boolean shifted = dimensions.contains(dimension);
        int x = shifted ? pos.getX() - offsetX : pos.getX();
        int z = shifted ? pos.getZ() - offsetZ : pos.getZ();
        return String.format(Locale.ROOT, "X %d, Z %d", x, z);
    }

    /** Compass direction from {@code from} to {@code to}; north is -Z. */
    static String direction(BlockPos from, BlockPos to) {
        double angle = Math.toDegrees(Math.atan2(to.getX() - from.getX(), from.getZ() - to.getZ()));
        int index = (int) Math.floorMod(Math.round(angle / 45.0), 8);
        return DIRECTIONS[index];
    }

    static long horizontalDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.round(Math.sqrt(dx * dx + dz * dz));
    }
}
