package babel.biblioteca.client.mixin;

import babel.biblioteca.client.LoopingSounds;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Folds local positional sounds before vanilla computes their distance-based playback delay. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSoundYLoopMixin {

    @ModifyVariable(
            method = "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double biblioteca$nearestSoundY(double y) {
        return LoopingSounds.nearestY(y);
    }
}
