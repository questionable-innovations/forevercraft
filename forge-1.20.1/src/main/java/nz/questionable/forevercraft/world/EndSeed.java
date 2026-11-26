package nz.questionable.forevercraft.world;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** The End's seed override for the running server. Set before levels are created, cleared on stop. */
public final class EndSeed {
    @Nullable private static volatile Long override;

    private EndSeed() {}

    public static void set(@Nullable Long seed) {
        override = seed;
    }

    @Nullable
    public static Long forLevel(ResourceKey<Level> dimension) {
        return dimension == Level.END ? override : null;
    }
}
