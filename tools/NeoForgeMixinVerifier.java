import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.startup.JUnitGameBootstrapper;
import net.neoforged.fml.startup.StartupArgs;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;

/** Boots the actual FML transformer and mod lifecycle without a GPU, audio device or game window. */
public final class NeoForgeMixinVerifier {
    public static void main(String[] args) {
        try {
            verify(Path.of(args[0]));
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private static void verify(Path configPath) throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
        boolean client = config.get("side").getAsString().equals("client");
        boolean noRender = config.get("variant").getAsString().equals("norender");
        Path classes = Path.of(config.get("artifactClasses").getAsString());
        Path gameDir = Path.of(config.get("gameDir").getAsString());
        Files.createDirectories(gameDir);
        Set<String> targets = new TreeSet<>();
        readTargets(classes, "biblioteca.mixins.json", "mixins", targets);
        if (client) readTargets(classes, "biblioteca.client.mixins.json", "client", targets);

        var startup = new StartupArgs(gameDir, true, client ? Dist.CLIENT : Dist.DEDICATED_SERVER,
                true, new String[0], Set.of(), List.of(), Thread.currentThread().getContextClassLoader());
        try (var fml = FMLLoader.create(startup)) {
            if (fml.getLoadingModList().hasErrors()) throw new AssertionError(fml.getLoadingModList().getModLoadingIssues());
            ClassLoader loader = fml.getCurrentClassLoader();
            int bootstrappers = 0;
            for (var bootstrapper : ServiceLoader.load(JUnitGameBootstrapper.class, loader)) {
                bootstrapper.bootstrap(fml);
                bootstrappers++;
            }
            if (bootstrappers != 1) throw new AssertionError("Expected the NeoForge game bootstrapper, got " + bootstrappers);
            if (client) loader.loadClass("net.neoforged.neoforge.client.network.registration.ClientNetworkRegistry")
                    .getMethod("setup").invoke(null);

            List<String> failed = new ArrayList<>();
            for (String target : targets) {
                try {
                    Class<?> type = Class.forName(target, false, loader);
                    type.getDeclaredMethods();
                    type.getDeclaredConstructors();
                    System.out.println("TARGET_OK " + target);
                } catch (Throwable failure) {
                    System.err.println("TARGET_FAILED " + target);
                    failure.printStackTrace();
                    failed.add(target);
                }
            }
            if (!failed.isEmpty()) throw new AssertionError("Failed transformed targets: " + failed);
            loader.loadClass("babel.biblioteca.validation.neoforge.NeoForgeChecks")
                    .getMethod("run", Path.class, boolean.class, boolean.class)
                    .invoke(null, classes, client, noRender);
            System.out.println("NEOFORGE_OK " + config.get("minecraft").getAsString() + " " + config.get("neoforge").getAsString() + " "
                    + (client ? "client" : "server") + " " + (noRender ? "norender" : "full")
                    + " " + targets.size() + " strict targets");
        }
    }

    private static void readTargets(Path classes, String file, String key, Set<String> targets) throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(classes.resolve(file))).getAsJsonObject();
        if (!config.get("required").getAsBoolean() || config.getAsJsonObject("injectors").get("defaultRequire").getAsInt() != 1) {
            throw new AssertionError("Mixin checking is not strict: " + file);
        }
        String pkg = config.get("package").getAsString().replace('.', '/');
        for (var value : config.getAsJsonArray(key)) {
            new ClassReader(Files.readAllBytes(classes.resolve(pkg + "/" + value.getAsString() + ".class")))
                    .accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (!descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return null;
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
