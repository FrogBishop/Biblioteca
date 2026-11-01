package biblioteca.periodicprobe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class FabricPeriodicEffectsProbe implements ClientModInitializer {
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(PeriodicEffectsChecks::clientTick);
        ServerTickEvents.END_SERVER_TICK.register(PeriodicEffectsChecks::serverTick);
    }
}
