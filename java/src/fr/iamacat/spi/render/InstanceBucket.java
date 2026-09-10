package fr.iamacat.spi.render;

import fr.iamacat.spi.render.Frustum.Intersection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GPU instancing spike, planning half: group per-mob instance records into
 * {@code (model, texture)} buckets and keep only the frustum-visible ones.
 * Derived from matoulib {@code client/gpu/instancing/GeoInstancing#record}:
 * same key (model + texture, emissive stays a key part on the forge side —
 * a bucket is emissive-uniform so the flush binds it once), same first-seen
 * order (a {@link LinkedHashMap} keeps the natural draw order for
 * translucent-overlap parity).
 *
 * <p>GPU order inside one call: bucket everything first, then drop the culled
 * per bucket. That is the order the future instanced draw executes (one draw
 * per bucket), and the gate proves both halves: same visible sets as
 * cull-first-then-bucket, key order stable (first-seen over all records, so
 * a culled-then-visible bucket does not jump the draw order).
 *
 * <p>Coordinates are world doubles; the eye is subtracted first, in double,
 * and only then narrowed to float for the plane tests (the engine's
 * {@code CameraView} rule — a float-direct subtraction at large coordinates
 * rounds neighbours onto the eye and keeps mobs that are gone). Yaw rides
 * along untouched: the cull never reads it, the future upload does.
 *
 * <p>Pure, zero MC, zero GL. Absent key means fully culled (an empty bucket
 * draws nothing, so it is omitted, not kept empty).
 */
public final class InstanceBucket {
    private InstanceBucket() {}

    /** One drawable instance: opaque keys plus a world-space bound. */
    public static final class Rec {
        public final String model;
        public final String texture;
        public final double x;
        public final double y;
        public final double z;
        public final float radius;
        public final float yawDeg;

        public Rec(String model, String texture,
                double x, double y, double z, float radius, float yawDeg) {
            if (model == null || model.isEmpty()
                    || texture == null || texture.isEmpty()) {
                throw new IllegalArgumentException("E_RENDER_REC:key <"
                        + model + "|" + texture + "> (want non-empty)");
            }
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)
                    || Float.isNaN(yawDeg)) {
                throw new IllegalArgumentException("E_RENDER_REC:nan"
                        + " (NaN poisons every comparison into a wrong keep)");
            }
            if (!(radius >= 0F)) {
                throw new IllegalArgumentException("E_RENDER_REC:radius <"
                        + radius + "> (want >= 0)");
            }
            this.model = model;
            this.texture = texture;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.yawDeg = yawDeg;
        }
    }

    /**
     * Buckets the sphere-visible records, keyed {@code model + "\0" + texture}
     * in first-seen order, values as input indices in record order. The
     * returned map and lists are unmodifiable.
     */
    public static Map<String, List<Integer>> plan(List<Rec> recs,
            double eyeX, double eyeY, double eyeZ, Frustum frustum) {
        if (recs == null) {
            throw new NullPointerException("E_RENDER_PLAN:null recs");
        }
        if (frustum == null) {
            throw new NullPointerException("E_RENDER_PLAN:null frustum");
        }
        if (Double.isNaN(eyeX) || Double.isNaN(eyeY) || Double.isNaN(eyeZ)) {
            throw new IllegalArgumentException("E_RENDER_PLAN:nan eye"
                    + " (NaN poisons every comparison into a wrong keep)");
        }
        Map<String, List<Integer>> buckets =
                new LinkedHashMap<String, List<Integer>>();
        for (int i = 0; i < recs.size(); i++) {
            Rec rec = recs.get(i);
            if (rec == null) {
                throw new NullPointerException("E_RENDER_PLAN:null rec <"
                        + i + ">");
            }
            String key = rec.model + "\0" + rec.texture;
            List<Integer> bucket = buckets.get(key);
            if (bucket == null) {
                bucket = new ArrayList<Integer>();
                buckets.put(key, bucket);
            }
            // Eye subtracted in double, narrowed after — never float-direct.
            float cx = (float) (rec.x - eyeX);
            float cy = (float) (rec.y - eyeY);
            float cz = (float) (rec.z - eyeZ);
            if (frustum.testSphere(cx, cy, cz, rec.radius)
                    != Intersection.OUTSIDE) {
                bucket.add(Integer.valueOf(i));
            }
        }
        Map<String, List<Integer>> sealed =
                new LinkedHashMap<String, List<Integer>>();
        for (Map.Entry<String, List<Integer>> e : buckets.entrySet()) {
            if (!e.getValue().isEmpty()) {
                sealed.put(e.getKey(),
                        Collections.unmodifiableList(e.getValue()));
            }
        }
        return Collections.unmodifiableMap(sealed);
    }
}
