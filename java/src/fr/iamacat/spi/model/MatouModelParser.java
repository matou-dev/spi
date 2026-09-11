package fr.iamacat.spi.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses a Blockbench Bedrock geometry file (frozen subset, see hub
 * decisions/MATOU_MODEL.md) into a MatouModel. First entry of
 * minecraft:geometry wins; multi-geometry files must split before import.
 * Refusals carry E_MODEL_* codes, never silent defaults. Java 8, zero dep.
 */
public final class MatouModelParser {
    private MatouModelParser() {}

    public static MatouModel parse(String json) {
        Object root = JsonParser.parse(json);
        Map<String, Object> doc = asObject(root, "E_MODEL_JSON:type (want a JSON object at root)");
        Object version = doc.get("format_version");
        if (!(version instanceof String)
                || ((String) version).trim().isEmpty()) {
            throw new IllegalArgumentException("E_MODEL_VERSION:missing (want format_version string)");
        }
        Object geos = doc.get("minecraft:geometry");
        if (!(geos instanceof List) || ((List<?>) geos).isEmpty()) {
            throw new IllegalArgumentException("E_MODEL_GEOMETRY:missing (want a non-empty minecraft:geometry array)");
        }
        Object first = ((List<?>) geos).get(0);
        if (!(first instanceof Map)) {
            throw new IllegalArgumentException("E_MODEL_GEOMETRY:shape (want an object per geometry entry)");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> geo = (Map<String, Object>) first;
        Object desc = geo.get("description");
        if (!(desc instanceof Map)) {
            throw new IllegalArgumentException("E_MODEL_IDENTIFIER:missing (want description.identifier)");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> description = (Map<String, Object>) desc;
        Object identifier = description.get("identifier");
        if (!(identifier instanceof String)
                || ((String) identifier).trim().isEmpty()) {
            throw new IllegalArgumentException("E_MODEL_IDENTIFIER:missing (want description.identifier)");
        }
        int texW = textureDim(description.get("texture_width"), "width");
        int texH = textureDim(description.get("texture_height"), "height");

        List<ModelBone> bones = new ArrayList<ModelBone>();
        Object bonesObj = geo.get("bones");
        if (bonesObj != null) {
            if (!(bonesObj instanceof List)) {
                throw new IllegalArgumentException("E_MODEL_BONE:shape (want a bones array)");
            }
            for (Object e : (List<?>) bonesObj) {
                bones.add(parseBone(e));
            }
        }
        Set<String> names = new HashSet<String>();
        for (ModelBone b : bones) {
            if (!names.add(b.name)) {
                throw new IllegalArgumentException("E_MODEL_BONE:duplicate <"
                        + b.name + "> (want unique bone names)");
            }
        }
        for (ModelBone b : bones) {
            if (b.parent != null && !names.contains(b.parent)) {
                throw new IllegalArgumentException("E_MODEL_BONE:parent <"
                        + b.name + " -> " + b.parent + "> (want an existing bone)");
            }
        }
        return new MatouModel(((String) identifier).trim(), texW, texH, bones);
    }

    private static ModelBone parseBone(Object e) {
        Map<String, Object> m = asObject(e, "E_MODEL_BONE:shape (want an object per bone)");
        Object name = m.get("name");
        if (!(name instanceof String) || ((String) name).trim().isEmpty()) {
            throw new IllegalArgumentException("E_MODEL_BONE:empty (want a non-blank bone name)");
        }
        Object parent = m.get("parent");
        String parentName = null;
        if (parent != null) {
            if (!(parent instanceof String)
                    || ((String) parent).trim().isEmpty()) {
                throw new IllegalArgumentException("E_MODEL_BONE:parent <"
                        + name + "> (want a non-blank bone name or no parent)");
            }
            parentName = ((String) parent).trim();
        }
        double[] pivot = {0.0, 0.0, 0.0};
        if (m.get("pivot") != null) {
            pivot = vec3(m.get("pivot"), "E_MODEL_BONE:pivot");
        }
        List<ModelCube> cubes = new ArrayList<ModelCube>();
        Object cubesObj = m.get("cubes");
        if (cubesObj != null) {
            if (!(cubesObj instanceof List)) {
                throw new IllegalArgumentException("E_MODEL_CUBE:shape (want a cubes array)");
            }
            for (Object ce : (List<?>) cubesObj) {
                cubes.add(parseCube(ce));
            }
        }
        return new ModelBone(((String) name).trim(), parentName,
                pivot[0], pivot[1], pivot[2], cubes);
    }

    private static ModelCube parseCube(Object e) {
        Map<String, Object> m = asObject(e, "E_MODEL_CUBE:shape (want an object per cube)");
        if (m.get("origin") == null) {
            throw new IllegalArgumentException("E_MODEL_CUBE:origin (want origin [x, y, z])");
        }
        double[] origin = vec3(m.get("origin"), "E_MODEL_CUBE:origin");
        if (m.get("size") == null) {
            throw new IllegalArgumentException("E_MODEL_CUBE:size (want size [x, y, z] all > 0)");
        }
        double[] size = vec3(m.get("size"), "E_MODEL_CUBE:size");
        double uvU = 0.0;
        double uvV = 0.0;
        if (m.get("uv") != null) {
            Object uv = m.get("uv");
            if (uv instanceof List) {
                List<?> l = (List<?>) uv;
                if (l.size() != 2) {
                    throw new IllegalArgumentException("E_MODEL_CUBE:uv (want [u, v] with u,v >= 0)");
                }
                uvU = num(l.get(0), "E_MODEL_CUBE:uv");
                uvV = num(l.get(1), "E_MODEL_CUBE:uv");
            } else if (!(uv instanceof Number)) {
                throw new IllegalArgumentException("E_MODEL_CUBE:uv (want [u, v] with u,v >= 0)");
            }
        }
        double inflate = 0.0;
        if (m.get("inflate") != null) {
            inflate = num(m.get("inflate"), "E_MODEL_CUBE:shape");
        }
        return new ModelCube(origin[0], origin[1], origin[2],
                size[0], size[1], size[2], uvU, uvV, inflate);
    }

    private static double[] vec3(Object o, String code) {
        if (!(o instanceof List) || ((List<?>) o).size() != 3) {
            throw new IllegalArgumentException(code + " (want [x, y, z] numbers)");
        }
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            out[i] = num(((List<?>) o).get(i), code);
        }
        return out;
    }

    private static double num(Object o, String code) {
        if (!(o instanceof Number) || Double.isNaN(((Number) o).doubleValue())
                || Double.isInfinite(((Number) o).doubleValue())) {
            throw new IllegalArgumentException(code + " (want a finite number)");
        }
        return ((Number) o).doubleValue();
    }

    private static int textureDim(Object o, String which) {
        if (!(o instanceof Number)) {
            throw new IllegalArgumentException("E_MODEL_TEXTURE:shape (want integral texture_"
                    + which + " in 1..4096)");
        }
        double d = ((Number) o).doubleValue();
        if (Double.isNaN(d) || d != Math.rint(d) || d < 1.0 || d > 4096.0) {
            throw new IllegalArgumentException("E_MODEL_TEXTURE:shape (want integral texture_"
                    + which + " in 1..4096)");
        }
        return (int) d;
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
