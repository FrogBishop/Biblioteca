package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

//? if >=1.21 {
@Mixin(EntityRenderer.class)
//?} else {
/*@Mixin(MobRenderer.class)
*///?}
public abstract class LegacyLeashRenderYLoopMixin {
    @ModifyVariable(method = "renderLeash", at = @At("STORE"), ordinal = 2)
    //? if >=1.21 {
    private float biblioteca$nearLeashDelta(float deltaY, @Local(argsOnly = true, ordinal = 0) Entity mob) {
    //?} else {
    /*private float biblioteca$nearLeashDelta(float deltaY, @Local(argsOnly = true) Mob mob) {
    *///?}
        return LoopingRegistry.isLooping(Compat.level(mob))
                ? (float)SeamPositions.foldForDistance(Compat.level(mob), deltaY, 0) : deltaY;
    }
}
