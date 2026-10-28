package babel.biblioteca.neoforge;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.SeamStats;
import babel.biblioteca.network.LoopingStatePayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(Biblioteca.MOD_ID)
public final class NeoForgeBiblioteca {
    public NeoForgeBiblioteca(IEventBus modBus) {
        Biblioteca.initialize((player, payload) -> PacketDistributor.sendToPlayer(player, payload));
        modBus.addListener(NeoForgeBiblioteca::registerStats);
        modBus.addListener(NeoForgeBiblioteca::registerPayloads);
        NeoForge.EVENT_BUS.addListener(NeoForgeBiblioteca::registerCommands);
        NeoForge.EVENT_BUS.addListener(NeoForgeBiblioteca::playerJoined);
    }

    private static void registerStats(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.CUSTOM_STAT)) SeamStats.init();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // The client entrypoint supplies the handler without loading client classes on a server.
        event.registrar("1").playToClient(LoopingStatePayload.TYPE, LoopingStatePayload.STREAM_CODEC);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        Biblioteca.registerCommands(event.getDispatcher());
    }

    private static void playerJoined(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Biblioteca.sendState(player, Biblioteca.currentPayload());
        }
    }
}
