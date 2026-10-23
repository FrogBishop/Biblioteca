package babel.biblioteca.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamEffects;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
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

/** Positional and attached sounds before SoundEvent packets used registry holders. */
@Mixin(ServerLevel.class)
public abstract class LegacyServerSoundsMixin {
    @Inject(
            //? if >=1.19 {
            method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            //?} else {
            /*method = "playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
            *///?}
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$positional(Player except, double x, double y, double z, SoundEvent sound,
            SoundSource source, float volume, float pitch,
            //? if >=1.19 {
            long seed,
            //?}
            CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (SeamEffects.broadcastPerListener(level, except, x, y, z, 16.0 * Math.max(1.0f, volume), row -> {
            //? if >=1.19 {
            return new ClientboundSoundPacket(sound, source, x, row, z, volume, pitch, seed);
            //?} else {
            /*return new ClientboundSoundPacket(sound, source, x, row, z, volume, pitch);
            *///?}
        })) ci.cancel();
    }

    @Inject(
            //? if >=1.19 {
            method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            //?} else {
            /*method = "playSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
            *///?}
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$attached(Player except, Entity entity, SoundEvent sound,
            SoundSource source, float volume, float pitch,
            //? if >=1.19 {
            long seed,
            //?}
            CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!LoopingRegistry.isLooping(level)) return;
        //? if >=1.19 {
        ClientboundSoundEntityPacket packet = new ClientboundSoundEntityPacket(sound, source, entity, volume, pitch, seed);
        //?} else {
        /*ClientboundSoundEntityPacket packet = new ClientboundSoundEntityPacket(sound, source, entity, volume, pitch);
        *///?}
        if (SeamEffects.broadcastPerListener(level, except, entity.getX(), entity.getY(), entity.getZ(),
                16.0 * Math.max(1.0f, volume), row -> packet)) ci.cancel();
    }
}
