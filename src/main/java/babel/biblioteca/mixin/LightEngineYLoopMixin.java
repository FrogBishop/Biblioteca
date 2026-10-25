package babel.biblioteca.mixin;

import babel.biblioteca.LoopingLight;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.LoopingSkyLight;
import babel.biblioteca.SkyLightMode;
import net.minecraft.core.SectionPos;
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
 * <p>Both queries use periodic rows. SkyLightEngineYLoopMixin supplies direct sky using the
 * selected exposure rule, then propagates it through the ring with vanilla attenuation and
 * face occlusion. The DISABLED sky-light mode masks that channel for every
 * consumer: terrain vertices, entities, spawning and canSeeSky.
 *
 * <p>A face is lit by the cell it faces into, so the top face of the topmost block samples the cell
 * above the stored world. Both light channels fold this query onto the opposite row.
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
        if (LoopingRegistry.skyLightMode(this.biblioteca$level) != SkyLightMode.DISABLED) return;
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

        return SeamPositions.foldBlockPos(this.biblioteca$level, pos);
    }

    @Inject(method = "hasLightWork", at = @At("RETURN"), cancellable = true)
    private void biblioteca$pendingSkyRebuild(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && this instanceof LoopingSkyLight sky && sky.biblioteca$hasSkyWork()) {
            cir.setReturnValue(true);
        }
    }

    //? if >=1.20 {
    @Inject(method = "runLightUpdates", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "runUpdates(IZZ)I", at = @At("HEAD"))
    *///?}
    private void biblioteca$rebuildSkyOnLightThread(CallbackInfoReturnable<Integer> cir) {
        if (this instanceof LoopingSkyLight sky) sky.biblioteca$prepareSkyUpdates();
    }

    @ModifyVariable(method = "updateSectionStatus", at = @At("HEAD"), argsOnly = true)
    private boolean biblioteca$keepPeriodicSkySections(boolean empty, SectionPos section) {
        return this instanceof LoopingSkyLight sky ? sky.biblioteca$skySectionEmpty(section, empty) : empty;
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
