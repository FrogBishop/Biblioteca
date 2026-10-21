package babel.biblioteca.client;

import it.unimi.dsi.fastutil.ints.IntArrays;
import java.nio.ByteBuffer;

/** Independent far-to-near quad order for one displayed copy of a source mesh. */
public final class TranslucentQuadOrder {
    private final QuadCenters centers;
    private final int[] order;
    private final double[] distances;
    private double lastX = Double.NaN;
    private double lastY = Double.NaN;
    private double lastZ = Double.NaN;

    public TranslucentQuadOrder(QuadCenters centers) {
        this.centers = centers;
        this.order = new int[centers.size()];
        this.distances = new double[centers.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
    }

    public void update(double x, double y, double z) {
        if (x == lastX && y == lastY && z == lastZ) return;
        lastX = x;
        lastY = y;
        lastZ = z;
        for (int i = 0; i < order.length; i++) {
            double dx = centers.x(i) - x;
            double dy = centers.y(i) - y;
            double dz = centers.z(i) - z;
            distances[i] = dx * dx + dy * dy + dz * dz;
        }
        for (int i = 1; i < order.length; i++) {
            if (compare(order[i - 1], order[i]) > 0) {
                IntArrays.mergeSort(order, this::compare);
                return;
            }
        }
    }

    private int compare(int a, int b) {
        int comparison = Double.compare(distances[b], distances[a]);
        return comparison != 0 ? comparison : Integer.compare(a, b);
    }

    public int size() { return order.length; }
    public int quad(int index) { return order[index]; }
    public double distance(int index) { return distances[order[index]]; }

    public static void writeQuad(ByteBuffer target, int indexBytes, int quad) {
        int base = quad * 4;
        if (indexBytes == 2) {
            target.putShort((short) base).putShort((short) (base + 1)).putShort((short) (base + 2));
            target.putShort((short) (base + 2)).putShort((short) (base + 3)).putShort((short) base);
        } else if (indexBytes == 4) {
            target.putInt(base).putInt(base + 1).putInt(base + 2);
            target.putInt(base + 2).putInt(base + 3).putInt(base);
        } else {
            throw new IllegalArgumentException("Unsupported index width: " + indexBytes);
        }
    }
}
