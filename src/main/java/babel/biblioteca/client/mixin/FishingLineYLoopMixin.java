package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Fold the endpoint difference before the native curve and its normals are evaluated. */
@Mixin(FishingHookRenderer.class)
public abstract class FishingLineYLoopMixin {
    @ModifyVariable(method = "stringVertex", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static float biblioteca$shortLine(float difference) {
        var level = Minecraft.getInstance().level;
        return level != null && LoopingRegistry.isLooping(level)
                ? (float) SeamPositions.foldToward(difference, 0, Compat.height(level)) : difference;
    }
}
