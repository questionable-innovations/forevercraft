package nz.questionable.forevercraft;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import nz.questionable.forevercraft.config.ForevercraftConfig;
import nz.questionable.forevercraft.net.ForevercraftNetwork;
import nz.questionable.forevercraft.world.ServerState;

@Mod(Forevercraft.MOD_ID)
public final class Forevercraft {
    public static final String MOD_ID = "forevercraft";

    public Forevercraft() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForevercraftConfig.SPEC);
        ForevercraftNetwork.register();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(Forevercraft::onConfigReload);
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
