package fr.iamacat.spi.model;

import fr.iamacat.spi.hit.BoneBox;
import fr.iamacat.spi.hit.HitTester;
import fr.iamacat.spi.hit.RayHit;
import fr.iamacat.spi.hit.Vec3d;
import java.util.List;

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
        testWinding();
        testBoneBoxes();
        testPlacedBoxes();
        testEmptyAndInflate();
        testRefusals();
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

    private static void testWinding() {
        MatouModel m = MatouModelParser.parse(BEAST);
        float[] v = m.bakeMesh();
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
    }
}
