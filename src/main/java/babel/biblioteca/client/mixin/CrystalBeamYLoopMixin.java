package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Metadata remains canonical; choose the beam endpoint beside its rendered source. */
@Mixin(EndCrystalRenderer.class)
public abstract class CrystalBeamYLoopMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;getBeamTarget()Lnet/minecraft/core/BlockPos;"), expect = 2)
    private BlockPos biblioteca$beamEndpoint(EndCrystal crystal, Operation<BlockPos> original) {
        BlockPos target = original.call(crystal);
        var level = Compat.level(crystal);
        if (target == null || !LoopingRegistry.isLooping(level)) return target;
        int period = Compat.height(level);
        long image = SeamPositions.layer(target.getY() + 0.5, crystal.getY(), period);
        return image == 0 ? target : target.offset(0, (int) (image * period), 0);
    }
}
