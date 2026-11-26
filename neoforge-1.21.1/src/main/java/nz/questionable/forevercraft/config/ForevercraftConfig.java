package nz.questionable.forevercraft.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/** COMMON config so values are available at command registration and before worlds load. */
public final class ForevercraftConfig {
    public enum EnderChestMode { STASH, CLEAR, KEEP }

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ONE_TO_ONE_NETHER;

    public static final ModConfigSpec.IntValue RESET_PERMISSION_LEVEL;
    public static final ModConfigSpec.IntValue MIN_SPAWN_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_SPAWN_DISTANCE;
    public static final ModConfigSpec.IntValue PREFERRED_MAX_RADIUS;
    public static final ModConfigSpec.EnumValue<EnderChestMode> ENDER_CHEST_MODE;
    public static final ModConfigSpec.BooleanValue RESET_END;
    public static final ModConfigSpec.BooleanValue REGENERATE_END_LIVE;
    public static final ModConfigSpec.BooleanValue BLOCK_END_UNTIL_RESTART;

    public static final ModConfigSpec.BooleanValue HIDE_COORDINATES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> OFFSET_DIMENSIONS;

    public static final ModConfigSpec.BooleanValue DISABLE_ELYTRA;

    public static final ModConfigSpec.IntValue PREVIOUS_BASES_PERMISSION_LEVEL;
    public static final ModConfigSpec.BooleanValue PREVIOUS_BASES_SHOW_OWN_STASHES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

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
        RESET_END = b.comment("Wipe the End (new seed, new dragon) on a reset.")
                .define("resetEnd", true);
        REGENERATE_END_LIVE = b.comment(
                        "Swap in the new End as soon as nobody is left in the old one, instead of at the next restart.",
                        "Turn off if another mod misbehaves when the End is replaced while the server runs.")
                .define("regenerateEndLive", true);
        BLOCK_END_UNTIL_RESTART = b.comment(
                        "When the End waits for a restart (regenerateEndLive off, or the live swap failed),",
                        "block travel to it until then.")
                .define("blockEndUntilRestart", true);
        b.pop();

        b.push("coordinates");
        HIDE_COORDINATES = b.comment("Show coordinates relative to the current era's spawn (F3 reads ~0,0 at spawn).")
                .define("hideTrueCoordinates", true);
        OFFSET_DIMENSIONS = b.comment("Dimensions whose displayed coordinates are shifted by the era offset.")
                .defineListAllowEmpty("dimensions", List.of("minecraft:overworld", "minecraft:the_nether"),
                        () -> "", o -> o instanceof String);
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
