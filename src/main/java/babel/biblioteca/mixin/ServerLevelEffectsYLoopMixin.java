package babel.biblioteca.mixin;

import babel.biblioteca.SeamEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rebuilds the effect broadcasts so each listener receives the copy of the position nearest to it.
 *
 * Breaking or placing a block on the far side of the seam produced neither sound nor particles:
 * the level event (which is what spawns break particles and plays the break sound on the client)
 * and the place sound are broadcast around a coordinate, and that coordinate travels inside the
 * packet. A block one row across the seam is a whole world height away by that measure, so the
 * radius check dropped it - and had it been sent, the client would have played it out of earshot.
 *
 * Vanilla sends one packet to everyone, which cannot be correct here: the right copy of the
 * position depends on where the listener stands. Both funnels therefore loop over the listeners and
 * build a packet each, with the same radius rule vanilla uses. The two other sound overloads need
 * nothing: the entity-relative one takes its coordinates from an entity, whose position is always
 * inside the world, and globalLevelEvent is meant for every player in the dimension regardless of
 * distance.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelEffectsYLoopMixin {

    /** Block break and place particles, plus the break sound. Vanilla radius: 64 blocks. */
    @Inject(method = "levelEvent", at = @At("HEAD"), cancellable = true)
    private void biblioteca$levelEventPerListener(
            Entity source, int type, BlockPos pos, int data, CallbackInfo ci) {

        ServerLevel self = (ServerLevel) (Object) this;
        Player except = source instanceof Player player ? player : null;

        boolean handled = SeamEffects.broadcastPerListener(
                self, except, pos.getX(), pos.getY(), pos.getZ(), 64.0,
                foldedY -> new ClientboundLevelEventPacket(
                        type,
                        new BlockPos(pos.getX(), (int) Math.floor(foldedY), pos.getZ()),
                        data,
                        false));

        if (handled) ci.cancel();
    }

    /** Positional sounds: place sounds, block sounds, entity sounds routed through the level. */
    @Inject(
            method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;"
                    + "Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$soundPerListener(
            Entity except, double x, double y, double z, Holder<SoundEvent> sound,
            SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {

        ServerLevel self = (ServerLevel) (Object) this;
        Player exceptPlayer = except instanceof Player player ? player : null;

        boolean handled = SeamEffects.broadcastPerListener(
                self, exceptPlayer, x, y, z, sound.value().getRange(volume),
                foldedY -> new ClientboundSoundPacket(sound, source, x, foldedY, z, volume, pitch, seed));

        if (handled) ci.cancel();
    }

    /**
     * The particle API. Vanilla builds one packet and hands it to every listener within 32 blocks
     * (512 with the limiter overridden), which drops effects on the far side of the seam entirely.
     */
    @Inject(
            method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDIDDDDDD"
                    + "Lnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket$RandomizationType;)I",
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$particlesPerListener(
            ParticleOptions particle, boolean overrideLimiter, boolean alwaysShow,
            double x, double y, double z, int count,
            double xDist, double yDist, double zDist,
            double xSpeed, double ySpeed, double zSpeed,
            ClientboundLevelParticlesPacket.RandomizationType randomizationType,
            CallbackInfoReturnable<Integer> cir) {

        int sent = SeamEffects.broadcastParticles(
                (ServerLevel) (Object) this, x, y, z, overrideLimiter ? 512.0 : 32.0,
                foldedY -> new ClientboundLevelParticlesPacket(
                        particle, overrideLimiter, alwaysShow, x, foldedY, z,
                        (float) xDist, (float) yDist, (float) zDist,
                        (float) xSpeed, (float) ySpeed, (float) zSpeed,
                        count, randomizationType));

        if (sent >= 0) cir.setReturnValue(sent);
    }
}