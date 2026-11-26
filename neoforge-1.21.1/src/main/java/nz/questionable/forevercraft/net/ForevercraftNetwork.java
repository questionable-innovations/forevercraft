package nz.questionable.forevercraft.net;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ForevercraftNetwork {
    private static final String PROTOCOL = "1";

    private ForevercraftNetwork() {}

    /**
     * Payloads are required by default: a client without the mod cannot hide coordinates, so it may not join.
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToClient(ServerSettingsPacket.TYPE, ServerSettingsPacket.STREAM_CODEC,
                (packet, ctx) -> SyncedSettings.setClient(packet));
    }

    public static void send(ServerPlayer player, ServerSettingsPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
