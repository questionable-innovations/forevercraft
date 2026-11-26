package nz.questionable.forevercraft;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import nz.questionable.forevercraft.config.ForevercraftConfig;
import nz.questionable.forevercraft.net.ForevercraftNetwork;
import nz.questionable.forevercraft.world.ServerState;

@Mod(Forevercraft.MOD_ID)
public final class Forevercraft {
    public static final String MOD_ID = "forevercraft";

    public Forevercraft(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, ForevercraftConfig.SPEC);
        modBus.addListener(ForevercraftNetwork::register);
        modBus.addListener(Forevercraft::onConfigReload);
    }

    /** Push edited elytra/coordinate settings to connected clients without a restart. */
    private static void onConfigReload(ModConfigEvent.Reloading event) {
        if (!event.getConfig().getModId().equals(MOD_ID)) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> {
            if (ServerState.isLoaded()) ServerState.syncAll(server);
        });
    }
}
