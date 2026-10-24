package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WitherBoss.class)
public abstract class WitherAimYLoopMixin {
    @WrapOperation(method = "performRangedAttack(IDDDZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;normalize()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$nearSkullDirection(Vec3 direction, Operation<Vec3> original) {
        Level level = ((WitherBoss)(Object)this).level();
        return original.call(LoopingRegistry.isLooping(level) ? SeamPositions.foldToward(level, direction, 0) : direction);
    }
}
