package fr.iamacat.spi.model;

import fr.iamacat.spi.hit.AABBd;
import fr.iamacat.spi.hit.BoneBox;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
        double[][] n = new double[6][];
        for (int fi = 0; fi < 6; fi++) {
            n[fi] = xformDir(FACE_NORMALS[fi][0], FACE_NORMALS[fi][1], FACE_NORMALS[fi][2], c, chain);
        }
        return emitFaces(out, at, c, q, n, 8, 0.0f);
    }

    /**
     * Posed twin of {@link #emitCube} for the gate oracle: same faces,
     * same UVs, corners and normals through the posed chain.
     */
    private int emitCubePosed(float[] out, int at, ModelCube c, List<PosedLevel> chain) {
        double[][] cn = inflatedCorners(c);
        double[][] q = new double[8][];
        for (int i = 0; i < 8; i++) {
            double[] r = xformPointPosed(cn[i], c, chain);
            q[i] = new double[] {r[0] / PX_PER_BLOCK, r[1] / PX_PER_BLOCK, r[2] / PX_PER_BLOCK};
        }
        double[][] n = new double[6][];
        for (int fi = 0; fi < 6; fi++) {
            n[fi] = xformDirPosed(FACE_NORMALS[fi][0], FACE_NORMALS[fi][1],
                    FACE_NORMALS[fi][2], c, chain);
        }
        return emitFaces(out, at, c, q, n, 8, 0.0f);
    }

    /**
     * Skinned emission for the GPU path: bind corners and normals plus
     * the file-order bone index (stride 9). Positions are the bind bake
     * — the shader applies the delta matrices, never a rebaked mesh.
     */
    private int emitCubeSkinned(float[] out, int at, ModelCube c,
            List<double[][]> chain, float bone) {
        double[][] cn = inflatedCorners(c);
        double[][] q = new double[8][];
        for (int i = 0; i < 8; i++) {
            double[] r = xformPoint(cn[i], c, chain);
            q[i] = new double[] {r[0] / PX_PER_BLOCK, r[1] / PX_PER_BLOCK, r[2] / PX_PER_BLOCK};
        }
        double[][] n = new double[6][];
        for (int fi = 0; fi < 6; fi++) {
            n[fi] = xformDir(FACE_NORMALS[fi][0], FACE_NORMALS[fi][1], FACE_NORMALS[fi][2], c, chain);
        }
        return emitFaces(out, at, c, q, n, 9, bone);
    }
    /** Base face normals in bake order (south, north, up, down, east, west). */
    private static final double[][] FACE_NORMALS = {
        {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0},
    };

    /**
     * Shared face emitter (the single derivation point for winding and
     * UVs — bind, posed and skinned paths all land here): positions and
     * normals are precomputed by the caller, this method only lays out
     * faces. Stride 8 ignores bone, stride 9 appends it after the
     * normal.
     */
    private int emitFaces(float[] out, int at, ModelCube c, double[][] q,
            double[][] normals, int stride, float bone) {
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
            double[] n = normals[fi];
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
                if (stride == 9) {
                    out[at++] = bone;
                }
            }
        }
        return at;
    }

    /**
     * Gate oracle for the animation tranche (hub
     * decisions/MATOU_ANIMATION.md): the stride-8 mesh under a pose,
     * same layout as {@link #bakeMesh()}. An identity pose bakes
     * float-for-float identical (the compat comparateur pins it).
     * Never the runtime path — the GPU consumes
     * {@link #poseDeltaMatrices}, never a rebaked mesh.
     */
    public float[] bakePosedMesh(MatouAnimation.AnimPose pose) {
        Map<String, MatouAnimation.BonePose> pmap = checkPose(pose);
        Map<String, ModelBone> byName = boneIndex();
        float[] out = new float[emittedVertexCount() * VERTEX_STRIDE];
        int at = 0;
        for (ModelBone b : bones) {
            List<PosedLevel> chain = chainForPosed(b, byName, pmap);
            for (ModelCube c : b.cubes) {
                at = emitCubePosed(out, at, c, chain);
            }
        }
        return out;
    }

    /**
     * Skinned mesh for the GPU path: bind positions and normals plus
     * the file-order bone index per vertex (stride 9: pos3, uv2,
     * normal3, bone1). The shader applies
     * {@link #poseDeltaMatrices} to these bind positions.
     */
    public float[] bakeSkinnedMesh() {
        Map<String, ModelBone> byName = boneIndex();
        float[] out = new float[emittedVertexCount() * 9];
        int at = 0;
        int bi = 0;
        for (ModelBone b : bones) {
            List<double[][]> chain = chainFor(b, byName);
            for (ModelCube c : b.cubes) {
                at = emitCubeSkinned(out, at, c, chain, (float) bi);
            }
            bi++;
        }
        return out;
    }

    /**
     * Posed hitboxes: conservative axis-aligned union over the posed
     * corners per non-empty bone (entity-local). The combat path rides
     * these, never the bind boxes, once a clip plays.
     */
    public List<BoneBox> posedBoxes(MatouAnimation.AnimPose pose) {
        return shiftedBoxesPosed(0.0, 0.0, 0.0, pose);
    }

    /**
     * World-space placement of {@link #posedBoxes} at the entity
     * origin (feet). Reuses E_MODEL_PLACE:nan (cited, never
     * redefined — anti-doublon).
     */
    public List<BoneBox> placedPosedBoxes(double x, double y, double z,
            MatouAnimation.AnimPose pose) {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            throw new IllegalArgumentException("E_MODEL_PLACE:nan (want a finite entity origin)");
        }
        return shiftedBoxesPosed(x, y, z, pose);
    }

    /**
     * GPU delivery contract: per-bone pose delta matrices, model bone
     * order, 4x4 row-major float[16] each, block units throughout. The
     * shader skins bind positions directly
     * ({@code worldPos = D_bone * bindPos}) where
     * {@code D = W_pose * W_bind^-1} — the bind bake is blocks while
     * the pivots are Bedrock px, so the px translation normalizes here
     * (a pivot-moving rotation otherwise displaces the skinned mesh by
     * 16x — caught by the 3-bone palette golden, invisible to the
     * older axis-invariant goldens). An identity pose yields identity
     * matrices (the delta comparateur pins it).
     */
    public Map<String, float[]> poseDeltaMatrices(MatouAnimation.AnimPose pose) {
        Map<String, MatouAnimation.BonePose> pmap = checkPose(pose);
        Map<String, ModelBone> byName = boneIndex();
        Map<String, float[]> out = new LinkedHashMap<String, float[]>();
        for (ModelBone b : bones) {
            List<PosedLevel> chain = chainForPosed(b, byName, pmap);
            double[] wPose = worldMatrix(chain, false);
            double[] wBind = worldMatrix(chain, true);
            double[] d = mul4(wPose, invertRts(wBind));
            d[3] /= PX_PER_BLOCK;
            d[7] /= PX_PER_BLOCK;
            d[11] /= PX_PER_BLOCK;
            out.put(b.name, toFloat(d));
        }
        return Collections.unmodifiableMap(out);
    }

    private List<BoneBox> shiftedBoxesPosed(double x, double y, double z,
            MatouAnimation.AnimPose pose) {
        Map<String, MatouAnimation.BonePose> pmap = checkPose(pose);
        Map<String, ModelBone> byName = boneIndex();
        List<BoneBox> out = new ArrayList<BoneBox>();
        for (ModelBone b : bones) {
            if (b.cubes.isEmpty()) {
                continue;
            }
            List<PosedLevel> chain = chainForPosed(b, byName, pmap);
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (ModelCube c : b.cubes) {
                for (double[] corner : inflatedCorners(c)) {
                    double[] q = xformPointPosed(corner, c, chain);
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

    /** Validates a pose against the model (sparse: absent means bind). */
    private Map<String, MatouAnimation.BonePose> checkPose(MatouAnimation.AnimPose pose) {
        if (pose == null) {
            throw new NullPointerException("E_ANIM_POSE:null (want a pose, possibly identity)");
        }
        Map<String, ModelBone> byName = boneIndex();
        for (String b : pose.bones.keySet()) {
            if (!byName.containsKey(b)) {
                throw new IllegalArgumentException("E_ANIM_BONE:unknown <" + b
                        + "> (want a bone of this model — never skipped)");
            }
        }
        return pose.bones;
    }

    /** One posed chain level: pivot, bind+pose Euler, offset, scale. */
    private static final class PosedLevel {
        double px;
        double py;
        double pz;
        double rx;
        double ry;
        double rz;
        double ox;
        double oy;
        double oz;
        double s;
        double brx;
        double bry;
        double brz;
    }

    private static List<PosedLevel> chainForPosed(ModelBone b,
            Map<String, ModelBone> byName, Map<String, MatouAnimation.BonePose> pmap) {
        List<PosedLevel> chain = new ArrayList<PosedLevel>();
        Set<String> seen = new HashSet<String>();
        ModelBone cur = b;
        while (cur != null) {
            if (!seen.add(cur.name)) {
                throw new IllegalArgumentException("E_MODEL_BONE:parent <cycle at "
                        + cur.name + "> (want an acyclic bone tree)");
            }
            MatouAnimation.BonePose p = pmap.get(cur.name);
            PosedLevel l = new PosedLevel();
            l.px = cur.pivotX;
            l.py = cur.pivotY;
            l.pz = cur.pivotZ;
            l.brx = cur.rotX;
            l.bry = cur.rotY;
            l.brz = cur.rotZ;
            l.rx = cur.rotX + (p == null ? 0.0 : p.rotX);
            l.ry = cur.rotY + (p == null ? 0.0 : p.rotY);
            l.rz = cur.rotZ + (p == null ? 0.0 : p.rotZ);
            l.ox = p == null ? 0.0 : p.posX;
            l.oy = p == null ? 0.0 : p.posY;
            l.oz = p == null ? 0.0 : p.posZ;
            l.s = p == null ? 1.0 : p.scale;
            chain.add(l);
            cur = (cur.parent == null) ? null : byName.get(cur.parent);
        }
        return chain;
    }

    /**
     * Posed twin of {@link #xformPoint}: cube bind rotation first, then
     * per level {@code p' = P + O + R(bind + pose) * S(s) * (p - P)}.
     * A fully identity level skips bit-for-bit.
     */
    private static double[] xformPointPosed(double[] p, ModelCube c, List<PosedLevel> chain) {
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
        for (PosedLevel l : chain) {
            if (l.rx == 0.0 && l.ry == 0.0 && l.rz == 0.0
                    && l.ox == 0.0 && l.oy == 0.0 && l.oz == 0.0 && l.s == 1.0) {
                continue;
            }
            double[] r = rotVec((x - l.px) * l.s, (y - l.py) * l.s, (z - l.pz) * l.s,
                    l.rx, l.ry, l.rz);
            x = r[0] + l.px + l.ox;
            y = r[1] + l.py + l.oy;
            z = r[2] + l.pz + l.oz;
        }
        return new double[] {x, y, z};
    }

    /** Posed twin of {@link #xformDir}: pose Euler only, renormalized. */
    private static double[] xformDirPosed(double nx, double ny, double nz,
            ModelCube c, List<PosedLevel> chain) {
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
        for (PosedLevel l : chain) {
            if (l.rx == 0.0 && l.ry == 0.0 && l.rz == 0.0) {
                continue;
            }
            double[] r = rotVec(x, y, z, l.rx, l.ry, l.rz);
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

    /**
     * World matrix of one chain (row-major 4x4): root leftmost,
     * {@code W = M_root * ... * M_leaf} with
     * {@code M = T(P + O) * R * S(s) * T(-P)}. Bind selects the bind
     * Euler and drops offset and scale.
     */
    private static double[] worldMatrix(List<PosedLevel> chain, boolean bind) {
        double[] w = identity4();
        for (int i = chain.size() - 1; i >= 0; i--) {
            PosedLevel l = chain.get(i);
            double rx = bind ? l.brx : l.rx;
            double ry = bind ? l.bry : l.ry;
            double rz = bind ? l.brz : l.rz;
            double ox = bind ? 0.0 : l.ox;
            double oy = bind ? 0.0 : l.oy;
            double oz = bind ? 0.0 : l.oz;
            double s = bind ? 1.0 : l.s;
            w = mul4(w, localMatrix(l.px, l.py, l.pz, rx, ry, rz, ox, oy, oz, s));
        }
        return w;
    }

    private static double[] identity4() {
        double[] m = new double[16];
        m[0] = 1.0;
        m[5] = 1.0;
        m[10] = 1.0;
        m[15] = 1.0;
        return m;
    }

    private static double[] mul4(double[] a, double[] b) {
        double[] out = new double[16];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                out[r * 4 + c] = a[r * 4] * b[c] + a[r * 4 + 1] * b[4 + c]
                        + a[r * 4 + 2] * b[8 + c] + a[r * 4 + 3] * b[12 + c];
            }
        }
        return out;
    }

    /**
     * {@code T(P + O) * R(rx,ry,rz) * S(s) * T(-P)} row-major. R matches
     * {@link #rotVec} exactly (Rz * Ry * Rx, right-handed).
     */
    private static double[] localMatrix(double px, double py, double pz,
            double rx, double ry, double rz, double ox, double oy, double oz, double s) {
        double ax = Math.toRadians(rx);
        double cx = Math.cos(ax);
        double sx = Math.sin(ax);
        double ay = Math.toRadians(ry);
        double cy = Math.cos(ay);
        double sy = Math.sin(ay);
        double az = Math.toRadians(rz);
        double cz = Math.cos(az);
        double sz = Math.sin(az);
        // Rx, Ry, Rz rows then R = Rz * Ry * Rx.
        double[] rxx = {1.0, 0.0, 0.0, 0.0, cx, -sx, 0.0, sx, cx};
        double[] ryy = {cy, 0.0, sy, 0.0, 1.0, 0.0, -sy, 0.0, cy};
        double[] rzz = {cz, -sz, 0.0, sz, cz, 0.0, 0.0, 0.0, 1.0};
        double[] r = mul3(rzz, mul3(ryy, rxx));
        double tx = px + ox;
        double ty = py + oy;
        double tz = pz + oz;
        double[] m = new double[16];
        m[0] = r[0] * s;
        m[1] = r[1] * s;
        m[2] = r[2] * s;
        m[3] = tx - s * (r[0] * px + r[1] * py + r[2] * pz);
        m[4] = r[3] * s;
        m[5] = r[4] * s;
        m[6] = r[5] * s;
        m[7] = ty - s * (r[3] * px + r[4] * py + r[5] * pz);
        m[8] = r[6] * s;
        m[9] = r[7] * s;
        m[10] = r[8] * s;
        m[11] = tz - s * (r[6] * px + r[7] * py + r[8] * pz);
        m[15] = 1.0;
        return m;
    }

    private static double[] mul3(double[] a, double[] b) {
        double[] out = new double[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c]
                        + a[r * 3 + 2] * b[6 + c];
            }
        }
        return out;
    }

    /**
     * Inverse of a rigid-plus-uniform-scale matrix {@code [sR | t]}
     * (the only shape {@link #worldMatrix} produces — eval refuses
     * zero/negative scales, so {@code s > 0} always).
     */
    static double[] invertRts(double[] m) {
        double sx = Math.sqrt(m[0] * m[0] + m[4] * m[4] + m[8] * m[8]);
        double[] out = new double[16];
        // (1/s) R^T rows.
        out[0] = m[0] / (sx * sx);
        out[1] = m[4] / (sx * sx);
        out[2] = m[8] / (sx * sx);
        out[4] = m[1] / (sx * sx);
        out[5] = m[5] / (sx * sx);
        out[6] = m[9] / (sx * sx);
        out[8] = m[2] / (sx * sx);
        out[9] = m[6] / (sx * sx);
        out[10] = m[10] / (sx * sx);
        out[3] = -(out[0] * m[3] + out[1] * m[7] + out[2] * m[11]);
        out[7] = -(out[4] * m[3] + out[5] * m[7] + out[6] * m[11]);
        out[11] = -(out[8] * m[3] + out[9] * m[7] + out[10] * m[11]);
        out[15] = 1.0;
        return out;
    }

    private static float[] toFloat(double[] m) {
        float[] out = new float[m.length];
        for (int i = 0; i < m.length; i++) {
            out[i] = (float) m[i];
        }
        return out;
    }
}
