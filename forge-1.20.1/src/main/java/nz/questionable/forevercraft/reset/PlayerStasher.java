package nz.questionable.forevercraft.reset;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;
import nz.questionable.forevercraft.config.ForevercraftConfig.EnderChestMode;

/** Empties a player into stacked chests and returns them to a fresh-spawn state. */
public final class PlayerStasher {
    public static final int CHEST_SLOTS = 27;
    private static final int SEARCH_RADIUS = 4;

    public record Placed(BlockPos bottom, int chests, int stacks) {}

    private PlayerStasher() {}

    /** Removes everything the player holds: inventory, armor, offhand, crafting grid, cursor, ender chest. */
    public static List<ItemStack> takeBelongings(ServerPlayer player, EnderChestMode enderChest) {
        List<ItemStack> items = new ArrayList<>();
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            items.add(carried);
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        drain(player.inventoryMenu.getCraftSlots(), items);
        Inventory inventory = player.getInventory();
        drain(inventory, items);
        // Closing an open menu (crafting table, anvil...) hands its grid back to the inventory, which is
        // empty now so nothing spills on the floor; then take those too.
        player.closeContainer();
        drain(inventory, items);

        switch (enderChest) {
            case STASH -> drain(player.getEnderChestInventory(), items);
            case CLEAR -> player.getEnderChestInventory().clearContent();
            case KEEP -> {}
        }
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
        return items;
    }

    private static void drain(Container container, List<ItemStack> into) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.removeItemNoUpdate(i);
            if (!stack.isEmpty()) into.add(stack);
        }
    }

    /**
     * Places as many single chests as needed, stacked vertically, at or near {@code origin}, topped with a waxed
     * sign carrying {@code signLines} when there is room for one.
     */
    @Nullable
    public static Placed placeStash(ServerLevel level, BlockPos origin, Direction facing, List<ItemStack> items,
                                    Component name, Component... signLines) {
        if (items.isEmpty()) return null;
        int chests = Mth.positiveCeilDiv(items.size(), CHEST_SLOTS);
        BlockPos bottom = findSpot(level, origin, chests);
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
        int next = 0;
        for (int c = 0; c < chests; c++) {
            BlockPos pos = bottom.above(c);
            level.setBlock(pos, chest, 3);
            if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity be)) {
                throw new IllegalStateException("Chest at " + pos + " has no block entity");
            }
            be.setCustomName(name);
            for (int slot = 0; slot < CHEST_SLOTS && next < items.size(); slot++) {
                be.setItem(slot, items.get(next++));
            }
            be.setChanged();
        }
        BlockPos top = bottom.above(chests);
        if (signLines.length > 0 && top.getY() < level.getMaxBuildHeight() && replaceable(level, top)) {
            placeSign(level, top, facing, signLines);
        }
        return new Placed(bottom, chests, items.size());
    }

    /** Standing sign facing the same way as the chests; waxed so nobody can overwrite it. */
    private static void placeSign(ServerLevel level, BlockPos pos, Direction facing, Component[] lines) {
        level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(facing)), 3);
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) return;
        SignText text = new SignText();
        for (int i = 0; i < Math.min(lines.length, 4); i++) text = text.setMessage(i, lines[i]);
        sign.setText(text, true);
        sign.setText(text, false);
        sign.setWaxed(true);
        sign.setChanged();
    }

    /**
     * Nearest column of {@code height} free blocks. Prefers room for the sign on top (which also keeps the top
     * chest openable), then just a non-solid block above, then anything.
     */
    static BlockPos findSpot(ServerLevel level, BlockPos origin, int height) {
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - height - 1;
        BlockPos start = new BlockPos(origin.getX(), Mth.clamp(origin.getY(), minY, maxY), origin.getZ());
        for (int pass = 0; pass < 3; pass++) {
            int need = pass == 0 ? height + 1 : height;
            boolean headroom = pass == 1;
            for (BlockPos p : BlockPos.withinManhattan(start, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)) {
                if (p.getY() < minY || p.getY() > maxY) continue;
                if (fits(level, p, need, headroom)) return p.immutable();
            }
        }
        // Everything nearby is solid: overwrite blocks, but never another block entity's contents.
        BlockPos p = start;
        while (p.getY() < maxY && hasBlockEntity(level, p, height)) p = p.above();
        return p;
    }

    private static boolean fits(ServerLevel level, BlockPos bottom, int height, boolean headroom) {
        if (!level.getWorldBorder().isWithinBounds(bottom)) return false;
        for (int i = 0; i < height; i++) {
            if (!replaceable(level, bottom.above(i))) return false;
        }
        if (!headroom) return true;
        BlockPos above = bottom.above(height);
        return !level.getBlockState(above).isRedstoneConductor(level, above);
    }

    private static boolean replaceable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        // Lava would burn the chest and its contents.
        return state.canBeReplaced() && !state.getFluidState().is(FluidTags.LAVA) && !state.hasBlockEntity();
    }

    private static boolean hasBlockEntity(Level level, BlockPos bottom, int height) {
        for (int i = 0; i < height; i++) {
            if (level.getBlockState(bottom.above(i)).hasBlockEntity()) return true;
        }
        return false;
    }

    /** Wipes progress that is not an item: levels, effects, respawn point, hunger, fire. */
    public static void resetVitals(ServerPlayer player) {
        player.stopRiding();
        if (player.isSleeping()) player.stopSleeping();
        if (player.isFallFlying()) player.stopFallFlying();
        player.removeAllEffects();
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.totalExperience = 0;
        player.setHealth(player.getMaxHealth());
        CompoundTag food = new CompoundTag();
        food.putInt("foodLevel", 20);
        food.putFloat("foodSaturationLevel", 5.0F);
        food.putFloat("foodExhaustionLevel", 0.0F);
        food.putInt("foodTickTimer", 0);
        player.getFoodData().readAdditionalSaveData(food);
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        player.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
    }
}
