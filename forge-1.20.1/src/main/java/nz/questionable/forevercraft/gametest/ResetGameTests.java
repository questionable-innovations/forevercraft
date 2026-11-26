package nz.questionable.forevercraft.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import org.slf4j.Logger;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import nz.questionable.forevercraft.Forevercraft;
import nz.questionable.forevercraft.reset.WorldResetService;
import nz.questionable.forevercraft.world.EndRegeneration;
import nz.questionable.forevercraft.world.Era;
import nz.questionable.forevercraft.world.PlayerMarks;
import nz.questionable.forevercraft.world.ServerState;
import nz.questionable.forevercraft.world.Stash;

/** End-to-end reset checks with mock players. Each test is its own batch because a reset is global. */
@GameTestHolder(Forevercraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResetGameTests {
    private static final Logger LOG = LogUtils.getLogger();

    private ResetGameTests() {}

    /**
     * Like GameTestHelper.makeMockServerPlayerInLevel, but with a live (embedded) channel: Forge's login path
     * and packet sending need one.
     */
    private static ServerPlayer joinMockPlayer(GameTestHelper helper, GameProfile profile) {
        MinecraftServer server = helper.getLevel().getServer();
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), profile);
        server.getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    private static ServerPlayer joinMockPlayer(GameTestHelper helper) {
        return joinMockPlayer(helper, new GameProfile(UUID.randomUUID(), "mock-" + helper.getTick()));
    }

    /** The test reporter only prints the message; log the trace so failures are debuggable. */
    private static void run(GameTestHelper helper, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | AssertionError e) {
            LOG.error("Forevercraft game test failed", e);
            throw e;
        }
    }

    @GameTest(template = "platform", batch = "reset_online", timeoutTicks = 600)
    public static void onlinePlayerIsStashedResetAndMoved(GameTestHelper helper) {
        run(helper, () -> onlinePlayer(helper));
    }

    private static void onlinePlayer(GameTestHelper helper) {
        ServerPlayer player = joinMockPlayer(helper);
        BlockPos stand = standOnPlatform(helper, player);
        fill(player);
        Era before = ServerState.get().currentEra();

        WorldResetService.Result result = WorldResetService.startNewEra(helper.getLevel().getServer());

        Era era = result.era();
        helper.assertTrue(era.index == before.index + 1, "era should advance");
        assertStashedNear(helper, before, player.getUUID(), stand, 39);
        assertFreshAtSpawn(helper, player, era);
        long dist = horizontal(before.spawn, era.spawn);
        helper.assertTrue(dist >= 6000 && dist <= 10000, "new spawn " + dist + " blocks from the old one");
        int shownX = era.spawn.getX() - era.offsetX;
        int shownZ = era.spawn.getZ() - era.offsetZ;
        helper.assertTrue(shownX >= 0 && shownX < 16 && shownZ >= 0 && shownZ < 16,
                "spawn should read as ~0,0, reads " + shownX + ", " + shownZ);
        helper.assertTrue(ServerState.get().pendingEndSeed != null, "End regeneration should be queued");
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "reset_offline", timeoutTicks = 600)
    public static void offlinePlayerIsHandledOnNextLogin(GameTestHelper helper) {
        run(helper, () -> offlinePlayer(helper));
    }

    private static void offlinePlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        GameProfile profile = player.getGameProfile();
        BlockPos stand = standOnPlatform(helper, player);
        fill(player);
        server.getPlayerList().remove(player); // saves playerdata, like logging out

        Era era = WorldResetService.startNewEra(server).era();
        helper.assertTrue(ServerState.get().pendingPlayers.contains(profile.getId()), "offline player should be pending");
        helper.assertTrue(stashOf(ServerState.get().eras, profile.getId()) == null, "nothing placed while they are away");

        ServerPlayer back = joinMockPlayer(helper, profile);

        helper.assertTrue(!ServerState.get().pendingPlayers.contains(profile.getId()), "pending cleared on login");
        Era lastPlayed = ServerState.get().era(era.index - 1);
        assertStashedNear(helper, lastPlayed, profile.getId(), stand, 39);
        assertFreshAtSpawn(helper, back, era);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "reset_end", timeoutTicks = 600)
    public static void playerInTheEndIsStashedAtOldSpawn(GameTestHelper helper) {
        run(helper, () -> playerInEnd(helper));
    }

    private static void playerInEnd(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = joinMockPlayer(helper);
        ServerLevel end = server.getLevel(Level.END);
        ServerState.get().pendingEndSeed = null; // an earlier test's End swap may still be waiting
        player.teleportTo(end, 100.5, 49, 0.5, 0, 0);
        helper.assertTrue(player.level().dimension() == Level.END, "player should be in the End before the reset");
        player.getInventory().add(new ItemStack(Items.ELYTRA));
        Era before = ServerState.get().currentEra();
        BlockPos oldSpawn = server.overworld().getSharedSpawnPos();

        Era era = WorldResetService.startNewEra(server).era();

        Stash stash = before.stashes.stream().filter(s -> s.player().equals(player.getUUID())).findFirst().orElse(null);
        helper.assertTrue(stash != null, "stash recorded");
        helper.assertTrue(stash.dimension().equals(Level.OVERWORLD.location()), "stash moved to the overworld");
        helper.assertTrue(horizontal(stash.pos(), oldSpawn) <= 8, "stash near the old spawn, was " + stash.pos());
        assertStashed(helper, server.overworld(), stash.pos(), 1, player.getGameProfile().getName());
        assertFreshAtSpawn(helper, player, era);
        helper.succeed();
    }

    @GameTest(template = "platform", batch = "reset_live_end", timeoutTicks = 600)
    public static void endIsReplacedLive(GameTestHelper helper) {
        run(helper, () -> endReplacedLive(helper));
    }

    private static void endReplacedLive(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel oldEnd = server.getLevel(Level.END);
        Path backups = ServerState.worldDir(server).resolve("forevercraft").resolve("old_ends");
        int backupsBefore = count(backups);
        ServerPlayer player = joinMockPlayer(helper);

        WorldResetService.startNewEra(server);
        helper.assertTrue(EndRegeneration.liveQueued(), "live End swap should be queued");
        player.teleportTo(oldEnd, 0.5, 70, 0.5, 0, 0);
        helper.assertTrue(player.level().dimension() == Level.OVERWORLD, "End travel is closed until the swap");

        helper.succeedWhen(() -> {
            ServerLevel end = server.getLevel(Level.END);
            helper.assertTrue(end != null && end != oldEnd, "End not replaced yet");
            helper.assertTrue(ServerState.get().pendingEndSeed == null, "pending End seed should be cleared");
            helper.assertTrue(end.getSeed() == ServerState.get().endSeed, "new End should use the new seed");
            helper.assertTrue(count(backups) == backupsBefore + 1, "old End should be kept as a backup");
            end.getChunk(0, 0);
            helper.assertTrue(end.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0) > end.getMinBuildHeight(),
                    "new End should generate its main island");
            player.teleportTo(end, 0.5, 70, 0.5, 0, 0);
            helper.assertTrue(player.level() == end, "player should be able to enter the new End");
            player.teleportTo(server.overworld(), 0.5, 100, 0.5, 0, 0);
        });
    }

    private static int count(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> files = Files.list(dir)) {
            return (int) files.count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Puts the player on the first air block above the platform's stone floor and returns that block. */
    private static BlockPos standOnPlatform(GameTestHelper helper, ServerPlayer player) {
        ServerLevel level = helper.getLevel();
        for (int y = 0; y < 4; y++) {
            BlockPos p = helper.absolutePos(new BlockPos(3, y, 3));
            if (level.getBlockState(p).isAir() && !level.getBlockState(p.below()).isAir()) {
                player.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                helper.assertTrue(player.blockPosition().equals(p), "player should stand at " + p);
                return p;
            }
        }
        throw new AssertionError("no floor found in the platform");
    }

    private static void fill(ServerPlayer player) {
        for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        player.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
        player.setExperienceLevels(30);
        player.setRespawnPosition(Level.OVERWORLD, player.blockPosition(), 0, true, false);
    }

    private static Stash stashOf(java.util.List<Era> eras, UUID player) {
        for (Era e : eras) {
            for (Stash s : e.stashes) if (s.player().equals(player)) return s;
        }
        return null;
    }

    /** The recorded stash is within a few blocks of where the player stood and holds everything. */
    private static void assertStashedNear(GameTestHelper helper, Era era, UUID player, BlockPos stand, int stacks) {
        Stash stash = stashOf(java.util.List.of(era), player);
        helper.assertTrue(stash != null, "no stash recorded in era " + era.index);
        helper.assertTrue(horizontal(stash.pos(), stand) <= 2 && Math.abs(stash.pos().getY() - stand.getY()) <= 3,
                "stash at " + stash.pos() + " is not near " + stand);
        assertStashed(helper, helper.getLevel(), stash.pos(), stacks, stash.playerName());
    }

    /** Counts item stacks in the chest column starting at {@code bottom}. */
    private static void assertStashed(GameTestHelper helper, ServerLevel level, BlockPos bottom, int expectedStacks,
                                      String playerName) {
        int stacks = 0;
        int chests = 0;
        for (BlockPos p = bottom; level.getBlockEntity(p) instanceof ChestBlockEntity chest; p = p.above()) {
            chests++;
            for (int i = 0; i < chest.getContainerSize(); i++) if (!chest.getItem(i).isEmpty()) stacks++;
        }
        helper.assertTrue(chests == (expectedStacks + 26) / 27, "expected " + ((expectedStacks + 26) / 27)
                + " chests at " + bottom + ", found " + chests);
        helper.assertTrue(stacks == expectedStacks, "expected " + expectedStacks + " stacks, found " + stacks);
        BlockPos top = bottom.above(chests);
        helper.assertTrue(level.getBlockEntity(top) instanceof SignBlockEntity, "expected a sign on top at " + top);
        SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(top);
        helper.assertTrue(sign.isWaxed(), "sign should be waxed");
        String signName = sign.getFrontText().getMessage(0, false).getString();
        helper.assertTrue(signName.equals(playerName), "sign should name " + playerName + ", was " + signName);
        helper.assertTrue(sign.getFrontText().getMessage(1, false).getString().matches("\\d{1,2} \\w{3} \\d{4}"),
                "sign should carry the date, was " + sign.getFrontText().getMessage(1, false).getString());
    }

    private static void assertFreshAtSpawn(GameTestHelper helper, ServerPlayer player, Era era) {
        helper.assertTrue(player.getInventory().isEmpty(), "inventory should be empty");
        helper.assertTrue(player.getEnderChestInventory().isEmpty(), "ender chest should be empty");
        helper.assertTrue(player.experienceLevel == 0, "levels should be wiped");
        helper.assertTrue(player.getRespawnPosition() == null, "respawn point should be cleared");
        helper.assertTrue(player.level().dimension() == Level.OVERWORLD, "player should be in the overworld");
        helper.assertTrue(horizontal(player.blockPosition(), era.spawn) <= 1,
                "player at " + player.blockPosition() + ", spawn " + era.spawn);
        helper.assertTrue(PlayerMarks.era(player).orElse(-1) == era.index, "era marker should be updated");
    }

    private static long horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.round(Math.sqrt(dx * dx + dz * dz));
    }
}
