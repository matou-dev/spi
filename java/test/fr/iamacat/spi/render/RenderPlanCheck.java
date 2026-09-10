package fr.iamacat.spi.render;

import fr.iamacat.spi.MatouRng;
import fr.iamacat.spi.render.Frustum.Intersection;
import fr.iamacat.spi.render.InstanceBucket.Rec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GPU spike gate (no JUnit): hand-computed ortho goldens lock the row-major
 * convention and the classic-depth plane combinations, two comparateurs prove
 * the load-bearing properties, refusals prove nothing defaults. Any violation
 * prints {@code FAIL render-plan : ...} and exits 1. Run by tools/check.sh.
 *
 * <p>Comparateur 1 (oracle vs clip-space brute force): {@code testAabb}
 * reports OUTSIDE exactly when no box corner survives the raw matrix in clip
 * space — two corners per plane answer like all eight, and a reported
 * OUTSIDE is never a wrong cull.
 *
 * <p>Comparateur 2 (order commutativity): bucket-then-cull ({@link
 * InstanceBucket#plan}, the GPU execution order) keeps the same visible
 * SETS per bucket as cull-then-bucket, while its key order stays first-seen
 * over ALL records (stable while mobs pop in and out of view) — the two
 * halves the compute-cull port must preserve.
 */
public final class RenderPlanCheck {
    private RenderPlanCheck() {}

    private static int n = 0;

    private static void check(boolean cond, String what) {
        if (!cond) {
            System.out.println("FAIL render-plan : " + what);
            System.exit(1);
        }
        n++;
    }

    private static void expectIAE(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException e) {
            n++;
            return;
        }
        System.out.println("FAIL render-plan : accepted " + what);
        System.exit(1);
    }

    private static void expectNPE(Runnable r, String what) {
        try {
            r.run();
        } catch (NullPointerException e) {
            n++;
            return;
        }
        System.out.println("FAIL render-plan : accepted " + what);
        System.exit(1);
    }

    /** Ortho l=-2 r=2 b=-1 t=1 n=1 f=9, row-major. All values exactly float. */
    private static final float[] ORTHO = {
        0.5f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, -0.25f, -1.25f,
        0f, 0f, 0f, 1f
    };

    /** Perspective fov90 aspect1 n=1 f=100, row-major, classic depth. */
    private static final float[] PERSP = {
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, -(101f / 99f), -(200f / 99f),
        0f, 0f, -1f, 0f
    };

    public static void main(String[] args) {
        Frustum ortho = Frustum.of(ORTHO);
        check(Arrays.equals(ortho.plane(0), new float[] {1f, 0f, 0f, 2f}),
                "ortho left plane");
        check(Arrays.equals(ortho.plane(1), new float[] {-1f, 0f, 0f, 2f}),
                "ortho right plane");
        check(Arrays.equals(ortho.plane(2), new float[] {0f, 1f, 0f, 1f}),
                "ortho bottom plane");
        check(Arrays.equals(ortho.plane(3), new float[] {0f, -1f, 0f, 1f}),
                "ortho top plane");
        check(Arrays.equals(ortho.plane(4), new float[] {0f, 0f, -1f, -1f}),
                "ortho near plane (classic depth, w + row2)");
        check(Arrays.equals(ortho.plane(5), new float[] {0f, 0f, 1f, 9f}),
                "ortho far plane (classic depth, w - row2)");

        // Box goldens, camera-relative (eye looks down -Z).
        check(ortho.testAabb(-1f, -0.5f, -5f, 1f, 0.5f, -2f)
                == Intersection.INSIDE, "ortho box inside");
        check(ortho.testAabb(3f, -0.5f, -5f, 4f, 0.5f, -2f)
                == Intersection.OUTSIDE, "ortho box past right");
        check(ortho.testAabb(-1f, -0.5f, -5f, 1f, 0.5f, -0.5f)
                == Intersection.INTERSECT, "ortho box straddles near");
        check(ortho.testAabb(-1f, -0.5f, 1f, 1f, 0.5f, 2f)
                == Intersection.OUTSIDE, "ortho box behind eye");
        // Accepted false positive, locked: no corner inside, still INTERSECT
        // (costs a draw, never a missing mob — never "fix" this).
        check(ortho.testAabb(-10f, -10f, -5f, 10f, 10f, -2f)
                == Intersection.INTERSECT, "ortho giant straddle stays kept");

        // Sphere goldens.
        check(ortho.testSphere(0f, 0f, -5f, 1f) == Intersection.INSIDE,
                "ortho sphere inside");
        check(ortho.testSphere(0f, 0f, -0.5f, 1f) == Intersection.INTERSECT,
                "ortho sphere crosses near");
        check(ortho.testSphere(5f, 0f, -5f, 1f) == Intersection.OUTSIDE,
                "ortho sphere past right");

        // Perspective behaviour (normalised planes are irrational — behaviour
        // only, no exact goldens).
        Frustum persp = Frustum.of(PERSP);
        check(persp.testSphere(0f, 0f, -50f, 1f) == Intersection.INSIDE,
                "persp centre visible");
        check(persp.testSphere(0f, 0f, -0.5f, 0.1f) == Intersection.OUTSIDE,
                "persp in front of near culled");
        check(persp.testSphere(0f, 0f, -150f, 1f) == Intersection.OUTSIDE,
                "persp past far culled");
        check(persp.testSphere(0f, 0f, 5f, 1f) == Intersection.OUTSIDE,
                "persp behind eye culled");

        // Eye-precision trap: eye at 1e8, mob 4 right of it (half-width 2,
        // radius 0.5 — truly OUTSIDE). A float-direct subtraction rounds the
        // neighbour onto the eye and keeps it; double-first culls it.
        List<Rec> trap = Arrays.asList(
                new Rec("pig", "a", 100000000.0 + 4.0, 0.0, -5.0, 0.5f, 0f));
        check(InstanceBucket.plan(trap, 100000000.0, 0.0, 0.0, ortho).isEmpty(),
                "far-eye mob culled via double subtraction");
        float naive = (float) (100000000.0 + 4.0) - (float) 100000000.0;
        check(ortho.testSphere(naive, 0f, -5f, 0.5f) != Intersection.OUTSIDE,
                "float-direct control would keep it (the trap)");

        // Comparateur 1: plane oracle vs raw-matrix clip brute force.
        float[][] matrices = {ORTHO, PERSP};
        for (int m = 0; m < matrices.length; m++) {
            Frustum f = Frustum.of(matrices[m]);
            MatouRng rng = MatouRng.forAddress("render-plan", "aabb",
                    Integer.toString(m));
            for (int i = 0; i < 256; i++) {
                float x = (rng.nextInt(8001) - 4000) / 100f;
                float y = (rng.nextInt(8001) - 4000) / 100f;
                float z = (rng.nextInt(8001) - 4000) / 100f;
                float sx = rng.nextInt(801) / 100f;
                float sy = rng.nextInt(801) / 100f;
                float sz = rng.nextInt(801) / 100f;
                boolean oracleOut = f.testAabb(x, y, z,
                        x + sx, y + sy, z + sz) == Intersection.OUTSIDE;
                boolean bruteKept = false;
                for (int cx = 0; cx < 2 && !bruteKept; cx++) {
                    for (int cy = 0; cy < 2 && !bruteKept; cy++) {
                        for (int cz = 0; cz < 2 && !bruteKept; cz++) {
                            if (clipInside(matrices[m],
                                    cx == 0 ? x : x + sx,
                                    cy == 0 ? y : y + sy,
                                    cz == 0 ? z : z + sz)) {
                                bruteKept = true;
                            }
                        }
                    }
                }
                check(oracleOut == !bruteKept,
                        "aabb-vs-clip scene " + m + " box " + i);
            }
        }

        // Comparateur 2: bucket-then-cull vs cull-then-bucket.
        String[] models = {"pig", "cow", "sheep"};
        String[] textures = {"a", "b"};
        double[][] eyes = {{0.0, 0.0, 0.0}, {100.5, -20.25, 300.125}};
        for (int m = 0; m < matrices.length; m++) {
            Frustum f = Frustum.of(matrices[m]);
            for (int s = 0; s < 4; s++) {
                MatouRng rng = MatouRng.forAddress("render-plan", "scene",
                        m + "/" + s);
                List<Rec> recs = new ArrayList<Rec>();
                for (int i = 0; i < 256; i++) {
                    recs.add(new Rec(models[rng.nextInt(3)],
                            textures[rng.nextInt(2)],
                            (rng.nextInt(8001) - 4000) / 100.0,
                            (rng.nextInt(8001) - 4000) / 100.0,
                            (rng.nextInt(8001) - 4000) / 100.0,
                            rng.nextInt(301) / 100f,
                            (float) rng.nextInt(360)));
                }
                for (int e = 0; e < eyes.length; e++) {
                    Map<String, List<Integer>> gpuOrder = InstanceBucket.plan(
                            recs, eyes[e][0], eyes[e][1], eyes[e][2], f);
                    // Reference: cull first, then bucket the visible only.
                    // Same visible SETS per bucket, but its key order is
                    // first-seen over the visible — plan() instead keeps
                    // first-seen over ALL records (stable draw order while
                    // mobs pop in and out of view). Both halves asserted.
                    Map<String, List<Integer>> cpuOrder =
                            new LinkedHashMap<String, List<Integer>>();
                    List<String> seenAll = new ArrayList<String>();
                    for (int i = 0; i < recs.size(); i++) {
                        Rec r = recs.get(i);
                        String key = r.model + "\0" + r.texture;
                        if (!seenAll.contains(key)) {
                            seenAll.add(key);
                        }
                        float cx = (float) (r.x - eyes[e][0]);
                        float cy = (float) (r.y - eyes[e][1]);
                        float cz = (float) (r.z - eyes[e][2]);
                        if (f.testSphere(cx, cy, cz, r.radius)
                                == Intersection.OUTSIDE) {
                            continue;
                        }
                        List<Integer> bucket = cpuOrder.get(key);
                        if (bucket == null) {
                            bucket = new ArrayList<Integer>();
                            cpuOrder.put(key, bucket);
                        }
                        bucket.add(Integer.valueOf(i));
                    }
                    check(eqMaps(gpuOrder, cpuOrder),
                            "order-commutes sets scene " + m + "/" + s
                            + " eye " + e);
                    List<String> stable = new ArrayList<String>();
                    for (String key : seenAll) {
                        if (cpuOrder.containsKey(key)) {
                            stable.add(key);
                        }
                    }
                    check(new ArrayList<String>(gpuOrder.keySet())
                            .equals(stable),
                            "order-stable scene " + m + "/" + s
                            + " eye " + e);
                }
            }
        }

        // Refusals: never a defaulted plane, record, or bucket.
        expectNPE(new Runnable() {
            @Override public void run() {
                Frustum.of(null);
            }
        }, "null matrix");
        expectIAE(new Runnable() {
            @Override public void run() {
                Frustum.of(new float[15]);
            }
        }, "short matrix");
        expectIAE(new Runnable() {
            @Override public void run() {
                Frustum.of(new float[16]);
            }
        }, "zero matrix (degenerate left plane)");
        expectIAE(new Runnable() {
            @Override public void run() {
                Frustum.of(ORTHO).plane(6);
            }
        }, "plane index 6");
        expectIAE(new Runnable() {
            @Override public void run() {
                Frustum.of(ORTHO).plane(-1);
            }
        }, "plane index -1");
        expectIAE(new Runnable() {
            @Override public void run() {
                Frustum.of(ORTHO).testAabb(0f, 0f, 0f,
                        Float.NaN, 1f, 1f);
            }
        }, "NaN box");
        expectIAE(new Runnable() {
            @Override public void run() {
                Frustum.of(ORTHO).testAabb(0f, 0f, 0f, -1f, 1f, 1f);
            }
        }, "inverted box");
        expectIAE(new Runnable() {
            @Override public void run() {
                Frustum.of(ORTHO).testSphere(0f, 0f, -5f, -1f);
            }
        }, "negative radius");
        expectIAE(new Runnable() {
            @Override public void run() {
                Frustum.of(ORTHO).testSphere(Float.NaN, 0f, -5f, 1f);
            }
        }, "NaN sphere centre");
        expectIAE(new Runnable() {
            @Override public void run() {
                new Rec("", "a", 0.0, 0.0, 0.0, 1f, 0f);
            }
        }, "empty model");
        expectIAE(new Runnable() {
            @Override public void run() {
                new Rec("pig", "a", 0.0, Double.NaN, 0.0, 1f, 0f);
            }
        }, "NaN record coord");
        expectIAE(new Runnable() {
            @Override public void run() {
                new Rec("pig", "a", 0.0, 0.0, 0.0, -1f, 0f);
            }
        }, "negative record radius");
        expectNPE(new Runnable() {
            @Override public void run() {
                InstanceBucket.plan(null, 0.0, 0.0, 0.0, Frustum.of(ORTHO));
            }
        }, "null recs");
        expectNPE(new Runnable() {
            @Override public void run() {
                InstanceBucket.plan(new ArrayList<Rec>(),
                        0.0, 0.0, 0.0, null);
            }
        }, "null frustum");
        expectIAE(new Runnable() {
            @Override public void run() {
                InstanceBucket.plan(new ArrayList<Rec>(),
                        Double.NaN, 0.0, 0.0, Frustum.of(ORTHO));
            }
        }, "NaN eye");
        final List<Rec> hole = new ArrayList<Rec>();
        hole.add(null);
        expectNPE(new Runnable() {
            @Override public void run() {
                InstanceBucket.plan(hole, 0.0, 0.0, 0.0, Frustum.of(ORTHO));
            }
        }, "null rec element");

        System.out.println("ok render-plan : " + n + " checks");
    }

    /** Raw-matrix clip test: inside iff w > 0 and |x|,|y|,|z| <= w. */
    private static boolean clipInside(float[] m, float x, float y, float z) {
        float cx = m[0] * x + m[1] * y + m[2] * z + m[3];
        float cy = m[4] * x + m[5] * y + m[6] * z + m[7];
        float cz = m[8] * x + m[9] * y + m[10] * z + m[11];
        float w = m[12] * x + m[13] * y + m[14] * z + m[15];
        return w > 0f && cx >= -w && cx <= w
                && cy >= -w && cy <= w && cz >= -w && cz <= w;
    }

    /**
     * Same key set with the same index lists per key (order-insensitive:
     * draw order is asserted separately by the stability half).
     */
    private static boolean eqMaps(Map<String, List<Integer>> a,
            Map<String, List<Integer>> b) {
        if (!a.keySet().equals(b.keySet())) {
            return false;
        }
        for (Map.Entry<String, List<Integer>> e : a.entrySet()) {
            if (!e.getValue().equals(b.get(e.getKey()))) {
                return false;
            }
        }
        return true;
    }
}
