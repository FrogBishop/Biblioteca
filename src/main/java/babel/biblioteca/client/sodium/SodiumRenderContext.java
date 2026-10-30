package babel.biblioteca.client.sodium;

/** Offsets are scoped to one native draw submission; mesh storage stays in canonical coordinates. */
public final class SodiumRenderContext {
    private static boolean active;
    private static final ThreadLocal<Integer> OFFSET = ThreadLocal.withInitial(() -> 0);
    private SodiumRenderContext() {}
    public static void activate() { active = true; }
    public static boolean isActive() { return active; }
    public static int offset() { return OFFSET.get(); }
    public static void offset(int y) { OFFSET.set(y); }
    public static void clear() { OFFSET.remove(); }
}
