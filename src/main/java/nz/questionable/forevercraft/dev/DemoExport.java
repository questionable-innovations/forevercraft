package nz.questionable.forevercraft.dev;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.slf4j.Logger;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import nz.questionable.forevercraft.config.ForevercraftConfig;
import nz.questionable.forevercraft.world.Era;
import nz.questionable.forevercraft.world.ServerState;
import nz.questionable.forevercraft.world.Stash;

/**
 * Writes what tools/demo-map needs: a coarse terrain/biome grid over every era (sampled from the noise, so
 * nothing is generated) and a block-level top-down scan around each era's spawn.
 */
final class DemoExport {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int PAD = 4000;
    static final int INSET = 128;

    private DemoExport() {}

    static void write(MinecraftServer server, Path out, int step) throws IOException {
        Files.createDirectories(out);
        ServerLevel level = server.overworld();
        List<Era> eras = ServerState.get().eras;

        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        List<ResourceLocation> biomeIds = biomes.keySet().stream().sorted().toList();
        Map<ResourceLocation, Integer> biomeIndex = new HashMap<>();
        for (int i = 0; i < biomeIds.size(); i++) biomeIndex.put(biomeIds.get(i), i);

        JsonObject meta = new JsonObject();
        meta.addProperty("seed", server.getWorldData().worldGenOptions().seed());
        meta.addProperty("seaLevel", level.getSeaLevel());
        JsonObject config = new JsonObject();
        config.addProperty("minSpawnDistance", ForevercraftConfig.MIN_SPAWN_DISTANCE.get());
        config.addProperty("maxSpawnDistance", ForevercraftConfig.MAX_SPAWN_DISTANCE.get());
        config.addProperty("preferredMaxRadius", ForevercraftConfig.PREFERRED_MAX_RADIUS.get());
        meta.add("config", config);
        meta.add("eras", eras(level, eras));
        meta.add("overview", overview(level, eras, step, out, biomeIndex));
        meta.add("insets", insets(level, eras, out, biomeIndex, meta));
        meta.add("biomes", biomeTable(biomes, biomeIds));
        Files.writeString(out.resolve("meta.json"), new GsonBuilder().setPrettyPrinting().create().toJson(meta),
                StandardCharsets.UTF_8);
        LOG.info("Demo export written to {}", out);
    }

    private static JsonArray eras(ServerLevel level, List<Era> eras) {
        JsonArray list = new JsonArray();
        for (Era era : eras) {
            JsonObject e = new JsonObject();
            e.addProperty("index", era.index);
            e.add("spawn", xyz(era.spawn));
            e.addProperty("offsetX", era.offsetX);
            e.addProperty("offsetZ", era.offsetZ);
            e.addProperty("style", DemoBases.Style.at(level, era.spawn).id());
            JsonArray stashes = new JsonArray();
            for (Stash s : era.stashes) {
                JsonObject o = new JsonObject();
                o.addProperty("player", s.playerName());
                o.addProperty("dimension", s.dimension().toString());
                o.add("pos", xyz(s.pos()));
                o.addProperty("chests", s.chests());
                o.addProperty("stacks", s.stacks());
                stashes.add(o);
            }
            e.add("stashes", stashes);
            list.add(e);
        }
        return list;
    }

    private static JsonObject overview(ServerLevel level, List<Era> eras, int step, Path out,
                                       Map<ResourceLocation, Integer> biomeIndex) throws IOException {
        int minX = eras.stream().mapToInt(e -> e.spawn.getX()).min().orElse(0);
        int maxX = eras.stream().mapToInt(e -> e.spawn.getX()).max().orElse(0);
        int minZ = eras.stream().mapToInt(e -> e.spawn.getZ()).min().orElse(0);
        int maxZ = eras.stream().mapToInt(e -> e.spawn.getZ()).max().orElse(0);
        int n = (Math.max(maxX - minX, maxZ - minZ) + 2 * PAD) / step;
        int x0 = (minX + maxX) / 2 - n * step / 2;
        int z0 = (minZ + maxZ) / 2 - n * step / 2;

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        BiomeSource source = generator.getBiomeSource();
        Climate.Sampler sampler = random.sampler();
        int sea = level.getSeaLevel();
        short[] height = new short[n * n];
        short[] biome = new short[n * n];
        AtomicInteger rows = new AtomicInteger();
        long started = System.currentTimeMillis();
        IntStream.range(0, n).parallel().forEach(row -> {
            int z = z0 + row * step;
            for (int col = 0; col < n; col++) {
                int x = x0 + col * step;
                int h = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                Holder<Biome> b = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(Math.max(h, sea)),
                        QuartPos.fromBlock(z), sampler);
                height[row * n + col] = (short) h;
                biome[row * n + col] = (short) (int) b.unwrapKey().map(k -> biomeIndex.get(k.location())).orElse(-1);
            }
            int done = rows.incrementAndGet();
            if (done % Math.max(1, n / 10) == 0) LOG.info("Demo overview: {}/{} rows", done, n);
        });
        LOG.info("Demo overview: {}x{} samples in {} s", n, n, (System.currentTimeMillis() - started) / 1000);
        writeShorts(out.resolve("overview.bin"), height, biome);

        JsonObject o = new JsonObject();
        o.addProperty("x0", x0);
        o.addProperty("z0", z0);
        o.addProperty("step", step);
        o.addProperty("size", n);
        o.addProperty("file", "overview.bin");
        return o;
    }

    /** Per column: top block, its y, the first non-water block under it, that block's y, and the biome. */
    private static JsonArray insets(ServerLevel level, List<Era> eras, Path out,
                                    Map<ResourceLocation, Integer> biomeIndex, JsonObject meta) throws IOException {
        Map<String, Integer> palette = new HashMap<>();
        JsonArray blocks = new JsonArray();
        JsonArray list = new JsonArray();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (Era era : eras) {
            int x0 = era.spawn.getX() - INSET / 2;
            int z0 = era.spawn.getZ() - INSET / 2;
            DemoBases.loadArea(level, era.spawn.getX(), era.spawn.getZ(), INSET / 2 + 1);
            short[][] data = new short[5][INSET * INSET];
            for (int dz = 0; dz < INSET; dz++) {
                for (int dx = 0; dx < INSET; dx++) {
                    int x = x0 + dx;
                    int z = z0 + dz;
                    int i = dz * INSET + dx;
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    BlockState topState = level.getBlockState(p.set(x, top, z));
                    data[0][i] = (short) blockIndex(level, p, topState, palette, blocks);
                    data[1][i] = (short) top;
                    int floor = top;
                    while (floor > level.getMinBuildHeight() && !level.getBlockState(p.set(x, floor, z)).getFluidState().isEmpty()) {
                        floor--;
                    }
                    data[2][i] = (short) blockIndex(level, p.set(x, floor, z), level.getBlockState(p), palette, blocks);
                    data[3][i] = (short) floor;
                    data[4][i] = (short) (int) level.getBiome(p.set(x, top, z)).unwrapKey()
                            .map(k -> biomeIndex.get(k.location())).orElse(-1);
                }
            }
            String file = "inset-" + era.index + ".bin";
            writeShorts(out.resolve(file), data);
            JsonObject o = new JsonObject();
            o.addProperty("era", era.index);
            o.addProperty("x0", x0);
            o.addProperty("z0", z0);
            o.addProperty("size", INSET);
            o.addProperty("file", file);
            list.add(o);
        }
        meta.add("blocks", blocks);
        return list;
    }

    private static int blockIndex(ServerLevel level, BlockPos pos, BlockState state, Map<String, Integer> palette,
                                  JsonArray blocks) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return palette.computeIfAbsent(id, k -> {
            JsonObject o = new JsonObject();
            o.addProperty("id", k);
            o.addProperty("mapColor", state.getMapColor(level, pos).col);
            blocks.add(o);
            return blocks.size() - 1;
        });
    }

    private static JsonArray biomeTable(Registry<Biome> biomes, List<ResourceLocation> ids) {
        JsonArray list = new JsonArray();
        for (ResourceLocation id : ids) {
            Biome b = biomes.get(id);
            BiomeSpecialEffects fx = b.getModifiedSpecialEffects();
            JsonObject o = new JsonObject();
            o.addProperty("id", id.toString());
            o.addProperty("temperature", b.getModifiedClimateSettings().temperature());
            o.addProperty("downfall", b.getModifiedClimateSettings().downfall());
            fx.getGrassColorOverride().ifPresent(c -> o.addProperty("grass", c));
            fx.getFoliageColorOverride().ifPresent(c -> o.addProperty("foliage", c));
            o.addProperty("water", fx.getWaterColor());
            o.addProperty("grassModifier", fx.getGrassColorModifier().getSerializedName());
            list.add(o);
        }
        return list;
    }

    private static void writeShorts(Path file, short[]... arrays) throws IOException {
        try (DataOutputStream data = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            for (short[] array : arrays) {
                for (short s : array) data.writeShort(s);
            }
        }
    }

    private static JsonArray xyz(BlockPos pos) {
        JsonArray a = new JsonArray();
        a.add(pos.getX());
        a.add(pos.getY());
        a.add(pos.getZ());
        return a;
    }
}
