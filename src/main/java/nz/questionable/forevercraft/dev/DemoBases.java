package nz.questionable.forevercraft.dev;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Builds a small homestead from vanilla village pieces on a levelled clearing around a spawn point. */
final class DemoBases {
    private static final int BLEND = 10;
    private static final int GAP = 3;
    private static final int[][] QUADRANTS = {{1, -1}, {-1, -1}, {-1, 1}, {1, 1}};
    private static final Block[] FLOWERS = {Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY,
            Blocks.AZURE_BLUET};

    enum Style {
        PLAINS(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT_PATH,
                List.of("plains_big_house_1", "plains_library_1", "plains_temple_3", "plains_medium_house_1"),
                List.of("plains_small_house_1", "plains_small_house_3", "plains_small_house_5", "plains_medium_house_2"),
                List.of("plains_large_farm_1", "plains_small_farm_1"),
                List.of("plains_animal_pen_1", "plains_animal_pen_2", "plains_stable_1", "plains_tool_smith_1")),
        TAIGA(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT_PATH,
                List.of("taiga_library_1", "taiga_temple_1", "taiga_medium_house_1", "taiga_medium_house_3"),
                List.of("taiga_small_house_1", "taiga_small_house_2", "taiga_small_house_4", "taiga_medium_house_2"),
                List.of("taiga_large_farm_1", "taiga_large_farm_2", "taiga_small_farm_1"),
                List.of("taiga_animal_pen_1", "taiga_tool_smith_1", "taiga_shepherds_house_1")),
        SNOWY(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT_PATH,
                List.of("snowy_library_1", "snowy_temple_1", "snowy_medium_house_1", "snowy_medium_house_3"),
                List.of("snowy_small_house_1", "snowy_small_house_3", "snowy_small_house_5", "snowy_small_house_7"),
                List.of("snowy_farm_1", "snowy_farm_2"),
                List.of("snowy_animal_pen_1", "snowy_animal_pen_2", "snowy_tool_smith_1")),
        SAVANNA(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT_PATH,
                List.of("savanna_library_1", "savanna_temple_1", "savanna_medium_house_1", "savanna_medium_house_2"),
                List.of("savanna_small_house_1", "savanna_small_house_3", "savanna_small_house_6", "savanna_small_house_8"),
                List.of("savanna_large_farm_1", "savanna_large_farm_2", "savanna_small_farm"),
                List.of("savanna_animal_pen_1", "savanna_animal_pen_2", "savanna_tool_smith_1")),
        DESERT(Blocks.SAND, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE,
                List.of("desert_library_1", "desert_temple_1", "desert_medium_house_1", "desert_medium_house_2"),
                List.of("desert_small_house_1", "desert_small_house_2", "desert_small_house_5", "desert_small_house_7"),
                List.of("desert_large_farm_1", "desert_farm_1", "desert_farm_2"),
                List.of("desert_animal_pen_1", "desert_animal_pen_2", "desert_tool_smith_1"));

        final Block top;
        final Block filler;
        final Block path;
        final List<List<String>> pieces;

        @SafeVarargs
        Style(Block top, Block filler, Block path, List<String>... pieces) {
            this.top = top;
            this.filler = filler;
            this.path = path;
            this.pieces = Arrays.asList(pieces);
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Style at(ServerLevel level, BlockPos pos) {
            Holder<Biome> biome = level.getBiome(pos);
            if (biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS)) return DESERT;
            if (biome.is(BiomeTags.IS_SAVANNA)) return SAVANNA;
            if (biome.value().coldEnoughToSnow(pos)) return SNOWY;
            if (biome.is(BiomeTags.IS_TAIGA)) return TAIGA;
            return PLAINS;
        }
    }

    record Base(Style style, int y, List<BlockPos> spots) {}

    private record Piece(StructureTemplate template, int sx, int sz) {}

    private DemoBases() {}

    static Base build(ServerLevel level, BlockPos spawn, RandomSource random, int players) {
        Style style = Style.at(level, spawn);
        int cx = spawn.getX();
        int cz = spawn.getZ();

        List<Piece> pieces = new ArrayList<>();
        int core = 16;
        for (int q = 0; q < QUADRANTS.length; q++) {
            List<String> options = style.pieces.get(q);
            String name = options.get(random.nextInt(options.size()));
            ResourceLocation id = new ResourceLocation("minecraft", "village/" + style.id() + "/houses/" + name);
            StructureTemplate template = level.getStructureManager().get(id)
                    .orElseThrow(() -> new IllegalStateException("Missing template " + id));
            pieces.add(new Piece(template, QUADRANTS[q][0], QUADRANTS[q][1]));
            core = Math.max(core, GAP + Math.max(template.getSize().getX(), template.getSize().getZ()) + 3);
        }

        loadArea(level, cx, cz, core + BLEND + 2);
        int y = Math.max(medianGround(level, cx, cz, core), level.getSeaLevel());
        terraform(level, cx, cz, y, core, style);
        for (Piece piece : pieces) place(level, piece, cx, cz, y, random);
        paths(level, cx, cz, y, core, style);
        scatter(level, cx, cz, y, core, style, random);
        return new Base(style, y, spots(cx, cz, y + 1, core, random, players));
    }

    /** Level.getHeight reports the world floor for unloaded chunks, so generate the area first. */
    static void loadArea(ServerLevel level, int cx, int cz, int radius) {
        for (int x = (cx - radius) >> 4; x <= (cx + radius) >> 4; x++) {
            for (int z = (cz - radius) >> 4; z <= (cz + radius) >> 4; z++) level.getChunk(x, z);
        }
    }

    /** Topmost block that is terrain rather than a tree, plant, snow layer or water. */
    static int ground(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
        while (p.getY() > level.getMinBuildHeight()) {
            p.move(Direction.DOWN);
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || !s.getFluidState().isEmpty()
                    || !s.isSolidRender(level, p)) continue;
            return p.getY();
        }
        return level.getSeaLevel();
    }

    private static int medianGround(ServerLevel level, int cx, int cz, int radius) {
        List<Integer> heights = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) heights.add(ground(level, cx + dx, cz + dz));
        }
        heights.sort(null);
        return heights.get(heights.size() / 2);
    }

    /** Flat disc of radius {@code core} at {@code y}, sloping back to natural ground over BLEND blocks. */
    private static void terraform(ServerLevel level, int cx, int cz, int y, int core, Style style) {
        int r = core + BLEND;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r) continue;
                int x = cx + dx;
                int z = cz + dz;
                int natural = ground(level, x, z);
                double t = d <= core ? 0 : Mth.smoothstep((d - core) / BLEND);
                int h = (int) Math.round(Mth.lerp(t, y, natural));
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                for (int yy = Math.min(h, natural) - 3; yy < h; yy++) {
                    BlockState s = level.getBlockState(p.set(x, yy, z));
                    if (yy > natural || !s.isSolidRender(level, p)) level.setBlock(p, style.filler.defaultBlockState(), 2);
                }
                level.setBlock(p.set(x, h, z), style.top.defaultBlockState(), 2);
                for (int yy = h + 1; yy <= Math.max(surface, natural) + 1; yy++) {
                    if (!level.getBlockState(p.set(x, yy, z)).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void place(ServerLevel level, Piece piece, int cx, int cz, int y, RandomSource random) {
        int w = piece.template.getSize().getX();
        int d = piece.template.getSize().getZ();
        int x0 = piece.sx > 0 ? cx + GAP : cx - GAP - w + 1;
        int z0 = piece.sz > 0 ? cz + GAP : cz - GAP - d + 1;
        BlockPos origin = new BlockPos(x0, y, z0);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(true)
                .addProcessor(JigsawReplacementProcessor.INSTANCE)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        piece.template.placeInWorld(level, origin, origin, settings, random, 2);
    }

    /** A three-wide cross of path through the middle, with lamp posts on the corners of the square. */
    private static void paths(ServerLevel level, int cx, int cz, int y, int core, Style style) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int t = -core + 1; t < core; t++) {
            for (int w = -1; w <= 1; w++) {
                pave(level, p.set(cx + t, y, cz + w), style);
                pave(level, p.set(cx + w, y, cz + t), style);
            }
        }
        for (int[] q : QUADRANTS) {
            BlockPos base = new BlockPos(cx + 2 * q[0], y, cz + 2 * q[1]);
            if (!level.getBlockState(base).is(style.top)) continue;
            level.setBlock(base.above(), Blocks.OAK_FENCE.defaultBlockState(), 2);
            level.setBlock(base.above(2), Blocks.OAK_FENCE.defaultBlockState(), 2);
            level.setBlock(base.above(3), Blocks.LANTERN.defaultBlockState(), 2);
        }
    }

    private static void pave(ServerLevel level, BlockPos pos, Style style) {
        if (!level.getBlockState(pos).is(style.top) || !level.getBlockState(pos.above()).isAir()) return;
        level.setBlock(pos, style.path.defaultBlockState(), 2);
    }

    private static void scatter(ServerLevel level, int cx, int cz, int y, int core, Style style, RandomSource random) {
        int r = core + BLEND;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                int x = cx + dx;
                int z = cz + dz;
                p.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
                if (!level.getBlockState(p).is(style.top)) continue;
                p.move(Direction.UP);
                float roll = random.nextFloat();
                BlockState plant = switch (style) {
                    case SNOWY -> Blocks.SNOW.defaultBlockState();
                    case DESERT -> roll < 0.02F ? Blocks.DEAD_BUSH.defaultBlockState() : null;
                    default -> roll < 0.025F ? FLOWERS[random.nextInt(FLOWERS.length)].defaultBlockState()
                            : roll < 0.2F ? Blocks.GRASS.defaultBlockState() : null;
                };
                if (plant != null) level.setBlock(p, plant, 2);
            }
        }
    }

    /** Distinct standing spots on the path arms, one per player. */
    private static List<BlockPos> spots(int cx, int cz, int y, int core, RandomSource random, int count) {
        List<Direction> arms = new ArrayList<>(List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST));
        List<BlockPos> spots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Direction arm = arms.remove(random.nextInt(arms.size()));
            int dist = 5 + random.nextInt(Math.max(1, core - 8));
            spots.add(new BlockPos(cx + arm.getStepX() * dist, y, cz + arm.getStepZ() * dist));
        }
        return spots;
    }
}
