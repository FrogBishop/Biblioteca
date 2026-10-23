package babel.biblioteca.validation;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Minecraft's merged development JAR can expose methods stripped from the dedicated server. */
public final class ApiChecks {
    private record Reference(String owner, String name, String descriptor, boolean field) {}

    public static void run(Path classes) throws Exception {
        Map<Reference, Set<String>> uses = new HashMap<>();
        try (var files = Files.walk(classes.resolve("babel/biblioteca"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")
                    && !classes.relativize(path).toString().replace('\\', '/').startsWith("babel/biblioteca/client/")).toList()) {
                String caller = classes.relativize(file).toString();
                new ClassReader(Files.readAllBytes(file)).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            private void record(String owner, String member, String desc, boolean field) {
                                if (owner.startsWith("net/minecraft/")) uses.computeIfAbsent(new Reference(owner, member, desc, field), key -> new LinkedHashSet<>()).add(caller + ":" + name);
                            }
                            @Override public void visitMethodInsn(int opcode, String owner, String member, String desc, boolean isInterface) { record(owner, member, desc, false); }
                            @Override public void visitFieldInsn(int opcode, String owner, String member, String desc) { record(owner, member, desc, true); }
                            @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
                                for (Object argument : arguments) if (argument instanceof Handle handle)
                                    record(handle.getOwner(), handle.getName(), handle.getDesc(), handle.getTag() <= Opcodes.H_PUTSTATIC);
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        Set<String> missing = new LinkedHashSet<>();
        for (var entry : uses.entrySet()) {
            Reference ref = entry.getKey();
            try {
                Class<?> owner = Class.forName(ref.owner().replace('/', '.'), false, ApiChecks.class.getClassLoader());
                if (!present(owner, ref, new HashSet<>())) missing.add(ref + " from " + entry.getValue());
            } catch (ClassNotFoundException | LinkageError absent) {
                missing.add(ref + " from " + entry.getValue() + ": " + absent);
            }
        }
        if (!missing.isEmpty()) throw new AssertionError("Unavailable common Minecraft API references:\n" + String.join("\n", missing));
        System.out.println("DATA_OK common API linkage: " + uses.size() + " method/field references in the actual runtime environment");
    }

    private static boolean present(Class<?> owner, Reference ref, Set<Class<?>> visited) {
        if (owner == null || !visited.add(owner)) return false;
        if (ref.field()) {
            for (var field : owner.getDeclaredFields()) if (field.getName().equals(ref.name()) && Type.getDescriptor(field.getType()).equals(ref.descriptor())) return true;
        } else if (ref.name().equals("<init>")) {
            for (var constructor : owner.getDeclaredConstructors()) if (Type.getConstructorDescriptor(constructor).equals(ref.descriptor())) return true;
            return false;
        } else {
            for (var method : owner.getDeclaredMethods()) if (method.getName().equals(ref.name()) && Type.getMethodDescriptor(method).equals(ref.descriptor())) return true;
        }
        if (present(owner.getSuperclass(), ref, visited)) return true;
        for (Class<?> parent : owner.getInterfaces()) if (present(parent, ref, visited)) return true;
        return false;
    }
}
