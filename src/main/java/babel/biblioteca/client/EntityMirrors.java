package babel.biblioteca.client;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.function.Supplier;

/** Visual copies use native renderer policies and independent per-copy extraction state. */
public final class EntityMirrors {
    private static final class Context {
        Camera camera;
        Frustum frustum;
        boolean culling;
        double cullShift;
        Entity imageOwner;
        double imageShift;
    }
    private static final ThreadLocal<Context> CONTEXT = ThreadLocal.withInitial(Context::new);
    private EntityMirrors() {}

    public static boolean enabled(Level level) {
        return level != null && LoopingRegistry.isLooping(level) && LoopingClientSettings.entityMirrors();
    }
    public static void remember(Camera camera, Frustum frustum) {
        if (!CONTEXT.get().culling) { CONTEXT.get().camera = camera; CONTEXT.get().frustum = frustum; }
    }
    public static void clear() { CONTEXT.remove(); }
    public static Camera camera() { return CONTEXT.get().camera; }
    public static Frustum frustum() { return CONTEXT.get().frustum; }
    public static boolean isCulling() { return CONTEXT.get().culling; }
    public static double cullShift() { return CONTEXT.get().culling ? CONTEXT.get().cullShift : 0.0; }
    public static boolean isImage(Entity entity) { return CONTEXT.get().imageOwner == entity; }
    public static double imageShift() { return CONTEXT.get().imageShift; }

    public static <T> T image(Entity owner, double shift, Supplier<T> operation) {
        Context context = CONTEXT.get();
        Entity previous = context.imageOwner; double oldShift = context.imageShift;
        context.imageOwner = owner; context.imageShift = shift;
        try { return operation.get(); }
        finally { context.imageOwner = previous; context.imageShift = oldShift; }
    }

    public static long first(double sourceY, double cameraY, int period) {
        long nearest = Math.round((cameraY - sourceY) / period);
        return Math.max(nearest - 32, (long) Math.ceil((cameraY - ClientCompat.renderDistance() * 16.0 - sourceY) / period));
    }
    public static long last(double sourceY, double cameraY, int period) {
        long nearest = Math.round((cameraY - sourceY) / period);
        return Math.min(nearest + 32, (long) Math.floor((cameraY + ClientCompat.renderDistance() * 16.0 - sourceY) / period));
    }
    public static boolean hiddenCameraBody(Entity entity, Camera camera) {
        return camera != null && ClientCompat.cameraEntity(camera) == entity && !camera.isDetached();
    }

    public static boolean visible(EntityRenderDispatcher dispatcher, Entity entity, Frustum frustum,
                                  double cameraX, double cameraY, double cameraZ, float partial, double shift) {
        if (frustum == null) return true;
        Context context = CONTEXT.get();
        boolean previous = context.culling, twin = SeamCull.inTwinTest();
        double oldShift = context.cullShift;
        context.culling = true; context.cullShift = shift;
        if (!twin) SeamCull.enterTwinTest();
        try {
            // The source retains its normal periodic entity-distance policy. Only
            // the frustum box is translated to the particular displayed image.
            //? if >=26.3 {
            return dispatcher.shouldRender(entity, frustum, cameraX, cameraY, cameraZ, partial);
            //?} else {
            /*return dispatcher.shouldRender(entity, frustum, cameraX, cameraY, cameraZ);
            *///?}
        } finally {
            context.culling = previous; context.cullShift = oldShift;
            if (!twin) SeamCull.exitTwinTest();
        }
    }

    public static boolean anyVisible(EntityRenderDispatcher dispatcher, Entity entity, Frustum frustum,
                                     double x, double y, double z, float partial) {
        int period = Compat.height(Compat.level(entity));
        for (long image = first(entity.getY(), y, period), last = last(entity.getY(), y, period); image <= last; image++) {
            if (visible(dispatcher, entity, frustum, x, y, z, partial, image * (double) period)) return true;
        }
        return false;
    }

    public static double distanceToSqr(Entity entity, Vec3 camera) {
        double dx = entity.getX() - camera.x, dy = entity.getY() + imageShift() - camera.y, dz = entity.getZ() - camera.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
