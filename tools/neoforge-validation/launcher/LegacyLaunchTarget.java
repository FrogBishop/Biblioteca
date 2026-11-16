package biblioteca.validation.launcher;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.targets.NeoForgeUserdevLaunchHandler;

/** A test launch target on top of FML's native userdev discovery and transformation pipeline. */
public final class LegacyLaunchTarget extends NeoForgeUserdevLaunchHandler {
    @Override public Dist getDist() {
        return Boolean.getBoolean("biblioteca.validation.client") ? Dist.CLIENT : Dist.DEDICATED_SERVER;
    }
    @Override public String name() { return "bibliotecavalidation"; }
    @Override protected void runService(String[] arguments, ModuleLayer gameLayer) throws Throwable {
        for (Module module : gameLayer.modules()) {
            Class<?> bootstrap = Class.forName(module, "babel.biblioteca.validation.neoforge.LegacyBootstrap");
            if (bootstrap != null) { bootstrap.getMethod("run").invoke(null); return; }
        }
        throw new AssertionError("Validation classes were not discovered by FML: " + gameLayer.modules());
    }
}
