package babel.biblioteca.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamEntityCollisions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import org.spongepowered.asm.mixin.Mixin;

/** The original loader cannot inject into interface defaults; override at the concrete world. */
@Mixin(Level.class)
public abstract class NeoForge1201EntityCollisionsMixin implements EntityGetter {
    @Override public List<VoxelShape> getEntityCollisions(Entity source, AABB area) {
        // Mixin 0.8.5 also rewrites interface-super invocations incorrectly. Preserve the
        // native query and immutable result here, then append the shared periodic shapes.
        List<VoxelShape> original = List.of();
        if (area.getSize() >= 1.0e-7) {
            var predicate = source == null ? EntitySelector.CAN_BE_COLLIDED_WITH : EntitySelector.NO_SPECTATORS.and(source::canCollideWith);
            original = getEntities(source, area.inflate(1.0e-7), predicate).stream().map(entity -> Shapes.create(entity.getBoundingBox())).toList();
        }
        Level level = (Level) (Object) this;
        if (!LoopingRegistry.isLooping(level)) return original;
        int min = Compat.minY(level), period = Compat.height(level);
        List<VoxelShape> extra = new ArrayList<>(4);
        if (area.minY < min + SeamEntityCollisions.REACH)
            SeamEntityCollisions.collect(level, source, area, period, extra, entity -> true);
        if (area.maxY > min + period - SeamEntityCollisions.REACH)
            SeamEntityCollisions.collect(level, source, area, -period, extra, entity -> true);
        if (extra.isEmpty()) return original;
        List<VoxelShape> merged = new ArrayList<>(original.size() + extra.size());
        merged.addAll(original); merged.addAll(extra);
        return merged;
    }
}
