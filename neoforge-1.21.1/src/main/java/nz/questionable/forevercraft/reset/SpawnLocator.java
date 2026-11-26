package nz.questionable.forevercraft.reset;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.PlayerRespawnLogic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/** Turns a target point into a standable land position, generating as few chunks as possible. */
public final class SpawnLocator {
    private static final int BIOME_STEP = 64;
    private static final int BIOME_RADIUS = 1536;
    private static final int MAX_LAND_CANDIDATES = 6;

    private SpawnLocator() {}

    public static BlockPos find(ServerLevel level, int targetX, int targetZ) {
        int tried = 0;
        // Spiral outwards on a coarse grid; biome lookups are cheap, chunk generation is not.
        for (int ring = 0; ring * BIOME_STEP <= BIOME_RADIUS; ring++) {
            for (int[] offset : ring(ring)) {
                int x = targetX + offset[0] * BIOME_STEP;
                int z = targetZ + offset[1] * BIOME_STEP;
                if (!isLandBiome(level, x, z)) continue;
                BlockPos pos = standableNear(level, new ChunkPos(new BlockPos(x, 0, z)));
                if (pos != null) return pos;
                if (++tried >= MAX_LAND_CANDIDATES) return fallback(level, targetX, targetZ);
            }
        }
        return fallback(level, targetX, targetZ);
    }

    private static boolean isLandBiome(ServerLevel level, int x, int z) {
        var source = level.getChunkSource();
        Holder<Biome> biome = source.getGenerator().getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(x), QuartPos.fromBlock(level.getSeaLevel()), QuartPos.fromBlock(z),
                source.randomState().sampler());
        return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_DEEP_OCEAN) && !biome.is(BiomeTags.IS_RIVER);
    }

    /** Vanilla's per-chunk spawn check scans from the chunk's corner, so spawn usually lands on a chunk corner. */
    @Nullable
    private static BlockPos standableNear(ServerLevel level, ChunkPos center) {
        for (int ring = 0; ring <= 1; ring++) {
            for (int[] offset : ring(ring)) {
                BlockPos pos = PlayerRespawnLogic.getSpawnPosInChunk(level,
                        new ChunkPos(center.x + offset[0], center.z + offset[1]));
                if (pos != null) return pos;
            }
        }
        return null;
    }

    private static BlockPos fallback(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return new BlockPos(x, Math.max(y, level.getSeaLevel()), z);
    }

    /** Grid offsets on the square ring at Chebyshev distance {@code r}, nearest-to-axis first. */
    static int[][] ring(int r) {
        if (r == 0) return new int[][] {{0, 0}};
        int[][] out = new int[8 * r][];
        int i = 0;
        for (int d = 0; d <= r; d++) {
            for (int sign : d == 0 ? new int[] {1} : new int[] {1, -1}) {
                int s = d * sign;
                out[i++] = new int[] {r, s};
                out[i++] = new int[] {-r, -s};
                if (d != r) {
                    out[i++] = new int[] {s, -r};
                    out[i++] = new int[] {-s, r};
                }
            }
        }
        return out;
    }
}
