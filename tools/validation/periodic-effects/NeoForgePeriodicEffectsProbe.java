package biblioteca.periodicprobe;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(value = "biblioteca_periodic_probe", dist = Dist.CLIENT)
public final class NeoForgePeriodicEffectsProbe {
    public NeoForgePeriodicEffectsProbe() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> PeriodicEffectsChecks.clientTick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> PeriodicEffectsChecks.serverTick(e.getServer()));
    }
}
