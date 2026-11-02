package babel.biblioteca.client.mixin;

import babel.biblioteca.Compat;
import babel.biblioteca.client.ClientCompat;
import babel.biblioteca.client.EntityCopyState;
import babel.biblioteca.client.EntityMirrors;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
//? if >=26.2 {
import net.minecraft.client.renderer.extract.LevelExtractor;
//?}
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.LevelRenderState;
//?} else {
/*import net.minecraft.client.renderer.state.LevelRenderState;
*///?}
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import java.util.List;

//? if >=26.2 {
@Mixin(LevelExtractor.class)
//?} else {
/*@Mixin(LevelRenderer.class)
*///?}
public abstract class EntityMirrorExtractionMixin {
    @Unique private EntityRenderState biblioteca$omittedState;
    //? if >=26.2 {
    @Shadow @Final private LevelRenderer levelRenderer;
    //?} else {
    /*@Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;
    *///?}

    @ModifyExpressionValue(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
    private boolean biblioteca$includeCameraEntity(boolean detached) {
        return detached || EntityMirrors.enabled(Minecraft.getInstance().level);
    }

    @WrapOperation(method = "extractVisibleEntities", at = @At(value = "INVOKE",
            //? if >=26.2 {
            target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
            //?} else {
            /*target = "Lnet/minecraft/client/renderer/LevelRenderer;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"))
            *///?}
    private EntityRenderState biblioteca$extractImages(@Coerce Object owner, Entity entity, float partial,
            Operation<EntityRenderState> original, @Local(argsOnly = true) Camera camera,
            @Local(argsOnly = true) Frustum frustum, @Local(argsOnly = true) LevelRenderState output) {
        biblioteca$omittedState = null;
        EntityRenderState nearestState = original.call(owner, entity, partial);
        if (nearestState == null || !EntityMirrors.enabled(Compat.level(entity))) return nearestState;
        //? if >=26.2 {
        EntityRenderDispatcher dispatcher = levelRenderer.entityRenderDispatcher();
        //?} else {
        /*EntityRenderDispatcher dispatcher = entityRenderDispatcher;
        *///?}
        var eye = ClientCompat.position(camera);
        int period = Compat.height(Compat.level(entity));
        double sourceY = ((EntityCopyState) nearestState).biblioteca$sourceY();
        long nearest = Math.round((eye.y - sourceY) / period);
        boolean hideNearest = EntityMirrors.hiddenCameraBody(entity, camera);
        EntityRenderState first = null;
        long start = Math.min(nearest, EntityMirrors.first(sourceY, eye.y, period));
        long end = Math.max(nearest, EntityMirrors.last(sourceY, eye.y, period));
        for (long image = start; image <= end; image++) {
            if (image == nearest && hideNearest) continue;
            double shift = image * (double) period;
            if (image != nearest && !EntityMirrors.visible(dispatcher, entity, frustum, eye.x, eye.y, eye.z, partial, shift)) continue;
            EntityRenderState state = image == nearest ? nearestState
                    : EntityMirrors.image(entity, shift, () -> original.call(owner, entity, partial));
            if (first == null) first = state;
            else output.entityRenderStates.add(state);
        }
        // Some native versions inspect glow metadata after adding the state. Keep a valid
        // bookkeeping value when the first-person body is the only candidate, and omit its draw.
        if (first == null) {
            biblioteca$omittedState = nearestState;
            return nearestState;
        }
        return first;
    }

    @WrapOperation(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean biblioteca$omitHiddenCameraBody(List<Object> states, Object state, Operation<Boolean> original) {
        if (state == biblioteca$omittedState) { biblioteca$omittedState = null; return false; }
        return state != null && original.call(states, state);
    }
}
