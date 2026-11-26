package nz.questionable.forevercraft.net;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import nz.questionable.forevercraft.Forevercraft;

/** Server → client: the coordinate offset to display and server-enforced toggles. */
public record ServerSettingsPacket(int offsetX, int offsetZ, Set<ResourceLocation> offsetDimensions,
                                   boolean elytraDisabled) implements CustomPacketPayload {
    public static final ServerSettingsPacket NONE = new ServerSettingsPacket(0, 0, Set.of(), false);

    public static final Type<ServerSettingsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Forevercraft.MOD_ID, "settings"));

    public static final StreamCodec<FriendlyByteBuf, ServerSettingsPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.encode(buf), ServerSettingsPacket::decode);

    private void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(offsetX);
        buf.writeVarInt(offsetZ);
        buf.writeCollection(offsetDimensions, FriendlyByteBuf::writeResourceLocation);
        buf.writeBoolean(elytraDisabled);
    }

    private static ServerSettingsPacket decode(FriendlyByteBuf buf) {
        int x = buf.readVarInt();
        int z = buf.readVarInt();
        Set<ResourceLocation> dims = buf.readCollection(HashSet::new, FriendlyByteBuf::readResourceLocation);
        boolean elytra = buf.readBoolean();
        return new ServerSettingsPacket(x, z, Set.copyOf(dims), elytra);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
