package babel.biblioteca.client;

import com.mojang.blaze3d.vertex.CompactVectorArray;
import com.mojang.renderpearl.api.pipeline.IndexType;
import it.unimi.dsi.fastutil.ints.IntArrays;

import java.nio.ByteBuffer;

/** A copy's quad order, measured from that copy's position relative to the camera. */
public final class TranslucentQuadOrder {

    private final CompactVectorArray centers;
    private final int[] order;
    private final double[] distances;
    private double lastX = Double.NaN;
    private double lastY = Double.NaN;
    private double lastZ = Double.NaN;

    public TranslucentQuadOrder(CompactVectorArray centers) {
        this.centers = centers;
        this.order = new int[centers.size()];
        this.distances = new double[centers.size()];
        for (int i = 0; i < this.order.length; i++) this.order[i] = i;
    }

    public void update(double cameraX, double cameraY, double cameraZ) {
        if (cameraX == this.lastX && cameraY == this.lastY && cameraZ == this.lastZ) return;
        this.lastX = cameraX;
        this.lastY = cameraY;
        this.lastZ = cameraZ;

        for (int i = 0; i < this.order.length; i++) {
            double x = this.centers.getX(i) - cameraX;
            double y = this.centers.getY(i) - cameraY;
            double z = this.centers.getZ(i) - cameraZ;
            this.distances[i] = x * x + y * y + z * z;
        }

        // Most small camera movements keep the existing order. Verify it in linear time and
        // only sort when a pair changes order, without quantizing the camera to whole blocks.
        for (int i = 1; i < this.order.length; i++) {
            if (compare(this.order[i - 1], this.order[i]) > 0) {
                IntArrays.mergeSort(this.order, this::compare);
                return;
            }
        }
    }

    private int compare(int left, int right) {
        int distance = Double.compare(this.distances[right], this.distances[left]);
        return distance != 0 ? distance : Integer.compare(left, right);
    }

    public int size() {
        return this.order.length;
    }

    public int quad(int index) {
        return this.order[index];
    }

    public double distance(int index) {
        return this.distances[this.order[index]];
    }

    /** Indices stay local to the source mesh, so every periodic copy shares its vertex buffer. */
    public static void writeQuad(ByteBuffer output, IndexType type, int quad) {
        int base = quad * 4;
        if (type == IndexType.SHORT) {
            output.putShort((short) base).putShort((short) (base + 1)).putShort((short) (base + 2));
            output.putShort((short) (base + 2)).putShort((short) (base + 3)).putShort((short) base);
        } else {
            output.putInt(base).putInt(base + 1).putInt(base + 2);
            output.putInt(base + 2).putInt(base + 3).putInt(base);
        }
    }
}
