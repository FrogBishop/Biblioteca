package babel.biblioteca.client.mixin;

import babel.biblioteca.SeamPositions;
import babel.biblioteca.client.LoopingSounds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Keeps positional audio beside the listener, including moving and already-playing sounds. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineYLoopMixin {

    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
    @Shadow private boolean loaded;
    @Unique private double biblioteca$previousListenerY;
    @Unique private int biblioteca$previousPeriod;

    @WrapOperation(method = "play", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getY()D"))
    private double biblioteca$initialSoundY(SoundInstance instance, Operation<Double> original) {
        double y = original.call(instance);
        return instance.isRelative() ? y : LoopingSounds.nearestY(y);
    }

    @WrapOperation(method = "tickInGameSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/sounds/TickableSoundInstance;getY()D"))
    private double biblioteca$movingSoundY(TickableSoundInstance instance, Operation<Double> original) {
        double y = original.call(instance);
        return instance.isRelative() ? y : LoopingSounds.nearestY(y);
    }

    @Inject(method = "updateSource", at = @At("TAIL"))
    private void biblioteca$movePlayingSoundsWithListener(Camera camera, CallbackInfo ci) {
        if (!this.loaded || !camera.isInitialized()) return;
        int period = LoopingSounds.period();
        double listenerY = camera.position().y;

        // Static sounds otherwise retain the position chosen when playback began. A boundary
        // crossing must move those channels too; tickable sounds are already handled above.
        if (period > 0 || this.biblioteca$previousPeriod > 0) {
            for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : this.instanceToChannel.entrySet()) {
                SoundInstance instance = entry.getKey();
                if (instance.isRelative()) continue;
                double y = instance.getY();
                double previousY = this.biblioteca$previousPeriod > 0
                        ? SeamPositions.foldToward(y, this.biblioteca$previousListenerY, this.biblioteca$previousPeriod) : y;
                double currentY = period > 0 ? SeamPositions.foldToward(y, listenerY, period) : y;
                if (currentY == previousY) continue;

                Vec3 position = new Vec3(instance.getX(), currentY, instance.getZ());
                entry.getValue().execute(channel -> channel.setSelfPosition(position));
            }
        }

        this.biblioteca$previousListenerY = listenerY;
        this.biblioteca$previousPeriod = period;
    }
}
