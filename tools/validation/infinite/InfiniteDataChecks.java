package babel.biblioteca.validation;

import babel.biblioteca.Compat;
import babel.biblioteca.SeamPositions;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.compat.InfiniteDimensionKey;
import babel.biblioteca.network.LoopingStatePayload;
import babel.biblioteca.world.LoopingWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.dimension.DimHash;
import net.minecraft.world.level.dimension.DimensionType;
import java.util.Map;
import java.util.Set;

/** Headless checks for APIs that existed in the Infinity snapshot; live worlds have separate probes. */
public final class InfiniteDataChecks {
    public static void run(boolean client) {
        net.minecraft.SharedConstants.getCurrentVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        new babel.biblioteca.fabric.FabricBiblioteca().onInitialize();
        // The old transport registry requires a live Minecraft singleton; the graphical probe
        // exercises that entrypoint. Here only the client data/model services are initialized.
        if (client) babel.biblioteca.client.BibliotecaClient.initialize(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
        InfiniteDimensionKey a = Compat.dimensionKey(DimensionType.getName(net.minecraft.core.Registry.DIMENSION_TYPE.byId(DimHash.getHash("biblioteca matrix A"))));
        InfiniteDimensionKey b = Compat.dimensionKey(DimensionType.getName(net.minecraft.core.Registry.DIMENSION_TYPE.byId(DimHash.getHash("biblioteca matrix B"))));
        check(!a.equals(b) && a.equals(Compat.dimensionKey(Compat.parseId(a.toString()))), "generated dimension key identity");
        var data = new LoopingWorldData(Set.of(a), Set.of(b), Set.of(a), Map.of(a, SkyLightMode.HYBRID, b, SkyLightMode.DISABLED));
        var restored = LoopingWorldData.decode(data.save(new CompoundTag()));
        check(restored.getLoopingDimensions().equals(Set.of(a)) && restored.getBeaconIgnoresSky().equals(Set.of(b))
                && restored.getNoVoidFogDimensions().equals(Set.of(a)) && restored.getSkyLightModes().equals(data.getSkyLightModes()), "snapshot NBT attributes");
        CompoundTag legacy = new CompoundTag(); ListTag list = new ListTag(); list.add(StringTag.valueOf(a.toString()));
        legacy.put("no_sky_light", list);
        check(LoopingWorldData.decode(legacy).getSkyLightMode(a) == SkyLightMode.DISABLED, "legacy light mode migration");
        CompoundTag modes = new CompoundTag(); modes.putString(a.toString(), "hybrid"); legacy.put("sky_light_mode", modes);
        check(LoopingWorldData.decode(legacy).getSkyLightMode(a) == SkyLightMode.HYBRID, "explicit light mode precedence");
        CompoundTag damaged = new CompoundTag();
        ListTag damagedIds = new ListTag(); damagedIds.add(StringTag.valueOf(a.toString())); damagedIds.add(StringTag.valueOf("invalid id!"));
        damaged.put("looping", damagedIds);
        CompoundTag damagedModes = new CompoundTag(); damagedModes.putString(a.toString(), "hybrid"); damagedModes.putString("invalid id!", "hybrid");
        damagedModes.putString(b.toString(), "unknown-mode"); damaged.put("sky_light_mode", damagedModes);
        var recovered = LoopingWorldData.decode(damaged);
        check(recovered.getLoopingDimensions().equals(Set.of(a)) && recovered.getSkyLightModes().equals(Map.of(a, SkyLightMode.HYBRID)),
                "invalid saved entries do not discard valid dimension settings");
        var packet = new LoopingStatePayload(Set.of(a.toString()), Set.of(b.toString()), Set.of(a.toString()), Map.of(b.toString(), SkyLightMode.DISABLED));
        var buffer = packet.toBuffer();
        try { check(packet.equals(LoopingStatePayload.fromBuffer(buffer)), "snapshot packet round trip"); }
        finally { buffer.release(); }
        check(SeamPositions.foldToward(255.75, 0.25, 256) == -0.25, "nearby lower image");
        check(SeamPositions.foldToward(0.25, 255.75, 256) == 256.25, "nearby upper image");
        var beforeCatalog = Set.copyOf(net.minecraft.core.Registry.DIMENSION_TYPE.keySet());
        String passphrase = DimHash.getLastPasshphrase();
        var catalog = babel.biblioteca.client.InfiniteEasterEggDimensions.entries();
        var listed = babel.biblioteca.client.InfiniteEasterEggDimensions.forCreation();
        check(catalog.size() == 43 && listed.size() == 46 && new java.util.HashSet<>(listed).size() == 46,
                "43 unique Easter eggs plus three vanilla dimensions");
        check(beforeCatalog.equals(net.minecraft.core.Registry.DIMENSION_TYPE.keySet()) && passphrase.equals(DimHash.getLastPasshphrase()),
                "listing does not register worlds or change the secret passphrase");
        try {
            var field = net.minecraft.world.level.dimension.DimensionGenerator.class.getDeclaredField("CUSTOMS"); field.setAccessible(true);
            var nativeIds = ((java.util.Map<?, ?>)field.get(null)).keySet();
            check(nativeIds.equals(catalog.stream().map(babel.biblioteca.client.InfiniteEasterEggDimensions.Entry::id).collect(java.util.stream.Collectors.toSet())),
                    "catalog covers all native hard-coded generators exactly");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        for (var entry : catalog) {
            check(DimHash.getHash(entry.name()) == entry.id(), "original string hashes to its native generator: " + entry.name());
            var nativeType = net.minecraft.core.Registry.DIMENSION_TYPE.byId(entry.id());
            check(entry.key().equals(Compat.dimensionKey(DimensionType.getName(nativeType))), "matches actual warp registry key: " + entry.name());
            check(babel.biblioteca.client.InfiniteEasterEggDimensions.displayName(entry.key()).equals(entry.name()), "untranslated original display name: " + entry.name());
        }
        if (client) {
            var creation = babel.biblioteca.client.CreateWorldDimensions.loaded(null);
            check(creation.equals(listed), "create-world UI includes unvisited Easter eggs");
            var ordered = babel.biblioteca.client.InfiniteEasterEggDimensions.ordered(creation);
            check(ordered.subList(0, 3).equals(listed.subList(0, 3)) && ordered.subList(3, 46).stream()
                    .map(babel.biblioteca.client.InfiniteEasterEggDimensions::displayName).toList().equals(catalog.stream().map(babel.biblioteca.client.InfiniteEasterEggDimensions.Entry::name).sorted().toList()),
                    "vanilla dimensions first, Easter eggs ordered by original string");
            var model = new babel.biblioteca.client.DimensionConfigModel(() -> java.util.List.of(b, a), Set.of(a), Set.of(), Set.of(), Map.of());
            check(model.dimensions().size() == 2 && model.enabled(a, 0) && !model.enabled(b, 0), "dimension editor keeps independent ids");
            check(babel.biblioteca.client.ClientCompat.valueLabel(Compat.literal("Example"), 2).equals("Example: 2"), "snapshot option label has no missing vanilla translation dependency");
        }
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
        System.out.println("DATA_OK Infinity " + message);
    }
}
