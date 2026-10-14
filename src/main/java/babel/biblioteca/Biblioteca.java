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

        PayloadTypeRegistry.clientboundPlay().register(LoopingStatePayload.TYPE, LoopingStatePayload.STREAM_CODEC);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Set<String> keys = LoopingDimensionManager.getLoopingDimensions().stream()
                    .map(k -> k.identifier().toString())
                    .collect(Collectors.toSet());
            ServerPlayNetworking.send(handler.getPlayer(), new LoopingStatePayload(keys));
        });

        registerCommands();
        LOGGER.info("[Biblioteca] Y-looping mod initialized.");
    }

    private static void registerCommands() {
        // Operators at level 2 (GAMEMASTERS) can toggle looping.
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
                                        Set<String> keys =
                                                LoopingDimensionManager.getLoopingDimensions()
                                                .stream()
                                                .map(k -> k.identifier().toString())
                                                .collect(Collectors.toSet());
                                        LoopingStatePayload payload = new LoopingStatePayload(keys);
                                        ctx.getSource().getServer().getPlayerList().getPlayers()
                                                .forEach(p -> ServerPlayNetworking.send(p, payload));
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "Set looping for " + loc + " = " + enabled), true);
                                        return 1;
                                    })
                                )
                            )
                        )
                    )
            )
        );
    }
}
