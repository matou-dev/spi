package fr.iamacat.spi.model;

import fr.iamacat.spi.hit.BoneBox;
import fr.iamacat.spi.hit.HitTester;
import fr.iamacat.spi.hit.RayHit;
import fr.iamacat.spi.hit.Vec3d;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gate ModelCheck: Bedrock parse goldens, mesh bake fidelity (positions,
 * UVs, outward winding proven by cross-product comparateur), bone-box
 * derivation with HitTester resolution, and the E_MODEL_* refusal battery.
 * Java 8, zero MC imports.
 */
public final class ModelCheck {
    private ModelCheck() {}

    private static final String BEAST =
            "{\"format_version\": \"1.12.0\", \"minecraft:geometry\": [{"
            + "\"description\": {\"identifier\": \"geometry.my_beast\","
            + " \"texture_width\": 64, \"texture_height\": 64},"
            + "\"bones\": ["
            + "{\"name\": \"body\", \"pivot\": [0, 8, 0],"
            + " \"cubes\": [{\"origin\": [-8, 0, -8], \"size\": [16, 16, 16],"
            + " \"uv\": [0, 0]}]},"
            + "{\"name\": \"head\", \"parent\": \"body\", \"pivot\": [0, 20, 0],"
            + " \"cubes\": [{\"origin\": [-4, 16, -4], \"size\": [8, 8, 8],"
            + " \"uv\": [32, 0], \"inflate\": 0.5}]}"
            + "]}]}";

    private static final String PFACE =
            "{\"format_version\": \"1.12.0\", \"minecraft:geometry\": [{"
            + "\"description\": {\"identifier\": \"geometry.faced\","
            + " \"texture_width\": 64, \"texture_height\": 64},"
            + "\"bones\": [{\"name\": \"body\","
            + " \"cubes\": [{\"origin\": [0, 0, 0], \"size\": [16, 16, 16],"
            + " \"uv\": {"
            + "\"south\": {\"uv\": [0, 0], \"uv_size\": [16, 16]},"
            + "\"north\": {\"uv\": [8, 8]},"
            + "\"down\": {\"uv\": [0, 32]},"
            + "\"east\": {\"uv\": [32, 0], \"material_instance\": \"*\"},"
            + "\"west\": {\"uv\": [32, 16], \"uv_rotation\": 0}"
            + "}}]}]}]}";

    private static void check(boolean cond, String msg) {
        if (!cond) {
            System.err.println("FAIL model-check : " + msg);
            System.exit(1);
        }
    }

    private static void assertThrows(Runnable r, String expectedFragment) {
        try {
            r.run();
            System.err.println("FAIL model-check : expected exception containing <" + expectedFragment + ">");
            System.exit(1);
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (msg == null || !msg.contains(expectedFragment)) {
                System.err.println("FAIL model-check : got <" + msg + "> (want fragment <" + expectedFragment + ">)");
                System.exit(1);
            }
        }
    }

    public static void main(String[] args) {
        testParse();
        testBakeMesh();
        testPerFaceUv();
        testWinding();
        testBoneBoxes();
        testPlacedBoxes();
        testEmptyAndInflate();
        testRotationCompat();
        testBoneRotation();
        testHierarchy();
        testCubeRotation();
        testBoneInflate();
        testRefusals();
        testMolang();
        testAnimParse();
        testAnimEval();
        testPoseCompat();
        testPosedYaw();
        testMatrixCrossCheck();
        testSkinned();
        testAnimRefusals();
        System.out.println("ok model-check : all declarative-model tests passed");
    }

    private static void testParse() {
        MatouModel m = MatouModelParser.parse(BEAST);
        check(m.identifier.equals("geometry.my_beast"), "identifier");
        check(m.textureWidth == 64 && m.textureHeight == 64, "texture grid");
        check(m.bones.size() == 2, "two bones");
        check(m.bones.get(0).name.equals("body"), "body first");
        check(m.bones.get(1).name.equals("head"), "head second");
        check(m.bones.get(1).parent.equals("body"), "head parent");
        check(m.bones.get(0).cubes.size() == 1, "body one cube");
        check(m.cubeCount() == 2, "cube count");
    }

    private static void testBakeMesh() {
        MatouModel m = MatouModelParser.parse(BEAST);
        float[] v = m.bakeMesh();
        check(v.length == 2 * 36 * 8, "two cubes bake 72 vertices of stride 8");
        // Body cube: origin [-8,0,-8] size 16 -> block units x[-0.5,0.5] y[0,1] z[-0.5,0.5].
        // First vertex = front face corner a = (x0, y0, z1).
        check(Math.abs(v[0] - (-0.5f)) < 1e-6, "first pos x");
        check(Math.abs(v[1] - 0.0f) < 1e-6, "first pos y");
        check(Math.abs(v[2] - 0.5f) < 1e-6, "first pos z");
        check(Math.abs(v[3] - 0.0f) < 1e-6, "first uv u");
        check(Math.abs(v[4] - 0.0f) < 1e-6, "first uv v");
        check(Math.abs(v[5]) < 1e-6 && Math.abs(v[6]) < 1e-6
                && Math.abs(v[7] - 1.0f) < 1e-6, "first normal +Z");
        // Head cube front corner b carries the box uv anchor (32/64, 0) + face width 9px.
        int headAt = 36 * 8;
        check(Math.abs(v[headAt + 11] - ((32.0f + 9.0f) / 64.0f)) < 1e-5, "head uv u offset");
        check(MatouModel.VERTEX_STRIDE == 8, "stride matches renderer layout");
    }

    /**
     * Per-face unwrap goldens (V2 tranche): five faces bake their own
     * rects with the Bedrock upper-left convention, the omitted up face
     * bakes nothing, positions and winding stay box-identical. Cube is
     * 16^3 at the origin on a 64 grid, so px rects read straight off.
     */
    private static void testPerFaceUv() {
        MatouModel m = MatouModelParser.parse(PFACE);
        check(m.bones.get(0).cubes.get(0).faceUv.size() == 5, "five faces parsed");
        float[] v = m.bakeMesh();
        check(v.length == 5 * 6 * 8, "omitted up face bakes nothing (30 verts)");
        // South first vertex = corner a (0,0,1 blocks), rect [0,0,16,16]
        // with the shared pattern a -> (u0, v0+h).
        check(Math.abs(v[0]) < 1e-6 && Math.abs(v[1]) < 1e-6
                && Math.abs(v[2] - 1.0f) < 1e-6, "south first pos");
        check(Math.abs(v[3]) < 1e-6
                && Math.abs(v[4] - 0.25f) < 1e-6, "south first uv (0, 16/64)");
        // North first vertex = corner a (1,0,0 blocks), rect [8,8,16,16]
        // defaulted to the face box dims, a -> (8, 24)/64.
        int nAt = 6 * 8;
        check(Math.abs(v[nAt] - 1.0f) < 1e-6
                && Math.abs(v[nAt + 1]) < 1e-6
                && Math.abs(v[nAt + 2]) < 1e-6, "north first pos");
        check(Math.abs(v[nAt + 3] - 0.125f) < 1e-6
                && Math.abs(v[nAt + 4] - 0.375f) < 1e-6, "north first uv (8/64, 24/64)");
        // Down third present face, corner a (0,0,0 blocks), rect [0,32]
        // defaulted to (16,16), down pattern a -> (u0+w, v0).
        int dAt = 12 * 8;
        check(Math.abs(v[dAt + 3] - 0.25f) < 1e-6
                && Math.abs(v[dAt + 4] - 0.5f) < 1e-6, "down first uv (16/64, 32/64)");
        // East fourth present face, corner a (1,0,1 blocks), rect [32,0]
        // defaulted to (16,16), shared pattern a -> (u0, v0+h).
        int eAt = 18 * 8;
        check(Math.abs(v[eAt] - 1.0f) < 1e-6
                && Math.abs(v[eAt + 2] - 1.0f) < 1e-6, "east first pos");
        check(Math.abs(v[eAt + 3] - 0.5f) < 1e-6
                && Math.abs(v[eAt + 4] - 0.25f) < 1e-6, "east first uv (32/64, 16/64)");
        // West last face, corner d (0,1,0 blocks), rect [32,16],
        // shared pattern d -> (u0, v0) = the rect top-left.
        int wAt = 24 * 8 + 5 * 8;
        check(Math.abs(v[wAt + 3] - 0.5f) < 1e-6
                && Math.abs(v[wAt + 4] - 0.25f) < 1e-6, "west d uv is the rect origin");
    }

    private static void testWinding() {
        windingOutward(MatouModelParser.parse(BEAST).bakeMesh());
        windingOutward(MatouModelParser.parse(PFACE).bakeMesh());
    }

    private static void windingOutward(float[] v) {
        int tris = v.length / 8 / 3;
        for (int t = 0; t < tris; t++) {
            int b = t * 3 * 8;
            float ax = v[b];
            float ay = v[b + 1];
            float az = v[b + 2];
            float bx = v[b + 8];
            float by = v[b + 9];
            float bz = v[b + 10];
            float cx = v[b + 16];
            float cy = v[b + 17];
            float cz = v[b + 18];
            float ux = bx - ax;
            float uy = by - ay;
            float uz = bz - az;
            float wx = cx - ax;
            float wy = cy - ay;
            float wz = cz - az;
            float nx = uy * wz - uz * wy;
            float ny = uz * wx - ux * wz;
            float nz = ux * wy - uy * wx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            check(len > 1e-9, "triangle " + t + " non-degenerate");
            float dx = v[b + 5];
            float dy = v[b + 6];
            float dz = v[b + 7];
            float dot = (nx / len) * dx + (ny / len) * dy + (nz / len) * dz;
            check(dot > 0.999f, "triangle " + t + " winds outward (dot " + dot + ")");
        }
    }

    private static void testBoneBoxes() {
        MatouModel m = MatouModelParser.parse(BEAST);
        List<BoneBox> boxes = m.boneBoxes();
        check(boxes.size() == 2, "one box per non-empty bone");
        check(boxes.get(0).boneName.equals("body"), "body box");
        check(boxes.get(0).box.minX == -0.5 && boxes.get(0).box.maxX == 0.5
                && boxes.get(0).box.minY == 0.0 && boxes.get(0).box.maxY == 1.0
                && boxes.get(0).box.minZ == -0.5 && boxes.get(0).box.maxZ == 0.5,
                "body box = cube bounds / 16");
        // Head cube origin [-4,16,-4] size 8 inflate 0.5 -> [-4.5,15.5,-4.5]..[4.5,24.5,4.5] px.
        check(Math.abs(boxes.get(1).box.minX - (-4.5 / 16.0)) < 1e-9, "head minX inflated");
        check(Math.abs(boxes.get(1).box.maxY - (24.5 / 16.0)) < 1e-9, "head maxY inflated");
        // Ray from the front hits the body first (closer than the head).
        RayHit hit = HitTester.test(boxes,
                new Vec3d(0.0, 0.5, 5.0), new Vec3d(0.0, 0.0, -1.0), 10.0);
        check(hit != null && hit.boneName.equals("body"), "front ray resolves body");
        // Ray aimed at the head heights resolves the head bone.
        RayHit head = HitTester.test(boxes,
                new Vec3d(0.0, 1.25, 5.0), new Vec3d(0.0, 0.0, -1.0), 10.0);
        check(head != null && head.boneName.equals("head"), "high ray resolves head");
    }

    private static void testPlacedBoxes() {
        MatouModel m = MatouModelParser.parse(BEAST);
        List<BoneBox> at = m.placedBoxes(10.0, 64.0, -3.0);
        check(at.size() == 2, "placed keeps both bones");
        check(Math.abs(at.get(0).box.minX - 9.5) < 1e-9
                && Math.abs(at.get(0).box.minY - 64.0) < 1e-9
                && Math.abs(at.get(0).box.minZ - (-3.5)) < 1e-9
                && Math.abs(at.get(0).box.maxX - 10.5) < 1e-9
                && Math.abs(at.get(0).box.maxY - 65.0) < 1e-9
                && Math.abs(at.get(0).box.maxZ - (-2.5)) < 1e-9,
                "placed body = local box + entity origin (blocks, offset after /16)");
        // The placed head still resolves through the shared ray-tester.
        RayHit head = HitTester.test(at,
                new Vec3d(10.0, 65.25, 2.0), new Vec3d(0.0, 0.0, -1.0), 10.0);
        check(head != null && head.boneName.equals("head"), "placed head hit at entity origin");
        assertThrows(() -> m.placedBoxes(Double.NaN, 0, 0), "E_MODEL_PLACE:nan");
    }

    private static void testEmptyAndInflate() {
        MatouModel empty = MatouModelParser.parse(
                "{\"format_version\": \"1.12.0\", \"minecraft:geometry\": [{"
                + "\"description\": {\"identifier\": \"geometry.empty\","
                + " \"texture_width\": 16, \"texture_height\": 16}}]}");
        check(empty.bones.isEmpty(), "missing bones = empty model");
        check(empty.bakeMesh().length == 0, "empty mesh");
        check(empty.boneBoxes().isEmpty(), "empty boxes");
        MatouModel bare = MatouModelParser.parse(
                "{\"format_version\": \"1.12.0\", \"minecraft:geometry\": [{"
                + "\"description\": {\"identifier\": \"geometry.bare\","
                + " \"texture_width\": 16, \"texture_height\": 16},"
                + "\"bones\": [{\"name\": \"lone\","
                + " \"cubes\": [{\"origin\": [0, 0, 0], \"size\": [16, 16, 16]}]}]}]}");
        check(bare.boneBoxes().size() == 1, "defaults (uv/inflate/pivot) accepted");
    }

    /**
     * Rotation compat comparateur: explicit zero rotations bake exactly
     * the unrotated bytes (mesh float-for-float, boxes field-for-field).
     * Bridges re-pin without re-proof on the strength of this test.
     */
    private static void testRotationCompat() {
        MatouModel plain = MatouModelParser.parse(BEAST);
        MatouModel zeroed = MatouModelParser.parse(
                "{\"format_version\": \"1.12.0\", \"minecraft:geometry\": [{"
                + "\"description\": {\"identifier\": \"geometry.my_beast\","
                + " \"texture_width\": 64, \"texture_height\": 64},"
                + "\"bones\": ["
                + "{\"name\": \"body\", \"pivot\": [0, 8, 0], \"rotation\": [0, 0, 0],"
                + " \"cubes\": [{\"origin\": [-8, 0, -8], \"size\": [16, 16, 16],"
                + " \"uv\": [0, 0]}]},"
                + "{\"name\": \"head\", \"parent\": \"body\", \"pivot\": [0, 20, 0],"
                + " \"rotation\": [0, 0, 0],"
                + " \"cubes\": [{\"origin\": [-4, 16, -4], \"size\": [8, 8, 8],"
                + " \"uv\": [32, 0], \"inflate\": 0.5}]}"
                + "]}]}");
        check(Arrays.equals(plain.bakeMesh(), zeroed.bakeMesh()), "zero rotation bakes identical mesh");
        List<BoneBox> a = plain.boneBoxes();
        List<BoneBox> b = zeroed.boneBoxes();
        check(a.size() == b.size(), "zero rotation keeps box count");
        for (int i = 0; i < a.size(); i++) {
            check(a.get(i).boneName.equals(b.get(i).boneName)
                    && a.get(i).box.equals(b.get(i).box), "zero rotation keeps boxes");
        }
    }

    /**
     * Bone yaw golden (bind-pose rotation tranche): a 2x1x1 slab yawed
     * 90 degrees about its own center stands 1x1x2. Positions, normals
     * and the conservative box all ride the rotation.
     */
    private static void testBoneRotation() {
        MatouModel m = MatouModelParser.parse(
                "{\"format_version\": \"1.12.0\", \"minecraft:geometry\": [{"
                + "\"description\": {\"identifier\": \"geometry.yaw\","
                + " \"texture_width\": 64, \"texture_height\": 64},"
                + "\"bones\": [{\"name\": \"slab\", \"pivot\": [16, 8, 8],"
                + " \"rotation\": [0, 90, 0],"
                + " \"cubes\": [{\"origin\": [0, 0, 0], \"size\": [32, 16, 16],"
                + " \"uv\": [0, 0]}]}]}]}");
        check(m.bones.get(0).rotY == 90.0, "yaw parsed");
        float[] v = m.bakeMesh();
        check(v.length == 36 * 8, "rotated cube keeps 36 verts");
        // South corner a (0,0,16)px swings to (24,0,24)px = (1.5, 0, 1.5) blocks.
        check(Math.abs(v[0] - 1.5f) < 1e-6, "yawed first pos x");
        check(Math.abs(v[1]) < 1e-6, "yawed first pos y");
        check(Math.abs(v[2] - 1.5f) < 1e-6, "yawed first pos z");
        // South normal +Z yaws to +X.
        check(Math.abs(v[5] - 1.0f) < 1e-6
                && Math.abs(v[6]) < 1e-6 && Math.abs(v[7]) < 1e-6, "yawed south normal +X");
        windingOutward(v);
        List<BoneBox> boxes = m.boneBoxes();
        check(boxes.size() == 1, "one rotated box");
        check(Math.abs(boxes.get(0).box.minX - 0.5) < 1e-9
                && Math.abs(boxes.get(0).box.maxX - 1.5) < 1e-9
                && Math.abs(boxes.get(0).box.minY) < 1e-9
                && Math.abs(boxes.get(0).box.maxY - 1.0) < 1e-9
                && Math.abs(boxes.get(0).box.minZ - (-0.5)) < 1e-9
                && Math.abs(boxes.get(0).box.maxZ - 1.5) < 1e-9,
                "yawed box = 1x1x2 blocks");
        // The high ray still resolves through the shared ray-tester.
        RayHit hit = HitTester.test(boxes,
                new Vec3d(1.0, 0.5, 5.0), new Vec3d(0.0, 0.0, -1.0), 10.0);
        check(hit != null && hit.boneName.equals("slab"), "yawed box hit");
    }

    /**
     * Hierarchy golden: a parented bone rides its parent's rotation —
     * the child cube orbits the parent pivot, it never stays behind.
     */
    private static void testHierarchy() {
        MatouModel m = MatouModelParser.parse(
                "{\"format_version\": \"1.12.0\", \"minecraft:geometry\": [{"
                + "\"description\": {\"identifier\": \"geometry.arm\","
                + " \"texture_width\": 64, \"texture_height\": 64},"
                + "\"bones\": ["
                + "{\"name\": \"root\", \"pivot\": [0, 0, 0], \"rotation\": [0, 90, 0]},"
                + "{\"name\": \"arm\", \"parent\": \"root\", \"pivot\": [16, 0, 0],"
                + " \"cubes\": [{\"origin\": [16, 0, 0], \"size\": [16, 16, 16],"
                + " \"uv\": [0, 0]}]}]}]}");
        float[] v = m.bakeMesh();
        check(v.length == 36 * 8, "parented cube keeps 36 verts");
        windingOutward(v);
        List<BoneBox> boxes = m.boneBoxes();
        check(boxes.size() == 1 && boxes.get(0).boneName.equals("arm"), "empty parent bakes no box");
        // Cube x[16,32] y[0,16] z[0,16] orbits the root yaw to x[0,16] z[-32,-16]px.
        check(Math.abs(boxes.get(0).box.minX) < 1e-9
                && Math.abs(boxes.get(0).box.maxX - 1.0) < 1e-9
                && Math.abs(boxes.get(0).box.minY) < 1e-9
                && Math.abs(boxes.get(0).box.maxY - 1.0) < 1e-9
                && Math.abs(boxes.get(0).box.minZ - (-2.0)) < 1e-9
                && Math.abs(boxes.get(0).box.maxZ - (-1.0)) < 1e-9,
                "child cube orbits the parent pivot");
    }

    /**
     * Cube-level pitch golden: rotation about the default box center
     * stands a 1x2x1 column into a 1x1x2 beam.
     */
    private static void testCubeRotation() {
        MatouModel m = MatouModelParser.parse(
                "{\"format_version\": \"1.12.0\", \"minecraft:geometry\": [{"
                + "\"description\": {\"identifier\": \"geometry.beam\","
                + " \"texture_width\": 64, \"texture_height\": 64},"
                + "\"bones\": [{\"name\": \"b\","
                + " \"cubes\": [{\"origin\": [0, 0, 0], \"size\": [16, 32, 16],"
                + " \"uv\": [0, 0], \"rotation\": [90, 0, 0]}]}]}]}");
        float[] v = m.bakeMesh();
        windingOutward(v);
        List<BoneBox> boxes = m.boneBoxes();
        check(Math.abs(boxes.get(0).box.minX) < 1e-9
                && Math.abs(boxes.get(0).box.maxX - 1.0) < 1e-9
                && Math.abs(boxes.get(0).box.minY - 0.5) < 1e-9
                && Math.abs(boxes.get(0).box.maxY - 1.5) < 1e-9
                && Math.abs(boxes.get(0).box.minZ - (-0.5)) < 1e-9
                && Math.abs(boxes.get(0).box.maxZ - 1.5) < 1e-9,
                "pitched cube = 1x1x2 beam");
    }

    /** Bone inflate funds cubes that carry none (cube inflate still wins). */
    private static void testBoneInflate() {
        MatouModel m = MatouModelParser.parse(
                "{\"format_version\": \"1.12.0\", \"minecraft:geometry\": [{"
                + "\"description\": {\"identifier\": \"geometry.puff\","
                + " \"texture_width\": 64, \"texture_height\": 64},"
                + "\"bones\": [{\"name\": \"puff\", \"inflate\": 1.0,"
                + " \"cubes\": [{\"origin\": [0, 0, 0], \"size\": [16, 16, 16]}]}]}]}");
        List<BoneBox> boxes = m.boneBoxes();
        check(boxes.get(0).box.minX == -1.0 / 16.0
                && boxes.get(0).box.maxX == 17.0 / 16.0
                && boxes.get(0).box.minY == -1.0 / 16.0
                && boxes.get(0).box.maxY == 17.0 / 16.0,
                "bone inflate grows bare cubes");
    }

    private static void testRefusals() {

        assertThrows(() -> MatouModelParser.parse(null), "E_MODEL_JSON:empty");
        assertThrows(() -> MatouModelParser.parse("  "), "E_MODEL_JSON:empty");
        assertThrows(() -> MatouModelParser.parse("{nope"), "E_MODEL_JSON:syntax");
        assertThrows(() -> MatouModelParser.parse("[1, 2]"), "E_MODEL_JSON:type");
        assertThrows(() -> MatouModelParser.parse("{}"), "E_MODEL_VERSION:missing");
        assertThrows(() -> MatouModelParser.parse("{\"format_version\": \"1.12.0\"}"),
                "E_MODEL_GEOMETRY:missing");
        assertThrows(() -> MatouModelParser.parse("{\"format_version\": \"1.12.0\","
                + " \"minecraft:geometry\": []}"), "E_MODEL_GEOMETRY:missing");
        assertThrows(() -> MatouModelParser.parse("{\"format_version\": \"1.12.0\","
                + " \"minecraft:geometry\": [{\"bones\": []}]}"), "E_MODEL_IDENTIFIER:missing");
        assertThrows(() -> MatouModelParser.parse("{\"format_version\": \"1.12.0\","
                + " \"minecraft:geometry\": [{\"description\": {\"identifier\": \"g.x\"}}]}"),
                "E_MODEL_TEXTURE:shape");
        assertThrows(() -> MatouModelParser.parse("{\"format_version\": \"1.12.0\","
                + " \"minecraft:geometry\": [{\"description\": {\"identifier\": \"g.x\","
                + " \"texture_width\": 0, \"texture_height\": 64}}]}"), "E_MODEL_TEXTURE:shape");
        String head = "{\"format_version\": \"1.12.0\","
                + " \"minecraft:geometry\": [{\"description\": {\"identifier\": \"g.x\","
                + " \"texture_width\": 64, \"texture_height\": 64}, \"bones\": [";
        String tail = "]}]}";
        assertThrows(() -> MatouModelParser.parse(head
                + "{\"cubes\": []}" + tail), "E_MODEL_BONE:empty");
        assertThrows(() -> MatouModelParser.parse(head
                + "{\"name\": \"a\"}, {\"name\": \"a\"}" + tail), "E_MODEL_BONE:duplicate");
        assertThrows(() -> MatouModelParser.parse(head
                + "{\"name\": \"a\", \"parent\": \"ghost\"}" + tail), "E_MODEL_BONE:parent");
        assertThrows(() -> MatouModelParser.parse(head
                + "{\"name\": \"a\", \"pivot\": [0, 0]}" + tail), "E_MODEL_BONE:pivot");
        assertThrows(() -> MatouModelParser.parse(head
                + "{\"name\": \"a\", \"cubes\": [{\"size\": [1, 1, 1]}]}" + tail),
                "E_MODEL_CUBE:origin");
        assertThrows(() -> MatouModelParser.parse(head
                + "{\"name\": \"a\", \"cubes\": [{\"origin\": [0, 0, 0],"
                + " \"size\": [1, 0, 1]}]}" + tail), "E_MODEL_CUBE:size");
        assertThrows(() -> MatouModelParser.parse(head
                + "{\"name\": \"a\", \"cubes\": [{\"origin\": [0, 0, 0],"
                + " \"size\": [1, 1, 1], \"uv\": [0]}]}" + tail), "E_MODEL_CUBE:uv");
        assertThrows(() -> MatouModelParser.parse("{\"format_version\": \"1.12.0\","
                + " \"minecraft:geometry\": [{\"description\": {\"identifier\": \"g.x\","
                + " \"texture_width\": 64, \"texture_height\": 64}, \"bones\": ["
                + "{\"name\": \"a\", \"cubes\": \"nope\"}]}]}"), "E_MODEL_CUBE:shape");
        String faceHead = "{\"format_version\": \"1.12.0\","
                + " \"minecraft:geometry\": [{\"description\": {\"identifier\": \"g.x\","
                + " \"texture_width\": 64, \"texture_height\": 64}, \"bones\": ["
                + "{\"name\": \"a\", \"cubes\": [{\"origin\": [0, 0, 0],"
                + " \"size\": [16, 16, 16], \"uv\": {";
        String faceTail = "}}]}]}]}";
        assertThrows(() -> MatouModelParser.parse(faceHead
                + "\"top\": {\"uv\": [0, 0]}" + faceTail), "E_MODEL_FACE:shape");
        assertThrows(() -> MatouModelParser.parse(faceHead
                + "\"north\": [0, 0]" + faceTail), "E_MODEL_FACE:shape");
        assertThrows(() -> MatouModelParser.parse(faceHead
                + "\"north\": {}" + faceTail), "E_MODEL_FACE:shape");
        assertThrows(() -> MatouModelParser.parse(faceHead
                + "\"north\": {\"uv\": [0, 0], \"bogus\": 1}" + faceTail),
                "E_MODEL_FACE:shape");
        assertThrows(() -> MatouModelParser.parse(faceHead
                + "\"north\": {\"uv\": [0, 0], \"uv_size\": [0, 16]}" + faceTail),
                "E_MODEL_FACE:size");
        assertThrows(() -> MatouModelParser.parse(faceHead
                + "\"north\": {\"uv\": [0, 0], \"uv_rotation\": 90}" + faceTail),
                "E_MODEL_FACE:rotation");
        assertThrows(() -> MatouModelParser.parse(faceHead
                + "\"north\": {\"uv\": [-1, 0]}" + faceTail), "E_MODEL_FACE:uv");
        assertThrows(() -> MatouModelParser.parse(faceHead
                + "\"north\": {\"uv\": [56, 56]}" + faceTail), "E_MODEL_FACE:uv");
        assertThrows(() -> MatouModelParser.parse(head
                + "{\"name\": \"a\", \"rotation\": [0, 0]}" + tail), "E_MODEL_BONE:rotation");
        assertThrows(() -> MatouModelParser.parse(head
                + "{\"name\": \"a\", \"rotation\": \"nope\"}" + tail), "E_MODEL_BONE:rotation");
        assertThrows(() -> MatouModelParser.parse(head
                + "{\"name\": \"a\", \"poly_mesh\": {}}" + tail), "E_MODEL_BONE:shape");
        assertThrows(() -> MatouModelParser.parse(head
                + "{\"name\": \"a\", \"texture_meshes\": []}" + tail), "E_MODEL_BONE:shape");
        assertThrows(() -> MatouModelParser.parse(head
                + "{\"name\": \"a\", \"parent\": \"b\"},"
                + "{\"name\": \"b\", \"parent\": \"a\"}" + tail), "E_MODEL_BONE:parent");
        assertThrows(() -> MatouModelParser.parse(head
                + "{\"name\": \"a\", \"cubes\": [{\"origin\": [0, 0, 0],"
                + " \"size\": [1, 1, 1], \"rotation\": [0, 0]}]}" + tail),
                "E_MODEL_CUBE:rotation");
        assertThrows(() -> MatouModelParser.parse(head
                + "{\"name\": \"a\", \"cubes\": [{\"origin\": [0, 0, 0],"
                + " \"size\": [1, 1, 1], \"pivot\": [0, 0]}]}" + tail),
                "E_MODEL_CUBE:pivot");
    }

    private static final String WALK =
            "{\"format_version\": \"1.10.0\", \"animations\": {"
            + "\"animation.beast.walk\": {\"loop\": true,"
            + " \"bones\": {"
            + "\"leg\": {\"rotation\": [\"math.cos(query.modified_distance_moved * 38.17) * 80.0\","
            + " 0.0, 0.0]},"
            + "\"body\": {\"position\": {\"0.0\": [0.0, 0.0, 0.0],"
            + " \"0.5\": [0.0, 2.0, 0.0]}}"
            + "}}}}";

    private static Molang.Ctx ctx(double t, double life, double dist) {
        return new Molang.Ctx(t, life, dist, 0.05,
                Collections.singletonMap("boost", Double.valueOf(3.0)));
    }

    private static void checkNear(double got, double want, double eps, String msg) {
        check(Math.abs(got - want) <= eps, msg + " (got " + got + ", want " + want + ")");
    }

    /**
     * MOLANG goldens (animation tranche): precedence, the four queries,
     * variable default-0, frozen math incl. the Math. alias, ternary,
     * short-circuit logic, plus the refusal battery.
     */
    private static void testMolang() {
        check(Molang.eval("1 + 2 * 3", Molang.zeroCtx()) == 7.0, "precedence");
        check(Molang.eval("(2 + 3) * 4", Molang.zeroCtx()) == 20.0, "parens");
        check(Molang.eval("10 % 3", Molang.zeroCtx()) == 1.0, "modulo");
        check(Molang.eval("query.anim_time + query.life_time",
                ctx(2.0, 100.0, 5.0)) == 102.0, "queries");
        checkNear(Molang.eval("query.modified_distance_moved * 2 + query.delta_time",
                ctx(0.0, 0.0, 5.0)), 10.05, 1e-12, "walk driver query");
        check(Molang.eval("variable.boost * 2", ctx(0.0, 0.0, 0.0)) == 6.0, "variable");
        check(Molang.eval("variable.missing + 1", ctx(0.0, 0.0, 0.0)) == 1.0, "variable default-0");
        checkNear(Molang.eval("math.sin(1.5707963267948966)", Molang.zeroCtx()),
                1.0, 1e-12, "math.sin radians");
        check(Molang.eval("Math.cos(0)", Molang.zeroCtx()) == 1.0, "Math. alias");
        check(Molang.eval("query.life_time > 50 ? 10 : 20", ctx(0.0, 100.0, 0.0)) == 10.0,
                "ternary");
        check(Molang.eval("1 > 2 || 3 < 4", Molang.zeroCtx()) == 1.0, "logic or");
        check(Molang.eval("1 && 2", Molang.zeroCtx()) == 1.0, "logic and");
        check(Molang.eval("0 || 0", Molang.zeroCtx()) == 0.0, "logic nor");
        check(Molang.eval("!0", Molang.zeroCtx()) == 1.0, "not");
        check(Molang.eval("math.clamp(5, 0, 3)", Molang.zeroCtx()) == 3.0, "clamp");
        check(Molang.eval("math.lerp(0, 10, 0.25)", Molang.zeroCtx()) == 2.5, "lerp");
        check(Molang.eval("math.max(1, 7)", Molang.zeroCtx()) == 7.0, "max");
        checkNear(Molang.eval("math.pi", Molang.zeroCtx()), Math.PI, 0.0, "math.pi");
        assertThrows(() -> Molang.eval("", Molang.zeroCtx()), "E_ANIM_MOLANG:empty");
        assertThrows(() -> Molang.eval("1 +", Molang.zeroCtx()), "E_ANIM_MOLANG:syntax");
        assertThrows(() -> Molang.eval("query.is_baby", Molang.zeroCtx()), "E_ANIM_MOLANG:query");
        assertThrows(() -> Molang.eval("this", Molang.zeroCtx()), "E_ANIM_MOLANG:this");
        assertThrows(() -> Molang.eval("temp.x", Molang.zeroCtx()), "E_ANIM_MOLANG:scope");
        assertThrows(() -> Molang.eval("math.tan(1)", Molang.zeroCtx()), "E_ANIM_MOLANG:fn");
        assertThrows(() -> Molang.eval("math.sin(1, 2)", Molang.zeroCtx()), "E_ANIM_MOLANG:syntax");
        assertThrows(() -> Molang.eval("x = 1", Molang.zeroCtx()), "E_ANIM_MOLANG:assign");
        assertThrows(() -> Molang.eval("foo(1)", Molang.zeroCtx()), "E_ANIM_MOLANG:fn");
        assertThrows(() -> Molang.eval("query.life_time(", Molang.zeroCtx()),
                "E_ANIM_MOLANG:syntax");
    }

    /** Animation document parse goldens: walk clip, loop modes, scale. */
    private static void testAnimParse() {
        Map<String, MatouAnimation> clips = MatouAnimationParser.parse(WALK);
        check(clips.size() == 1, "one clip");
        MatouAnimation walk = clips.get("animation.beast.walk");
        check(walk != null, "clip by name");
        check(walk.loop == MatouAnimation.Loop.LOOP, "loop true");
        check(walk.length == 0.5, "length defaults to last key");
        check(walk.bones.size() == 2, "two animated bones");
        check(walk.bones.get("leg").rotation != null
                && walk.bones.get("leg").position == null, "leg rotation only");
        Map<String, MatouAnimation> hold = MatouAnimationParser.parse(
                "{\"format_version\": \"1.10.0\", \"animations\": {"
                + "\"animation.x.idle\": {\"loop\": \"hold_on_last_frame\","
                + " \"animation_length\": 2.0,"
                + " \"bones\": {\"b\": {\"scale\": [2.0]}}}}}");
        MatouAnimation idle = hold.get("animation.x.idle");
        check(idle.loop == MatouAnimation.Loop.HOLD, "hold parses");
        check(idle.length == 2.0, "explicit length kept");
    }

    /** Single-clip eval goldens: continuity, lerp, wrap, clamp, update. */
    private static void testAnimEval() {
        MatouAnimation walk = MatouAnimationParser.parse(WALK).get("animation.beast.walk");
        MatouAnimation.BonePose at0 = walk.evaluate(0.0, ctx(0.0, 0.0, 0.0)).bones.get("leg");
        checkNear(at0.rotX, 80.0, 1e-9, "walk starts at +80");
        double neg = Math.PI / 38.17;
        MatouAnimation.BonePose atNeg = walk.evaluate(0.0, ctx(0.0, 0.0, neg)).bones.get("leg");
        checkNear(atNeg.rotX, -80.0, 1e-9, "walk half-period at -80");
        MatouAnimation.BonePose mid = walk.evaluate(0.25, ctx(0.25, 0.0, 0.0)).bones.get("body");
        checkNear(mid.posY, 1.0, 1e-9, "keyframe midpoint lerps exact");
        MatouAnimation.BonePose wrapped = walk.evaluate(0.6, ctx(0.6, 0.0, 0.0)).bones.get("body");
        checkNear(wrapped.posY, 0.4, 1e-9, "loop wraps t mod length");
        Map<String, MatouAnimation> once = MatouAnimationParser.parse(
                "{\"format_version\": \"1.10.0\", \"animations\": {"
                + "\"animation.x.once\": {\"loop\": false,"
                + " \"bones\": {\"b\": {\"position\": {\"0.0\": [0.0, 0.0, 0.0],"
                + " \"1.0\": [0.0, 4.0, 0.0]}}}}}}");
        MatouAnimation.BonePose frozen =
                once.get("animation.x.once").evaluate(99.0, ctx(99.0, 0.0, 0.0)).bones.get("b");
        checkNear(frozen.posY, 4.0, 1e-9, "clamp freezes at the last key");
        Map<String, MatouAnimation> upd = MatouAnimationParser.parse(
                "{\"format_version\": \"1.10.0\", \"animations\": {"
                + "\"animation.x.tick\": {\"anim_time_update\": \"query.life_time * 2\","
                + " \"bones\": {\"b\": {\"rotation\": [\"query.anim_time\", 0.0, 0.0]}}}}}");
        MatouAnimation.BonePose driven =
                upd.get("animation.x.tick").evaluate(1.0, ctx(1.0, 3.0, 0.0)).bones.get("b");
        checkNear(driven.rotX, 6.0, 1e-9, "anim_time_update drives the clip time");
        Map<String, MatouAnimation> nan = MatouAnimationParser.parse(
                "{\"format_version\": \"1.10.0\", \"animations\": {"
                + "\"animation.x.bad\": {\"bones\": {\"b\": {\"rotation\": [\"0.0/0.0\","
                + " 0.0, 0.0]}}}}}");
        assertThrows(() -> nan.get("animation.x.bad").evaluate(0.0, ctx(0.0, 0.0, 0.0)),
                "E_ANIM_MOLANG:nan");
        Map<String, MatouAnimation> skew = MatouAnimationParser.parse(
                "{\"format_version\": \"1.10.0\", \"animations\": {"
                + "\"animation.x.skew\": {\"bones\": {\"b\": {\"scale\": [\"1\", \"2\", \"1\"]}}}}}");
        assertThrows(() -> skew.get("animation.x.skew").evaluate(0.0, ctx(0.0, 0.0, 0.0)),
                "E_ANIM_CHANNEL:scale");
        assertThrows(() -> walk.evaluate(-1.0, ctx(0.0, 0.0, 0.0)), "E_ANIM_TIME");
    }

    /**
     * Pose compat comparateur: the identity pose bakes the bind bytes
     * (mesh float-for-float, boxes field-for-field) and yields identity
     * delta matrices. Bridges re-pin on this strength.
     */
    private static void testPoseCompat() {
        MatouModel m = MatouModelParser.parse(BEAST);
        MatouAnimation.AnimPose id = MatouAnimation.AnimPose.identity();
        check(Arrays.equals(m.bakeMesh(), m.bakePosedMesh(id)), "identity pose bakes identical mesh");
        List<BoneBox> a = m.boneBoxes();
        List<BoneBox> b = m.posedBoxes(id);
        check(a.size() == b.size(), "identity pose keeps box count");
        for (int i = 0; i < a.size(); i++) {
            check(a.get(i).boneName.equals(b.get(i).boneName)
                    && a.get(i).box.equals(b.get(i).box), "identity pose keeps boxes");
        }
        Map<String, float[]> deltas = m.poseDeltaMatrices(id);
        check(deltas.size() == 2, "one delta per bone");
        for (float[] d : deltas.values()) {
            for (int i = 0; i < 16; i++) {
                double want = (i == 0 || i == 5 || i == 10 || i == 15) ? 1.0 : 0.0;
                check(Math.abs(d[i] - want) < 1e-9, "identity pose delta is identity");
            }
        }
    }

    private static MatouAnimation.AnimPose yawHead(double yawDeg) {
        Map<String, MatouAnimation.BonePose> pm =
                new LinkedHashMap<String, MatouAnimation.BonePose>();
        pm.put("head", new MatouAnimation.BonePose(0.0, yawDeg, 0.0,
                0.0, 0.0, 0.0, 1.0));
        return new MatouAnimation.AnimPose(pm);
    }

    /**
     * Posed yaw golden: a 45-degree head pose widens the head box to
     * 4.5*sqrt(2) px exactly (same conservative cover as the rotated
     * proof asset), leaves the body alone, and the head delta carries
     * the yaw rotation.
     */
    private static void testPosedYaw() {
        MatouModel m = MatouModelParser.parse(BEAST);
        MatouAnimation.AnimPose pose = yawHead(45.0);
        List<BoneBox> boxes = m.posedBoxes(pose);
        check(boxes.size() == 2, "posed keeps both boxes");
        double half = 4.5 * Math.sqrt(2.0) / 16.0;
        check(Math.abs(boxes.get(1).box.minX - (-half)) < 1e-9, "posed head minX widened");
        check(Math.abs(boxes.get(1).box.maxX - half) < 1e-9, "posed head maxX widened");
        check(Math.abs(boxes.get(1).box.minZ - (-half)) < 1e-9, "posed head minZ widened");
        check(Math.abs(boxes.get(1).box.maxZ - half) < 1e-9, "posed head maxZ widened");
        check(Math.abs(boxes.get(1).box.minY - (15.5 / 16.0)) < 1e-9, "posed head minY kept");
        check(Math.abs(boxes.get(1).box.maxY - (24.5 / 16.0)) < 1e-9, "posed head maxY kept");
        List<BoneBox> bind = m.boneBoxes();
        check(boxes.get(0).box.equals(bind.get(0).box), "unposed body box untouched");
        windingOutward(m.bakePosedMesh(pose));
        Map<String, float[]> deltas = m.poseDeltaMatrices(pose);
        float[] body = deltas.get("body");
        check(Math.abs(body[0] - 1.0f) < 1e-6 && Math.abs(body[5] - 1.0f) < 1e-6
                && Math.abs(body[10] - 1.0f) < 1e-6, "unposed bone delta is identity");
        float[] head = deltas.get("head");
        double c = Math.cos(Math.toRadians(45.0));
        double s = Math.sin(Math.toRadians(45.0));
        check(Math.abs(head[0] - c) < 1e-6 && Math.abs(head[10] - c) < 1e-6
                && Math.abs(head[2] - s) < 1e-6 && Math.abs(head[8] + s) < 1e-6,
                "posed head delta carries the yaw");
        check(Math.abs(head[3]) < 1e-6 && Math.abs(head[7]) < 1e-6
                && Math.abs(head[11]) < 1e-6, "yaw about the pivot keeps no offset");
    }

    /**
     * Delta cross-check: delta-transformed bind corners equal the posed
     * oracle mesh within 1e-5 — this is what lets the GPU path trust
     * the matrices without ever uploading a rebaked mesh.
     */
    private static void testMatrixCrossCheck() {
        MatouModel m = MatouModelParser.parse(BEAST);
        MatouAnimation.AnimPose pose = yawHead(30.0);
        float[] bind = m.bakeMesh();
        float[] skinned = m.bakeSkinnedMesh();
        float[] oracle = m.bakePosedMesh(pose);
        Map<String, float[]> deltas = m.poseDeltaMatrices(pose);
        check(bind.length == oracle.length, "oracle keeps the stride-8 layout");
        int verts = bind.length / 8;
        for (int v = 0; v < verts; v++) {
            int bone = (int) skinned[v * 9 + 8];
            float[] d = deltas.get(m.bones.get(bone).name);
            double x = bind[v * 8];
            double y = bind[v * 8 + 1];
            double z = bind[v * 8 + 2];
            double wx = d[0] * x + d[1] * y + d[2] * z + d[3];
            double wy = d[4] * x + d[5] * y + d[6] * z + d[7];
            double wz = d[8] * x + d[9] * y + d[10] * z + d[11];
            check(Math.abs(wx - oracle[v * 8]) < 1e-5
                    && Math.abs(wy - oracle[v * 8 + 1]) < 1e-5
                    && Math.abs(wz - oracle[v * 8 + 2]) < 1e-5,
                    "delta-skinned vertex " + v + " matches the oracle");
        }
    }

    /** Skinned layout golden: stride 9, bind positions, bone indices. */
    private static void testSkinned() {
        MatouModel m = MatouModelParser.parse(BEAST);
        float[] bind = m.bakeMesh();
        float[] skinned = m.bakeSkinnedMesh();
        check(skinned.length == 2 * 36 * 9, "skinned keeps 72 vertices of stride 9");
        check(skinned[0] == bind[0]
                && skinned[1] == bind[1]
                && skinned[2] == bind[2], "skinned first pos is the bind pos");
        check(skinned[3] == bind[3]
                && skinned[4] == bind[4], "skinned first uv is the bind uv");
        check(skinned[8] == 0.0f, "body vertices ride bone 0");
        int headAt = 36 * 9;
        check(skinned[headAt + 8] == 1.0f, "head vertices ride bone 1");
        windingOutward9(skinned);
    }

    private static void windingOutward9(float[] v) {
        int tris = v.length / 9 / 3;
        for (int t = 0; t < tris; t++) {
            int b = t * 3 * 9;
            float ax = v[b];
            float ay = v[b + 1];
            float az = v[b + 2];
            float bx = v[b + 9];
            float by = v[b + 10];
            float bz = v[b + 11];
            float cx = v[b + 18];
            float cy = v[b + 19];
            float cz = v[b + 20];
            float ux = bx - ax;
            float uy = by - ay;
            float uz = bz - az;
            float wx = cx - ax;
            float wy = cy - ay;
            float wz = cz - az;
            float nx = uy * wz - uz * wy;
            float ny = uz * wx - ux * wz;
            float nz = ux * wy - uy * wx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            check(len > 1e-9, "skinned triangle " + t + " non-degenerate");
            float dx = v[b + 5];
            float dy = v[b + 6];
            float dz = v[b + 7];
            float dot = (nx / len) * dx + (ny / len) * dy + (nz / len) * dz;
            check(dot > 0.999f, "skinned triangle " + t + " winds outward");
        }
    }

    /** Refusal battery over the animation document catalog. */
    private static void testAnimRefusals() {
        assertThrows(() -> MatouAnimationParser.parse("{}"), "E_ANIM_DOC:missing");
        assertThrows(() -> MatouAnimationParser.parse("{\"format_version\": \"1.10.0\"}"),
                "E_ANIM_CLIP:empty");
        assertThrows(() -> MatouAnimationParser.parse("{\"format_version\": \"1.10.0\","
                + " \"animations\": {}, \"extra\": 1}"), "E_ANIM_DOC:shape");
        String head = "{\"format_version\": \"1.10.0\", \"animations\": {";
        String tail = "}}";
        assertThrows(() -> MatouAnimationParser.parse(head
                + "\"walk\": {\"bones\": {\"b\": {\"rotation\": [0, 0, 0]}}}" + tail),
                "E_ANIM_NAME:shape");
        String clip = head + "\"animation.x.t\": {";
        String clipTail = "}" + tail;
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"blend_weight\": \"1.0\","
                + " \"bones\": {\"b\": {\"rotation\": [0, 0, 0]}}" + clipTail), "E_ANIM_BLEND");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"override_previous_animation\": true,"
                + " \"bones\": {\"b\": {\"rotation\": [0, 0, 0]}}" + clipTail), "E_ANIM_OVERRIDE");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"timeline\": {\"0.0\": \"x\"},"
                + " \"bones\": {\"b\": {\"rotation\": [0, 0, 0]}}" + clipTail), "E_ANIM_FX");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"bogus\": 1,"
                + " \"bones\": {\"b\": {\"rotation\": [0, 0, 0]}}" + clipTail), "E_ANIM_CLIP:shape");
        assertThrows(() -> MatouAnimationParser.parse(clip + "\"bones\": {}" + clipTail),
                "E_ANIM_CLIP:empty");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"bones\": {\"b\": {\"relative_to\": {\"rotation\": \"entity\"}}}" + clipTail),
                "E_ANIM_RELATIVE");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"bones\": {\"b\": {}}" + clipTail), "E_ANIM_CHANNEL:empty");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"bones\": {\"b\": {\"spin\": [0, 0, 0]}}" + clipTail), "E_ANIM_CHANNEL:shape");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"bones\": {\"b\": {\"rotation\": [0, 0]}}" + clipTail), "E_ANIM_CHANNEL:shape");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"bones\": {\"b\": {\"rotation\": {\"0.0\": {\"pre\": [0, 0, 0]}}}}" + clipTail),
                "E_ANIM_KEY:shape");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"bones\": {\"b\": {\"rotation\": {\"soon\": [0, 0, 0]}}}" + clipTail),
                "E_ANIM_KEY:time");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"bones\": {\"b\": {\"rotation\": {\"-1.0\": [0, 0, 0]}}}" + clipTail),
                "E_ANIM_KEY:time");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"bones\": {\"b\": {\"scale\": [1, 2, 1]}}" + clipTail), "E_ANIM_CHANNEL:scale");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"loop\": true,"
                + " \"bones\": {\"b\": {\"rotation\": [0, 0, 0]}}" + clipTail),
                "E_ANIM_LENGTH:empty");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"animation_length\": 0.25,"
                + " \"bones\": {\"b\": {\"rotation\": {\"0.0\": [0, 0, 0],"
                + " \"0.5\": [1, 0, 0]}}}}" + tail), "E_ANIM_LENGTH:short");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"animation_length\": 0,"
                + " \"bones\": {\"b\": {\"rotation\": [0, 0, 0]}}" + clipTail),
                "E_ANIM_LENGTH:missing");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"loop\": \"yes\","
                + " \"bones\": {\"b\": {\"rotation\": [0, 0, 0]}}" + clipTail), "E_ANIM_CLIP:shape");
        assertThrows(() -> MatouAnimationParser.parse(clip
                + "\"anim_time_update\": 5,"
                + " \"bones\": {\"b\": {\"rotation\": [0, 0, 0]}}" + clipTail), "E_ANIM_CLIP:shape");
        MatouModel m = MatouModelParser.parse(BEAST);
        assertThrows(() -> m.bakePosedMesh(null), "E_ANIM_POSE:null");
        assertThrows(() -> m.posedBoxes(null), "E_ANIM_POSE:null");
        Map<String, MatouAnimation.BonePose> pm =
                new LinkedHashMap<String, MatouAnimation.BonePose>();
        pm.put("ghost", new MatouAnimation.BonePose(0, 0, 0, 0, 0, 0, 1));
        MatouAnimation.AnimPose ghost = new MatouAnimation.AnimPose(pm);
        assertThrows(() -> m.bakePosedMesh(ghost), "E_ANIM_BONE:unknown");
        assertThrows(() -> m.poseDeltaMatrices(ghost), "E_ANIM_BONE:unknown");
    }
}
