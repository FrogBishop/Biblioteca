package babel.biblioteca.validation.save;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NeoForge event adapter for the shared native save/reopen checks. */
@Mod("biblioteca_save_check")
public final class SaveLifecycleProbe {
    public SaveLifecycleProbe() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> SaveLifecycleChecks.started(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> SaveLifecycleChecks.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof net.minecraft.world.level.Level level) SaveLifecycleChecks.unloaded(level);
        });
    }
}
