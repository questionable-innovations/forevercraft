package nz.questionable.forevercraft.net;

import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import nz.questionable.forevercraft.config.ForevercraftConfig;

/** Server-authoritative settings as last received by this client. Reset on disconnect. */
public final class SyncedSettings {
    private static volatile ServerSettingsPacket client = ServerSettingsPacket.NONE;

    private SyncedSettings() {}

    public static void setClient(ServerSettingsPacket packet) {
        client = packet;
    }

    public static void clearClient() {
        client = ServerSettingsPacket.NONE;
    }

    public static ServerSettingsPacket client() {
        return client;
    }

    /** Client side uses the server's value; server side reads its own config. */
    public static boolean elytraDisabled(Level level) {
        return level.isClientSide ? client.elytraDisabled() : ForevercraftConfig.DISABLE_ELYTRA.get();
    }

    public static boolean offsetAppliesTo(ResourceLocation dimension) {
        ServerSettingsPacket s = client;
        Set<ResourceLocation> dims = s.offsetDimensions();
        return (s.offsetX() != 0 || s.offsetZ() != 0) && dims.contains(dimension);
    }
}
