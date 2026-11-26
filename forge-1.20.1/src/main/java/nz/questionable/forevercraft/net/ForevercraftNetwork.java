package nz.questionable.forevercraft.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import nz.questionable.forevercraft.Forevercraft;

public final class ForevercraftNetwork {
    private static final String PROTOCOL = "1";

    // Exact version match on both sides: a client without the mod cannot hide coordinates, so it may not join.
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Forevercraft.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ForevercraftNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(ServerSettingsPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerSettingsPacket::encode)
                .decoder(ServerSettingsPacket::decode)
                .consumerMainThread((msg, ctx) -> msg.handle(ctx))
                .add();
    }

    public static void send(ServerPlayer player, ServerSettingsPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
