package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.GuardianRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The source and target are canonical entity positions, even for a distant visual image. */
@Mixin(GuardianRenderer.class)
public abstract class GuardianBeamYLoopMixin {
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$shortBeam(Vec3 target, Vec3 source, Operation<Vec3> original) {
        var level = Minecraft.getInstance().level;
        if (level != null && LoopingRegistry.isLooping(level)) target = SeamPositions.foldToward(level, target, source.y);
        return original.call(target, source);
    }
}
