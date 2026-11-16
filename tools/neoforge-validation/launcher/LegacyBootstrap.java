package babel.biblioteca.validation.neoforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import org.objectweb.asm.*;

/** Executes inside the transformed mod module after the real legacy FML launch target starts. */
public final class LegacyBootstrap {
    public static void run() {
        try {
            JsonObject config = JsonParser.parseString(Files.readString(Path.of(System.getProperty("biblioteca.validation.config")))).getAsJsonObject();
            boolean client = config.get("side").getAsString().equals("client");
            boolean noRender = config.get("variant").getAsString().equals("norender");
            Path classes = Path.of(config.get("artifactClasses").getAsString());
            ClassLoader loader = LegacyBootstrap.class.getClassLoader();
            boolean transformsOnly = config.has("transformsOnly") && config.get("transformsOnly").getAsBoolean();
            if (config.has("requiredMods")) {
                Object loading = Class.forName("net.neoforged.fml.loading.FMLLoader").getMethod("getLoadingModList").invoke(null);
                var mods = (java.util.List<?>) loading.getClass().getMethod("getMods").invoke(loading);
                for (var wanted : config.getAsJsonArray("requiredMods")) {
                    Object found = null;
                    for (Object mod : mods) if (wanted.getAsString().equals(mod.getClass().getMethod("getModId").invoke(mod))) found = mod;
                    if (found == null) throw new AssertionError("Missing required mod " + wanted);
                    System.out.println("EXTRA_MOD_OK " + wanted.getAsString() + " " + found.getClass().getMethod("getVersion").invoke(found));
                }
            }
            if (!transformsOnly) {
            net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
            // Older NeoForge builds populate the client's creative search registry during setup.
            // Keep this non-graphical native fixture alive until the validation process exits.
            if (client) Class.forName("babel.biblioteca.validation.neoforge.NeoForgeClientChecks").getMethod("installMinecraftFixture").invoke(null);
            // FML's own JUnit target uses the same native server bootstrap to load registries,
            // mod constructors and setup events without a graphics device. Dist stays unchanged.
            net.neoforged.neoforge.server.loading.ServerModLoader.load();
            if (client && config.get("network").getAsString().equals("client-handler")) {
                Class.forName("net.neoforged.neoforge.client.network.registration.ClientNetworkRegistry", true, loader)
                        .getMethod("setup").invoke(null);
            }
            }
            Set<String> targets = new TreeSet<>();
            targets(classes, "biblioteca.mixins.json", "mixins", targets);
            if (client) targets(classes, "biblioteca.client.mixins.json", "client", targets);
            if (client && Files.isRegularFile(classes.resolve("biblioteca.sodium-compat.mixins.json"))) {
                Set<String> optional = new TreeSet<>();
                targets(classes, "biblioteca.sodium-compat.mixins.json", "client", optional);
                for (String target : optional) if (loader.getResource(target.replace('.', '/') + ".class") != null) targets.add(target);
            }
            var failed = new ArrayList<String>();
            for (String target : targets) {
                try {
                    Class<?> type = Class.forName(target, false, loader);
                    type.getDeclaredMethods(); type.getDeclaredConstructors();
                    if ((!config.has("sodiumOnly") || !config.get("sodiumOnly").getAsBoolean())
                            && (target.endsWith(".DefaultChunkRenderer") || target.endsWith(".RegionChunkRenderer"))
                            && java.util.Arrays.stream(type.getDeclaredMethods()).noneMatch(method -> method.getName().contains("biblioteca$images")))
                        throw new AssertionError("Sodium draw adapter was not selected: " + target);
                    System.out.println("TARGET_OK " + target);
                } catch (Throwable error) {
                    failed.add(target); System.out.println("TARGET_FAILED " + target); error.printStackTrace();
                }
            }
            if (!failed.isEmpty()) throw new AssertionError("Failed transformed targets: " + failed);
            if (!transformsOnly) Class.forName("babel.biblioteca.validation.neoforge.NeoForgeChecks")
                    .getMethod("run", Path.class, boolean.class, boolean.class).invoke(null, classes, client, noRender);
            System.out.println("NEOFORGE_OK " + config.get("minecraft").getAsString() + " " + config.get("neoforge").getAsString()
                    + " " + (client ? "client" : "server") + " " + (noRender ? "norender" : "full") + " " + targets.size() + " strict targets");
            System.exit(0);
        } catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }

    private static void targets(Path classes, String file, String key, Set<String> targets) throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(classes.resolve(file))).getAsJsonObject();
        if (!config.get("required").getAsBoolean() || config.getAsJsonObject("injectors").get("defaultRequire").getAsInt() != 1)
            throw new AssertionError("Non-strict mixin config: " + file);
        String pkg = config.get("package").getAsString().replace('.', '/');
        for (var value : config.getAsJsonArray(key)) {
            new ClassReader(Files.readAllBytes(classes.resolve(pkg + "/" + value.getAsString() + ".class")))
                    .accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                            if (!desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return null;
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public AnnotationVisitor visitArray(String name) {
                                    if (!name.equals("value") && !name.equals("targets")) return null;
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String key, Object value) {
                                            targets.add(value instanceof Type type ? type.getClassName() : value.toString().replace('/', '.'));
                                        }
                                    };
                                }
                            };
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }
}
