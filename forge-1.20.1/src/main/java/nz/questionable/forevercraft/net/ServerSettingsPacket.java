package nz.questionable.forevercraft.net;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/** Server → client: the coordinate offset to display and server-enforced toggles. */
public record ServerSettingsPacket(int offsetX, int offsetZ, Set<ResourceLocation> offsetDimensions,
                                   boolean elytraDisabled) {
    public static final ServerSettingsPacket NONE = new ServerSettingsPacket(0, 0, Set.of(), false);

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(offsetX);
        buf.writeVarInt(offsetZ);
        buf.writeCollection(offsetDimensions, FriendlyByteBuf::writeResourceLocation);
        buf.writeBoolean(elytraDisabled);
    }

    public static ServerSettingsPacket decode(FriendlyByteBuf buf) {
        int x = buf.readVarInt();
        int z = buf.readVarInt();
        Set<ResourceLocation> dims = buf.readCollection(HashSet::new, FriendlyByteBuf::readResourceLocation);
        boolean elytra = buf.readBoolean();
        return new ServerSettingsPacket(x, z, Set.copyOf(dims), elytra);
    }

    /** Runs on the main thread (consumerMainThread). Stores plain data only; no client classes. */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        SyncedSettings.setClient(this);
    }
}
