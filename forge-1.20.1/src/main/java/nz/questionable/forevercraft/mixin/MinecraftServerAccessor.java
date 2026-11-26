package nz.questionable.forevercraft.mixin;

import java.util.concurrent.Executor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;

/** What the ServerLevel constructor needs, for building a fresh End while the server runs. */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Accessor("executor")
    Executor forevercraft$executor();

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess forevercraft$storageSource();
}
