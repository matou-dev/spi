package fr.iamacat.spi.hit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gate HitCheck: comprehensive verification of pure geometric ray-testing and virtual hitboxes.
 * Java 8, zero MC imports.
 */
public final class HitCheck {
    private HitCheck() {}

    private static void check(boolean cond, String msg) {
        if (!cond) {
            System.err.println("FAIL hit-check : " + msg);
            System.exit(1);
        }
    }

    private static void assertThrows(Runnable r, String expectedFragment) {
        try {
            r.run();
            System.err.println("FAIL hit-check : expected exception containing <" + expectedFragment + ">");
            System.exit(1);
        } catch (Throwable t) {
            String msg = t.getMessage();
            if (msg == null || !msg.contains(expectedFragment)) {
                System.err.println("FAIL hit-check : got <" + msg + "> (want fragment <" + expectedFragment + ">)");
                System.exit(1);
            }
        }
    }

    public static void main(String[] args) {
        testVec3d();
        testAABBd();
        testBoneBoxAndRayHit();
        testHitTester();
        testHittable();
        System.out.println("ok hit-check : all geometric virtual-hitbox tests passed");
    }

    private static void testVec3d() {
        Vec3d v1 = new Vec3d(1.0, 2.0, 3.0);
        Vec3d v2 = new Vec3d(4.0, 5.0, 6.0);

        check(v1.add(v2).equals(new Vec3d(5.0, 7.0, 9.0)), "vec add");
        check(v2.sub(v1).equals(new Vec3d(3.0, 3.0, 3.0)), "vec sub");
        check(v1.scale(2.0).equals(new Vec3d(2.0, 4.0, 6.0)), "vec scale");
        check(Math.abs(v1.dot(v2) - 32.0) < 1e-9, "vec dot");
        check(Math.abs(new Vec3d(3.0, 4.0, 0.0).length() - 5.0) < 1e-9, "vec length");
        check(Math.abs(new Vec3d(3.0, 4.0, 0.0).normalize().length() - 1.0) < 1e-9, "vec normalize length");

        // Refusals
        assertThrows(() -> new Vec3d(Double.NaN, 0, 0), "E_HIT_VEC:nan");
        assertThrows(() -> new Vec3d(0, Double.NaN, 0), "E_HIT_VEC:nan");
        assertThrows(() -> new Vec3d(0, 0, Double.NaN), "E_HIT_VEC:nan");
        assertThrows(() -> v1.add(null), "E_HIT_VEC:null");
        assertThrows(() -> v1.sub(null), "E_HIT_VEC:null");
        assertThrows(() -> v1.dot(null), "E_HIT_VEC:null");
        assertThrows(() -> v1.scale(Double.NaN), "E_HIT_VEC:nan");
        assertThrows(() -> new Vec3d(0.0, 0.0, 0.0).normalize(), "E_HIT_DIR:zero");
    }

    private static void testAABBd() {
        AABBd box = new AABBd(0.0, 0.0, 0.0, 2.0, 2.0, 2.0);

        // Contains
        check(box.contains(1.0, 1.0, 1.0), "box contains inside");
        check(box.contains(0.0, 2.0, 1.0), "box contains boundary");
        check(!box.contains(3.0, 1.0, 1.0), "box contains outside");

        // Ray center hit (+X direction towards box)
        double t = box.intersect(-5.0, 1.0, 1.0, 1.0, 0.0, 0.0, 10.0);
        check(Math.abs(t - 5.0) < 1e-9, "ray hit +X entry dist 5.0");

        // Ray origin inside box -> entry t is 0.0
        double tInside = box.intersect(1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 10.0);
        check(tInside == 0.0, "ray hit inside box entry t=0");

        // Ray pointing away from box -> miss (NaN)
        double tAway = box.intersect(-5.0, 1.0, 1.0, -1.0, 0.0, 0.0, 10.0);
        check(Double.isNaN(tAway), "ray pointing away misses");

        // Ray parallel to slab outside box -> miss (NaN)
        double tParOut = box.intersect(-5.0, 5.0, 1.0, 1.0, 0.0, 0.0, 10.0);
        check(Double.isNaN(tParOut), "ray parallel outside misses");

        // Ray reach too short
        double tShort = box.intersect(-5.0, 1.0, 1.0, 1.0, 0.0, 0.0, 4.0);
        check(Double.isNaN(tShort), "ray maxDist too short misses");

        // Refusals
        assertThrows(() -> new AABBd(2.0, 0.0, 0.0, 1.0, 2.0, 2.0), "E_HIT_BOX:degenerate");
        assertThrows(() -> new AABBd(0.0, 2.0, 0.0, 2.0, 1.0, 2.0), "E_HIT_BOX:degenerate");
        assertThrows(() -> new AABBd(0.0, 0.0, 2.0, 2.0, 2.0, 1.0), "E_HIT_BOX:degenerate");
        assertThrows(() -> new AABBd(Double.NaN, 0, 0, 1, 1, 1), "E_HIT_BOX:nan");
        assertThrows(() -> box.intersect(Double.NaN, 0, 0, 1, 0, 0, 10), "E_HIT_ORIGIN:nan");
        assertThrows(() -> box.intersect(0, 0, 0, Double.NaN, 0, 0, 10), "E_HIT_DIR:nan");
        assertThrows(() -> box.intersect(0, 0, 0, 1, 0, 0, Double.NaN), "E_HIT_DIST:nan");
        assertThrows(() -> box.intersect(0, 0, 0, 1, 0, 0, -1.0), "E_HIT_DIST:negative");
    }

    private static void testBoneBoxAndRayHit() {
        AABBd box = new AABBd(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        BoneBox bb = new BoneBox("head", box);
        check(bb.boneName.equals("head") && bb.box.equals(box), "bonebox fields");

        assertThrows(() -> new BoneBox(null, box), "E_HIT_BONE:empty");
        assertThrows(() -> new BoneBox("  ", box), "E_HIT_BONE:empty");
        assertThrows(() -> new BoneBox("head", null), "E_HIT_BOX:null");

        Vec3d p = new Vec3d(0.0, 0.5, 0.5);
        RayHit rh = new RayHit("head", p, 2.5);
        check(rh.boneName.equals("head") && rh.hitVec.equals(p) && rh.distance == 2.5, "rayhit fields");

        assertThrows(() -> new RayHit(null, p, 2.5), "E_HIT_BONE:empty");
        assertThrows(() -> new RayHit("head", null, 2.5), "E_HIT_VEC:null");
        assertThrows(() -> new RayHit("head", p, -1.0), "E_HIT_DIST:negative_or_nan");
        assertThrows(() -> new RayHit("head", p, Double.NaN), "E_HIT_DIST:negative_or_nan");
    }

    private static void testHitTester() {
        // Multi-bone setup:
        // "head": x=[4, 6], y=[-1, 1], z=[-1, 1]
        // "torso": x=[8, 12], y=[-1, 1], z=[-1, 1]
        List<BoneBox> bones = new ArrayList<BoneBox>();
        bones.add(new BoneBox("torso", new AABBd(8.0, -1.0, -1.0, 12.0, 1.0, 1.0)));
        bones.add(new BoneBox("head", new AABBd(4.0, -1.0, -1.0, 6.0, 1.0, 1.0)));

        Vec3d origin = new Vec3d(0.0, 0.0, 0.0);
        Vec3d dir = new Vec3d(1.0, 0.0, 0.0); // towards +X

        // 1. Ray pierces head first at x=4 (t=4), then torso at x=8.
        // Head must win because it is closer!
        RayHit hit = HitTester.test(bones, origin, dir, 20.0);
        check(hit != null, "hit is not null");
        check("head".equals(hit.boneName), "head struck (closer bone wins)");
        check(Math.abs(hit.distance - 4.0) < 1e-9, "hit distance is 4.0");
        check(hit.hitVec.equals(new Vec3d(4.0, 0.0, 0.0)), "hit position is (4,0,0)");

        // 2. Ray from behind: origin at x=15, dir towards -X
        Vec3d revOrigin = new Vec3d(15.0, 0.0, 0.0);
        Vec3d revDir = new Vec3d(-1.0, 0.0, 0.0);
        RayHit revHit = HitTester.test(bones, revOrigin, revDir, 20.0);
        check(revHit != null, "rev hit not null");
        check("torso".equals(revHit.boneName), "torso struck first from behind");
        check(Math.abs(revHit.distance - 3.0) < 1e-9, "rev hit distance is 3.0 (from 15 to 12)");
        check(revHit.hitVec.equals(new Vec3d(12.0, 0.0, 0.0)), "rev hit position is (12,0,0)");

        // 3. Direction vector unnormalized: dir = (10, 0, 0)
        // Must normalize internally and return true distance
        RayHit unnormHit = HitTester.test(bones, origin, new Vec3d(10.0, 0.0, 0.0), 20.0);
        check(unnormHit != null && "head".equals(unnormHit.boneName), "unnormalized dir hits head");
        check(Math.abs(unnormHit.distance - 4.0) < 1e-9, "unnormalized dir returns true distance 4.0");

        // 4. Reach cutoff: maxDist = 3.0 -> cannot reach head at 4.0
        RayHit shortHit = HitTester.test(bones, origin, dir, 3.0);
        check(shortHit == null, "reach cutoff returns null");

        // 5. Total miss: ray pointing up (+Y)
        RayHit missHit = HitTester.test(bones, origin, new Vec3d(0.0, 1.0, 0.0), 20.0);
        check(missHit == null, "miss returns null");

        // Refusals
        assertThrows(() -> HitTester.test((List<BoneBox>) null, origin, dir, 10.0), "E_HIT_BOXES:null");
        assertThrows(() -> HitTester.test(bones, null, dir, 10.0), "E_HIT_ORIGIN:null");
        assertThrows(() -> HitTester.test(bones, origin, null, 10.0), "E_HIT_DIR:null");
        assertThrows(() -> HitTester.test(bones, origin, dir, Double.NaN), "E_HIT_DIST:nan");
        assertThrows(() -> HitTester.test(bones, origin, dir, -1.0), "E_HIT_DIST:negative");
        assertThrows(() -> HitTester.test((Hittable) null, origin, dir, 10.0), "E_HIT_TARGET:null");
    }

    private static void testHittable() {
        final List<BoneBox> boxes = Collections.singletonList(
                new BoneBox("head", new AABBd(1.0, 1.0, 1.0, 2.0, 2.0, 2.0)));
        final Map<String, Float> weakspots = new HashMap<String, Float>();
        weakspots.put("head", 2.5F);

        Hittable target = new Hittable() {
            @Override
            public List<BoneBox> hitBoxes() {
                return boxes;
            }

            @Override
            public Map<String, Float> hitWeakspots() {
                return weakspots;
            }
        };

        check(target.weakspotMultiplier("head") == 2.5F, "head weakspot is 2.5");
        check(target.weakspotMultiplier("torso") == 1.0F, "unknown bone weakspot is 1.0");
        check(target.weakspotMultiplier(null) == 1.0F, "null bone weakspot is 1.0");

        RayHit hit = HitTester.test(target, new Vec3d(0.0, 1.5, 1.5), new Vec3d(1.0, 0.0, 0.0), 5.0);
        check(hit != null && "head".equals(hit.boneName), "target hittable ray-test success");
    }
}
