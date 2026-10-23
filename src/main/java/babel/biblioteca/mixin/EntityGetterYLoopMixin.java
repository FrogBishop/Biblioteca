package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamEntityCollisions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes entities collide across the seam of a Y-looping dimension.
 *
 * Entity collision is a plain bounding box query: EntityGetter.getEntityCollisions() asks
 * the level for the entities whose boxes intersect the query box and reports their shapes
 * as they are. Two entities standing on opposite sides of the seam are 384 blocks apart in
 * absolute terms, so neither ever sees the other and they pass through each other even
 * though the ring puts them shoulder to shoulder.
 *
 * Any query that comes within reach of a seam is therefore repeated one period away, and
 * the shapes found there are moved back into the query's own space. Reach is generous
 * because an entity's box can itself poke past the seam (a 1.8 block tall entity standing
 * at maxY extends above it, which in the ring is below minY).
 *
 * Both directions are handled: the bottom row looks up at the top row and the top row
 * looks down at the bottom row. Queries that stay away from both rows pay one comparison
 * and no extra work.
 */
@Mixin(EntityGetter.class)
public interface EntityGetterYLoopMixin {

    @Inject(method = "getEntityCollisions", at = @At("RETURN"), cancellable = true)
    private void biblioteca$addSeamCollisions(
            Entity source, AABB testArea,
//? if >=1.18 {
            CallbackInfoReturnable<List<VoxelShape>> cir
//?} else {
/*            java.util.function.Predicate<Entity> selector, CallbackInfoReturnable<java.util.stream.Stream<VoxelShape>> cir
*///?}
            ) {

        if (!(this instanceof Level level) || !LoopingRegistry.isLooping(level)) return;

        //? if >=1.18 {
        java.util.function.Predicate<Entity> extraFilter = entity -> true;
        //?} else {
        /*java.util.function.Predicate<Entity> extraFilter = selector;
        *///?}

        int minY = Compat.minY(level);
        int height = Compat.height(level);
        double reach = SeamEntityCollisions.REACH;

        List<VoxelShape> extra = null;

        if (testArea.minY < minY + reach) {
            extra = new ArrayList<>(4);
            SeamEntityCollisions.collect(level, source, testArea, height, extra, extraFilter);
        }
        if (testArea.maxY > minY + height - reach) {
            if (extra == null) extra = new ArrayList<>(4);
            SeamEntityCollisions.collect(level, source, testArea, -height, extra, extraFilter);
        }

        if (extra == null || extra.isEmpty()) return;

//? if >=1.18 {
        List<VoxelShape> merged = new ArrayList<>(cir.getReturnValue().size() + extra.size());
        merged.addAll(cir.getReturnValue());
        merged.addAll(extra);
        cir.setReturnValue(merged);
//?} else {
/*        cir.setReturnValue(java.util.stream.Stream.concat(cir.getReturnValue(), extra.stream()));
*///?}

    }
}
