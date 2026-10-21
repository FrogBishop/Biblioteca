package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
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
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Generates rain impact sounds, splashes and hot-surface smoke on either side of the boundary. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelWeatherYLoopMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow private int rainSoundTime;

    @Inject(method = "tickWeatherEffects", at = @At("HEAD"), cancellable = true)
    private void biblioteca$rainImpactsAcrossBoundary(CallbackInfo ci) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (!LoopingRegistry.isLooping(level)) return;
        int period = level.getHeight();
        if (period <= 0) return;

        ci.cancel();
        float rain = level.getRainLevel(1.0F);
        if (rain <= 0.0F) return;

        ParticleStatus particles = this.minecraft.options.particles().get();
        int radius = this.minecraft.options.weatherRadius().get();
        int diameter = 2 * radius + 1;
        int attempts = (int) (0.225F * diameter * diameter * rain * rain)
                / (particles == ParticleStatus.DECREASED ? 2 : 1);
        RandomSource random = RandomSource.createThreadLocalInstance(level.getGameTime() * 312987231L);
        BlockPos camera = BlockPos.containing(this.minecraft.gameRenderer.mainCamera().position());
        BlockPos soundPos = null;

        for (int i = 0; i < attempts; i++) {
            int dx = random.nextInt(diameter) - radius;
            int dz = random.nextInt(diameter) - radius;
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, camera.offset(dx, 0, dz));
            // Check the real height first: an empty column has no surface for rain to hit.
            if (surface.getY() <= level.getMinY()) continue;
            int nearbyY = (int) SeamPositions.foldToward(surface.getY(), camera.getY(), period);
            if (nearbyY > camera.getY() + 10 || nearbyY < camera.getY() - 10) continue;
            if (level.getPrecipitationAt(SeamPositions.foldBlockPos(level, surface)) != Biome.Precipitation.RAIN) continue;

            BlockPos blockPos = surface.below();
            int shiftY = nearbyY - surface.getY();
            soundPos = blockPos.offset(0, shiftY, 0);
            // Vanilla keeps the rain sound when particle detail is minimal.
            if (particles == ParticleStatus.MINIMAL) break;

            double x = random.nextDouble();
            double z = random.nextDouble();
            BlockState block = level.getBlockState(blockPos);
            FluidState fluid = level.getFluidState(blockPos);
            double blockTop = block.getCollisionShape(level, blockPos).max(Direction.Axis.Y, x, z);
            double particleY = Math.max(blockTop, fluid.getHeight(level, blockPos));
            ParticleOptions type = !fluid.is(FluidTags.LAVA) && !block.is(Blocks.MAGMA_BLOCK)
                    && !CampfireBlock.isLitCampfire(block) ? ParticleTypes.RAIN : ParticleTypes.SMOKE;
            level.addParticle(type, blockPos.getX() + x, soundPos.getY() + particleY, blockPos.getZ() + z,
                    0.0, 0.0, 0.0);
        }

        if (soundPos != null && random.nextInt(3) < this.rainSoundTime++) {
            this.rainSoundTime = 0;
            int roofY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, camera).getY();
            double nearbyRoofY = SeamPositions.foldToward(roofY, camera.getY(), period);
            boolean sheltered = soundPos.getY() > camera.getY() + 1
                    && roofY > level.getMinY() && nearbyRoofY > camera.getY();
            level.playLocalSound(soundPos, sheltered ? SoundEvents.WEATHER_RAIN_ABOVE : SoundEvents.WEATHER_RAIN,
                    SoundSource.WEATHER, sheltered ? 0.1F : 0.2F, sheltered ? 0.5F : 1.0F, false);
        }
    }
}
