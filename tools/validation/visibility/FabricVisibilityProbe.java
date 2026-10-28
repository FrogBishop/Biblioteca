package biblioteca.visibility;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class FabricVisibilityProbe implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> VisibilityChecks.tick());
    }
}
