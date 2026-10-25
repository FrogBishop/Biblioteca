package babel.biblioteca.validation.main100;

import babel.biblioteca.Biblioteca;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

import static babel.biblioteca.validation.main100.SeamChecks.check;

/** Brigadier completion on the actual mod command branch, using a live dimension source. */
public final class DimensionCommandChecks {
    @SuppressWarnings("unchecked")
    public static void run() throws Exception {
        var branch = Biblioteca.class.getDeclaredMethod("loopingCommand");
        branch.setAccessible(true);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(Commands.literal("biblioteca").then((LiteralArgumentBuilder<CommandSourceStack>) branch.invoke(null)));
        var node = (ArgumentCommandNode<CommandSourceStack, ?>) dispatcher.getRoot().getChild("biblioteca")
                .getChild("looping").getChild("set").getChild("dimension");
        check(node.getType() instanceof DimensionArgument, "dimension argument uses vanilla client completion");
        check(ArgumentTypeInfos.isClassRecognized(node.getType().getClass()), "dimension argument can be sent to multiplayer clients");
        Source source = new Source();
        source.dimensions = Set.of(Level.OVERWORLD, Level.NETHER, Level.END, key("example:sky_islands"));
        check(complete(dispatcher, source, "").equals(List.of("example:sky_islands", "minecraft:overworld", "minecraft:the_end", "minecraft:the_nether")),
                "empty dimension input offers vanilla and datapack dimensions");
        check(complete(dispatcher, source, "example:sky_i").equals(List.of("example:sky_islands")), "namespace and partial ID completion");
        check(complete(dispatcher, source, "over").equals(List.of("minecraft:overworld")), "vanilla shorthand completion");
        check(complete(dispatcher, source, "example:sky_islands ").equals(List.of("false", "true")), "boolean completion remains available");
        source.dimensions = Set.of(Level.OVERWORLD, key("another:deep_caverns"));
        check(complete(dispatcher, source, "").equals(List.of("another:deep_caverns", "minecraft:overworld")),
                "completion follows changed loaded dimensions without rebuilding the command tree");
        check(complete(dispatcher, source, "example:").isEmpty(), "unloaded pack dimension is not suggested");
        var skyBranch = Biblioteca.class.getDeclaredMethod("skyLightCommand"); skyBranch.setAccessible(true);
        dispatcher.register(Commands.literal("biblioteca").then((LiteralArgumentBuilder<CommandSourceStack>) skyBranch.invoke(null)));
        String prefix = "biblioteca sky_light ";
        check(suggest(dispatcher, source, prefix).equals(List.of("disabled", "hybrid", "looping")), "all three sky mode literals synchronize and complete");
        check(suggest(dispatcher, source, prefix + "h").equals(List.of("hybrid")), "partial sky mode completion");
        check(dispatcher.parse(prefix + "true", source).getReader().canRead(), "old boolean syntax is rejected");
        check(dispatcher.parse(prefix + "invalid", source).getReader().canRead(), "unknown modes are rejected");
        for (String mode : List.of("disabled", "looping", "hybrid")) {
            var parsed = dispatcher.parse(prefix + mode, source);
            check(!parsed.getReader().canRead() && parsed.getContext().getCommand() != null, "each sky mode reaches its executable command");
        }
        check(dispatcher.getRoot().getChild("biblioteca").getChild("sky_light").getCommand() != null, "mode query needs no argument");
        System.out.println("PASS: dimension command: vanilla/custom candidates, prefixes, boolean completion, live source, multiplayer argument registration");
    }

    private static List<String> complete(CommandDispatcher<CommandSourceStack> dispatcher, Source source, String suffix) throws Exception {
        return suggest(dispatcher, source, "biblioteca looping set " + suffix);
    }
    private static List<String> suggest(CommandDispatcher<CommandSourceStack> dispatcher, Source source, String input) throws Exception {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source))
                .get().getList().stream().map(Suggestion::getText).toList();
    }
    private static ResourceKey<Level> key(String id) { return ResourceKey.create(Registries.DIMENSION, Identifier.parse(id)); }
    private static final class Source extends CommandSourceStack {
        Set<ResourceKey<Level>> dimensions;
        Source() { super(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, LevelBasedPermissionSet.ALL, Component.literal("test"), null); }
        @Override public Set<ResourceKey<Level>> levels() { return dimensions; }
    }
}
