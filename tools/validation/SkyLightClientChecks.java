package babel.biblioteca.validation;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SkyLightMode;
import babel.biblioteca.client.LoopingDimensionCache;
import babel.biblioteca.network.LoopingStatePayload;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/** Calls the same native light and height samplers used for water top and side vertices. */
public final class SkyLightClientChecks {
    public static void run() throws Exception {
        SkyLightChecks.Fixture f = SkyLightChecks.tunnel(384);
        f.put(6, 319, 8, Blocks.WATER.defaultBlockState());
        f.put(6, -64, 8, Blocks.WATER.defaultBlockState()); f.drain();
        f.expect(6, 319, 8, 12); f.expect(6, -64, 8, 11); f.expect(6, 320, 8, 11);
        BlockAndTintGetter view = new BlockAndTintGetter() {
            public CardinalLighting cardinalLighting() { return CardinalLighting.DEFAULT; }
            public int getBlockTint(BlockPos pos, ColorResolver resolver) { return -1; }
            public LevelLightEngine getLightEngine() { return f.engine; }
            public BlockState getBlockState(BlockPos pos) { return f.view.getBlockState(pos); }
            public FluidState getFluidState(BlockPos pos) { return f.view.getFluidState(pos); }
            public BlockEntity getBlockEntity(BlockPos pos) { return null; }
            public int getMinY() { return f.min; }
            public int getHeight() { return f.top - f.min; }
        };
        FluidRenderer renderer = new FluidRenderer(null);
        Method light = FluidRenderer.class.getDeclaredMethod("getLightCoords", BlockAndTintGetter.class, BlockPos.class);
        light.setAccessible(true);
        int top = (int) light.invoke(renderer, view, new BlockPos(6, 319, 8));
        int copy = (int) light.invoke(renderer, view, new BlockPos(6, -65, 8));
        SkyLightChecks.require(LightCoordsUtil.sky(top) == 12 && top == copy, "y=319 water must sample periodic sky, never implicit full sky above y=319");
        Method height = FluidRenderer.class.getDeclaredMethod("getHeight", BlockAndTintGetter.class, Fluid.class, BlockPos.class);
        height.setAccessible(true);
        SkyLightChecks.require((float) height.invoke(renderer, view, Fluids.WATER, new BlockPos(6, 319, 8)) == 1.0f, "Water geometry stays continuous across seam");
        Field check = LoopingRegistry.class.getDeclaredField("serverSkyLightMode"); check.setAccessible(true);
        Object saved = check.get(null);
        try {
            LoopingRegistry.setServerSkyLightModeGetter(dim -> SkyLightMode.DISABLED);
            SkyLightChecks.require(LightCoordsUtil.sky((int) light.invoke(renderer, view, new BlockPos(6, 319, 8))) == 0, "Disable skylight masks water vertices too");
            LoopingRegistry.setServerSkyLightModeGetter(dim -> SkyLightMode.HYBRID); f.drain();
            int hybrid = (int) light.invoke(renderer, view, new BlockPos(6, 319, 8));
            SkyLightChecks.require(LightCoordsUtil.sky(hybrid) == 12 && hybrid == (int) light.invoke(renderer, view, new BlockPos(6, -65, 8)), "Hybrid water vertices retain periodic sampling and do not inject external level 15");
            clientPackets(f);
        } finally { check.set(null, saved); LoopingRegistry.bumpRevision(); }
        System.out.println("PASS: native y=319 water vertex sky=12 (wrapped y=320 sky=11), periodic copies, disabled sky and continuous water geometry");
    }

    private static void clientPackets(SkyLightChecks.Fixture server) throws Exception {
        Field getter = LoopingRegistry.class.getDeclaredField("clientSkyLightMode"); getter.setAccessible(true);
        Object saved = getter.get(null);
        LoopingRegistry.setClientSkyLightModeGetter(LoopingDimensionCache::getSkyLightMode);
        try {
            SkyLightChecks.Fixture client = new SkyLightChecks.Fixture(384, true);
            client.load(0, 0, false);
            // Native light data can arrive before the custom attributes; applying the mode must
            // preserve those layers, including the different light on the two seam rows.
            for (int sy = server.level.getMinSectionY(); sy <= server.level.getMaxSectionY(); sy++) {
                SectionPos pos = SectionPos.of(0, sy, 0);
                client.engine.queueSectionData(LightLayer.SKY, pos, server.engine.getLayerListener(LightLayer.SKY).getDataLayerData(pos).copy());
            }
            client.drain();
            LoopingDimensionCache.applyPayload(new LoopingStatePayload(Set.of("minecraft:overworld"), Set.of(), Set.of(), Map.of("minecraft:overworld", SkyLightMode.HYBRID)));
            client.drain(); client.expect(6, 319, 8, 12); client.expect(6, 320, 8, 11);
            SkyLightChecks.require(LoopingDimensionCache.getSkyLightMode(Level.OVERWORLD) == SkyLightMode.HYBRID, "client snapshot carries hybrid mode");
            LoopingDimensionCache.applyPayload(new LoopingStatePayload(Set.of("minecraft:overworld"), Set.of(), Set.of(), Map.of("minecraft:overworld", SkyLightMode.DISABLED)));
            client.drain(); client.expect(6, 319, 8, 0);
            LoopingDimensionCache.clear();
            SkyLightChecks.require(LoopingDimensionCache.getSkyLightMode(Level.OVERWORLD) == SkyLightMode.DEFAULT && !LoopingDimensionCache.isLooping(Level.OVERWORLD), "disconnect clears prior server's modes and dimensions");
        } finally { getter.set(null, saved); LoopingDimensionCache.clear(); }
        System.out.println("PASS: client sky-mode snapshots, native light layers arriving before attributes, suppression and disconnect reset");
    }
}
