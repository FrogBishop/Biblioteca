package biblioteca.periodicprobe.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** This probe only opens a disposable copy; accept its custom-dimension lifecycle warning. */
@Mixin(WorldOpenFlows.class)
public abstract class VisualWorldOpenObserver {
    @Inject(method = "askForBackup", at = @At("HEAD"), cancellable = true)
    private void probe(LevelStorageSource.LevelStorageAccess world, boolean customized, Runnable proceed, Runnable cancel, CallbackInfo ci) {
        if (world.getLevelId().equals("effects-world")) { proceed.run(); ci.cancel(); }
    }
}
