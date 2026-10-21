package babel.biblioteca.client;

import java.util.ArrayList;
import java.util.List;

/** Splits the weather renderer's vertical range at each periodic world boundary. */
public final class LoopingWeather {

    private LoopingWeather() {}

    /**
     * Each slice uses real world rows for terrain, biome and light lookups. Its offset is applied
     * only to the resulting rain or snow geometry, keeping weather on the far side of the boundary
     * beside the camera without putting precipitation below that copy's terrain surface.
     */
    public static List<Slice> visibleSlices(int cameraY, int radius, int minY, int period) {
        if (radius <= 0 || period <= 0) return List.of();

        int maxY = minY + period;
        int viewBottom = cameraY - radius;
        int viewTop = cameraY + radius;
        int firstLayer = Math.floorDiv(viewBottom - minY, period);
        int lastLayer = Math.floorDiv(viewTop - 1 - minY, period);
        List<Slice> slices = new ArrayList<>(lastLayer - firstLayer + 1);

        for (int layer = firstLayer; layer <= lastLayer; layer++) {
            int offsetY = layer * period;
            slices.add(new Slice(
                    Math.max(viewBottom - offsetY, minY),
                    Math.min(viewTop - offsetY, maxY),
                    Math.max(minY, Math.min(cameraY - offsetY, maxY - 1)),
                    offsetY));
        }

        return slices;
    }

    /** Source rows are [minY, maxY); cameraY is a source row and offsetY is a whole world height. */
    public record Slice(int minY, int maxY, int cameraY, int offsetY) {

        public int bottomY(int terrainHeight) {
            return Math.max(minY, terrainHeight) + offsetY;
        }

        public int topY() {
            return maxY + offsetY;
        }
    }
}
