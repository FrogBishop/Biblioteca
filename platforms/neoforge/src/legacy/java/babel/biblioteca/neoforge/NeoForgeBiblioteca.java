package babel.biblioteca.neoforge;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.SeamStats;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Entrypoint for loaders predating separate client payload registration. */
@Mod(Biblioteca.MOD_ID)
public final class NeoForgeBiblioteca {
    public NeoForgeBiblioteca(IEventBus modBus) {
        NeoForgeTransport.initialize(modBus);
        Biblioteca.initialize(NeoForgeTransport::send);
        modBus.addListener(NeoForgeBiblioteca::registerStats);
        NeoForge.EVENT_BUS.addListener(NeoForgeBiblioteca::registerCommands);
        NeoForge.EVENT_BUS.addListener(NeoForgeBiblioteca::playerJoined);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            babel.biblioteca.client.neoforge.NeoForgeBibliotecaClient.initialize();
        }
    }

    private static void registerStats(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.CUSTOM_STAT)) SeamStats.init();
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        Biblioteca.registerCommands(event.getDispatcher());
    }

    private static void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Biblioteca.sendState(player, Biblioteca.currentPayload());
    }
}
