package babel.biblioteca;

import babel.biblioteca.network.LoopingStatePayload;
import babel.biblioteca.world.LoopingDimensionManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.Level;

import com.mojang.brigadier.arguments.BoolArgumentType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

public class Biblioteca implements ModInitializer {
    public static final String MOD_ID = "biblioteca";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LoopingRegistry.setServerChecker(LoopingDimensionManager::isLooping);
        LoopingRegistry.setServerBeaconChecker(LoopingDimensionManager::beaconIgnoresSky);
        LoopingRegistry.setServerVoidFogChecker(LoopingDimensionManager::showsVoidFog);
        LoopingRegistry.setServerSkyLightChecker(LoopingDimensionManager::keepsSkyLight);

        SeamStats.init();

        PayloadTypeRegistry.clientboundPlay().register(LoopingStatePayload.TYPE, LoopingStatePayload.STREAM_CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerPlayNetworking.send(handler.getPlayer(), currentPayload()));

        registerCommands();
        LOGGER.info("[Biblioteca] Y-looping mod initialized.");
    }

    /** The full attribute snapshot, as sent to clients. */
    public static LoopingStatePayload currentPayload() {
        return new LoopingStatePayload(
                keysOf(LoopingDimensionManager.getLoopingDimensions()),
                keysOf(LoopingDimensionManager.getBeaconIgnoresSkyDimensions()),
                keysOf(LoopingDimensionManager.getVoidFogDimensions()),
                keysOf(LoopingDimensionManager.getSkyLightDimensions()));
    }

    private static Set<String> keysOf(Set<ResourceKey<Level>> dimensions) {
        return dimensions.stream()
                .map(key -> key.identifier().toString())
                .collect(Collectors.toSet());
    }

    public static void broadcastState(MinecraftServer server) {
        LoopingStatePayload payload = currentPayload();
        server.getPlayerList().getPlayers()
                .forEach(player -> ServerPlayNetworking.send(player, payload));
    }

    private static void registerCommands() {
        // Operators at level 2 (GAMEMASTERS) can toggle the attributes.
        Permission opPermission = new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                Commands.literal("biblioteca")
                    .requires(src -> src.permissions().hasPermission(opPermission))
                    .then(Commands.literal("looping")
                        .then(Commands.literal("list")
                            .executes(ctx -> {
                                Set<ResourceKey<Level>> dims =
                                        LoopingDimensionManager.getLoopingDimensions();
                                String list = dims.isEmpty() ? "(none)" :
                                        dims.stream()
                                            .map(k -> k.identifier().toString())
                                            .sorted()
                                            .collect(Collectors.joining(", "));
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("Looping dimensions: " + list),
                                        false);
                                return dims.size();
                            })
                        )
                        .then(Commands.literal("set")
                            .then(Commands.argument("dimension", IdentifierArgument.id())
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                    .executes(ctx -> {
                                        Identifier loc = IdentifierArgument.getId(ctx, "dimension");
                                        ResourceKey<Level> dim =
                                                ResourceKey.create(Registries.DIMENSION, loc);
                                        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                        LoopingDimensionManager.setLooping(dim, enabled);
                                        broadcastState(ctx.getSource().getServer());
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "Set looping for " + loc + " = " + enabled), true);
                                        return 1;
                                    })
                                )
                            )
                        )
                    )
                    // Applies to the dimension the command was run in.
                    .then(Commands.literal("beacon")
                        .executes(ctx -> {
                            ResourceKey<Level> dim = ctx.getSource().getLevel().dimension();
                            boolean ignoresSky = LoopingDimensionManager.beaconIgnoresSky(dim);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Beacon ignores sky in " + dim.identifier() + " = " + ignoresSky),
                                    false);
                            return ignoresSky ? 1 : 0;
                        })
                        .then(Commands.argument("ignoresSky", BoolArgumentType.bool())
                            .executes(ctx -> {
                                ResourceKey<Level> dim = ctx.getSource().getLevel().dimension();
                                boolean ignoresSky = BoolArgumentType.getBool(ctx, "ignoresSky");
                                LoopingDimensionManager.setBeaconIgnoresSky(dim, ignoresSky);
                                broadcastState(ctx.getSource().getServer());
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Beacon ignores sky in " + dim.identifier() + " = " + ignoresSky),
                                        true);
                                return 1;
                            })
                        )
                    )
                    // Applies to the dimension the command was run in.
                    .then(Commands.literal("voidfog")
                        .executes(ctx -> {
                            ResourceKey<Level> dim = ctx.getSource().getLevel().dimension();
                            boolean shown = LoopingDimensionManager.showsVoidFog(dim);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Void fog in " + dim.identifier() + " = " + shown),
                                    false);
                            return shown ? 1 : 0;
                        })
                        .then(Commands.argument("shown", BoolArgumentType.bool())
                            .executes(ctx -> {
                                ResourceKey<Level> dim = ctx.getSource().getLevel().dimension();
                                boolean shown = BoolArgumentType.getBool(ctx, "shown");
                                LoopingDimensionManager.setVoidFog(dim, shown);
                                broadcastState(ctx.getSource().getServer());
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Void fog in " + dim.identifier() + " = " + shown),
                                        true);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("skylight")
                        .executes(ctx -> {
                            ResourceKey<Level> dim = ctx.getSource().getLevel().dimension();
                            boolean kept = LoopingDimensionManager.keepsSkyLight(dim);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Sky light in " + dim.identifier() + " = " + kept),
                                    false);
                            return kept ? 1 : 0;
                        })
                        .then(Commands.argument("kept", BoolArgumentType.bool())
                            .executes(ctx -> {
                                ResourceKey<Level> dim = ctx.getSource().getLevel().dimension();
                                boolean kept = BoolArgumentType.getBool(ctx, "kept");
                                LoopingDimensionManager.setSkyLight(dim, kept);
                                broadcastState(ctx.getSource().getServer());
                                ctx.getSource().sendSuccess(() -> Component.literal(
                                        "Sky light in " + dim.identifier() + " = " + kept),
                                        true);
                                return 1;
                            })
                        )
                    )
            )
        );
    }
}
