package biblioteca.visibility;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "biblioteca_visibility_probe", dist = Dist.CLIENT)
public final class NeoForgeVisibilityProbe {
    public NeoForgeVisibilityProbe() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> VisibilityChecks.tick());
    }
}
