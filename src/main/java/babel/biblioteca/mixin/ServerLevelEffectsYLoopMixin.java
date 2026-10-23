package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamEffects;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
//? if >=1.19.3 {
import net.minecraft.core.Holder;
//?}
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
//? if >=1.21.11 {
import net.minecraft.world.level.gamerules.GameRules;
//?} else {
/*import net.minecraft.world.level.GameRules;
*///?}
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sends each listener the nearby periodic image of sounds, events and particles. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelEffectsYLoopMixin {
    @Inject(
            //? if >=1.21.5 {
            method = "levelEvent(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/core/BlockPos;I)V",
            //?} else {
            /*method = "levelEvent(Lnet/minecraft/world/entity/player/Player;ILnet/minecraft/core/BlockPos;I)V",
            *///?}
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$levelEventPerListener(
            //? if >=1.21.5 {
            Entity source,
            //?} else {
            /*Player source,
            *///?}
            int type, BlockPos pos, int data, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (SeamEffects.broadcastPerListener(level, biblioteca$player(source), pos.getX(), pos.getY(), pos.getZ(), 64.0,
                y -> new ClientboundLevelEventPacket(type, new BlockPos(pos.getX(), (int) Math.floor(y), pos.getZ()), data, false))) {
            ci.cancel();
        }
    }

    //? if >=1.19.3 {
    @Inject(
            //? if >=1.21.5 {
            method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            //?} else {
            /*method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            *///?}
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$soundPerListener(
            //? if >=1.21.5 {
            Entity except,
            //?} else {
            /*Player except,
            *///?}
            double x, double y, double z, Holder<SoundEvent> sound, SoundSource source,
            float volume, float pitch, long seed, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (SeamEffects.broadcastPerListener(level, biblioteca$player(except), x, y, z, sound.value().getRange(volume),
                row -> new ClientboundSoundPacket(sound, source, x, row, z, volume, pitch, seed))) ci.cancel();
    }

    @Inject(
            //? if >=1.21.5 {
            method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            //?} else {
            /*method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            *///?}
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$entitySoundAcrossBoundary(
            //? if >=1.21.5 {
            Entity except,
            //?} else {
            /*Player except,
            *///?}
            Entity sourceEntity, Holder<SoundEvent> sound, SoundSource source,
            float volume, float pitch, long seed, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!LoopingRegistry.isLooping(level)) return;
        ClientboundSoundEntityPacket packet = new ClientboundSoundEntityPacket(sound, source, sourceEntity, volume, pitch, seed);
        if (SeamEffects.broadcastPerListener(level, biblioteca$player(except), sourceEntity.getX(), sourceEntity.getY(),
                sourceEntity.getZ(), sound.value().getRange(volume), y -> packet)) ci.cancel();
    }

    //?}

    @Inject(method = "globalLevelEvent", at = @At("HEAD"), cancellable = true)
    private void biblioteca$globalEventAcrossBoundary(int type, BlockPos pos, int data, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!LoopingRegistry.isLooping(level)) return;
        //? if >=1.21.11 {
        boolean global = level.getGameRules().get(GameRules.GLOBAL_SOUND_EVENTS);
        //?} else if >=1.19.3 {
        /*boolean global = level.getGameRules().getBoolean(GameRules.RULE_GLOBAL_SOUND_EVENTS);
        *///?} else {
        /*boolean global = true;
        *///?}
        if (!global) return; // Vanilla routes this case through the periodic levelEvent hook.
        Vec3 center = Vec3.atCenterOf(pos);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            Vec3 source = player.position();
            if (Compat.level(player) == level) {
                source = new Vec3(center.x, SeamPositions.foldToward(center.y, player.getY(), Compat.height(level)), center.z);
                if (source.distanceToSqr(player.position()) >= 32.0 * 32.0) {
                    source = player.position().add(source.subtract(player.position()).normalize().scale(32.0));
                }
            }
            player.connection.send(new ClientboundLevelEventPacket(type, Compat.blockPos(source), data, true));
        }
        ci.cancel();
    }

    @ModifyVariable(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double biblioteca$targetedParticleY(double y, @Local(argsOnly = true) ServerPlayer player) {
        ServerLevel level = (ServerLevel) (Object) this;
        return LoopingRegistry.isLooping(level)
                ? SeamPositions.foldToward(y, player.blockPosition().getY() + 0.5, Compat.height(level)) : y;
    }

    @Inject(
            //? if >=26.3 {
            method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDIDDDDDDLnet/minecraft/network/protocol/game/ClientboundLevelParticlesPacket$RandomizationType;)I",
            //?} else if >=1.21.5 {
            /*method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDIDDDD)I",
            *///?} else {
            /*method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I",
            *///?}
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$particlesPerListener(ParticleOptions particle,
            //? if >=1.21.5 {
            boolean overrideLimiter, boolean alwaysShow,
            //?}
            double x, double y, double z, int count, double xDist, double yDist, double zDist,
            //? if >=26.3 {
            double xSpeed, double ySpeed, double zSpeed, ClientboundLevelParticlesPacket.RandomizationType randomizationType,
            //?} else {
            /*double speed,
            *///?}
            CallbackInfoReturnable<Integer> cir) {
        //? if <1.21.5 {
        /*boolean overrideLimiter = false;
        boolean alwaysShow = false;
        *///?}
        int sent = SeamEffects.broadcastParticles((ServerLevel) (Object) this, x, y, z,
                overrideLimiter ? 512.0 : 32.0, row -> {
                    //? if >=26.3 {
                    return new ClientboundLevelParticlesPacket(particle, overrideLimiter, alwaysShow, x, row, z,
                            (float) xDist, (float) yDist, (float) zDist, (float) xSpeed, (float) ySpeed, (float) zSpeed, count, randomizationType);
                    //?} else if >=1.21.4 {
                    /*return new ClientboundLevelParticlesPacket(particle, overrideLimiter, alwaysShow, x, row, z,
                            (float) xDist, (float) yDist, (float) zDist, (float) speed, count);
                    *///?} else {
                    /*return new ClientboundLevelParticlesPacket(particle, overrideLimiter, x, row, z,
                            (float) xDist, (float) yDist, (float) zDist, (float) speed, count);
                    *///?}
                });
        if (sent >= 0) cir.setReturnValue(sent);
    }

    private static Player biblioteca$player(Entity entity) {
        return entity instanceof Player player ? player : null;
    }
}
