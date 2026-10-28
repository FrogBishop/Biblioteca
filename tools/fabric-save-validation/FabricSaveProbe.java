package babel.biblioteca.validation.save.fabric;

import babel.biblioteca.validation.save.SaveLifecycleChecks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;

/** Runs the same native save/reopen checks through Fabric's lifecycle events. */
public final class FabricSaveProbe implements ModInitializer {
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(SaveLifecycleChecks::started);
        ServerTickEvents.END_SERVER_TICK.register(SaveLifecycleChecks::tick);
        ServerLevelEvents.UNLOAD.register((server, level) -> SaveLifecycleChecks.unloaded(level));
    }
}
