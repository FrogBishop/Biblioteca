package babel.biblioteca.mixin;

import babel.biblioteca.SeamEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;

/**
 * Pre-1.21.5 effect folding: these releases identify the source as a Player and
 * their particle packet has no alwaysShow/randomization fields.
 */
@Mixin(ServerLevel.class)
public abstract class LegacyServerLevelEffectsYLoopMixin {

    @Inject(method = "levelEvent(Lnet/minecraft/world/entity/player/Player;ILnet/minecraft/core/BlockPos;I)V",
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$levelEventPerListener(
            Player source, int type, BlockPos pos, int data, CallbackInfo ci) {

        ServerLevel self = (ServerLevel) (Object) this;
        boolean handled = SeamEffects.broadcastPerListener(
                self, source, pos.getX(), pos.getY(), pos.getZ(), 64.0,
                foldedY -> new ClientboundLevelEventPacket(
                        type,
                        new BlockPos(pos.getX(), (int) Math.floor(foldedY), pos.getZ()),
                        data,
                        false));

        if (handled) ci.cancel();
    }

    @Inject(
            method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;"
                    + "Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$soundPerListener(
            Player except, double x, double y, double z, Holder<SoundEvent> sound,
            SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {

        ServerLevel self = (ServerLevel) (Object) this;
        boolean handled = SeamEffects.broadcastPerListener(
                self, except, x, y, z, sound.value().getRange(volume),
                foldedY -> new ClientboundSoundPacket(sound, source, x, foldedY, z, volume, pitch, seed));

        if (handled) ci.cancel();
    }

    @Inject(
            method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I",
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$particlesPerListener(
            ParticleOptions particle, double x, double y, double z, int count,
            double xDist, double yDist, double zDist, double speed,
            CallbackInfoReturnable<Integer> cir) {

        int sent = SeamEffects.broadcastParticles(
                (ServerLevel) (Object) this, x, y, z, 32.0,
                foldedY -> biblioteca$particlePacket(
                        particle, x, foldedY, z,
                        (float) xDist, (float) yDist, (float) zDist, (float) speed, count));

        if (sent >= 0) cir.setReturnValue(sent);
    }

    /**
     * 1.21.4 inserted an alwaysShow flag into the packet constructor, while
     * 1.21.2/1.21.3 still use the ten-argument form. The grouped jar spans both,
     * so pick the constructor that exists at runtime.
     */
    private static Packet<?> biblioteca$particlePacket(
            ParticleOptions particle, double x, double y, double z,
            float xDist, float yDist, float zDist, float speed, int count) {
        try {
            for (Constructor<?> constructor : ClientboundLevelParticlesPacket.class.getConstructors()) {
                if (constructor.getParameterCount() == 11) {
                    return (Packet<?>) constructor.newInstance(
                            particle, false, false, x, y, z, xDist, yDist, zDist, speed, count);
                }
                if (constructor.getParameterCount() == 10) {
                    return (Packet<?>) constructor.newInstance(
                            particle, false, x, y, z, xDist, yDist, zDist, speed, count);
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot build a particle packet", exception);
        }
        throw new IllegalStateException("No ClientboundLevelParticlesPacket constructor found");
    }
}