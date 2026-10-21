package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.ClientCompat;
import babel.biblioteca.client.LoopingRainImpacts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
//? if >=1.21.2 {
import net.minecraft.server.level.ParticleStatus;
//?} else {
/*import net.minecraft.client.ParticleStatus;
*///?}
//? if >=1.21.2 && <26.2 {
/*import net.minecraft.client.renderer.WeatherEffectRenderer;
*///?} else if <1.21.2 {
/*import net.minecraft.client.renderer.LevelRenderer;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves the impact generator with its owner: LevelRenderer, WeatherEffectRenderer, then ClientLevel. */
//? if >=26.2 {
@Mixin(ClientLevel.class)
//?} else if >=1.21.2 {
/*@Mixin(WeatherEffectRenderer.class)
*///?} else {
/*@Mixin(LevelRenderer.class)
*///?}
public abstract class RainImpactsYLoopMixin {
    @Shadow private int rainSoundTime;
    //? if <1.21.2 {
    /*@Shadow private int ticks;
    *///?}

    //? if >=26.2 {
    @Inject(method = "tickWeatherEffects", at = @At("HEAD"), cancellable = true)
    private void biblioteca$impacts(CallbackInfo ci) {
        ClientLevel level = (ClientLevel) (Object) this;
        long ticks = level.getGameTime();
        Camera camera = ClientCompat.camera();
        ParticleStatus particles = Minecraft.getInstance().options.particles().get();
        int radius = Minecraft.getInstance().options.weatherRadius().get();
    //?} else if >=1.21.2 {
    /*@Inject(method = "tickRainParticles", at = @At("HEAD"), cancellable = true)
    private void biblioteca$impacts(ClientLevel level, Camera camera, int ticks, ParticleStatus particles,
            //? if >=1.21.11 {
            int radius,
            //?}
            CallbackInfo ci) {
        //? if <1.21.11 {
        /^int radius = 10;
        ^///?}
    *///?} else {
    /*@Inject(method = "tickRain", at = @At("HEAD"), cancellable = true)
    private void biblioteca$impacts(Camera camera, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        ParticleStatus particles = Minecraft.getInstance().options.particles().get();
        int radius = 10;
    *///?}
        if (level == null || !LoopingRegistry.isLooping(level)) return;
        ci.cancel();
        float rain = level.getRainLevel(1.0f);
        //? if >=1.21.11 {
        int attempts = (int) (0.225f * (radius * 2 + 1) * (radius * 2 + 1) * rain * rain);
        //?} else {
        /*rain /= Minecraft.useFancyGraphics() ? 1.0f : 2.0f;
        int attempts = (int) (100.0f * rain * rain);
        *///?}
        if (particles == ParticleStatus.DECREASED) attempts /= 2;
        rainSoundTime = LoopingRainImpacts.tick(level, ClientCompat.position(camera), ticks, particles, radius, rain, attempts, rainSoundTime);
    }
}
