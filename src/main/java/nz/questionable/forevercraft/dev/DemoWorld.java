package nz.questionable.forevercraft.dev;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import nz.questionable.forevercraft.Forevercraft;
import nz.questionable.forevercraft.reset.WorldResetService;
import nz.questionable.forevercraft.world.Era;
import nz.questionable.forevercraft.world.ServerState;

/**
 * Dev-only (left out of the jar). With FOREVERCRAFT_DEMO_OUT set, a fresh world gets five eras: a base at each
 * spawn, three mock players kitted out and standing in it, then a real reset. Exports map data for
 * tools/demo-map and stops the server. On a world that already has eras it only re-exports.
 */
@Mod.EventBusSubscriber(modid = Forevercraft.MOD_ID)
public final class DemoWorld {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String[] PLAYERS = {"Wren", "Juniper", "Ash"};
    private static final int ERAS = 5;

    private DemoWorld() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onStarted(ServerStartedEvent event) {
        String out = System.getenv("FOREVERCRAFT_DEMO_OUT");
        if (out == null || out.isBlank()) return;
        MinecraftServer server = event.getServer();
        try {
            if (ServerState.get().eras.size() == 1) build(server);
            String step = System.getenv("FOREVERCRAFT_DEMO_STEP");
            DemoExport.write(server, Path.of(out), step == null ? 16 : Integer.parseInt(step));
        } catch (Exception e) {
            LOG.error("Demo world failed", e);
        }
        server.halt(false);
    }

    private static void build(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        RandomSource random = RandomSource.create(20261123L);
        List<ServerPlayer> players = new ArrayList<>();
        for (String name : PLAYERS) players.add(join(server, name));

        for (int i = 0; i < ERAS; i++) {
            Era era = ServerState.get().currentEra();
            DemoBases.Base base = DemoBases.build(overworld, era.spawn, random, players.size());
            LOG.info("Demo: era {} base ({}) at {}", era.index, base.style(), era.spawn);
            for (int p = 0; p < players.size(); p++) {
                ServerPlayer player = players.get(p);
                BlockPos spot = base.spots().get(p);
                player.teleportTo(overworld, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                        random.nextFloat() * 360 - 180, 0);
                kit(player, i, p, random);
            }
            if (i < ERAS - 1) WorldResetService.startNewEra(server);
        }
        server.saveEverything(true, false, true);
    }

    private static ServerPlayer join(MinecraftServer server, String name) {
        GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile);
        server.getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    private static final Item[][] ARMOR = {
            {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
            {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
            {Items.IRON_HELMET, Items.DIAMOND_CHESTPLATE, Items.IRON_LEGGINGS, Items.DIAMOND_BOOTS},
            {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
            {Items.NETHERITE_HELMET, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.DIAMOND_BOOTS},
    };
    private static final Item[][] TOOLS = {
            {Items.STONE_PICKAXE, Items.STONE_AXE, Items.WOODEN_SWORD},
            {Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SWORD, Items.SHIELD},
            {Items.DIAMOND_PICKAXE, Items.IRON_AXE, Items.IRON_SWORD, Items.BOW},
            {Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SWORD, Items.BOW},
            {Items.NETHERITE_PICKAXE, Items.DIAMOND_AXE, Items.NETHERITE_SWORD, Items.CROSSBOW},
    };
    private static final Item[] BULK = {Items.COBBLESTONE, Items.OAK_LOG, Items.OAK_PLANKS, Items.TORCH,
            Items.BREAD, Items.COAL, Items.IRON_INGOT, Items.WHEAT_SEEDS, Items.DIRT, Items.STONE_BRICKS,
            Items.GLASS, Items.REDSTONE, Items.GOLD_INGOT, Items.COOKED_BEEF, Items.SPRUCE_LOG, Items.ANDESITE,
            Items.GRAVEL, Items.SAND, Items.STRING, Items.BONE_MEAL, Items.LAPIS_LAZULI, Items.FLINT,
            Items.FEATHER, Items.LEATHER, Items.SUGAR_CANE, Items.PAPER, Items.BOOK, Items.DIAMOND,
            Items.OBSIDIAN, Items.ENDER_PEARL, Items.BLAZE_ROD, Items.QUARTZ, Items.GLOWSTONE_DUST};

    /** Era-appropriate gear; the first player carries enough to need two chests. */
    private static void kit(ServerPlayer player, int era, int who, RandomSource random) {
        Item[] armor = ARMOR[era];
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(armor[0]));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(armor[1]));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(armor[2]));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(armor[3]));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH, 32));
        for (Item tool : TOOLS[era]) player.getInventory().add(new ItemStack(tool));
        int stacks = who == 0 ? 30 : 8 + random.nextInt(10);
        for (int s = 0; s < stacks; s++) {
            Item item = BULK[(s * 7 + era * 3 + who) % BULK.length];
            player.getInventory().add(new ItemStack(item, 1 + random.nextInt(new ItemStack(item).getMaxStackSize())));
        }
        if (who == 1) player.getEnderChestInventory().addItem(new ItemStack(Items.DIAMOND, 3 + era * 4));
        player.setExperienceLevels(5 + era * 6 + random.nextInt(5));
    }
}
