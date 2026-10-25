package babel.biblioteca.validation;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.Compat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Set;

/** Exercises the actual command branches and vanilla dimension suggestions in every release. */
public final class CommandChecks implements Opcodes {
    private static Set<ResourceKey<Level>> available = Set.of();
    public static Set<ResourceKey<Level>> dimensions() { return available; }
    @SuppressWarnings("unchecked")
    public static void run() throws Exception {
        var loop = Biblioteca.class.getDeclaredMethod("loopingCommand"); loop.setAccessible(true);
        var sky = Biblioteca.class.getDeclaredMethod("skyLightCommand"); sky.setAccessible(true);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(Commands.literal("biblioteca")
                .then((LiteralArgumentBuilder<CommandSourceStack>)loop.invoke(null))
                .then((LiteralArgumentBuilder<CommandSourceStack>)sky.invoke(null)));
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(V17, ACC_PUBLIC, "babel/biblioteca/validation/DimensionCommandSource", null, Type.getInternalName(CommandSourceStack.class), null);
        var method = CommandSourceStack.class.getMethod("levels");
        var mv = w.visitMethod(ACC_PUBLIC, method.getName(), Type.getMethodDescriptor(method), null, null); mv.visitCode();
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(CommandChecks.class), "dimensions", "()Ljava/util/Set;", false);
        mv.visitInsn(ARETURN); mv.visitMaxs(0,0); mv.visitEnd(); w.visitEnd();
        var source = (CommandSourceStack)SeamFixture.allocate(MethodHandles.lookup().defineClass(w.toByteArray()));
        available = Set.of(Level.OVERWORLD, Level.NETHER, Compat.dimensionKey(Compat.id("example", "orbit")));
        check(suggest(dispatcher, source, "biblioteca looping set ").equals(List.of("example:orbit", "minecraft:overworld", "minecraft:the_nether")), "loaded dimensions are suggested");
        check(suggest(dispatcher, source, "biblioteca looping set example:o").equals(List.of("example:orbit")), "partial custom dimension completion");
        check(suggest(dispatcher, source, "biblioteca looping set example:orbit ").equals(List.of("false", "true")), "looping retains boolean arguments");
        available = Set.of(Level.END);
        check(suggest(dispatcher, source, "biblioteca looping set ").equals(List.of("minecraft:the_end")), "dimension suggestions follow the current source");
        check(suggest(dispatcher, source, "biblioteca sky_light ").equals(List.of("disabled", "hybrid", "looping")), "three sky modes complete");
        check(suggest(dispatcher, source, "biblioteca sky_light h").equals(List.of("hybrid")), "partial sky mode completion");
        check(dispatcher.parse("biblioteca sky_light true", source).getReader().canRead(), "sky booleans are rejected");
        check(dispatcher.getRoot().getChild("biblioteca").getChild("no_sky_light") == null, "no old command alias");
        for (String mode : List.of("disabled", "looping", "hybrid")) {
            var parsed = dispatcher.parse("biblioteca sky_light " + mode, source);
            check(!parsed.getReader().canRead() && parsed.getContext().getCommand() != null, "sky mode reaches its executable branch");
        }
        System.out.println("PASS: native command completion: live/custom dimensions, prefixes, three sky modes and removed boolean/alias syntax");
    }
    private static List<String> suggest(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source, String input) throws Exception {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input,source)).get().getList().stream().map(Suggestion::getText).toList();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
