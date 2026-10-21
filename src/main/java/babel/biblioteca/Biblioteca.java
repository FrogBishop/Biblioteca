package babel.biblioteca;

import babel.biblioteca.network.LoopingStatePayload;
import babel.biblioteca.world.LoopingDimensionManager;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
//? if >=1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//?}
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.Registries;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Common entrypoint: per-dimension attribute storage, client synchronization and the
 * {@code /biblioteca} command tree.
 *
 * <p>Four attributes exist per dimension: whether the Y axis loops, whether beacon beams ignore the
 * sky, whether vanilla void fog is disabled, and whether the vanilla sky-light channel is disabled.
 * The two lighting overrides default to false, preserving vanilla effects. Turning one on disables
 * that effect in the looping dimension.
 */
public class Biblioteca implements ModInitializer {

    public static final String MOD_ID = "biblioteca";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LoopingRegistry.setServerChecker(LoopingDimensionManager::isLooping);
        LoopingRegistry.setServerBeaconChecker(LoopingDimensionManager::beaconIgnoresSky);
        LoopingRegistry.setServerNoVoidFogChecker(LoopingDimensionManager::hasNoVoidFog);
        LoopingRegistry.setServerNoSkyLightChecker(LoopingDimensionManager::hasNoSkyLight);

        SeamStats.init();

        //? if >=26.1 {
        PayloadTypeRegistry.clientboundPlay().register(LoopingStatePayload.TYPE, LoopingStatePayload.STREAM_CODEC);
        //?} else if >=1.20.5 {
        /*PayloadTypeRegistry.playS2C().register(LoopingStatePayload.TYPE, LoopingStatePayload.STREAM_CODEC);
        *///?}

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sendState(handler.getPlayer(), currentPayload()));

        registerCommands();
        LOGGER.info("[Biblioteca] Y-looping mod initialized.");
    }

    /** The full attribute snapshot, as sent to clients. */
    public static LoopingStatePayload currentPayload() {
        return new LoopingStatePayload(
                keysOf(LoopingDimensionManager.getLoopingDimensions()),
                keysOf(LoopingDimensionManager.getBeaconIgnoresSkyDimensions()),
                keysOf(LoopingDimensionManager.getNoVoidFogDimensions()),
                keysOf(LoopingDimensionManager.getNoSkyLightDimensions()));
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

    private static void sendState(ServerPlayer player, LoopingStatePayload payload) {
        //? if >=1.20.5 {
        ServerPlayNetworking.send(player, payload);
        //?} else {
        /*ServerPlayNetworking.send(player, LoopingStatePayload.ID, payload.toBuffer());
        *///?}
    }

    private static void registerCommands() {
        // Operators at level 2 (GAMEMASTERS) can toggle the attributes.
        //? if >=1.21.11 {
        Permission opPermission = new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS);
        //?}

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
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
                                .then(dimensionAttribute("no_sky_light", "disabled", "No sky light",
                                        LoopingDimensionManager::hasNoSkyLight,
                                        LoopingDimensionManager::setNoSkyLight))));
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
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Looping dimensions: " + list),
                                    false);
                            return dimensions.size();
                        }))
                .then(Commands.literal("set")
                        .then(Commands.argument("dimension", IdentifierArgument.id())
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            Identifier id = IdentifierArgument.getId(ctx, "dimension");
                                            ResourceKey<Level> dimension =
                                                    ResourceKey.create(Registries.DIMENSION, id);
                                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                            LoopingDimensionManager.setLooping(dimension, enabled);
                                            broadcastState(ctx.getSource().getServer());
                                            ctx.getSource().sendSuccess(() -> Component.literal(
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
                    ctx.getSource().sendSuccess(
                            () -> Component.literal(
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
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal(
                                            label + " in " + Compat.keyId(dimension) + " = " + value),
                                    true);
                            return 1;
                        }));
    }
}
