package babel.biblioteca.mixin;

import babel.biblioteca.LoopingLight;
import babel.biblioteca.LoopingRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
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
 * Removes sky light in a Y-looping dimension.
 *
 * A ring has no sky, but sky light is not a property of the sky in this code - it is a light
 * channel that starts at 15 at the top of the world and falls off as it is blocked on the way
 * down. So the top rows end up at 15 and the bottom rows, which are 384 blocks of rock away from
 * the sky, end up at 0. While the terrain was cut in half at the seam that was invisible; now that
 * the far side is drawn overhead, the two halves are lit by completely different amounts of sky
 * light and meet in a hard bright-over-dark line.
 *
 * Since nothing in a ring can see the sky, the honest answer is that the channel is always zero.
 * Doing it here, in the single method every sky-light query funnels through, means the baked
 * section vertices, entity and block entity lighting, mob spawning and canSeeSky all agree, with
 * no per-caller special cases.
 *
 * The sky_light attribute can keep the vanilla channel for a dimension that wants it; it is read
 * at this funnel so the baked section vertices, entity and block entity lighting, mob spawning
 * and canSeeSky all follow the same switch.
 *
 * Block light is left alone: it is the only light a looping dimension has, and
 * BlockLightEngineYLoopMixin makes it wrap around the seam. Its queries are folded instead, so a
 * lookup just outside the world answers with the row the ring puts there.
 */
@Mixin(LightEngine.class)
public abstract class LightEngineYLoopMixin {

    @Shadow @Final protected LightChunkGetter chunkSource;

    @Unique private Level biblioteca$level;
    @Unique private int biblioteca$revision = -1;
    @Unique private boolean biblioteca$looping;

    @Inject(method = "getLightValue", at = @At("HEAD"), cancellable = true)
    private void biblioteca$noSkyLight(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof SkyLightEngine)) return;
        if (!biblioteca$looping()) return;
        // The sky_light attribute describes the vanilla channel: on means keep it (with the
        // vanilla bright-top/dark-bottom split), off (the default) keeps the ring uniform.
        if (LoopingRegistry.keepsSkyLight(this.biblioteca$level)) return;
        cir.setReturnValue(0);
    }

    /**
     * Answers a query that falls outside the world with the light of the row the ring puts there.
     *
     * A face is lit by the cell it faces into, so the top face of the topmost block samples the
     * cell above the world - and the bottom face of the bottom block samples the cell below it.
     * Those cells are real rows of a ring, and leaving the query to fall off the world is what
     * kept the outermost faces dark while the terrain behind them moved across the seam.
     *
     * The same fold fixes the player's own model: the light probe is taken at the eye, and a
     * player standing on the top row has their eyes past the top of the world, in the row the
     * ring puts the bottom of it in.
     */
    @ModifyVariable(method = "getLightValue", at = @At("HEAD"), argsOnly = true)
    private BlockPos biblioteca$foldQueriedPosition(BlockPos pos) {
        if (!biblioteca$looping()) return pos;
        if ((Object) this instanceof SkyLightEngine) return pos;

        return LoopingLight.foldBlockPos(pos, this.biblioteca$level);
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
