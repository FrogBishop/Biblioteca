package babel.biblioteca.client.sodiumcompat;

import babel.biblioteca.Compat;
import babel.biblioteca.LoopingRegistry;
import babel.biblioteca.client.ClientCompat;
import babel.biblioteca.client.LoopingClientSettings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Periodic source discovery and draw instances using Sodium's own meshes and scheduling. */
public final class SodiumBridge {
    private static final ThreadLocal<Integer> IMAGE = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_IMAGES = 65;
    private SodiumBridge() {}

    public static Level level(Object owner) { return (Level) SodiumAccess.get(owner, "world", "level"); }
    public static boolean looping(Object owner) { Level level = level(owner); return level != null && LoopingRegistry.isLooping(level); }
    public static boolean looping() {
        Level level = Minecraft.getInstance().level;
        return level != null && LoopingRegistry.isLooping(level);
    }
    public static int imageOffset() { return IMAGE.get(); }
    public static int foldSection(Level level, int y) {
        int min = Compat.minSection(level), count = Compat.height(level) >> 4;
        return count <= 0 ? y : min + Math.floorMod(y - min, count);
    }

    private record Source(Object section, int x, int y, int z, double distance) {}
    private static List<Source> sources(Collection<?> sections, Vec3 eye, Level level, double reach) {
        int period = Compat.height(level);
        ArrayList<Source> result = new ArrayList<>();
        for (Object section : sections) {
            int x = ((Number) SodiumAccess.call(section, "getOriginX")).intValue();
            int y = ((Number) SodiumAccess.call(section, "getOriginY")).intValue();
            int z = ((Number) SodiumAccess.call(section, "getOriginZ")).intValue();
            double dx = Math.max(0, Math.abs(x + 8.0 - eye.x) - 8);
            double dz = Math.max(0, Math.abs(z + 8.0 - eye.z) - 8);
            if (dx * dx + dz * dz > (reach + 16) * (reach + 16)) continue;
            double dy = y + 8.0 - eye.y;
            dy -= Math.round(dy / period) * period;
            result.add(new Source(section, x, y, z, dx * dx + dy * dy + dz * dz));
        }
        result.sort(Comparator.comparingDouble(Source::distance));
        return result;
    }

    private static Vec3 camera(Object transform) {
        boolean modern = SodiumAccess.has(transform, "x");
        return new Vec3(((Number) SodiumAccess.get(transform, modern ? "x" : "posX")).doubleValue(),
                ((Number) SodiumAccess.get(transform, modern ? "y" : "posY")).doubleValue(),
                ((Number) SodiumAccess.get(transform, modern ? "z" : "posZ")).doubleValue());
    }

    private static boolean visible(Object viewport, Source source, Vec3 eye, int period, double reach) {
        double slack = LoopingClientSettings.preloadBlocks();
        int first = (int) Math.ceil((eye.y - reach - slack - source.y - 16) / period);
        int last = (int) Math.floor((eye.y + reach + slack - source.y) / period);
        for (int image = first; image <= last; image++) {
            int y = source.y + image * period;
            if (!SodiumAccess.hasMethod(viewport, "getTransform", 0)) {
                // Legacy Frustum.isBoxVisible also has six arguments, but accepts min/max bounds,
                // not the center/radii used by Viewport. Test its explicit enum-returning method.
                Object visibility = SodiumAccess.call(viewport, "testBox", (float)(source.x - slack), (float)(y - slack),
                        (float)(source.z - slack), (float)(source.x + 16 + slack), (float)(y + 16 + slack), (float)(source.z + 16 + slack));
                if (!visibility.toString().equals("OUTSIDE")) return true;
            } else if (SodiumAccess.hasMethod(viewport, "isBoxVisible", 3)) {
                if ((boolean) SodiumAccess.call(viewport, "isBoxVisible", source.x + 8, y + 8, source.z + 8)) return true;
            } else if (SodiumAccess.hasMethod(viewport, "isBoxVisible", 6)) {
                if ((boolean) SodiumAccess.call(viewport, "isBoxVisible", source.x + 8, y + 8, source.z + 8,
                        (float)(9.125 + slack), (float)(9.125 + slack), (float)(9.125 + slack))) return true;
            }
        }
        return false;
    }

    public static void collectLegacy(Object manager, Camera camera, Object frustum, int frame) {
        Level level = level(manager);
        Vec3 eye = ClientCompat.position(camera);
        int period = Compat.height(level);
        double reach = ClientCompat.renderDistance() * 16.0;
        SodiumAccess.set(manager, "currentFrame", frame);
        Collection<?> sections = ((Map<?, ?>) SodiumAccess.get(manager, "sections")).values();
        for (Source source : sources(sections, eye, level, reach)) {
            if (!visible(frustum, source, eye, period, reach)) continue;
            Object graph = SodiumAccess.call(source.section, "getGraphInfo");
            SodiumAccess.call(graph, "setLastVisibleFrame", frame);
            SodiumAccess.call(manager, "schedulePendingUpdates", source.section);
            SodiumAccess.call(manager, "addChunkToVisible", source.section);
            SodiumAccess.call(manager, "addEntitiesToRenderLists", source.section);
        }
    }

    public static void collect(Object culler, Object visitor, Object viewport, float requestedDistance, int frame) {
        Level level = level(culler);
        Vec3 eye = camera(SodiumAccess.call(viewport, "getTransform"));
        double reach = Math.max(requestedDistance, ClientCompat.renderDistance() * 16.0);
        Collection<?> sections = ((Map<?, ?>) SodiumAccess.get(culler, "sections")).values();
        for (Source source : sources(sections, eye, level, reach)) {
            if (!visible(viewport, source, eye, Compat.height(level), reach)) continue;
            SodiumAccess.call(source.section, "setLastVisibleFrame", frame);
            // New collectors also have a private visit(section, int flags) helper. Prefer the
            // public one-argument visitor instead of mistaking that helper for the old boolean ABI.
            if (SodiumAccess.hasMethod(visitor, "visit", 1)) SodiumAccess.call(visitor, "visit", source.section);
            else SodiumAccess.call(visitor, "visit", source.section, true);
        }
    }

    public static void waitForCulling(Object manager) {
        SodiumAccess.call(manager, "consumeCullTaskResults", true);
    }

    public static void collectAsync(Object manager, Object viewport, Object fog) {
        Level level = level(manager);
        waitForCulling(manager);
        Object storage = SodiumAccess.get(manager, "renderSections");
        Object regions = SodiumAccess.get(manager, "regions");
        int frame = ((Number) SodiumAccess.get(manager, "frame")).intValue();
        float reach = Math.max(((Number) SodiumAccess.call(manager, "getSearchDistance", fog)).floatValue(), ClientCompat.renderDistance() * 16.0f);
        Object collector = SodiumAccess.construct(SodiumAccess.sibling(manager, "client.render.chunk.lists.FallbackVisibleChunkCollector"),
                viewport, reach, frame, storage, regions, level);
        Collection<?> sections = ((Map<?, ?>) SodiumAccess.get(storage, "sections")).values();
        Vec3 eye = camera(SodiumAccess.call(viewport, "getTransform"));
        for (Source source : sources(sections, eye, level, reach)) {
            if (visible(viewport, source, eye, Compat.height(level), reach))
                SodiumAccess.call(collector, "visit", source.x >> 4, source.y >> 4, source.z >> 4);
        }
        SodiumAccess.set(manager, "renderLists", SodiumAccess.call(collector, "createRenderLists", viewport));
        SodiumAccess.set(manager, "taskLists", SodiumAccess.call(collector, "getPendingTaskLists"));
        SodiumAccess.call(collector, "prepareForTraversal");
        SodiumAccess.set(manager, "renderTree", collector);
        SodiumAccess.set(manager, "needsRenderListUpdate", false);
        SodiumAccess.set(manager, "cameraChanged", false);
    }

    /** Reuse each native draw list, geometry buffer and prepared batch at a periodic camera origin. */
    public static void draw(Object camera, Consumer<Object> draw) {
        Level level = Minecraft.getInstance().level;
        if (level == null || !LoopingRegistry.isLooping(level)) { draw.accept(camera); return; }
        Vec3 eye = camera(camera);
        int period = Compat.height(level), minimum = Compat.minSection(level) << 4;
        double reach = ClientCompat.renderDistance() * 16.0;
        long nearest = Math.round((eye.y - minimum - period * 0.5) / period);
        long first = Math.max(nearest - MAX_IMAGES / 2, (long)Math.floor((eye.y - reach - minimum) / period));
        long last = Math.min(nearest + MAX_IMAGES / 2, (long)Math.floor((eye.y + reach - minimum) / period));
        ArrayList<Long> images = new ArrayList<>();
        for (long image = first; image <= last; image++) images.add(image);
        images.sort(Comparator.<Long>comparingDouble(image -> Math.abs(minimum + (image + 0.5) * period - eye.y)).reversed());
        int previous = IMAGE.get();
        try {
            for (long image : images) {
                long offset = image * period;
                if (offset < Integer.MIN_VALUE || offset > Integer.MAX_VALUE) continue;
                IMAGE.set((int)offset);
                Object shifted = offset == 0 ? camera : SodiumAccess.construct(camera.getClass(), eye.x, eye.y - offset, eye.z);
                draw.accept(shifted);
            }
        } finally { IMAGE.set(previous); }
    }
}
