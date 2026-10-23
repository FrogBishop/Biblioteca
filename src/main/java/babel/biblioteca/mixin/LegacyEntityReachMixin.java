package babel.biblioteca.mixin;

import babel.biblioteca.Compat;

import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.SeamPositions;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** 1.20.4 performs both attack and interaction reach validation directly in the packet handler. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class LegacyEntityReachMixin {
    @Shadow public ServerPlayer player;

    @WrapOperation(method = "handleInteract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/AABB;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double biblioteca$ringReach(AABB box, Vec3 eyes, Operation<Double> original) {
        return LoopingRegistry.isLooping(Compat.level(this.player))
                ? SeamPositions.distanceToSqr(Compat.level(this.player), eyes, box) : original.call(box, eyes);
    }
}
