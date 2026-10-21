package babel.biblioteca.client;

import babel.biblioteca.SeamPositions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
//? if >=1.21.2 {
import net.minecraft.server.level.ParticleStatus;
//?} else {
/*import net.minecraft.client.ParticleStatus;
*///?}
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/** Shared rain impact generation; the caller retains its native tick counter and particle density. */
public final class LoopingRainImpacts {
    private LoopingRainImpacts() {}

    public static int tick(ClientLevel level, Vec3 cameraPosition, long ticks, ParticleStatus particles,
                           int radius, float rain, int attempts, int soundTime) {
        int period = level.getHeight();
        if (period <= 0 || rain <= 0.0f || radius <= 0) return soundTime;
        int diameter = radius * 2 + 1;
        RandomSource random = RandomSource.create(ticks * 312987231L);
        BlockPos camera = BlockPos.containing(cameraPosition);
        BlockPos soundPos = null;
        for (int i = 0; i < attempts; i++) {
            int dx = random.nextInt(diameter) - radius;
            int dz = random.nextInt(diameter) - radius;
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, camera.offset(dx, 0, dz));
            if (surface.getY() <= level.getMinY()) continue;
            int nearbyY = (int) SeamPositions.foldToward(surface.getY(), camera.getY(), period);
            if (nearbyY > camera.getY() + 10 || nearbyY < camera.getY() - 10) continue;
            if (WeatherCompat.precipitation(level, SeamPositions.foldBlockPos(level, surface)) != Biome.Precipitation.RAIN) continue;
            BlockPos blockPos = surface.below();
            soundPos = blockPos.offset(0, nearbyY - surface.getY(), 0);
            // Minimal detail still plays rain audio, as in vanilla.
            if (particles == ParticleStatus.MINIMAL) break;
            double x = random.nextDouble();
            double z = random.nextDouble();
            BlockState block = level.getBlockState(blockPos);
            FluidState fluid = level.getFluidState(blockPos);
            double top = Math.max(block.getCollisionShape(level, blockPos).max(Direction.Axis.Y, x, z), fluid.getHeight(level, blockPos));
            ParticleOptions type = !fluid.is(FluidTags.LAVA) && !block.is(Blocks.MAGMA_BLOCK)
                    && !CampfireBlock.isLitCampfire(block) ? ParticleTypes.RAIN : ParticleTypes.SMOKE;
            level.addParticle(type, blockPos.getX() + x, soundPos.getY() + top, blockPos.getZ() + z, 0.0, 0.0, 0.0);
        }
        if (soundPos != null && random.nextInt(3) < soundTime++) {
            soundTime = 0;
            int roofY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, camera).getY();
            double nearbyRoofY = SeamPositions.foldToward(roofY, camera.getY(), period);
            boolean sheltered = soundPos.getY() > camera.getY() + 1 && roofY > level.getMinY() && nearbyRoofY > camera.getY();
            level.playLocalSound(soundPos, sheltered ? SoundEvents.WEATHER_RAIN_ABOVE : SoundEvents.WEATHER_RAIN,
                    SoundSource.WEATHER, sheltered ? 0.1f : 0.2f, sheltered ? 0.5f : 1.0f, false);
        }
        return soundTime;
    }
}
