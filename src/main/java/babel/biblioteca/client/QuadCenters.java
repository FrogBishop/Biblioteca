package babel.biblioteca.client;

//? if >=1.21.9 {
import com.mojang.blaze3d.vertex.CompactVectorArray;
//?}
import org.joml.Vector3f;
import java.nio.ByteBuffer;

/** Read-only access to the centroid storage used by each renderer generation. */
public interface QuadCenters {
    int size();
    float x(int index);
    float y(int index);
    float z(int index);
    Object identity();

    /** Classic terrain stores XYZ floats first in each BLOCK vertex; opposite corners define a quad centre. */
    static QuadCenters fromVertices(ByteBuffer bytes, int stride, int vertexCount) {
        float[] coordinates = new float[vertexCount / 4 * 3];
        for (int quad = 0; quad < vertexCount / 4; quad++) {
            int first = bytes.position() + quad * 4 * stride;
            int opposite = first + 2 * stride;
            for (int axis = 0; axis < 3; axis++) {
                coordinates[quad * 3 + axis] = (bytes.getFloat(first + axis * 4) + bytes.getFloat(opposite + axis * 4)) * 0.5f;
            }
        }
        return of(coordinates);
    }

    static QuadCenters of(float[] coordinates) {
        if (coordinates.length % 3 != 0) throw new IllegalArgumentException("Centroids must be XYZ triples");
        return new QuadCenters() {
            public int size() { return coordinates.length / 3; }
            public float x(int i) { return coordinates[i * 3]; }
            public float y(int i) { return coordinates[i * 3 + 1]; }
            public float z(int i) { return coordinates[i * 3 + 2]; }
            public Object identity() { return coordinates; }
        };
    }

    static QuadCenters of(Vector3f[] points) {
        return new QuadCenters() {
            public int size() { return points.length; }
            public float x(int i) { return points[i].x; }
            public float y(int i) { return points[i].y; }
            public float z(int i) { return points[i].z; }
            public Object identity() { return points; }
        };
    }

    //? if >=1.21.9 {
    static QuadCenters of(CompactVectorArray points) {
        return new QuadCenters() {
            // getZ() reads the Y component in 1.21.9–26.1.2. The vector getter is correct.
            private final Vector3f scratch = new Vector3f();
            public int size() { return points.size(); }
            public float x(int i) { return points.getX(i); }
            public float y(int i) { return points.getY(i); }
            public float z(int i) { return points.get(i, scratch).z; }
            public Object identity() { return points; }
        };
    }
    //?}
}
