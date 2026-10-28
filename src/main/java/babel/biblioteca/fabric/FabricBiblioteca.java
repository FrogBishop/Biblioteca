package babel.biblioteca.fabric;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.SeamStats;
import babel.biblioteca.network.LoopingStatePayload;
import net.fabricmc.api.ModInitializer;
//? if >=1.19 {
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
//?} else {
/*import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
*///?}
//? if >=1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//?}
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Fabric lifecycle and transport adapter for the shared gameplay code. */
public final class FabricBiblioteca implements ModInitializer {
    @Override
    public void onInitialize() {
        Biblioteca.initialize((player, payload) -> {
            //? if >=1.20.5 {
            ServerPlayNetworking.send(player, payload);
            //?} else {
            /*ServerPlayNetworking.send(player, LoopingStatePayload.ID, payload.toBuffer());
            *///?}
        });
        SeamStats.init();
        //? if >=26.1 {
        PayloadTypeRegistry.clientboundPlay().register(LoopingStatePayload.TYPE, LoopingStatePayload.STREAM_CODEC);
        //?} else if >=1.20.5 {
        /*PayloadTypeRegistry.playS2C().register(LoopingStatePayload.TYPE, LoopingStatePayload.STREAM_CODEC);
        *///?}
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            //? if >=1.17 {
            Biblioteca.sendState(handler.getPlayer(), Biblioteca.currentPayload());
            //?} else {
            /*Biblioteca.sendState(handler.player, Biblioteca.currentPayload());
            *///?}
        });
        //? if >=1.19 {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                Biblioteca.registerCommands(dispatcher));
        //?} else {
        /*CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                Biblioteca.registerCommands(dispatcher));
        *///?}
    }
}
