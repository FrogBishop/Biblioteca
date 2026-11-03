package babel.biblioteca.client.sodiumcompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Choose optional adapters from the installed Sodium ABI, including backported releases. */
public final class SodiumCompatPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(target.replace('.', '/') + ".class")) {
            if (input == null) return false;
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            String name = mixin.substring(mixin.lastIndexOf('.') + 1);
            return switch (name) {
                case "LegacySectionManagerMixin" -> has(node, "iterateChunks", 4);
                case "CullerMixin" -> has(node, "findVisible", 5);
                case "OutOfGraphMixin" -> has(node, "isOutOfGraph", 1) && !has(node, "prepareRenderTrees", 3);
                case "AsyncSectionManagerMixin" -> has(node, "prepareRenderTrees", 3);
                case "SnapshotInitMixin" -> has(node, "init", 2);
                case "LegacyBiomeSnapshotMixin" -> has(node, "getBiomeForNoiseGen", 3) && has(node, "init", 2);
                case "SnapshotCloneMixin" -> has(node, "clone", 3);
                case "CacheInvalidationMixin" -> has(node, "invalidate", 3);
                case "PackedCacheInvalidationMixin" -> has(node, "invalidate", 1);
                case "SnapshotPositionMixin" -> node.fields.stream().anyMatch(f -> f.name.equals("pos"));
                case "RendererDraw5Mixin" -> has(node, "render", 5);
                case "RendererDraw6Mixin" -> has(node, "render", 6);
                case "RendererDraw7Mixin" -> has(node, "render", 7);
                case "RendererDraw8Mixin" -> has(node, "render", 8);
                case "RendererDraw9Mixin" -> has(node, "render", 9);
                case "RendererDraw10Mixin" -> has(node, "render", 10);
                case "RendererDraw11Mixin" -> has(node, "render", 11);
                case "FacesMixin" -> has(node, "getVisibleFaces", 6);
                case "CloudDraw7Mixin" -> has(node, "render", 7);
                case "CloudDraw6Mixin" -> has(node, "render", 6);
                case "CloudDraw9Mixin" -> has(node, "render", 9);
                case "CloudDraw10Mixin" -> has(node, "render", 10);
                case "CloudOrientationMixin" -> has(node, "getOrientation", 3);
                case "WorldRendererVisibilityMixin" -> node.methods.stream().anyMatch(m -> m.name.equals("isEntityVisible"));
                default -> throw new IllegalStateException("Unclassified Sodium adapter: " + name);
            };
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot inspect Sodium target " + target, failure);
        }
    }
    private static boolean has(ClassNode node, String name, int arguments) {
        for (MethodNode method : node.methods) if (method.name.equals(name) && Type.getArgumentTypes(method.desc).length == arguments) return true;
        return false;
    }
    @Override public void onLoad(String pkg) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> ours, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
