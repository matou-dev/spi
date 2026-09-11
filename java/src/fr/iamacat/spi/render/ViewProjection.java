package fr.iamacat.spi.render;

/**
 * GPU instancing adapter half: combine the driver's column-major view
 * and projection matrices into the row-major view-projection product
 * {@link Frustum#of} consumes. The GL adapters read both matrices with
 * {@code glGetFloat} (column-major, one matrix each) while the pure
 * planner only ever sees their product — this class is the single
 * conversion point, so every bridge multiplies the same way. Pure,
 * zero MC, zero GL. Java 8.
 */
public final class ViewProjection {
    private ViewProjection() {}

    /**
     * Multiplies projection by view ({@code clip = P * V * pos}, the GL
     * convention) and returns the product row-major
     * ({@code out[row * 4 + col]}). Both inputs arrive column-major
     * ({@code in[col * 4 + row]}) as the driver stores them; each side
     * is transposed once, then row-major multiplied. Never null, never
     * aliased: the product is a fresh array.
     */
    public static float[] vpRowMajor(float[] viewColMajor,
            float[] projColMajor) {
        if (viewColMajor == null || projColMajor == null) {
            throw new NullPointerException(
                    "E_RENDER_MATRIX:null matrix (want two float[16])");
        }
        if (viewColMajor.length != 16 || projColMajor.length != 16) {
            throw new IllegalArgumentException("E_RENDER_MATRIX:shape <"
                    + viewColMajor.length + "," + projColMajor.length
                    + "> (want 16,16 column-major)");
        }
        float[] view = transpose(viewColMajor);
        float[] proj = transpose(projColMajor);
        float[] out = new float[16];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                float acc = 0F;
                for (int k = 0; k < 4; k++) {
                    acc += proj[row * 4 + k] * view[k * 4 + col];
                }
                out[row * 4 + col] = acc;
            }
        }
        return out;
    }

    private static float[] transpose(float[] colMajor) {
        float[] rowMajor = new float[16];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                rowMajor[row * 4 + col] = colMajor[col * 4 + row];
            }
        }
        return rowMajor;
    }
}
