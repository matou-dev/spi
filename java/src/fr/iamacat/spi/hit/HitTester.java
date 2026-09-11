package fr.iamacat.spi.hit;

import java.util.List;

/**
 * Pure static ray-box test evaluating ray intersections across a target's bone boxes.
 * Zero Minecraft/GL imports, Java 8.
 */
public final class HitTester {
    private HitTester() {}

    public static RayHit test(Hittable target, Vec3d origin, Vec3d dir, double maxDist) {
        if (target == null) {
            throw new NullPointerException("E_HIT_TARGET:null");
        }
        return test(target.hitBoxes(), origin, dir, maxDist);
    }

    public static RayHit test(List<BoneBox> boxes, Vec3d origin, Vec3d dir, double maxDist) {
        if (boxes == null) {
            throw new NullPointerException("E_HIT_BOXES:null");
        }
        if (origin == null) {
            throw new NullPointerException("E_HIT_ORIGIN:null");
        }
        if (dir == null) {
            throw new NullPointerException("E_HIT_DIR:null");
        }
        if (Double.isNaN(maxDist)) {
            throw new IllegalArgumentException("E_HIT_DIST:nan");
        }
        if (maxDist < 0.0) {
            throw new IllegalArgumentException("E_HIT_DIST:negative");
        }

        Vec3d d = dir.normalize();
        String bestBone = null;
        double bestT = Double.POSITIVE_INFINITY;

        for (BoneBox bb : boxes) {
            if (bb == null || bb.box == null) continue;
            double t = bb.box.intersect(origin.x, origin.y, origin.z, d.x, d.y, d.z, maxDist);
            if (!Double.isNaN(t) && t < bestT) {
                bestT = t;
                bestBone = bb.boneName;
            }
        }

        if (bestBone == null) {
            return null;
        }

        Vec3d hitPoint = origin.add(d.scale(bestT));
        return new RayHit(bestBone, hitPoint, bestT);
    }
}
