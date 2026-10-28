package babel.biblioteca;

import babel.biblioteca.network.LoopingStatePayload;
import babel.biblioteca.world.LoopingDimensionManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.11 {
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
//?}
import net.minecraft.world.level.Level;
//? if >=1.18.2 {
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//?} else {
/*import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
*///?}


import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Common entrypoint: per-dimension attribute storage, client synchronization and the
 * {@code /biblioteca} command tree.
 *
 * <p>Four attributes exist per dimension: whether the Y axis loops, whether beacon beams ignore the
 * sky, whether void fog is disabled, and the sky-light source mode (disabled, looping, hybrid).
 */
public final class Biblioteca {

    public static final String MOD_ID = "biblioteca";
//? if >=1.18.2 {
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
//?} else {
/*    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
*///?}


    private static BiConsumer<ServerPlayer, LoopingStatePayload> stateSender;

    private Biblioteca() {}

    /** Loader entrypoints supply transport; gameplay and commands stay shared. */
    public static void initialize(BiConsumer<ServerPlayer, LoopingStatePayload> sender) {
        stateSender = java.util.Objects.requireNonNull(sender);
        LoopingRegistry.setServerChecker(LoopingDimensionManager::isLooping);
        LoopingRegistry.setServerBeaconChecker(LoopingDimensionManager::beaconIgnoresSky);
        LoopingRegistry.setServerNoVoidFogChecker(LoopingDimensionManager::hasNoVoidFog);
        LoopingRegistry.setServerSkyLightModeGetter(LoopingDimensionManager::getSkyLightMode);

        LOGGER.info("[Biblioteca] Y-looping mod initialized.");
    }

    /** The full attribute snapshot, as sent to clients. */
    public static LoopingStatePayload currentPayload() {
        return new LoopingStatePayload(
                keysOf(LoopingDimensionManager.getLoopingDimensions()),
                keysOf(LoopingDimensionManager.getBeaconIgnoresSkyDimensions()),
                keysOf(LoopingDimensionManager.getNoVoidFogDimensions()),
                LoopingDimensionManager.getSkyLightModes().entrySet().stream().collect(Collectors.toMap(
                        entry -> Compat.keyId(entry.getKey()).toString(), java.util.Map.Entry::getValue)));
    }

    private static Set<String> keysOf(Set<ResourceKey<Level>> dimensions) {
        return dimensions.stream()
                .map(key -> Compat.keyId(key).toString())
                .collect(Collectors.toSet());
    }

    public static void broadcastState(MinecraftServer server) {
        LoopingStatePayload payload = currentPayload();
        server.getPlayerList().getPlayers()
                .forEach(player -> sendState(player, payload));
    }

    public static void sendState(ServerPlayer player, LoopingStatePayload payload) {
        stateSender.accept(player, payload);
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Operators at level 2 (GAMEMASTERS) can toggle the attributes.
        //? if >=1.21.11 {
        Permission opPermission = new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS);
        //?}

                dispatcher.register(
                        Commands.literal("biblioteca")
                                .requires(source -> {
                                    //? if >=1.21.11 {
                                    return source.permissions().hasPermission(opPermission);
                                    //?} else {
                                    /*return source.hasPermission(2);
                                    *///?}
                                })
                                .then(loopingCommand())
                                .then(dimensionAttribute("beacon", "ignoresSky", "Beacon ignores sky",
                                        LoopingDimensionManager::beaconIgnoresSky,
                                        LoopingDimensionManager::setBeaconIgnoresSky))
                                .then(dimensionAttribute("no_void_fog", "disabled", "No void fog",
                                        LoopingDimensionManager::hasNoVoidFog,
                                        LoopingDimensionManager::setNoVoidFog))
                                .then(skyLightCommand()));
    }

    /** Literal mode names synchronize and complete on clients without a custom argument type. */
    private static LiteralArgumentBuilder<CommandSourceStack> skyLightCommand() {
        var command = Commands.literal("sky_light").executes(ctx -> {
            ResourceKey<Level> dimension = ctx.getSource().getLevel().dimension();
            SkyLightMode mode = LoopingDimensionManager.getSkyLightMode(dimension);
            Compat.sendSuccess(ctx.getSource(), () -> Compat.translatable("biblioteca.command.sky_light.query",
                    Compat.keyId(dimension).toString(), Compat.translatable(mode.translationKey()), mode.id()), false);
            return 1;
        });
        for (SkyLightMode mode : SkyLightMode.values()) {
            command.then(Commands.literal(mode.id()).executes(ctx -> {
                ResourceKey<Level> dimension = ctx.getSource().getLevel().dimension();
                LoopingDimensionManager.setSkyLightMode(dimension, mode);
                broadcastState(ctx.getSource().getServer());
                Compat.sendSuccess(ctx.getSource(), () -> Compat.translatable("biblioteca.command.sky_light.set",
                        Compat.keyId(dimension).toString(), Compat.translatable(mode.translationKey()), mode.id()), true);
                return 1;
            }));
        }
        return command;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> loopingCommand() {
        return Commands.literal("looping")
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            Set<ResourceKey<Level>> dimensions =
                                    LoopingDimensionManager.getLoopingDimensions();
                            String list = dimensions.isEmpty() ? "(none)" : dimensions.stream()
                                    .map(key -> Compat.keyId(key).toString())
                                    .sorted()
                                    .collect(Collectors.joining(", "));
                            Compat.sendSuccess(ctx.getSource(),
                                    () -> Compat.literal("Looping dimensions: " + list),
                                    false);
                            return dimensions.size();
                        }))
                .then(Commands.literal("set")
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            Identifier id = IdentifierArgument.getId(ctx, "dimension");
                                            ResourceKey<Level> dimension =
                                                    Compat.dimensionKey(id);
                                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                            LoopingDimensionManager.setLooping(dimension, enabled);
                                            broadcastState(ctx.getSource().getServer());
                                            Compat.sendSuccess(ctx.getSource(), () -> Compat.literal(
                                                    "Set looping for " + id + " = " + enabled), true);
                                            return 1;
                                        }))));
    }

    /**
     * A toggle that acts on the dimension the command was run in.
     *
     * Reading reports the stored value without changing anything; writing also broadcasts the new
     * snapshot to every connected client, so the change is visible without a reconnect.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> dimensionAttribute(
            String literal,
            String argument,
            String label,
            Predicate<ResourceKey<Level>> query,
            BiConsumer<ResourceKey<Level>, Boolean> setter) {

        return Commands.literal(literal)
                .executes(ctx -> {
                    ResourceKey<Level> dimension = ctx.getSource().getLevel().dimension();
                    boolean value = query.test(dimension);
                    Compat.sendSuccess(ctx.getSource(),
                            () -> Compat.literal(
                                    label + " in " + Compat.keyId(dimension) + " = " + value),
                            false);
                    return value ? 1 : 0;
                })
                .then(Commands.argument(argument, BoolArgumentType.bool())
                        .executes(ctx -> {
                            ResourceKey<Level> dimension = ctx.getSource().getLevel().dimension();
                            boolean value = BoolArgumentType.getBool(ctx, argument);
                            setter.accept(dimension, value);
                            broadcastState(ctx.getSource().getServer());
                            Compat.sendSuccess(ctx.getSource(),
                                    () -> Compat.literal(
                                            label + " in " + Compat.keyId(dimension) + " = " + value),
                                    true);
                            return 1;
                        }));
    }
}
