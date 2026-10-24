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
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

/** Loads every registered target and the behavior checks through Fabric's real Mixin transformer. */
public final class SeamMixinVerifier {
    public static void main(String[] args) throws Exception {
        java.nio.file.spi.FileSystemProvider.installedProviders();
        JsonObject config = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        boolean client = args[1].equals("client");
        Path runDir = Path.of(args[2]);
        Files.createDirectories(runDir);
        for (JsonElement value : config.getAsJsonArray(client ? "clientJvmArgs" : "serverJvmArgs")) {
            String arg = value.getAsString();
            if (arg.startsWith("-D") && arg.contains("=")) {
                int equals = arg.indexOf('=');
                System.setProperty(arg.substring(2, equals), arg.substring(equals + 1));
            }
        }
        String group = "";
        for (String line : Files.readAllLines(Path.of(System.getProperty("fabric.dli.config")))) {
            if (!line.startsWith("\t")) group = line.trim();
            else if (group.equals("commonProperties") || group.equals(client ? "clientProperties" : "serverProperties")) {
                String value = line.trim();
                int equals = value.indexOf('=');
                if (equals >= 0) System.setProperty(value.substring(0, equals), value.substring(equals + 1));
            }
        }
        System.setProperty("mixin.debug.countInjections", "true");
        Path logging = runDir.resolve("checks-log4j2.xml");
        Files.writeString(logging, "<Configuration status=\"WARN\"><Appenders><Console name=\"Console\">"
                + "<PatternLayout pattern=\"[%level] %msg%n\"/></Console></Appenders><Loggers><Root level=\"info\">"
                + "<AppenderRef ref=\"Console\"/></Root></Loggers></Configuration>");
        System.setProperty("log4j.configurationFile", logging.toString());
        Set<String> targets = new TreeSet<>();
        readTargets(config, "resources", "classes", "biblioteca.mixins.json", "mixins", targets);
        if (client) readTargets(config, "clientResources", "clientClasses", "biblioteca.client.mixins.json", "client", targets);
        ClassLoader loader = new Knot(client ? EnvType.CLIENT : EnvType.SERVER).init(new String[]{
                "--gameDir", runDir.toString(), "--version", config.get("minecraft").getAsString()
        });
        String expected = JsonParser.parseString(Files.readString(Path.of(config.get("resources").getAsString(), "fabric.mod.json")))
                .getAsJsonObject().get("version").getAsString();
        String actual = FabricLoader.getInstance().getModContainer("biblioteca").orElseThrow().getMetadata().getVersion().getFriendlyString();
        if (!actual.equals(expected)) throw new AssertionError("Wrong mod artifact: " + actual);
        var failures = new ArrayList<String>();
        for (String target : targets) {
            try {
                Class<?> type = Class.forName(target, false, loader);
                type.getDeclaredMethods();
                type.getDeclaredConstructors();
                System.out.println("TARGET_OK " + target);
            } catch (Throwable failure) {
                failure.printStackTrace();
                failures.add(target);
            }
        }
        if (!failures.isEmpty()) throw new AssertionError("Mixin targets failed: " + failures);
        loader.loadClass("babel.biblioteca.validation.SeamChecks").getMethod("run", boolean.class).invoke(null, client);
        loader.loadClass("babel.biblioteca.validation.MobTargetingChecks").getMethod("run").invoke(null);
        loader.loadClass("babel.biblioteca.validation.DimensionCommandChecks").getMethod("run").invoke(null);
        loader.loadClass("babel.biblioteca.validation.SkyLightSettingsChecks").getMethod("run").invoke(null);
        loader.loadClass("babel.biblioteca.validation.SkyLightChecks").getMethod("run").invoke(null);
        if (client) {
            loader.loadClass("babel.biblioteca.validation.SeamClientChecks").getMethod("run").invoke(null);
            loader.loadClass("babel.biblioteca.validation.SkyLightClientChecks").getMethod("run").invoke(null);
            loader.loadClass("babel.biblioteca.validation.DimensionConfigChecks").getMethod("run").invoke(null);
        }
        System.out.println("SEAM_OK " + args[1] + " " + targets.size() + " strict targets");
    }

    private static void readTargets(JsonObject paths, String resourcesKey, String classesKey, String file, String key, Set<String> targets) throws Exception {
        JsonObject config = JsonParser.parseString(Files.readString(Path.of(paths.get(resourcesKey).getAsString(), file))).getAsJsonObject();
        String packageName = config.get("package").getAsString().replace('.', '/');
        for (JsonElement name : config.getAsJsonArray(key)) {
            Path bytecode = Path.of(paths.get(classesKey).getAsString(), packageName, name.getAsString() + ".class");
            new ClassReader(Files.readAllBytes(bytecode)).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return null;
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitArray(String key) {
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String ignored, Object value) {
                                    if (value instanceof Type type) targets.add(type.getClassName());
                                    else if (key.equals("targets")) targets.add(value.toString());
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }
}
