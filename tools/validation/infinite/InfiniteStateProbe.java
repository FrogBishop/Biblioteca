package biblioteca.infiniteprobe;

import babel.biblioteca.Biblioteca;
import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.compat.InfiniteDimensionKey;
import babel.biblioteca.network.LoopingStatePayload;
import babel.biblioteca.world.LoopingDimensionManager;
import babel.biblioteca.world.LoopingWorldData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.server.ServerStartCallback;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.dimension.DimHash;
import net.minecraft.world.level.dimension.DimensionType;
import java.util.Map;
import java.util.Set;

/** Exercises the real dynamically generating registry, commands and persisted server data. */
public final class InfiniteStateProbe implements ModInitializer {
    @Override public void onInitialize() {
        ServerStartCallback.EVENT.register(server -> {
            try { run(server); }
            catch (Throwable error) { System.out.println("INFINITE_STATE_FAILED"); error.printStackTrace(); }
            finally { server.halt(false); }
        });
    }

    private static void run(MinecraftServer server) throws Exception {
        boolean reload = Boolean.getBoolean("biblioteca.infinite.reload");
        DimensionType typeA = net.minecraft.core.Registry.DIMENSION_TYPE.byId(DimHash.getHash("biblioteca regression A"));
        DimensionType typeB = net.minecraft.core.Registry.DIMENSION_TYPE.byId(DimHash.getHash("biblioteca regression B"));
        InfiniteDimensionKey a = Compat.dimensionKey(DimensionType.getName(typeA));
        InfiniteDimensionKey b = Compat.dimensionKey(DimensionType.getName(typeB));
        check(!a.equals(b), "different books have distinct dimension keys");
        var levelA = server.getLevel(typeA);
        var levelB = server.getLevel(typeB);
        check(Compat.dimension(levelA).equals(a) && Compat.dimension(levelB).equals(b), "native generated level identities");
        if (!reload) {
            check(!LoopingRegistry.isLooping(levelA) && !LoopingRegistry.isLooping(levelB), "new random dimensions opt in independently");
            int result = server.getCommands().performCommand(server.createCommandSourceStack(), "biblioteca looping set " + a + " true");
            check(result == 1, "registered command accepts the native random dimension id");
            LoopingDimensionManager.setBeaconIgnoresSky(a, true);
            LoopingDimensionManager.setNoVoidFog(a, true);
            LoopingDimensionManager.setSkyLightMode(a, SkyLightMode.HYBRID);
            LoopingDimensionManager.setSkyLightMode(b, SkyLightMode.DISABLED);
        }
        check(LoopingRegistry.isLooping(levelA) && !LoopingRegistry.isLooping(levelB), "dimension settings remain isolated");
        check(LoopingDimensionManager.beaconIgnoresSky(a) && LoopingDimensionManager.hasNoVoidFog(a), "boolean attributes survive " + reload);
        check(LoopingDimensionManager.getSkyLightMode(a) == SkyLightMode.HYBRID
                && LoopingDimensionManager.getSkyLightMode(b) == SkyLightMode.DISABLED, "light modes survive " + reload);
        FallingBlockEntity entityA = new FallingBlockEntity(EntityType.FALLING_BLOCK, levelA);
        FallingBlockEntity entityB = new FallingBlockEntity(EntityType.FALLING_BLOCK, levelB);
        entityA.setPos(4, 257.25, 4); entityB.setPos(4, 257.25, 4);
        check(entityA.getY() == 1.25 && entityB.getY() == 257.25, "actual generated-world entity commits use each dimension's policy");
        LoopingStatePayload payload = Biblioteca.currentPayload();
        var buffer = payload.toBuffer();
        try {
            check(payload.equals(LoopingStatePayload.fromBuffer(buffer)), "snapshot packet round trip");
            check(payload.loopingDimensions().contains(a.toString()) && !payload.loopingDimensions().contains(b.toString()), "packet retains independent random ids");
        } finally { buffer.release(); }
        var original = new LoopingWorldData(Set.of(a), Set.of(b), Set.of(a), Map.of(a, SkyLightMode.HYBRID));
        var decoded = LoopingWorldData.decode(original.save(new CompoundTag()));
        check(decoded.getLoopingDimensions().equals(Set.of(a)) && decoded.getBeaconIgnoresSky().equals(Set.of(b))
                && decoded.getNoVoidFogDimensions().equals(Set.of(a)) && decoded.getSkyLightMode(a) == SkyLightMode.HYBRID, "all fields round trip through native NBT");
        CompoundTag legacy = new CompoundTag(); ListTag disabled = new ListTag(); disabled.add(StringTag.valueOf(a.toString()));
        legacy.put("no_sky_light", disabled);
        check(LoopingWorldData.decode(legacy).getSkyLightMode(a) == SkyLightMode.DISABLED, "legacy light flag migrates");
        CompoundTag explicit = new CompoundTag(); explicit.putString(a.toString(), "hybrid"); legacy.put("sky_light_mode", explicit);
        check(LoopingWorldData.decode(legacy).getSkyLightMode(a) == SkyLightMode.HYBRID, "explicit light mode overrides legacy flag");
        CompoundTag damaged = new CompoundTag();
        ListTag damagedIds = new ListTag(); damagedIds.add(StringTag.valueOf(a.toString())); damagedIds.add(StringTag.valueOf("invalid id!"));
        damaged.put("looping", damagedIds);
        CompoundTag damagedModes = new CompoundTag(); damagedModes.putString(a.toString(), "hybrid"); damagedModes.putString("invalid id!", "hybrid");
        damagedModes.putString(b.toString(), "unknown-mode"); damaged.put("sky_light_mode", damagedModes);
        var recovered = LoopingWorldData.decode(damaged);
        check(recovered.getLoopingDimensions().equals(Set.of(a)) && recovered.getSkyLightModes().equals(Map.of(a, SkyLightMode.HYBRID)),
                "invalid saved entries do not discard valid dimension settings");
        System.out.println("INFINITE_STATE_OK phase=" + (reload ? "reload" : "save") + " a=" + a + " b=" + b);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        System.out.println("INFINITE_STATE_CASE_OK " + message);
    }
}
