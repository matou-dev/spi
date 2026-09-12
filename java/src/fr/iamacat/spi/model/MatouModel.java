package fr.iamacat.spi.model;

import fr.iamacat.spi.hit.AABBd;
import fr.iamacat.spi.hit.BoneBox;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Declarative Bedrock/Blockbench model: identifier, texture grid and bones
 * in file order. Single derivation point for the two bridge consumers:
 * bakeMesh feeds the instanced renderer VBO, boneBoxes feeds HitTester.
 *
 * <p>Units: Bedrock pixels in, block units out (PX_PER_BLOCK = 16).
 * Cubes bake in bind pose: each cube first rotates around its own pivot
 * (explicit pivot, else the box center), then around its bone pivot by the
 * bone rotation, then around every ancestor pivot up to the root (a
 * parented bone rides its parent — the Bedrock skeleton). All rotations
 * are Euler degrees in x-then-y-then-z order (Bedrock schema). Faces keep
 * the live-proven winding (outward CCW, same corner order as the
 * BOX_VERTICES box it replaces); normals rotate with the geometry
 * (translation-free). UVs never move under rotation.
 *
 * <p>A zero rotation anywhere is skipped bit-for-bit: an unrotated model
 * bakes byte-identical to the pre-rotation code (the compat comparateur
 * in ModelCheck pins it).
 *
 * <p>Zero MC/GL imports, Java 8.
 */
public final class MatouModel {
    public static final double PX_PER_BLOCK = 16.0;
    public static final int VERTEX_STRIDE = 8;
    public static final int VERTICES_PER_CUBE = 36;

    public final String identifier;
    public final int textureWidth;
    public final int textureHeight;
    public final List<ModelBone> bones;

    public MatouModel(String identifier, int textureWidth, int textureHeight,
            List<ModelBone> bones) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException("E_MODEL_IDENTIFIER:missing (want description.identifier)");
        }
        if (textureWidth <= 0 || textureHeight <= 0
                || textureWidth > 4096 || textureHeight > 4096) {
            throw new IllegalArgumentException("E_MODEL_TEXTURE:shape <"
                    + textureWidth + "x" + textureHeight + "> (want 1..4096 each)");
        }
        if (bones == null) {
            throw new NullPointerException("E_MODEL_BONE:null (want a list, possibly empty)");
        }
        this.identifier = identifier;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.bones = Collections.unmodifiableList(new ArrayList<ModelBone>(bones));
    }

    public int cubeCount() {
        int n = 0;
        for (ModelBone b : bones) {
            n += b.cubes.size();
        }
        return n;
    }

    /**
     * Bakes every cube into 36 interleaved vertices
     * (pos3 block units, uv2 normalized, normal3) in bone/cube order.
     * A per-face cube bakes 6 vertices per present face only (an absent
     * face is dropped, vanilla parity) — box-anchor cubes always bake
     * the full 36, byte-identical to V1 when unrotated.
     */
    public float[] bakeMesh() {
        Map<String, ModelBone> byName = boneIndex();
        float[] out = new float[emittedVertexCount() * VERTEX_STRIDE];
        int at = 0;
        for (ModelBone b : bones) {
            List<double[][]> chain = chainFor(b, byName);
            for (ModelCube c : b.cubes) {
                at = emitCube(out, at, c, chain);
            }
        }
        return out;
    }

    private int emittedVertexCount() {
        int n = 0;
        for (ModelBone b : bones) {
            for (ModelCube c : b.cubes) {
                if (c.faceUv == null) {
                    n += VERTICES_PER_CUBE;
                } else {
                    for (String f : ModelCube.FACES) {
                        if (c.faceUv.containsKey(f)) {
                            n += 6;
                        }
                    }
                }
            }
        }
        return n;
    }

    /**
     * Derives one bind-pose BoneBox per non-empty bone: the axis-aligned
     * union of the bone's rotated cube corners (block units,
     * entity-local). The union over a rotated cube is conservative (it
     * covers the rotated shape, never less) — HitTester stays AABB, an
     * exact oriented test is a named follow-up if a rotated limb ever
     * proves too generous. Bones without cubes contribute nothing.
     */
    public List<BoneBox> boneBoxes() {
        return shiftedBoxes(0.0, 0.0, 0.0);
    }

    /**
     * World-space placement of {@link #boneBoxes()} at the entity origin
     * (feet): the single translation every bridge applies instead of
     * copying the offset loop per version. Yaw rotation stays bind-pose
     * (see the class note) — a rotated pole lands with the animation
     * tranche, never a per-bridge guess.
     */
    public List<BoneBox> placedBoxes(double x, double y, double z) {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            throw new IllegalArgumentException("E_MODEL_PLACE:nan (want a finite entity origin)");
        }
        return shiftedBoxes(x, y, z);
    }

    private List<BoneBox> shiftedBoxes(double x, double y, double z) {
        Map<String, ModelBone> byName = boneIndex();
        List<BoneBox> out = new ArrayList<BoneBox>();
        for (ModelBone b : bones) {
            if (b.cubes.isEmpty()) {
                continue;
            }
            List<double[][]> chain = chainFor(b, byName);
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (ModelCube c : b.cubes) {
                for (double[] corner : inflatedCorners(c)) {
                    double[] q = xformPoint(corner, c, chain);
                    if (q[0] < minX) {
                        minX = q[0];
                    }
                    if (q[1] < minY) {
                        minY = q[1];
                    }
                    if (q[2] < minZ) {
                        minZ = q[2];
                    }
                    if (q[0] > maxX) {
                        maxX = q[0];
                    }
                    if (q[1] > maxY) {
                        maxY = q[1];
                    }
                    if (q[2] > maxZ) {
                        maxZ = q[2];
                    }
                }
            }
            out.add(new BoneBox(b.name, new AABBd(minX / PX_PER_BLOCK + x,
                    minY / PX_PER_BLOCK + y, minZ / PX_PER_BLOCK + z,
                    maxX / PX_PER_BLOCK + x, maxY / PX_PER_BLOCK + y,
                    maxZ / PX_PER_BLOCK + z)));
        }
        return Collections.unmodifiableList(out);
    }

    private Map<String, ModelBone> boneIndex() {
        Map<String, ModelBone> byName = new HashMap<String, ModelBone>();
        for (ModelBone b : bones) {
            byName.put(b.name, b);
        }
        return byName;
    }

    /**
     * Rotation chain for one bone, innermost first: the bone itself, then
     * its parent up to the root. Each level is {pivot[3] px, rotation[3]
     * degrees}. A dangling parent (direct-ctor models only — the parser
     * refuses it) ends the chain; a cycle refuses loudly (the parser
     * refuses it too — this is the defense for hand-built models).
     */
    private static List<double[][]> chainFor(ModelBone b, Map<String, ModelBone> byName) {
        List<double[][]> chain = new ArrayList<double[][]>();
        Set<String> seen = new HashSet<String>();
        ModelBone cur = b;
        while (cur != null) {
            if (!seen.add(cur.name)) {
                throw new IllegalArgumentException("E_MODEL_BONE:parent <cycle at "
                        + cur.name + "> (want an acyclic bone tree)");
            }
            chain.add(new double[][] {
                {cur.pivotX, cur.pivotY, cur.pivotZ},
                {cur.rotX, cur.rotY, cur.rotZ},
            });
            cur = (cur.parent == null) ? null : byName.get(cur.parent);
        }
        return chain;
    }

    /** The 8 inflated corners of one cube, px, unrotated. */
    private static double[][] inflatedCorners(ModelCube c) {
        double x0 = c.minX();
        double y0 = c.minY();
        double z0 = c.minZ();
        double x1 = c.maxX();
        double y1 = c.maxY();
        double z1 = c.maxZ();
        return new double[][] {
            {x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0},
            {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1},
        };
    }

    /**
     * Full bind-pose transform of one cube corner (px): cube rotation
     * about the cube pivot (explicit pivot, else the box center), then
     * the bone chain innermost-first. Zero rotations skip bit-for-bit.
     */
    private static double[] xformPoint(double[] p, ModelCube c, List<double[][]> chain) {
        double x = p[0];
        double y = p[1];
        double z = p[2];
        if (c.rotX != 0.0 || c.rotY != 0.0 || c.rotZ != 0.0) {
            double px = c.hasPivot ? c.pivotX : c.originX + c.sizeX / 2.0;
            double py = c.hasPivot ? c.pivotY : c.originY + c.sizeY / 2.0;
            double pz = c.hasPivot ? c.pivotZ : c.originZ + c.sizeZ / 2.0;
            double[] r = rotAbout(x, y, z, px, py, pz, c.rotX, c.rotY, c.rotZ);
            x = r[0];
            y = r[1];
            z = r[2];
        }
        for (double[][] level : chain) {
            double[] piv = level[0];
            double[] rot = level[1];
            if (rot[0] == 0.0 && rot[1] == 0.0 && rot[2] == 0.0) {
                continue;
            }
            double[] r = rotAbout(x, y, z, piv[0], piv[1], piv[2], rot[0], rot[1], rot[2]);
            x = r[0];
            y = r[1];
            z = r[2];
        }
        return new double[] {x, y, z};
    }

    /**
     * Translation-free twin of {@link #xformPoint} for face normals,
     * renormalized (a rigid rotation preserves length up to fp error).
     * A fully unrotated normal returns bit-for-bit.
     */
    private static double[] xformDir(double nx, double ny, double nz,
            ModelCube c, List<double[][]> chain) {
        double x = nx;
        double y = ny;
        double z = nz;
        boolean moved = false;
        if (c.rotX != 0.0 || c.rotY != 0.0 || c.rotZ != 0.0) {
            double[] r = rotVec(x, y, z, c.rotX, c.rotY, c.rotZ);
            x = r[0];
            y = r[1];
            z = r[2];
            moved = true;
        }
        for (double[][] level : chain) {
            double[] rot = level[1];
            if (rot[0] == 0.0 && rot[1] == 0.0 && rot[2] == 0.0) {
                continue;
            }
            double[] r = rotVec(x, y, z, rot[0], rot[1], rot[2]);
            x = r[0];
            y = r[1];
            z = r[2];
            moved = true;
        }
        if (!moved) {
            return new double[] {nx, ny, nz};
        }
        double len = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / len, y / len, z / len};
    }

    private static double[] rotAbout(double x, double y, double z,
            double px, double py, double pz, double dx, double dy, double dz) {
        double[] r = rotVec(x - px, y - py, z - pz, dx, dy, dz);
        return new double[] {r[0] + px, r[1] + py, r[2] + pz};
    }

    /**
     * Euler rotation of one vector, degrees, x-then-y-then-z extrinsic
     * (R = Rz * Ry * Rx — the Bedrock schema order). Right-handed:
     * +X turns +Y toward +Z, +Y turns +Z toward +X, +Z turns +X toward +Y.
     */
    private static double[] rotVec(double vx, double vy, double vz,
            double dx, double dy, double dz) {
        double ax = Math.toRadians(dx);
        double cx = Math.cos(ax);
        double sx = Math.sin(ax);
        double y1 = vy * cx - vz * sx;
        double z1 = vy * sx + vz * cx;
        double ay = Math.toRadians(dy);
        double cy = Math.cos(ay);
        double sy = Math.sin(ay);
        double x2 = vx * cy + z1 * sy;
        double z2 = -vx * sy + z1 * cy;
        double az = Math.toRadians(dz);
        double cz = Math.cos(az);
        double sz = Math.sin(az);
        double x3 = x2 * cz - y1 * sz;
        double y3 = x2 * sz + y1 * cz;
        return new double[] {x3, y3, z2};
    }

    private int emitCube(float[] out, int at, ModelCube c, List<double[][]> chain) {
        double[][] cn = inflatedCorners(c);
        double[][] q = new double[8][];
        for (int i = 0; i < 8; i++) {
            double[] r = xformPoint(cn[i], c, chain);
            q[i] = new double[] {r[0] / PX_PER_BLOCK, r[1] / PX_PER_BLOCK, r[2] / PX_PER_BLOCK};
        }
        // Faces: normal + 4 corner indices wound so (a,b,c)+(a,c,d) face
        // out. Corner order mirrors the live BOX_VERTICES box exactly:
        // 0:(x0,y0,z0) 1:(x1,y0,z0) 2:(x1,y1,z0) 3:(x0,y1,z0)
        // 4:(x0,y0,z1) 5:(x1,y0,z1) 6:(x1,y1,z1) 7:(x0,y1,z1).
        // Per-face corner patterns ride the Bedrock upper-left convention
        // (hub decisions/MATOU_MODEL.md, V2 tranche): five faces read the
        // rect with its top-left at c/d, down anchors its origin at b.
        // su/sv size the box-anchored planar projection (V1 path).
        double sx = (c.maxX() - c.minX());
        double sy = (c.maxY() - c.minY());
        double sz = (c.maxZ() - c.minZ());
        double[][] cornerPat = {{0.0, 1.0}, {1.0, 1.0}, {1.0, 0.0}, {0.0, 0.0}};
        double[][] downPat = {{1.0, 0.0}, {0.0, 0.0}, {0.0, 1.0}, {1.0, 1.0}};
        double[][] faces = {
            // nx, ny, nz, ia, ib, ic, id, su, sv
            {0, 0, 1, 4, 5, 6, 7, sx, sy}, // south
            {0, 0, -1, 1, 0, 3, 2, sx, sy}, // north
            {0, 1, 0, 7, 6, 2, 3, sx, sz}, // up
            {0, -1, 0, 0, 1, 5, 4, sx, sz}, // down
            {1, 0, 0, 5, 1, 2, 6, sz, sy}, // east
            {-1, 0, 0, 0, 4, 7, 3, sz, sy}, // west
        };
        for (int fi = 0; fi < faces.length; fi++) {
            double[] f = faces[fi];
            double[] rect = null;
            if (c.faceUv != null) {
                rect = c.faceUv.get(ModelCube.FACES[fi]);
                if (rect == null) {
                    continue;
                }
            }
            double[] n = xformDir(f[0], f[1], f[2], c, chain);
            double[][] pat = "down".equals(ModelCube.FACES[fi]) ? downPat : cornerPat;
            double[] cornerU = {0.0, f[7], f[7], 0.0};
            double[] cornerV = {0.0, 0.0, f[8], f[8]};
            int[] idx = {(int) f[3], (int) f[4], (int) f[5], (int) f[6]};
            int[] tri = {0, 1, 2, 0, 2, 3};
            for (int k : tri) {
                double[] p = q[idx[k]];
                out[at++] = (float) p[0];
                out[at++] = (float) p[1];
                out[at++] = (float) p[2];
                if (rect != null) {
                    out[at++] = (float) ((rect[0] + pat[k][0] * rect[2]) / textureWidth);
                    out[at++] = (float) ((rect[1] + pat[k][1] * rect[3]) / textureHeight);
                } else {
                    out[at++] = (float) ((c.uvU + cornerU[k]) / textureWidth);
                    out[at++] = (float) ((c.uvV + cornerV[k]) / textureHeight);
                }
                out[at++] = (float) n[0];
                out[at++] = (float) n[1];
                out[at++] = (float) n[2];
            }
        }
        return at;
    }
}
