package fr.iamacat.spi.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One Bedrock box: origin (min corner, px), size (px, strictly positive
 * after inflate), box UV anchor (px) and inflate (px, expands every side).
 * A cube optionally carries per-face UV rects instead of the box anchor
 * (hub decisions/MATOU_MODEL.md, V2 tranche): face name to {u, v, w, h}
 * in px, null when the cube rides the box anchor. A cube optionally
 * carries a bind-pose Euler rotation (degrees, x-then-y-then-z per the
 * Bedrock schema) around its pivot (explicit pivot, else the box center):
 * inflate applies to the unrotated box, then the inflated corners rotate
 * rigidly about the pivot. Zero MC/GL imports, Java 8.
 */
public final class ModelCube {
    /** Bedrock face names in bake order (south, north, up, down, east, west). */
    public static final String[] FACES = {
        "south", "north", "up", "down", "east", "west",
    };

    public final double originX;
    public final double originY;
    public final double originZ;
    public final double sizeX;
    public final double sizeY;
    public final double sizeZ;
    public final double uvU;
    public final double uvV;
    public final double inflate;

    /**
     * Per-face UV rects ({u, v, w, h} px, unmodifiable) or null for box
     * mode. A present face bakes its rect, an absent face bakes nothing
     * (vanilla parity — omitting a face drops it, never a default rect).
     */
    public final Map<String, double[]> faceUv;

    /** Bind-pose Euler rotation, degrees, x-then-y-then-z (all zero = unrotated). */
    public final double rotX;
    public final double rotY;
    public final double rotZ;

    /**
     * True when the cube carries an explicit rotation pivot (px).
     * False = rotate around the box center (origin + size / 2).
     */
    public final boolean hasPivot;
    public final double pivotX;
    public final double pivotY;
    public final double pivotZ;

    public ModelCube(double originX, double originY, double originZ,
            double sizeX, double sizeY, double sizeZ,
            double uvU, double uvV, double inflate) {
        this(originX, originY, originZ, sizeX, sizeY, sizeZ,
                uvU, uvV, inflate, null);
    }

    public ModelCube(double originX, double originY, double originZ,
            double sizeX, double sizeY, double sizeZ,
            double uvU, double uvV, double inflate,
            Map<String, double[]> faceUv) {
        this(originX, originY, originZ, sizeX, sizeY, sizeZ,
                uvU, uvV, inflate, faceUv, null, null);
    }

    /**
     * Full constructor: rotation in finite degrees (null = unrotated),
     * pivot in finite px (null = box center). Arrays are copied.
     */
    public ModelCube(double originX, double originY, double originZ,
            double sizeX, double sizeY, double sizeZ,
            double uvU, double uvV, double inflate,
            Map<String, double[]> faceUv,
            double[] rotation, double[] pivot) {
        if (Double.isNaN(originX) || Double.isNaN(originY) || Double.isNaN(originZ)
                || Double.isNaN(sizeX) || Double.isNaN(sizeY) || Double.isNaN(sizeZ)
                || Double.isNaN(uvU) || Double.isNaN(uvV) || Double.isNaN(inflate)) {
            throw new IllegalArgumentException("E_MODEL_CUBE:nan (origin/size/uv/inflate must be finite)");
        }
        if (!(sizeX > 0.0) || !(sizeY > 0.0) || !(sizeZ > 0.0)) {
            throw new IllegalArgumentException("E_MODEL_CUBE:size <"
                    + sizeX + "," + sizeY + "," + sizeZ + "> (want all > 0)");
        }
        if (uvU < 0.0 || uvV < 0.0) {
            throw new IllegalArgumentException("E_MODEL_CUBE:uv <"
                    + uvU + "," + uvV + "> (want >= 0)");
        }
        if (sizeX + 2.0 * inflate <= 0.0 || sizeY + 2.0 * inflate <= 0.0
                || sizeZ + 2.0 * inflate <= 0.0) {
            throw new IllegalArgumentException("E_MODEL_CUBE:size <inflate "
                    + inflate + " inverts the box> (want size + 2*inflate > 0)");
        }
        double rx = 0.0;
        double ry = 0.0;
        double rz = 0.0;
        if (rotation != null) {
            if (rotation.length != 3) {
                throw new IllegalArgumentException("E_MODEL_CUBE:rotation (want [x, y, z] degrees)");
            }
            for (double a : rotation) {
                if (Double.isNaN(a) || Double.isInfinite(a)) {
                    throw new IllegalArgumentException("E_MODEL_CUBE:rotation (want finite degrees)");
                }
            }
            rx = rotation[0];
            ry = rotation[1];
            rz = rotation[2];
        }
        boolean hp = false;
        double px = 0.0;
        double py = 0.0;
        double pz = 0.0;
        if (pivot != null) {
            if (pivot.length != 3) {
                throw new IllegalArgumentException("E_MODEL_CUBE:pivot (want [x, y, z] px)");
            }
            for (double a : pivot) {
                if (Double.isNaN(a) || Double.isInfinite(a)) {
                    throw new IllegalArgumentException("E_MODEL_CUBE:pivot (want finite px)");
                }
            }
            hp = true;
            px = pivot[0];
            py = pivot[1];
            pz = pivot[2];
        }
        Map<String, double[]> faces = null;
        if (faceUv != null) {
            faces = new LinkedHashMap<String, double[]>();
            for (Map.Entry<String, double[]> e : faceUv.entrySet()) {
                String name = e.getKey();
                double[] r = e.getValue();
                if (!isFace(name) || r == null || r.length != 4
                        || Double.isNaN(r[0]) || Double.isNaN(r[1])
                        || Double.isNaN(r[2]) || Double.isNaN(r[3])
                        || Double.isInfinite(r[0]) || Double.isInfinite(r[1])
                        || Double.isInfinite(r[2]) || Double.isInfinite(r[3])) {
                    throw new IllegalArgumentException("E_MODEL_FACE:shape <"
                            + name + "> (want a known face to {u, v, w, h})");
                }
                if (r[0] < 0.0 || r[1] < 0.0) {
                    throw new IllegalArgumentException("E_MODEL_FACE:uv <"
                            + name + " " + r[0] + "," + r[1]
                            + "> (want u,v >= 0 — the sampler clamps, never wraps)");
                }
                if (!(r[2] > 0.0) || !(r[3] > 0.0)) {
                    throw new IllegalArgumentException("E_MODEL_FACE:size <"
                            + name + " " + r[2] + "x" + r[3]
                            + "> (want w,h > 0)");
                }
                faces.put(name, new double[] {r[0], r[1], r[2], r[3]});
            }
            faces = Collections.unmodifiableMap(faces);
        }
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.uvU = uvU;
        this.uvV = uvV;
        this.inflate = inflate;
        this.faceUv = faces;
        this.rotX = rx;
        this.rotY = ry;
        this.rotZ = rz;
        this.hasPivot = hp;
        this.pivotX = px;
        this.pivotY = py;
        this.pivotZ = pz;
    }

    /** True for the six Bedrock face names, nothing else. */
    public static boolean isFace(String name) {
        for (String f : FACES) {
            if (f.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public double minX() {
        return originX - inflate;
    }

    public double minY() {
        return originY - inflate;
    }

    public double minZ() {
        return originZ - inflate;
    }

    public double maxX() {
        return originX + sizeX + inflate;
    }

    public double maxY() {
        return originY + sizeY + inflate;
    }

    public double maxZ() {
        return originZ + sizeZ + inflate;
    }
}
