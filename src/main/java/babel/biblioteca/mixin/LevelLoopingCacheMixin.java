package babel.biblioteca.mixin;

import babel.biblioteca.LoopingLevel;
import babel.biblioteca.LoopingRegistry;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Caches the per-dimension "does the Y axis loop" answer on the level itself.
 *
 * Block access, collision and rendering paths ask this frequently. A per-level cache avoids
 * repeatedly querying the dimension set until its revision changes.
 *
 * The cache is a single volatile reference to an immutable pair (revision, answer). Reading it is
 * a reference read plus a compare. Replacing the pair atomically keeps its revision and answer
 * together; observing a newer attribute revision causes the next lookup to recompute.
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
