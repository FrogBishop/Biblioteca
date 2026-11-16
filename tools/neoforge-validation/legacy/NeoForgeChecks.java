package babel.biblioteca.validation.neoforge;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.validation.*;
import com.mojang.brigadier.CommandDispatcher;
import java.nio.file.Path;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** The shared regression suite plus the actual pre-codec loader bindings. */
public final class NeoForgeChecks {
    public static void run(Path classes, boolean client, boolean noRender) throws Exception {
        try (AutoCloseable fixture = client ? NeoForgeClientChecks.installMinecraftFixture() : () -> {}) {
            String version = ModList.get().getModContainerById(Biblioteca.MOD_ID).orElseThrow().getModInfo().getVersion().toString();
            if (!version.equals(System.getProperty("biblioteca.validation.version"))) throw new AssertionError("Wrong packaged mod " + version);
            ApiChecks.run(classes); CoreChecks.run(client); LightSyncChecks.run(); GameEventChecks.run(); EffectBroadcastChecks.run();
            var dispatcher = new CommandDispatcher<CommandSourceStack>();
            var context = CommandBuildContext.simple(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                    net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
            NeoForge.EVENT_BUS.post(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.ALL, context));
            var command = dispatcher.getRoot().getChild("biblioteca");
            if (command == null || command.getChild("looping") == null || command.getChild("sky_light") == null
                    || command.getChild("beacon") == null || command.getChild("no_void_fog") == null)
                throw new AssertionError("Native command event missed the command tree");
            LegacyTransportChecks.run(client);
            SeamChecks.run(client && !noRender); SkyChecks.run(); CommandChecks.run();
            if (client) {
                NeoForgeClientChecks.run(); DimensionModelChecks.run();
                if (!noRender) {
                    ParticleRenderChecks.run(); CloudRenderChecks.run(); AttachmentRenderChecks.run(); TerrainChecks.run();
                }
            }
        }
    }
}
