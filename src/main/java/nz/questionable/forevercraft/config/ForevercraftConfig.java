package nz.questionable.forevercraft.config;

import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;

/** COMMON config so values are available at command registration and before worlds load. */
public final class ForevercraftConfig {
    public enum EnderChestMode { STASH, CLEAR, KEEP }

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ONE_TO_ONE_NETHER;

    public static final ForgeConfigSpec.IntValue RESET_PERMISSION_LEVEL;
    public static final ForgeConfigSpec.IntValue MIN_SPAWN_DISTANCE;
    public static final ForgeConfigSpec.IntValue MAX_SPAWN_DISTANCE;
    public static final ForgeConfigSpec.IntValue PREFERRED_MAX_RADIUS;
    public static final ForgeConfigSpec.EnumValue<EnderChestMode> ENDER_CHEST_MODE;
    public static final ForgeConfigSpec.BooleanValue RESET_END;
    public static final ForgeConfigSpec.BooleanValue BLOCK_END_UNTIL_RESTART;

    public static final ForgeConfigSpec.BooleanValue HIDE_COORDINATES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> OFFSET_DIMENSIONS;

    public static final ForgeConfigSpec.BooleanValue DISABLE_ELYTRA;

    public static final ForgeConfigSpec.IntValue PREVIOUS_BASES_PERMISSION_LEVEL;
    public static final ForgeConfigSpec.BooleanValue PREVIOUS_BASES_SHOW_OWN_STASHES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("nether");
        ONE_TO_ONE_NETHER = b.comment("Nether portals map coordinates 1:1 instead of 1:8.")
                .define("oneToOne", true);
        b.pop();

        b.push("reset");
        RESET_PERMISSION_LEVEL = b.comment("Permission level needed for /resetworld (4 = server owner ops).")
                .defineInRange("permissionLevel", 4, 0, 4);
        MIN_SPAWN_DISTANCE = b.comment("A new spawn is at least this many blocks from every previous spawn.")
                .defineInRange("minSpawnDistance", 6000, 256, 1_000_000);
        MAX_SPAWN_DISTANCE = b.comment("A new spawn is at most this many blocks from the previous spawn.")
                .defineInRange("maxSpawnDistance", 10000, 256, 1_000_000);
        PREFERRED_MAX_RADIUS = b.comment(
                        "Prefer new spawns within this distance of the true world origin, so strongholds",
                        "(which only generate within ~24k blocks of 0,0) stay reachable. Ignored if no candidate fits.")
                .defineInRange("preferredMaxRadius", 20000, 0, 29_000_000);
        ENDER_CHEST_MODE = b.comment(
                        "What happens to ender chest contents on reset:",
                        "STASH = moved into the stash chests, CLEAR = deleted, KEEP = left alone.")
                .defineEnum("enderChest", EnderChestMode.STASH);
        RESET_END = b.comment("Wipe the End (new seed, new dragon) on the next server start after a reset.")
                .define("resetEnd", true);
        BLOCK_END_UNTIL_RESTART = b.comment("Block travel to the End between a reset and the restart that regenerates it.")
                .define("blockEndUntilRestart", true);
        b.pop();

        b.push("coordinates");
        HIDE_COORDINATES = b.comment("Show coordinates relative to the current era's spawn (F3 reads ~0,0 at spawn).")
                .define("hideTrueCoordinates", true);
        OFFSET_DIMENSIONS = b.comment("Dimensions whose displayed coordinates are shifted by the era offset.")
                .defineListAllowEmpty("dimensions", List.of("minecraft:overworld", "minecraft:the_nether"),
                        o -> o instanceof String);
        b.pop();

        b.push("elytra");
        DISABLE_ELYTRA = b.comment("Prevent elytra flight.")
                .define("disabled", true);
        b.pop();

        b.push("previousBases");
        PREVIOUS_BASES_PERMISSION_LEVEL = b.comment(
                        "Permission level needed for /previousbases. Ops-only by default so players have to find",
                        "old remains themselves; 0 lets everyone use it.")
                .defineInRange("permissionLevel", 2, 0, 4);
        PREVIOUS_BASES_SHOW_OWN_STASHES = b.comment(
                        "Also list the caller's own stash chests. Ops (level 2+) always see them.")
                .define("showOwnStashes", false);
        b.pop();

        SPEC = b.build();
    }

    private ForevercraftConfig() {}
}
