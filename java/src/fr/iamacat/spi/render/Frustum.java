package fr.iamacat.spi.render;

import java.util.Arrays;

/**
 * GPU instancing spike, render-planning half: the six clipping planes of a
 * view volume, extracted from a view-projection matrix (Gribb-Hartmann), plus
 * the per-instance box/sphere tests the future per-bridge GL adapters will
 * execute. Pure math, zero MC, zero GL: the matrix arrives as 16 floats, the
 * bridge owns every call into the driver.
 *
 * <p>Derived from matou-engine {@code engine-core/.../core/view/Frustum.java}
 * with two measured deviations, both load-bearing:
 * <ul>
 * <li>Classic GL depth, not reverse-Z. Every Minecraft runtime uses
 * {@code -w <= z <= w}, so all six planes use the symmetric {@code w +/- row}
 * form (near = w + row2, far = w - row2). The engine file documents the trap:
 * its first version used the symmetric form for all six, right for
 * {@code [-1, 1]} and silently wrong under reverse-Z. Ours is the mirror
 * trap: copying the engine's depth planes verbatim would cull the near half
 * of every MC view.</li>
 * <li>Java 8: no switch expressions, no pattern-matching instanceof. SPI
 * builds with {@code javac --release 8}.</li>
 * </ul>
 *
 * <p>Kept verbatim: planes derived from the matrix (never maintained beside
 * it — "what was culled" and "what was drawn" cannot disagree), normalised
 * planes (a test returns metres, not side signs), camera-relative coordinates
 * (the caller subtracts the eye first, in double — see
 * {@link InstanceBucket}), two-corners-per-plane box test, six-dot sphere
 * test. A straddling box with no corner inside still reports INTERSECT: a
 * false positive, never a false negative, so it costs a draw and never a
 * missing mob.
 */
public final class Frustum {
    /** Report order; the tests take all six. */
    public enum Intersection {
        OUTSIDE, INTERSECT, INSIDE
    }

    private static final String[] NAMES = {
        "left", "right", "bottom", "top", "near", "far"
    };

    private final float[] planes;

    private Frustum(float[] planes) {
        this.planes = planes;
    }

    /**
     * Extracts the six planes of a row-major view-projection matrix
     * ({@code m[row * 4 + col]}). A point is inside when
     * {@code a*x + b*y + c*z + d >= 0} on all six planes.
     *
     * <p>Classic-depth combinations: left = w + row0, right = w - row0,
     * bottom = w + row1, top = w - row1, near = w + row2
     * ({@code z >= -w}), far = w - row2 ({@code z <= w}).
     */
    public static Frustum of(float[] viewProjection) {
        if (viewProjection == null) {
            throw new NullPointerException("E_RENDER_FRUSTUM:null matrix");
        }
        if (viewProjection.length != 16) {
            throw new IllegalArgumentException("E_RENDER_FRUSTUM:shape <"
                    + viewProjection.length + "> (want 16 row-major)");
        }
        float[] out = new float[24];
        for (int plane = 0; plane < 6; plane++) {
            // left/right use row 0, bottom/top row 1, near/far row 2;
            // even planes add the w row, odd planes subtract it.
            int row = plane / 2;
            boolean add = plane % 2 == 0;
            for (int axis = 0; axis < 4; axis++) {
                float w = viewProjection[3 * 4 + axis];
                float r = viewProjection[row * 4 + axis];
                out[plane * 4 + axis] = add ? w + r : w - r;
            }
            float length = (float) StrictMath.sqrt(
                    out[plane * 4] * out[plane * 4]
                    + out[plane * 4 + 1] * out[plane * 4 + 1]
                    + out[plane * 4 + 2] * out[plane * 4 + 2]);
            if (!(length > 0F)) {
                throw new IllegalArgumentException(
                        "E_RENDER_FRUSTUM:degenerate <" + NAMES[plane]
                        + "> (not a projection)");
            }
            for (int axis = 0; axis < 4; axis++) {
                out[plane * 4 + axis] /= length;
            }
        }
        return new Frustum(out);
    }

    /** One plane as {@code {a, b, c, d}}, index 0..5 left/right/bottom/top/near/far. */
    public float[] plane(int index) {
        if (index < 0 || index > 5) {
            throw new IllegalArgumentException(
                    "E_RENDER_PLANE:range <" + index + "> (want 0..5)");
        }
        return Arrays.copyOfRange(planes, index * 4, index * 4 + 4);
    }

    /**
     * Tests an axis-aligned box in CAMERA-RELATIVE coordinates. Two corners
     * per plane: the furthest along the normal decides OUTSIDE, the furthest
     * against it decides INSIDE.
     */
    public Intersection testAabb(float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ) {
        if (Float.isNaN(minX) || Float.isNaN(minY) || Float.isNaN(minZ)
                || Float.isNaN(maxX) || Float.isNaN(maxY) || Float.isNaN(maxZ)) {
            throw new IllegalArgumentException("E_RENDER_AABB:nan"
                    + " (NaN poisons every comparison into a wrong keep)");
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("E_RENDER_AABB:range"
                    + " (inverted box culls wrong silently)");
        }
        boolean fullyInside = true;
        for (int plane = 0; plane < 6; plane++) {
            float a = planes[plane * 4];
            float b = planes[plane * 4 + 1];
            float c = planes[plane * 4 + 2];
            float d = planes[plane * 4 + 3];
            float positive = a * (a >= 0F ? maxX : minX)
                    + b * (b >= 0F ? maxY : minY)
                    + c * (c >= 0F ? maxZ : minZ) + d;
            if (positive < 0F) {
                return Intersection.OUTSIDE;
            }
            float negative = a * (a >= 0F ? minX : maxX)
                    + b * (b >= 0F ? minY : maxY)
                    + c * (c >= 0F ? minZ : maxZ) + d;
            if (negative < 0F) {
                fullyInside = false;
            }
        }
        return fullyInside ? Intersection.INSIDE : Intersection.INTERSECT;
    }

    /** Tests a sphere in CAMERA-RELATIVE coordinates: six dot products. */
    public Intersection testSphere(float x, float y, float z, float radius) {
        if (!(radius >= 0F)) {
            throw new IllegalArgumentException("E_RENDER_SPHERE:range <"
                    + radius + "> (want >= 0)");
        }
        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
            throw new IllegalArgumentException("E_RENDER_SPHERE:nan"
                    + " (NaN poisons every comparison into a wrong keep)");
        }
        boolean fullyInside = true;
        for (int plane = 0; plane < 6; plane++) {
            float distance = planes[plane * 4] * x
                    + planes[plane * 4 + 1] * y
                    + planes[plane * 4 + 2] * z + planes[plane * 4 + 3];
            if (distance < -radius) {
                return Intersection.OUTSIDE;
            }
            if (distance < radius) {
                fullyInside = false;
            }
        }
        return fullyInside ? Intersection.INSIDE : Intersection.INTERSECT;
    }
}
