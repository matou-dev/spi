package fr.iamacat.spi.render;

import java.nio.FloatBuffer;

/**
 * Packed layout of per-instance data for the GPU instancing vertex shader.
 * Zero LWJGL/MC imports, Java 8.
 */
public final class InstanceFormat {
    public static final int FLOATS_PER_INSTANCE = 12;
    public static final int STRIDE_BYTES = FLOATS_PER_INSTANCE * 4;

    private InstanceFormat() {}

    /**
     * Packs one instance's rendering attributes into the target FloatBuffer:
     * [0..2]: relX, relY, relZ (camera-relative position)
     * [3]: yaw (radians)
     * [4]: pitch (radians)
     * [5]: scale
     * [6..9]: r, g, b, a (tint)
     * [10..11]: lightU, lightV (lightmap coordinates)
     */
    public static void pack(FloatBuffer dst,
                            double relX, double relY, double relZ,
                            float yaw, float pitch, float scale,
                            float r, float g, float b, float a,
                            float lightU, float lightV) {
        if (dst == null) {
            throw new NullPointerException("E_GL_BUFFER:null");
        }
        if (Double.isNaN(relX) || Double.isNaN(relY) || Double.isNaN(relZ)
                || Float.isNaN(yaw) || Float.isNaN(pitch) || Float.isNaN(scale)
                || Float.isNaN(r) || Float.isNaN(g) || Float.isNaN(b) || Float.isNaN(a)
                || Float.isNaN(lightU) || Float.isNaN(lightV)) {
            throw new IllegalArgumentException("E_INSTANCE_DATA:nan");
        }
        if (dst.remaining() < FLOATS_PER_INSTANCE) {
            throw new IllegalArgumentException("E_GL_BUFFER:overflow");
        }

        dst.put((float) relX);
        dst.put((float) relY);
        dst.put((float) relZ);
        dst.put(yaw);
        dst.put(pitch);
        dst.put(scale);
        dst.put(r);
        dst.put(g);
        dst.put(b);
        dst.put(a);
        dst.put(lightU);
        dst.put(lightV);
    }
}
