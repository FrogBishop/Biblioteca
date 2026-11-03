package babel.biblioteca.mixin;

import babel.biblioteca.SeamMovement;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/** Move both endpoints together when their owning entity changes periodic coordinate frame. */
@Mixin(targets = "net.minecraft.world.entity.Entity$Movement")
public abstract class EntityMovementRecordMixin implements SeamMovement {
    @Shadow @Final @Mutable private Vec3 from;
    @Shadow @Final @Mutable private Vec3 to;

    public void biblioteca$shiftY(double offset) {
        from = from.add(0.0, offset, 0.0);
        to = to.add(0.0, offset, 0.0);
    }
    public double biblioteca$toY() { return to.y; }
}
