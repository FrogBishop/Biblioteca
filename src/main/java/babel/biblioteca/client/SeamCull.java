package babel.biblioteca.client;

import babel.biblioteca.client.mixin.FrustumInvoker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;

/**
 * Plain frustum tests for code that must not be answered by a section's periodic twin.
 *
 * FrustumYLoopMixin makes the frustum answer for a box's ring twin as well as for the box, which
 * is what keeps the far side of the seam in the visible set. That makes it wrong for two questions
 * asked while building the draw list:
 *
 *   - "is this section's own row on screen?"  If it is not, the section is only in the list because
 *     its twin is, and drawing it at its own row is work for nothing.
 *   - "is this copy's row on screen?"  A copy's twin is the section it was copied from, which is
 *     visible by construction, so the ring-aware answer is always yes and every section would be
 *     drawn three times over.
 *
 * Both need the answer vanilla would give. The ring behaviour is switched off for the duration of
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

    /** Exactly what Frustum.isVisible would answer without the ring: -2/-1 visible, 0/1 culled. */
    public static boolean plainlyVisible(Frustum frustum, AABB box) {
        return plainlyVisible(frustum, box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ);
    }

    /**
     * Allocation-free form for the seam scans, which test thousands of virtual copy bounds.
     *
     * AABB is avoided deliberately: the scan runs per section per periodic row, and a temporary
     * box per candidate was a measurable allocation source during the height-16 tests.
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
