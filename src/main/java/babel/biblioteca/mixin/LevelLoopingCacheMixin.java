package babel.biblioteca.mixin;

import babel.biblioteca.LoopingLevel;
import babel.biblioteca.LoopingRegistry;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Caches the per-dimension "does the Y axis loop" answer on the level itself.
 *
 * LoopingRegistry answered this with a set lookup on a ResourceKey, and LevelBlockAccessMixin asks
 * it on every single block state access. The server makes hundreds of thousands of those per tick,
 * so the lookup alone was a measurable share of the tick - it is the reason server ticks in a
 * looping world were running at 50 to 250 ms, which the seam trace caught.
 *
 * The cache is a single volatile reference to an immutable pair (revision, answer). Reading it is
 * a reference read plus a compare, and a stale pair can never be observed: the reference is
 * replaced atomically, so the revision and the answer always belong to each other. Whenever an
 * attribute changes, the revision moves and the next reader recomputes.
 */
@Mixin(Level.class)
public abstract class LevelLoopingCacheMixin implements LoopingLevel {

    @Unique private volatile LoopingLevel.Snapshot biblioteca$loopingSnapshot;

    @Override
    public boolean biblioteca$isLooping() {
        int revision = LoopingRegistry.revision();

        LoopingLevel.Snapshot snapshot = this.biblioteca$loopingSnapshot;
        if (snapshot != null && snapshot.revision() == revision) {
            return snapshot.looping();
        }

        boolean looping = LoopingRegistry.isLoopingUncached((Level) (Object) this);
        this.biblioteca$loopingSnapshot = new LoopingLevel.Snapshot(revision, looping);
        return looping;
    }
}
