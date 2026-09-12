package fr.iamacat.spi.model;

import fr.iamacat.spi.hit.AABBd;
import fr.iamacat.spi.hit.BoneBox;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Declarative Bedrock/Blockbench model: identifier, texture grid and bones
 * in file order. Single derivation point for the two bridge consumers:
 * bakeMesh feeds the instanced renderer VBO, boneBoxes feeds HitTester.
 *
 * <p>Units: Bedrock pixels in, block units out (PX_PER_BLOCK = 16).
 * Faces bake in bind pose, axis-aligned, CCW with outward normals —
 * same winding as the live-proven BOX_VERTICES it replaces
 * (bridge-1122 InstancedMeshRenderer). A box-anchor cube bakes the V1
 * planar projection of the cube rect (kept byte-identical); a per-face
 * cube bakes each present face from its own rect with the Bedrock
 * upper-left convention (v = 0 at the texture top, matching the
 * top-row-first upload the bridges perform — never flipped), an absent
 * face bakes nothing.
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
     * the full 36, byte-identical to V1.
     */
    public float[] bakeMesh() {
        float[] out = new float[emittedVertexCount() * VERTEX_STRIDE];
        int at = 0;
        for (ModelBone b : bones) {
            for (ModelCube c : b.cubes) {
                at = emitCube(out, at, c);
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
     * Derives one bind-pose BoneBox per non-empty bone (union of its cubes,
     * block units, entity-local). Bones without cubes contribute nothing.
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
        List<BoneBox> out = new ArrayList<BoneBox>();
        for (ModelBone b : bones) {
            if (b.cubes.isEmpty()) {
                continue;
            }
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (ModelCube c : b.cubes) {
                if (c.minX() < minX) {
                    minX = c.minX();
                }
                if (c.minY() < minY) {
                    minY = c.minY();
                }
                if (c.minZ() < minZ) {
                    minZ = c.minZ();
                }
                if (c.maxX() > maxX) {
                    maxX = c.maxX();
                }
                if (c.maxY() > maxY) {
                    maxY = c.maxY();
                }
                if (c.maxZ() > maxZ) {
                    maxZ = c.maxZ();
                }
            }
            out.add(new BoneBox(b.name, new AABBd(minX / PX_PER_BLOCK + x,
                    minY / PX_PER_BLOCK + y, minZ / PX_PER_BLOCK + z,
                    maxX / PX_PER_BLOCK + x, maxY / PX_PER_BLOCK + y,
                    maxZ / PX_PER_BLOCK + z)));
        }
        return Collections.unmodifiableList(out);
    }

    private int emitCube(float[] out, int at, ModelCube c) {
        double x0 = c.minX() / PX_PER_BLOCK;
        double y0 = c.minY() / PX_PER_BLOCK;
        double z0 = c.minZ() / PX_PER_BLOCK;
        double x1 = c.maxX() / PX_PER_BLOCK;
        double y1 = c.maxY() / PX_PER_BLOCK;
        double z1 = c.maxZ() / PX_PER_BLOCK;
        double sx = (c.maxX() - c.minX());
        double sy = (c.maxY() - c.minY());
        double sz = (c.maxZ() - c.minZ());
        // Faces: normal + 4 corners (a,b,c,d) wound so (a,b,c)+(a,c,d) face out.
        // Corner order mirrors the live BOX_VERTICES box exactly.
        // Per-face corner patterns ride the Bedrock upper-left convention
        // (hub decisions/MATOU_MODEL.md, V2 tranche): five faces read the
        // rect with its top-left at c/d, down anchors its origin at b.
        double[][] cornerPat = {{0.0, 1.0}, {1.0, 1.0}, {1.0, 0.0}, {0.0, 0.0}};
        double[][] downPat = {{1.0, 0.0}, {0.0, 0.0}, {0.0, 1.0}, {1.0, 1.0}};
        double[][] faces = {
            {0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, sx, sy},
            {0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, sx, sy},
            {0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, sx, sz},
            {0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, sx, sz},
            {1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, sz, sy},
            {-1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, sz, sy},
        };
        for (int fi = 0; fi < faces.length; fi++) {
            double[] f = faces[fi];
            double nx = f[0];
            double ny = f[1];
            double nz = f[2];
            double[] rect = null;
            if (c.faceUv != null) {
                rect = c.faceUv.get(ModelCube.FACES[fi]);
                if (rect == null) {
                    continue;
                }
            }
            double[][] pat = "down".equals(ModelCube.FACES[fi]) ? downPat : cornerPat;
            double[] cornerU = {0.0, f[15], f[15], 0.0};
            double[] cornerV = {0.0, 0.0, f[16], f[16]};
            double[][] p = {
                {f[3], f[4], f[5]}, {f[6], f[7], f[8]},
                {f[9], f[10], f[11]}, {f[12], f[13], f[14]},
            };
            int[] tri = {0, 1, 2, 0, 2, 3};
            for (int k : tri) {
                out[at++] = (float) p[k][0];
                out[at++] = (float) p[k][1];
                out[at++] = (float) p[k][2];
                if (rect != null) {
                    out[at++] = (float) ((rect[0] + pat[k][0] * rect[2]) / textureWidth);
                    out[at++] = (float) ((rect[1] + pat[k][1] * rect[3]) / textureHeight);
                } else {
                    out[at++] = (float) ((c.uvU + cornerU[k]) / textureWidth);
                    out[at++] = (float) ((c.uvV + cornerV[k]) / textureHeight);
                }
                out[at++] = (float) nx;
                out[at++] = (float) ny;
                out[at++] = (float) nz;
            }
        }
        return at;
    }
}
