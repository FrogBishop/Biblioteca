package babel.biblioteca;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Select the terrain hooks belonging to the installed renderer before either target is loaded. */
public final class RendererMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> VANILLA_TERRAIN = Set.of(
            "FixedTerrainYLoopMixin", "ClassicTerrainYLoopMixin", "GpuLayerTerrainYLoopMixin",
            "GpuTerrainYLoopMixin", "LevelRendererYLoopMixin", "LegacyTerrainVisibilityMixin",
            "SectionOcclusionGraphLoopMixin");

    public static boolean sodiumPresent() {
        ClassLoader loader = RendererMixinPlugin.class.getClassLoader();
        return loader.getResource("me/jellysquid/mods/sodium/client/render/SodiumWorldRenderer.class") != null
                || loader.getResource("net/caffeinemc/mods/sodium/client/render/SodiumWorldRenderer.class") != null;
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        String name = mixin.substring(mixin.lastIndexOf('.') + 1);
        if ((name.equals("LegacyClouds1211Mixin") || name.equals("CloudRendererLoopMixin"))
                && (getClass().getClassLoader().getResource("me/jellysquid/mods/sodium/client/render/immediate/CloudRenderer.class") != null
                || getClass().getClassLoader().getResource("net/caffeinemc/mods/sodium/client/render/immediate/CloudRenderer.class") != null)) return false;
        return !sodiumPresent() || !VANILLA_TERRAIN.contains(name);
    }
    @Override public void onLoad(String pkg) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> ours, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
