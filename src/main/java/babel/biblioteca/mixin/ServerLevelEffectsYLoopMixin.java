package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamEffects;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
 * position depends on where the listener stands. Entity-bound sound packets also need a periodic
 * broadcast radius even though they carry an entity ID; the client's sound engine then follows
 * that entity's nearest copy. Global events keep their vanilla audience and 32-block placement,
 * but choose the direction toward the nearest periodic copy of the event.
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

    /** An entity ID does not make the server's broadcast-distance check periodic by itself. */
    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$entitySoundAcrossBoundary(
            Entity except, Entity sourceEntity, Holder<SoundEvent> sound, SoundSource source,
            float volume, float pitch, long seed, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!LoopingRegistry.isLooping(level)) return;
        ClientboundSoundEntityPacket packet =
                new ClientboundSoundEntityPacket(sound, source, sourceEntity, volume, pitch, seed);
        boolean handled = SeamEffects.broadcastPerListener(level, except instanceof Player player ? player : null,
                sourceEntity.getX(), sourceEntity.getY(), sourceEntity.getZ(), sound.value().getRange(volume),
                y -> packet);
        if (handled) ci.cancel();
    }

    @WrapOperation(method = "lambda$globalLevelEvent$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 biblioteca$nearestGlobalEvent(
            Vec3i pos, Operation<Vec3> original,
            @Local(argsOnly = true) ServerPlayer player) {
        Vec3 source = original.call(pos);
        ServerLevel level = (ServerLevel) (Object) this;
        if (!LoopingRegistry.isLooping(level)) return source;
        return new Vec3(source.x, SeamPositions.foldToward(source.y, player.getY(), level.getHeight()), source.z);
    }

    /** The client folds particle positions; the single-recipient server range check must agree. */
    @ModifyVariable(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDD"
            + "Lnet/minecraft/network/protocol/Packet;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double biblioteca$targetedParticleY(double y, @Local(argsOnly = true) ServerPlayer player) {
        ServerLevel level = (ServerLevel) (Object) this;
        return LoopingRegistry.isLooping(level)
                ? SeamPositions.foldToward(y, player.blockPosition().getY() + 0.5, level.getHeight()) : y;
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
                (ServerLevel) (Object) this, particle, overrideLimiter, alwaysShow,
                x, y, z, count, xDist, yDist, zDist, xSpeed, ySpeed, zSpeed, randomizationType);

        if (sent >= 0) cir.setReturnValue(sent);
    }
}
