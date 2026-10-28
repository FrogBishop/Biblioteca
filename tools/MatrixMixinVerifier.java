import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.knot.Knot;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/** Loads every registered target through the real Fabric transformer, without starting the game. */
public class MatrixMixinVerifier {
    public static void main(String[] args) throws Exception {
        // Resolve ZipFS before Fabric's parallel remapper uses service loading from worker threads.
        java.nio.file.spi.FileSystemProvider.installedProviders();
        JsonObject config = new JsonParser().parse(Files.readString(Path.of(args[0]))).getAsJsonObject();
        EnvType environment = args[1].equals("server") ? EnvType.SERVER : EnvType.CLIENT;
        Path runDir = Path.of(args[2]);
        Files.createDirectories(runDir);

        JsonArray jvmArgs = config.getAsJsonArray(environment == EnvType.CLIENT ? "clientJvmArgs" : "serverJvmArgs");
        for (JsonElement element : jvmArgs) {
            String value = element.getAsString();
            if (!value.startsWith("-D")) continue;
            int equals = value.indexOf('=');
            if (equals >= 0) System.setProperty(value.substring(2, equals), value.substring(equals + 1));
        }
        String configPath = System.getProperty("fabric.dli.config");
        if (configPath == null) throw new IllegalStateException("Missing Loom development launch configuration");
        String group = "";
        for (String line : Files.readAllLines(Path.of(configPath))) {
            if (!line.startsWith("\t")) group = line.trim();
            else if (group.equals("commonProperties")
                    || group.equals(environment == EnvType.CLIENT ? "clientProperties" : "serverProperties")) {
                String value = line.trim();
                int equals = value.indexOf('=');
                if (equals >= 0) System.setProperty(value.substring(0, equals), value.substring(equals + 1));
            }
        }
        // Use the published Fabric API bundle, including the renderer's normal companion mixins.
        if (config.has("fabricApiOnClasspath") && config.get("fabricApiOnClasspath").getAsBoolean()) {
            System.clearProperty("fabric.addMods");
        } else {
            System.setProperty("fabric.addMods", config.get("fabricApi").getAsString());
        }
        System.setProperty("mixin.debug.countInjections", "true");
        // The old Log4j bundled with 1.16/1.17 does not understand Loom's newer GUI/arbiter appenders.
        Path logging = runDir.resolve("matrix-log4j2.xml");
        Files.writeString(logging, "<Configuration status=\"WARN\"><Appenders>"
                + "<Console name=\"Console\" target=\"SYSTEM_OUT\"><PatternLayout pattern=\"[%level] %msg%n\"/>"
                + "</Console></Appenders><Loggers><Root level=\"info\"><AppenderRef ref=\"Console\"/>"
                + "</Root></Loggers></Configuration>");
        System.setProperty("log4j.configurationFile", logging.toString());

        Path resources = Path.of(config.get("resources").getAsString());
        Path classes = Path.of(config.get("classes").getAsString());
        Set<String> targets = new TreeSet<>();
        readTargets(resources.resolve("biblioteca.mixins.json"), classes, "mixins", targets);
        if (environment == EnvType.CLIENT) readTargets(resources.resolve("biblioteca.client.mixins.json"), classes, "client", targets);

        ClassLoader loader = new Knot(environment).init(new String[] {
                "--gameDir", runDir.toString(), "--version", config.get("minecraft").getAsString()
        });
        if (config.has("fabricApiModules")) {
            int checked = 0;
            for (JsonElement value : config.getAsJsonArray("fabricApiModules")) {
                JsonObject module = value.getAsJsonObject();
                String side = module.get("environment").getAsString();
                if (side.equals(environment == EnvType.CLIENT ? "server" : "client")) continue;
                String id = module.get("id").getAsString();
                if (FabricLoader.getInstance().getModContainer(id).isEmpty()) throw new AssertionError("Missing Fabric API runtime module: " + id);
                checked++;
            }
            System.out.println("DATA_OK Fabric API runtime modules: " + checked);
        }
        String version = FabricLoader.getInstance().getModContainer("biblioteca").orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
        String expectedVersion = new JsonParser().parse(Files.readString(resources.resolve("fabric.mod.json")))
                .getAsJsonObject().get("version").getAsString();
        if (!version.equals(expectedVersion) || !version.endsWith("+" + config.get("minecraft").getAsString())) {
            throw new AssertionError("Loaded the wrong node: " + version);
        }
        java.util.List<String> failures = new java.util.ArrayList<>();
        for (String target : targets) {
            try {
                Class<?> type = Class.forName(target, false, loader);
                type.getDeclaredMethods();
                type.getDeclaredConstructors();
            } catch (Throwable failure) {
                try (var stream = loader.getResourceAsStream(target.replace('.', '/') + ".class")) {
                    if (stream != null) new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            System.err.println("TARGET_METHOD " + target + " " + name + descriptor);
                            return null;
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
                System.err.println("TARGET_FAILED " + target);
                failure.printStackTrace();
                failures.add(target);
                continue;
            }
            System.out.println("TARGET_OK " + target);
        }
        if (!failures.isEmpty()) throw new AssertionError("Failed transformed targets: " + failures);
        if (args.length > 3) {
            loader.loadClass("babel.biblioteca.validation.CoreChecks").getMethod("bootstrap").invoke(null);
            Class<?> entrypoint = loader.loadClass("babel.biblioteca.fabric.FabricBiblioteca");
            entrypoint.getMethod("onInitialize").invoke(entrypoint.getConstructor().newInstance());
            if (environment == EnvType.CLIENT) {
                Class<?> clientEntrypoint = loader.loadClass("babel.biblioteca.client.fabric.FabricBibliotecaClient");
                clientEntrypoint.getMethod("onInitializeClient").invoke(clientEntrypoint.getConstructor().newInstance());
            }
            loader.loadClass("babel.biblioteca.validation.ApiChecks").getMethod("run", Path.class).invoke(null, classes);
            loader.loadClass("babel.biblioteca.validation.CoreChecks").getMethod("run", boolean.class)
                    .invoke(null, environment == EnvType.CLIENT);
            loader.loadClass("babel.biblioteca.validation.SeamChecks").getMethod("run", boolean.class)
                    .invoke(null, environment == EnvType.CLIENT);
            loader.loadClass("babel.biblioteca.validation.SkyChecks").getMethod("run").invoke(null);
            loader.loadClass("babel.biblioteca.validation.CommandChecks").getMethod("run").invoke(null);
            if (config.get("minecraft").getAsString().equals("26.3")) {
                String checksPackage = "babel.biblioteca.validation.main100.";
                loader.loadClass(checksPackage + "SeamChecks").getMethod("run", boolean.class).invoke(null, environment == EnvType.CLIENT);
                for (String check : java.util.List.of("MobTargetingChecks", "DimensionCommandChecks", "SkyLightSettingsChecks", "SkyLightChecks")) {
                    loader.loadClass(checksPackage + check).getMethod("run").invoke(null);
                }
                if (environment == EnvType.CLIENT) for (String check : java.util.List.of("SeamClientChecks", "SkyLightClientChecks", "DimensionConfigChecks")) {
                    loader.loadClass(checksPackage + check).getMethod("run").invoke(null);
                }
            }
            if (environment == EnvType.CLIENT) {
                loader.loadClass("babel.biblioteca.validation.DimensionModelChecks").getMethod("run").invoke(null);
                loader.loadClass("babel.biblioteca.validation.TerrainChecks").getMethod("run").invoke(null);
                if (config.get("minecraft").getAsString().equals("26.3")) {
                    loader.loadClass("babel.biblioteca.validation.RenderingChecks").getMethod("run").invoke(null);
                }
            }
        }
        System.out.println("MATRIX_OK " + config.get("minecraft").getAsString() + " "
                + environment + " " + targets.size() + " strict targets");
    }

    private static void readTargets(Path file, Path classes, String key, Set<String> targets) throws Exception {
        JsonObject config = new JsonParser().parse(Files.readString(file)).getAsJsonObject();
        if (config.getAsJsonObject("injectors").get("defaultRequire").getAsInt() != 1) {
            throw new AssertionError("Injection checking is not strict: " + file);
        }
        String pkg = config.get("package").getAsString().replace('.', '/');
        for (JsonElement value : config.getAsJsonArray(key)) {
            Path source = classes.resolve(pkg + "/" + value.getAsString() + ".class");
            ClassReader reader = new ClassReader(Files.readAllBytes(source));
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return null;
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitArray(String name) {
                            if (!name.equals("value") && !name.equals("targets")) return null;
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String key, Object value) {
                                    targets.add(value instanceof Type ? ((Type) value).getClassName() : value.toString().replace('/', '.'));
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }
}
