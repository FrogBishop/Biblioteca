package babel.biblioteca.client.mixin;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.ClientCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
//? if >=1.21.5 {
import net.minecraft.world.level.block.entity.BeaconBeamOwner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders the scanned beam length on each visible period, without vanilla's unbounded last segment. */
@Mixin(BeaconRenderer.class)
public abstract class LegacyBeaconBeamYLoopMixin {
    @Inject(
            //? if >=1.21.5 {
            method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/world/phys/Vec3;)V",
            //?} else {
            /*method = "render(Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            *///?}
            at = @At("HEAD"), cancellable = true)
    private void biblioteca$beam(
            //? if >=1.21.5 {
            BlockEntity entity,
            //?} else {
            /*BeaconBlockEntity entity,
            *///?}
            float partialTicks, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
            //? if >=1.21.5 {
            Vec3 camera,
            //?}
            CallbackInfo ci) {
        var level = entity.getLevel();
        if (level == null || !LoopingRegistry.isLooping(level)) return;
        ci.cancel();
        //? if >=1.21.5 {
        var sections = ((BeaconBeamOwner) entity).getBeamSections();
        Minecraft minecraft = Minecraft.getInstance();
        float horizontalDistance = (float) camera.subtract(entity.getBlockPos().getCenter()).horizontalDistance();
        float radiusScale = minecraft.player != null && minecraft.player.isScoping() ? 1 : Math.max(1, horizontalDistance / 96);
        //?} else {
        /*var sections = entity.getBeamSections();
        Minecraft minecraft = Minecraft.getInstance();
        float radiusScale = 1;
        *///?}
        int height = 0;
        for (var section : sections) height += section.getHeight();
        int period = level.getHeight();
        if (height <= 0 || period <= 0) return;
        double cameraY = ClientCompat.cameraPosition().y;
        double reach = minecraft.options.getEffectiveRenderDistance() * 16.0;
        int origin = entity.getBlockPos().getY();
        int lowest = (int) Math.floor((cameraY - reach - origin - height) / period);
        int highest = (int) Math.ceil((cameraY + reach - origin) / period);
        lowest = Math.max(lowest, highest - 8);
        for (int copy = lowest; copy <= highest; copy++) {
            pose.pushPose();
            pose.translate(0, (double) copy * period, 0);
            int start = 0;
            for (var section : sections) {
                BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTicks, 1.0f,
                        level.getGameTime(), start, section.getHeight(), section.getColor(), 0.2f * radiusScale, 0.25f * radiusScale);
                start += section.getHeight();
            }
            pose.popPose();
        }
    }
}
