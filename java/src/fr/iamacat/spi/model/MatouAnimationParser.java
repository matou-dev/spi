package fr.iamacat.spi.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a Blockbench Bedrock animation document (frozen subset, see hub
 * decisions/MATOU_ANIMATION.md) into clips by name. Malformed JSON
 * surfaces the shared E_MODEL_JSON codes (never an E_ANIM_JSON
 * duplicate); every refused animation shape carries an E_ANIM_* code,
 * never a silent drop. Java 8, zero dep.
 */
public final class MatouAnimationParser {
    private MatouAnimationParser() {}

    /** Parses every clip of one animation document, in file order. */
    public static Map<String, MatouAnimation> parse(String json) {
        Object root = JsonParser.parse(json);
        Map<String, Object> doc = asObject(root,
                "E_ANIM_DOC:shape (want a JSON object at root)");
        for (String k : doc.keySet()) {
            if (!k.equals("format_version") && !k.equals("animations")) {
                throw new IllegalArgumentException("E_ANIM_DOC:shape <" + k
                        + "> (want format_version/animations only)");
            }
        }
        Object version = doc.get("format_version");
        if (!(version instanceof String) || ((String) version).trim().isEmpty()) {
            throw new IllegalArgumentException("E_ANIM_DOC:missing (want format_version string)");
        }
        Object anims = doc.get("animations");
        if (!(anims instanceof Map) || ((Map<?, ?>) anims).isEmpty()) {
            throw new IllegalArgumentException("E_ANIM_CLIP:empty (want a non-empty"
                    + " animations object)");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> clips = (Map<String, Object>) anims;
        Map<String, MatouAnimation> out = new LinkedHashMap<String, MatouAnimation>();
        for (Map.Entry<String, Object> e : clips.entrySet()) {
            out.put(e.getKey(), parseClip(e.getKey(), e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    private static MatouAnimation parseClip(String name, Object o) {
        if (name == null || !name.matches("^animation\\..+")) {
            throw new IllegalArgumentException("E_ANIM_NAME:shape <" + name
                    + "> (want ^animation\\.)");
        }
        Map<String, Object> m = asObject(o,
                "E_ANIM_CLIP:shape <" + name + "> (want an object per clip)");
        MatouAnimation.Loop loop = MatouAnimation.Loop.CLAMP;
        Double explicitLength = null;
        Molang.Node timeUpdate = null;
        Map<String, MatouAnimation.BoneAnim> bones = null;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            String k = e.getKey();
            if (k.equals("loop")) {
                loop = parseLoop(name, e.getValue());
            } else if (k.equals("animation_length")) {
                explicitLength = parseLength(name, e.getValue());
            } else if (k.equals("anim_time_update")) {
                timeUpdate = parseTimeUpdate(name, e.getValue());
            } else if (k.equals("bones")) {
                bones = parseBones(name, e.getValue());
            } else if (k.equals("blend_weight")) {
                throw new IllegalArgumentException("E_ANIM_BLEND <" + name
                        + "> (multi-clip blending is a named follow-up, never silent)");
            } else if (k.equals("override_previous_animation")) {
                throw new IllegalArgumentException("E_ANIM_OVERRIDE <" + name
                        + "> (layered override is a named follow-up — single-clip eval"
                        + " always starts from bind)");
            } else if (k.equals("particle_effects") || k.equals("sound_effects")
                    || k.equals("timeline")) {
                throw new IllegalArgumentException("E_ANIM_FX <" + name + "." + k
                        + "> (effect timelines are a named follow-up, never silent)");
            } else {
                throw new IllegalArgumentException("E_ANIM_CLIP:shape <" + name + "." + k
                        + "> (want loop/animation_length/anim_time_update/bones)");
            }
        }
        if (bones == null || bones.isEmpty()) {
            throw new IllegalArgumentException("E_ANIM_CLIP:empty <" + name
                    + "> (want >= 1 animated bone)");
        }
        double lastKey = 0.0;
        for (MatouAnimation.BoneAnim b : bones.values()) {
            lastKey = Math.max(lastKey, lastKeyOf(b.rotation));
            lastKey = Math.max(lastKey, lastKeyOf(b.position));
            lastKey = Math.max(lastKey, lastKeyOf(b.scale));
        }
        double length = explicitLength == null ? lastKey : explicitLength.doubleValue();
        if (explicitLength != null && explicitLength.doubleValue() < lastKey) {
            throw new IllegalArgumentException("E_ANIM_LENGTH:short <" + name
                    + " length " + explicitLength + " < last key " + lastKey
                    + "> (keys past the length would never play)");
        }
        return new MatouAnimation(name, loop, length, timeUpdate, bones);
    }

    private static double lastKeyOf(MatouAnimation.Channel ch) {
        if (ch == null) {
            return 0.0;
        }
        return ch.keys.get(ch.keys.size() - 1).time;
    }

    private static MatouAnimation.Loop parseLoop(String name, Object o) {
        if (o instanceof Boolean) {
            return ((Boolean) o).booleanValue()
                    ? MatouAnimation.Loop.LOOP : MatouAnimation.Loop.CLAMP;
        }
        if ("hold_on_last_frame".equals(o)) {
            return MatouAnimation.Loop.HOLD;
        }
        throw new IllegalArgumentException("E_ANIM_CLIP:shape <" + name
                + ".loop> (want true/false/hold_on_last_frame)");
    }

    private static Double parseLength(String name, Object o) {
        if (!(o instanceof Number)) {
            throw new IllegalArgumentException("E_ANIM_LENGTH:missing <" + name
                    + "> (want animation_length as a number > 0)");
        }
        double d = ((Number) o).doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d) || !(d > 0.0)) {
            throw new IllegalArgumentException("E_ANIM_LENGTH:missing <" + name
                    + "> (want animation_length as a number > 0)");
        }
        return Double.valueOf(d);
    }

    private static Molang.Node parseTimeUpdate(String name, Object o) {
        if (!(o instanceof String) || ((String) o).trim().isEmpty()) {
            throw new IllegalArgumentException("E_ANIM_CLIP:shape <" + name
                    + ".anim_time_update> (want a MOLANG string)");
        }
        return Molang.parse((String) o);
    }

    private static Map<String, MatouAnimation.BoneAnim> parseBones(String name, Object o) {
        Map<String, Object> m = asObject(o,
                "E_ANIM_CLIP:shape <" + name + ".bones> (want an object)");
        if (m.isEmpty()) {
            throw new IllegalArgumentException("E_ANIM_CLIP:empty <" + name
                    + "> (want >= 1 animated bone)");
        }
        Map<String, MatouAnimation.BoneAnim> out =
                new LinkedHashMap<String, MatouAnimation.BoneAnim>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            out.put(e.getKey(), parseBoneAnim(name, e.getKey(), e.getValue()));
        }
        return out;
    }

    private static MatouAnimation.BoneAnim parseBoneAnim(String clip, String bone, Object o) {
        Map<String, Object> m = asObject(o,
                "E_ANIM_CHANNEL:shape <" + clip + "." + bone + "> (want an object)");
        MatouAnimation.Channel rotation = null;
        MatouAnimation.Channel position = null;
        MatouAnimation.Channel scale = null;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            String k = e.getKey();
            if (k.equals("rotation")) {
                rotation = parseChannel(clip, bone, "rotation", e.getValue());
            } else if (k.equals("position")) {
                position = parseChannel(clip, bone, "position", e.getValue());
            } else if (k.equals("scale")) {
                scale = parseChannel(clip, bone, "scale", e.getValue());
            } else if (k.equals("relative_to")) {
                throw new IllegalArgumentException("E_ANIM_RELATIVE <" + clip + "." + bone
                        + "> (entity-relative bones are a named follow-up, never silent)");
            } else {
                throw new IllegalArgumentException("E_ANIM_CHANNEL:shape <" + clip + "."
                        + bone + "." + k + "> (want rotation/position/scale)");
            }
        }
        if (rotation == null && position == null && scale == null) {
            throw new IllegalArgumentException("E_ANIM_CHANNEL:empty <" + clip + "." + bone
                    + "> (want >= 1 of rotation/position/scale)");
        }
        if (scale != null) {
            checkUniformLiteral(clip, bone, scale);
        }
        return new MatouAnimation.BoneAnim(rotation, position, scale);
    }

    /** Uniformity for literal scales (expression scales check at eval). */
    private static void checkUniformLiteral(String clip, String bone,
            MatouAnimation.Channel scale) {
        for (MatouAnimation.Keyframe k : scale.keys) {
            if (!k.x.isExpr && !k.y.isExpr && !k.z.isExpr
                    && (k.x.num != k.y.num || k.x.num != k.z.num)) {
                throw new IllegalArgumentException("E_ANIM_CHANNEL:scale <" + clip + "."
                        + bone + " " + k.x.num + "," + k.y.num + "," + k.z.num
                        + "> (scale stays uniform — Bedrock parity)");
            }
        }
    }

    private static MatouAnimation.Channel parseChannel(String clip, String bone,
            String channel, Object o) {
        if (o instanceof Number || o instanceof String || o instanceof List) {
            return new MatouAnimation.Channel(singleKeyList(compTriple(
                    clip, bone, channel, "flat", o)));
        }
        if (o instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> keys = (Map<String, Object>) o;
            if (keys.isEmpty()) {
                throw new IllegalArgumentException("E_ANIM_KEY:empty <" + clip + "."
                        + bone + "." + channel + "> (want >= 1 keyframe)");
            }
            List<double[]> times = new ArrayList<double[]>();
            for (Map.Entry<String, Object> e : keys.entrySet()) {
                times.add(new double[] {parseKeyTime(clip, bone, channel, e.getKey())});
            }
            List<MatouAnimation.Keyframe> out =
                    new ArrayList<MatouAnimation.Keyframe>();
            int i = 0;
            for (Map.Entry<String, Object> e : keys.entrySet()) {
                out.add(new MatouAnimation.Keyframe(times.get(i)[0],
                        compTriple(clip, bone, channel, e.getKey(), e.getValue())[0],
                        compTriple(clip, bone, channel, e.getKey(), e.getValue())[1],
                        compTriple(clip, bone, channel, e.getKey(), e.getValue())[2]));
                i++;
            }
            List<MatouAnimation.Keyframe> sorted =
                    new ArrayList<MatouAnimation.Keyframe>(out);
            for (int a = 1; a < sorted.size(); a++) {
                MatouAnimation.Keyframe k = sorted.get(a);
                int j = a - 1;
                while (j >= 0 && sorted.get(j).time > k.time) {
                    sorted.set(j + 1, sorted.get(j));
                    j--;
                }
                sorted.set(j + 1, k);
            }
            return new MatouAnimation.Channel(sorted);
        }
        throw new IllegalArgumentException("E_ANIM_CHANNEL:shape <" + clip + "." + bone
                + "." + channel + "> (want a number/[n]/[x,y,z] or keyframes)");
    }

    private static List<MatouAnimation.Keyframe> singleKeyList(MatouAnimation.Comp[] t) {
        List<MatouAnimation.Keyframe> out = new ArrayList<MatouAnimation.Keyframe>();
        out.add(new MatouAnimation.Keyframe(0.0, t[0], t[1], t[2]));
        return out;
    }

    private static double parseKeyTime(String clip, String bone, String channel, String k) {
        double t;
        try {
            t = Double.parseDouble(k);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("E_ANIM_KEY:time <" + clip + "." + bone
                    + "." + channel + " key <" + k + ">> (want decimal seconds >= 0)");
        }
        if (Double.isNaN(t) || Double.isInfinite(t) || t < 0.0) {
            throw new IllegalArgumentException("E_ANIM_KEY:time <" + clip + "." + bone
                    + "." + channel + " key <" + k + ">> (want decimal seconds >= 0)");
        }
        return t;
    }

    /** One value form into a triple (numbers or MOLANG strings mixed). */
    private static MatouAnimation.Comp[] compTriple(String clip, String bone,
            String channel, String where, Object o) {
        String tag = clip + "." + bone + "." + channel + " " + where;
        if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            MatouAnimation.Comp c = numComp(tag, d);
            return new MatouAnimation.Comp[] {c, new MatouAnimation.Comp(d), new MatouAnimation.Comp(d)};
        }
        if (o instanceof String) {
            // A scalar MOLANG broadcasts like a scalar number (Bedrock
            // single-value convention — one value drives x, y and z).
            MatouAnimation.Comp c = new MatouAnimation.Comp((String) o);
            return new MatouAnimation.Comp[] {c, c, c};
        }
        if (o instanceof List) {
            List<?> l = (List<?>) o;
            if (l.size() == 1) {
                MatouAnimation.Comp c = compOf(tag, l.get(0));
                return new MatouAnimation.Comp[] {c, c, c};
            }
            if (l.size() == 3) {
                return new MatouAnimation.Comp[] {
                    compOf(tag, l.get(0)), compOf(tag, l.get(1)), compOf(tag, l.get(2)),
                };
            }
            throw new IllegalArgumentException("E_ANIM_CHANNEL:shape <" + tag
                    + "> (want [n] or [x, y, z] — a pair is neither)");
        }
        throw new IllegalArgumentException("E_ANIM_KEY:shape <" + tag
                + "> (want a number/[n]/[x,y,z] — pre/post objects are a named follow-up)");
    }

    private static MatouAnimation.Comp numComp(String tag, double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("E_ANIM_CHANNEL:shape <" + tag
                    + "> (want finite numbers)");
        }
        return new MatouAnimation.Comp(d);
    }

    private static MatouAnimation.Comp compOf(String tag, Object o) {
        if (o instanceof Number) {
            return numComp(tag, ((Number) o).doubleValue());
        }
        if (o instanceof String) {
            return new MatouAnimation.Comp((String) o);
        }
        throw new IllegalArgumentException("E_ANIM_CHANNEL:shape <" + tag
                + "> (want per-component numbers or MOLANG strings)");
    }

    private static Map<String, Object> asObject(Object o, String msg) {
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException(msg);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) o;
        return m;
    }
}
