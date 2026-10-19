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
 * 1.21.5 - 26.2 effect folding: the source is an Entity and the particle packet
 * has alwaysShow, but not yet the 26.3 randomization fields.
 */
@Mixin(ServerLevel.class)
public abstract class MidServerLevelEffectsYLoopMixin {

    @Inject(method = "levelEvent(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/core/BlockPos;I)V",
            at = @At("HEAD"), cancellable = true)
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

    @Inject(
            method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDIDDDD)I",
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$particlesPerListener(
            ParticleOptions particle, boolean overrideLimiter, boolean alwaysShow,
            double x, double y, double z, int count,
            double xDist, double yDist, double zDist, double speed,
            CallbackInfoReturnable<Integer> cir) {

        int sent = SeamEffects.broadcastParticles(
                (ServerLevel) (Object) this, x, y, z, overrideLimiter ? 512.0 : 32.0,
                foldedY -> new ClientboundLevelParticlesPacket(
                        particle, overrideLimiter, alwaysShow, x, foldedY, z,
                        (float) xDist, (float) yDist, (float) zDist, (float) speed, count));

        if (sent >= 0) cir.setReturnValue(sent);
    }
}
