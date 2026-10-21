package babel.biblioteca.client;

import babel.biblioteca.client.mixin.FrustumInvoker;
import net.minecraft.client.renderer.culling.Frustum;

/**
 * Plain frustum tests for code that must not be answered by a section's periodic twin.
 *
 * FrustumYLoopMixin makes the frustum answer for a box's ring twin as well as for the box, which
 * is what keeps the far side of the boundary in the visible set. Copy discovery and draw submission
 * need to test each copy's actual box instead. The ring behaviour is switched off for the duration of
 * the call through a thread local that FrustumYLoopMixin honours, which also keeps the two from
 * recursing into each other.
 */
public final class SeamCull {

    private static final ThreadLocal<Boolean> IN_TWIN_TEST = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private SeamCull() {}

    public static boolean inTwinTest() {
        return IN_TWIN_TEST.get();
    }

    public static void enterTwinTest() {
        IN_TWIN_TEST.set(Boolean.TRUE);
    }

    public static void exitTwinTest() {
        IN_TWIN_TEST.set(Boolean.FALSE);
    }

    /**
     * Tests copy bounds without allocating an AABB. Negative frustum results mean visible;
     * nonnegative results identify the plane that rejected the box.
     */
    public static boolean plainlyVisible(Frustum frustum,
                                         double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ) {
        if (IN_TWIN_TEST.get()) {
            // Already inside a twin test; asking again must not re-enter the ring logic.
            return ((FrustumInvoker) frustum).biblioteca$cubeInFrustum(
                    minX, minY, minZ, maxX, maxY, maxZ) < 0;
        }

        IN_TWIN_TEST.set(Boolean.TRUE);
        try {
            return ((FrustumInvoker) frustum).biblioteca$cubeInFrustum(
                    minX, minY, minZ, maxX, maxY, maxZ) < 0;
        } finally {
            IN_TWIN_TEST.set(Boolean.FALSE);
        }
    }
}
