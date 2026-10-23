package babel.biblioteca.mixin;

import babel.biblioteca.SeamDecorations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item frames own their box recalculation before 1.21 and from 1.21.9 onward. */
@Mixin(ItemFrame.class)
public abstract class ItemFrameYLoopMixin {
    @Inject(method = "recalculateBoundingBox", at = @At("RETURN"))
    private void biblioteca$foldFrame(CallbackInfo ci) {
        SeamDecorations.fold((Entity) (Object) this);
    }
}
