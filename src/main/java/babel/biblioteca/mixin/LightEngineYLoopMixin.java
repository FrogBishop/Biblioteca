package babel.biblioteca.mixin;

import babel.biblioteca.LoopingLight;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LightChunkGetter;
//? if >=1.20 {
import net.minecraft.world.level.lighting.LightEngine;
//?} else {
/*import net.minecraft.world.level.lighting.LayerLightEngine;
*///?}

import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles the two light channels of a looping dimension.
 *
 * <p>Sky light: a ring has no sky, but sky light is not a property of the sky in this code - it is
 * a channel that starts at 15 at the top of the world and falls off as it is blocked on the way
 * down. The top rows therefore end up at 15 and the bottom rows, a whole world height of rock away
 * from the sky, at 0; where the two halves meet, the seam is a hard bright-over-dark line. The
 * no_sky_light attribute can remove this channel. It defaults to false, keeping vanilla lighting.
 * When enabled, zeroing getLightValue - the single method every sky-light query funnels through -
 * means baked section vertices, entity and block entity lighting, mob spawning and canSeeSky all
 * agree, with no per-caller special cases.
 *
 * <p>Block light is left alone as a channel and folded at the query boundary instead: a face is lit
 * by the cell it faces into, so the top face of the topmost block samples the cell above the world.
 * That cell is a real row of the ring, and folding the query answers with the row the ring puts
 * there instead of letting the light fall off the world. {@link BlockLightEngineYLoopMixin} handles
 * propagation, which is the other half of the same problem.
 */
//? if >=1.20 {
@Mixin(LightEngine.class)
//?} else {
/*@Mixin(LayerLightEngine.class)
*///?}

public abstract class LightEngineYLoopMixin {

    @Shadow @Final protected LightChunkGetter chunkSource;

    @Unique private Level biblioteca$level;
    @Unique private int biblioteca$revision = -1;
    @Unique private boolean biblioteca$looping;

    @Inject(method = "getLightValue", at = @At("HEAD"), cancellable = true)
    private void biblioteca$noSkyLight(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof SkyLightEngine)) return;
        if (!biblioteca$looping()) return;
        // false (the default) keeps vanilla sky light; true disables the channel.
        if (!LoopingRegistry.hasNoSkyLight(this.biblioteca$level)) return;
        cir.setReturnValue(0);
    }

    /**
     * Answers a query that falls outside the world with the light of the row the ring puts there.
     *
     * <p>The same fold fixes the player's own model: the light probe is taken at the eye, and a
     * player standing on the top row has their eyes past the top of the world, in the row the ring
     * puts the bottom of it in.
     */
    @ModifyVariable(method = "getLightValue", at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$foldQueriedPosition(BlockPos pos) {
        if (!biblioteca$looping()) return pos;
        if ((Object) this instanceof SkyLightEngine) return pos;

        return SeamPositions.foldBlockPos(this.biblioteca$level, pos);
    }

    /**
     * Cached because this runs once per light query, and a section bake makes thousands of them.
     * The cache is invalidated by the attribute revision, so toggling the dimension attribute
     * takes effect immediately.
     */
    @Unique
    private boolean biblioteca$looping() {
        Level level = this.biblioteca$level;

        if (level == null) {
            level = LoopingLight.levelOf(this.chunkSource);
            if (level == null) return false;
            this.biblioteca$level = level;
        }

        int revision = LoopingRegistry.revision();
        if (revision != this.biblioteca$revision) {
            this.biblioteca$revision = revision;
            this.biblioteca$looping = LoopingRegistry.isLooping(level);
        }

        return this.biblioteca$looping;
    }
}
