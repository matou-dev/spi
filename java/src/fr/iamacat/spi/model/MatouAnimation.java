package fr.iamacat.spi.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One Bedrock animation clip (hub decisions/MATOU_ANIMATION.md, frozen
 * subset): name, loop mode, length and per-bone rotation / position /
 * scale channels. Single-clip evaluation only — layering several clips
 * is the named follow-up, never an overloaded signature. The evaluated
 * {@link AnimPose} is sparse offsets over bind pose (degrees / px /
 * uniform scale), consumed by the GPU delta matrices and the posed
 * hitboxes. Pure, Java 8, zero dep.
 */
public final class MatouAnimation {
    /** End-of-clip behaviour for single-clip evaluation. */
    public enum Loop {
        /** Freeze at the ends (also serves hold_on_last_frame). */
        CLAMP,
        /** Wrap (t mod length). */
        LOOP,
        /** Freeze at the ends (parsed distinctly for the later tranche). */
        HOLD,
    }

    /** One resolvable channel component: a number or a MOLANG tree. */
    public static final class Comp {
        public final boolean isExpr;
        public final double num;
        public final Molang.Node node;

        public Comp(double num) {
            if (Double.isNaN(num) || Double.isInfinite(num)) {
                throw new IllegalArgumentException("E_ANIM_CHANNEL:shape <" + num
                        + "> (want a finite constant)");
            }
            this.isExpr = false;
            this.num = num;
            this.node = null;
        }

        public Comp(String expr) {
            if (expr == null || expr.trim().isEmpty()) {
                throw new IllegalArgumentException("E_ANIM_MOLANG:empty (want an expression)");
            }
            this.isExpr = true;
            this.num = 0.0;
            this.node = Molang.parse(expr);
        }

        double resolve(Molang.Ctx ctx) {
            double v = isExpr ? node.eval(ctx) : num;
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                throw new IllegalArgumentException("E_ANIM_MOLANG:nan (a NaN/Infinite pose"
                        + " component never reaches a matrix)");
            }
            return v;
        }
    }

    /** One keyframe: time (s) plus one resolvable triple. */
    public static final class Keyframe {
        public final double time;
        public final Comp x;
        public final Comp y;
        public final Comp z;

        public Keyframe(double time, Comp x, Comp y, Comp z) {
            if (Double.isNaN(time) || Double.isInfinite(time) || time < 0.0) {
                throw new IllegalArgumentException("E_ANIM_KEY:time <" + time
                        + "> (want finite keyframe times >= 0)");
            }
            if (x == null || y == null || z == null) {
                throw new NullPointerException("E_ANIM_CHANNEL:shape (want a triple per key)");
            }
            this.time = time;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** One channel: strictly increasing keys, linear interpolation. */
    public static final class Channel {
        public final List<Keyframe> keys;

        public Channel(List<Keyframe> keys) {
            if (keys == null || keys.isEmpty()) {
                throw new IllegalArgumentException("E_ANIM_KEY:empty (want >= 1 keyframe)");
            }
            for (int i = 1; i < keys.size(); i++) {
                if (!(keys.get(i).time > keys.get(i - 1).time)) {
                    throw new IllegalArgumentException("E_ANIM_KEY:time (want strictly"
                            + " increasing keyframe times)");
                }
            }
            this.keys = Collections.unmodifiableList(new ArrayList<Keyframe>(keys));
        }

        double[] eval(double t, Molang.Ctx ctx) {
            Keyframe first = keys.get(0);
            Keyframe last = keys.get(keys.size() - 1);
            if (t <= first.time) {
                return triple(first, ctx);
            }
            if (t >= last.time) {
                return triple(last, ctx);
            }
            for (int i = 1; i < keys.size(); i++) {
                Keyframe b = keys.get(i);
                if (t <= b.time) {
                    Keyframe a = keys.get(i - 1);
                    double f = (t - a.time) / (b.time - a.time);
                    double[] va = triple(a, ctx);
                    double[] vb = triple(b, ctx);
                    return new double[] {
                        va[0] + (vb[0] - va[0]) * f,
                        va[1] + (vb[1] - va[1]) * f,
                        va[2] + (vb[2] - va[2]) * f,
                    };
                }
            }
            return triple(last, ctx);
        }

        private static double[] triple(Keyframe k, Molang.Ctx ctx) {
            return new double[] {
                k.x.resolve(ctx), k.y.resolve(ctx), k.z.resolve(ctx),
            };
        }
    }

    /** Per-bone animation: at least one channel present. */
    public static final class BoneAnim {
        public final Channel rotation;
        public final Channel position;
        public final Channel scale;

        public BoneAnim(Channel rotation, Channel position, Channel scale) {
            if (rotation == null && position == null && scale == null) {
                throw new IllegalArgumentException("E_ANIM_CHANNEL:empty (a bone animates"
                        + " >= 1 of rotation/position/scale)");
            }
            this.rotation = rotation;
            this.position = position;
            this.scale = scale;
        }
    }

    /** One bone pose: Euler degree offsets, px offsets, uniform scale. */
    public static final class BonePose {
        public final double rotX;
        public final double rotY;
        public final double rotZ;
        public final double posX;
        public final double posY;
        public final double posZ;
        public final double scale;

        public BonePose(double rotX, double rotY, double rotZ,
                double posX, double posY, double posZ, double scale) {
            this.rotX = rotX;
            this.rotY = rotY;
            this.rotZ = rotZ;
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.scale = scale;
        }

        /** True when this pose leaves bind exactly alone. */
        public boolean isIdentity() {
            return rotX == 0.0 && rotY == 0.0 && rotZ == 0.0
                    && posX == 0.0 && posY == 0.0 && posZ == 0.0
                    && scale == 1.0;
        }
    }

    /** Sparse evaluated pose: named bones only, absent means bind. */
    public static final class AnimPose {
        public final Map<String, BonePose> bones;

        public AnimPose(Map<String, BonePose> bones) {
            if (bones == null) {
                throw new NullPointerException("E_ANIM_POSE:null (want a bone map, possibly empty)");
            }
            this.bones = Collections.unmodifiableMap(
                    new LinkedHashMap<String, BonePose>(bones));
        }

        /** Empty pose = identity everywhere. */
        public static AnimPose identity() {
            return new AnimPose(Collections.<String, BonePose>emptyMap());
        }
    }

    public final String name;
    public final Loop loop;
    public final double length;
    public final Molang.Node timeUpdate;
    public final Map<String, BoneAnim> bones;

    public MatouAnimation(String name, Loop loop, double length,
            Molang.Node timeUpdate, Map<String, BoneAnim> bones) {
        if (name == null || !name.matches("^animation\\..+")) {
            throw new IllegalArgumentException("E_ANIM_NAME:shape <" + name
                    + "> (want ^animation\\.)");
        }
        if (loop == null) {
            throw new NullPointerException("E_ANIM_CLIP:shape (want a loop mode, never null)");
        }
        if (Double.isNaN(length) || Double.isInfinite(length) || length < 0.0) {
            throw new IllegalArgumentException("E_ANIM_LENGTH:missing <" + length
                    + "> (want a finite length >= 0)");
        }
        if (bones == null || bones.isEmpty()) {
            throw new IllegalArgumentException("E_ANIM_CLIP:empty (want >= 1 animated bone)");
        }
        if (loop == Loop.LOOP && !(length > 0.0)) {
            throw new IllegalArgumentException("E_ANIM_LENGTH:empty (a looped clip needs"
                    + " length > 0 — a modulo by zero would be silent)");
        }
        this.name = name;
        this.loop = loop;
        this.length = length;
        this.timeUpdate = timeUpdate;
        this.bones = Collections.unmodifiableMap(
                new LinkedHashMap<String, BoneAnim>(bones));
    }

    /**
     * Evaluates one clip at caller time (s) — pure, deterministic.
     * Wraps (LOOP) or clamps (CLAMP/HOLD) through length, resolves
     * keyframes linearly, then checks uniform scale per bone.
     */
    public AnimPose evaluate(double time, Molang.Ctx ctx) {
        if (Double.isNaN(time) || Double.isInfinite(time) || time < 0.0) {
            throw new IllegalArgumentException("E_ANIM_TIME <" + time
                    + "> (want finite caller seconds >= 0)");
        }
        if (ctx == null) {
            throw new NullPointerException("E_ANIM_MOLANG:scope (null eval context)");
        }
        double base = time;
        if (timeUpdate != null) {
            Molang.Ctx tick = new Molang.Ctx(time, ctx.lifeTime,
                    ctx.distMoved, ctx.deltaTime, ctx.variables);
            base = timeUpdate.eval(tick);
            if (Double.isNaN(base) || Double.isInfinite(base)) {
                throw new IllegalArgumentException("E_ANIM_MOLANG:nan (anim_time_update"
                        + " resolved non-finite)");
            }
        }
        if (base < 0.0) {
            throw new IllegalArgumentException("E_ANIM_TIME <" + base
                    + "> (the clip time resolved negative — want >= 0)");
        }
        double t = base;
        if (loop == Loop.LOOP) {
            t = base % length;
        } else if (length > 0.0 && t > length) {
            // A zero-length clip is pure continuous expression (flat keys
            // at t = 0): nothing to clamp against, the driver time rides
            // through so query.anim_time stays live.
            t = length;
        }
        Molang.Ctx at = new Molang.Ctx(t, ctx.lifeTime,
                ctx.distMoved, ctx.deltaTime, ctx.variables);
        Map<String, BonePose> out = new LinkedHashMap<String, BonePose>();
        for (Map.Entry<String, BoneAnim> e : bones.entrySet()) {
            BoneAnim b = e.getValue();
            double[] r = b.rotation == null
                    ? new double[] {0.0, 0.0, 0.0} : b.rotation.eval(t, at);
            double[] p = b.position == null
                    ? new double[] {0.0, 0.0, 0.0} : b.position.eval(t, at);
            double[] s = b.scale == null
                    ? new double[] {1.0, 1.0, 1.0} : b.scale.eval(t, at);
            if (s[0] != s[1] || s[0] != s[2]) {
                throw new IllegalArgumentException("E_ANIM_CHANNEL:scale <"
                        + e.getKey() + " " + s[0] + "," + s[1] + "," + s[2]
                        + "> (scale stays uniform — Bedrock parity)");
            }
            if (!(s[0] > 0.0)) {
                throw new IllegalArgumentException("E_ANIM_CHANNEL:scale <"
                        + e.getKey() + " " + s[0]
                        + "> (want finite > 0 — zero has no delta-inverse,"
                        + " negative flips winding)");
            }
            out.put(e.getKey(), new BonePose(r[0], r[1], r[2],
                    p[0], p[1], p[2], s[0]));
        }
        return new AnimPose(out);
    }
}
