package fr.iamacat.spi;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reference parser for SYNTAX-V1/V2/V3/V4/V5 (spec: spec/SYNTAX-V1.md,
 * spec/SYNTAX-V2.md, spec/SYNTAX-V3.md, spec/SYNTAX-V4.md,
 * spec/SYNTAX-V5.md).
 * Java port of parser/matou_parse.py — the goldens are the shared oracle:
 * both implementations must agree. Zero Minecraft imports. Java 8 bytecode.
 */
public final class MatouParse {
    private static final String[] GENRES_V1 = {"block", "item", "mob", "feature"};
    private static final String[] GENRES_V3 =
            {"block", "item", "mob", "feature", "structure"};
    private static final String[] GENRES_V4 =
            {"block", "item", "mob", "feature", "structure", "vein"};
    private static final String[] GENRES_V5 =
            {"block", "item", "mob", "feature", "structure", "vein",
                    "weakspot"};

    // Table-driven lookup: word -> Decl (block -> Block), reserved words,
    // scalar types. Single derivation point for the version-gated vocabularies.
    private static final Map<String, String> DECL_BY_WORD_V1 =
            new HashMap<String, String>();
    private static final Map<String, String> DECL_BY_WORD_V3 =
            new HashMap<String, String>();
    private static final Map<String, String> DECL_BY_WORD_V4 =
            new HashMap<String, String>();
    private static final Map<String, String> DECL_BY_WORD_V5 =
            new HashMap<String, String>();
    private static final Set<String> RESERVED = new HashSet<String>(Arrays.asList(
            "syntax", "namespace", "from", "use", "genre", "field",
            "true", "false"));
    private static final Set<String> SCALARS_V1 = new HashSet<String>(Arrays.asList(
            "f32", "u32", "bool", "string"));
    private static final Set<String> SCALARS_V2_ONLY =
            new HashSet<String>(Arrays.asList("i32", "vec3"));

    // Precompiled once: the hot line classifiers below must not recompile.
    private static final Pattern P_SYNTAX =
            Pattern.compile("syntax ([12345])");
    private static final Pattern P_NAMESPACE =
            Pattern.compile("namespace ([A-Za-z_][A-Za-z0-9_.]*)");
    private static final Pattern P_FROM =
            Pattern.compile("from ([A-Za-z_][A-Za-z0-9_.]*) use (.+)");
    private static final Pattern P_GENRE =
            Pattern.compile("genre ([A-Za-z_][A-Za-z0-9_]*) : Data");
    private static final Pattern P_FIELD =
            Pattern.compile("field ([A-Za-z_][A-Za-z0-9_]*) : (\\S+)");
    private static final Pattern P_INSTANCE =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_]*) ([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern P_FIELDLINE =
            Pattern.compile("  ([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+)");
    private static final Pattern P_FQID = Pattern.compile(
            "([A-Za-z_][A-Za-z0-9_.]*):([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern P_UINT = Pattern.compile("\\d+");
    private static final Pattern P_STRING = Pattern.compile("\"(.*)\"");

    static {
        for (String g : GENRES_V1) {
            DECL_BY_WORD_V1.put(g,
                    Character.toUpperCase(g.charAt(0)) + g.substring(1));
        }
        for (String g : GENRES_V3) {
            DECL_BY_WORD_V3.put(g,
                    Character.toUpperCase(g.charAt(0)) + g.substring(1));
        }
        for (String g : GENRES_V4) {
            DECL_BY_WORD_V4.put(g,
                    Character.toUpperCase(g.charAt(0)) + g.substring(1));
        }
        for (String g : GENRES_V5) {
            DECL_BY_WORD_V5.put(g,
                    Character.toUpperCase(g.charAt(0)) + g.substring(1));
        }
    }

    private MatouParse() {}

    private static boolean isReserved(String w) {
        return RESERVED.contains(w);
    }

    private static String declOf(String gword, int syntax) {
        if (syntax >= 5) {
            return DECL_BY_WORD_V5.get(gword);
        }
        if (syntax >= 4) {
            return DECL_BY_WORD_V4.get(gword);
        }
        return (syntax >= 3 ? DECL_BY_WORD_V3 : DECL_BY_WORD_V1).get(gword);
    }

    private static final class Line {
        final int n;
        final String text;
        Line(int n, String text) { this.n = n; this.text = text; }
    }

    private static final class RawField {
        final int line;
        final String name;
        final String raw;
        RawField(int line, String name, String raw) {
            this.line = line; this.name = name; this.raw = raw;
        }
    }

    private static final class Shell {
        final String decl;
        final String name;
        final List<RawField> raw = new ArrayList<RawField>();
        Shell(String decl, String name) { this.decl = decl; this.name = name; }
    }

    private static String stripComment(String line) {
        StringBuilder out = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            }
            if (ch == '#' && !quoted) {
                break;
            }
            out.append(ch);
        }
        String s = out.toString();
        int e = s.length();
        while (e > 0 && Character.isWhitespace(s.charAt(e - 1))) {
            e--;
        }
        return s.substring(0, e);
    }

    private static Matcher match(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.matches() ? m : null;
    }

    public static Map<String, Object> parseFile(String path) throws Exception {
        List<String> raw = Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
        List<Line> lines = new ArrayList<Line>();
        for (int i = 0; i < raw.size(); i++) {
            String s = stripComment(raw.get(i));
            if (!s.trim().isEmpty()) {
                lines.add(new Line(i + 1, s));
            }
        }
        if (lines.isEmpty()) {
            throw new MatouParseException("E_MATOU_VERSION", 1, "empty file");
        }
        Line first = lines.get(0);
        Matcher syntaxHead = match(P_SYNTAX, first.text);
        if (syntaxHead == null) {
            throw new MatouParseException("E_MATOU_VERSION", first.n, first.text);
        }
        int syntax = Integer.parseInt(syntaxHead.group(1));

        String namespace = null;
        TreeSet<String> imports = new TreeSet<String>();
        Map<String, Map<String, String>> genres =
                new LinkedHashMap<String, Map<String, String>>();
        List<Shell> shells = new ArrayList<Shell>();
        String openGenre = null;
        Shell pending = null;
        int pos = 1;
        while (pos < lines.size()) {
            Line ln = lines.get(pos);
            String l = ln.text;
            int n = ln.n;
            if (l.startsWith(" ") || l.startsWith("\t")) {
                Matcher m = match(P_FIELDLINE, l);
                if (m == null || pending == null) {
                    throw new MatouParseException("E_MATOU_INDENT", n, l);
                }
                String fname = m.group(1);
                String fraw = m.group(2).trim();
                Map<String, String> gf = genres.get(pending.decl);
                if (!gf.containsKey(fname)) {
                    throw new MatouParseException("E_MATOU_FIELD", n, fname);
                }
                for (RawField r : pending.raw) {
                    if (r.name.equals(fname)) {
                        throw new MatouParseException("E_MATOU_FIELD", n,
                                "duplicate " + fname);
                    }
                }
                pending.raw.add(new RawField(n, fname, fraw));
                pos++;
                continue;
            }
            if (pending != null) {
                closeCheck(pending, genres, n);
                shells.add(pending);
                pending = null;
            }
            if (l.startsWith("namespace ")) {
                Matcher m = match(P_NAMESPACE, l);
                if (m == null) {
                    throw new MatouParseException("E_MATOU_HEADER", n, l);
                }
                if (namespace != null) {
                    throw new MatouParseException("E_MATOU_HEADER", n,
                            "duplicate namespace");
                }
                namespace = m.group(1);
                openGenre = null;
            } else if (l.startsWith("from ")) {
                Matcher m = match(P_FROM, l);
                if (m == null) {
                    throw new MatouParseException("E_MATOU_HEADER", n, l);
                }
                imports.add(m.group(1));
                openGenre = null;
            } else if (l.startsWith("genre ")) {
                Matcher m = match(P_GENRE, l);
                if (m == null) {
                    throw new MatouParseException("E_MATOU_GENRE", n, l);
                }
                String decl = m.group(1);
                if (declOf(decl.toLowerCase(), syntax) == null
                        || !declOf(decl.toLowerCase(), syntax).equals(decl)) {
                    throw new MatouParseException("E_MATOU_GENRE", n, decl);
                }
                if (genres.containsKey(decl)) {
                    throw new MatouParseException("E_MATOU_GENRE", n,
                            "duplicate " + decl);
                }
                genres.put(decl, new LinkedHashMap<String, String>());
                openGenre = decl;
            } else if (l.startsWith("field ")) {
                Matcher m = match(P_FIELD, l);
                if (m == null || openGenre == null) {
                    throw new MatouParseException("E_MATOU_FIELD", n, l);
                }
                String fname = m.group(1);
                String ftyp = m.group(2);
                if (isReserved(fname)) {
                    throw new MatouParseException("E_MATOU_RESERVED", n, fname);
                }
                if (!validType(ftyp, syntax)) {
                    throw new MatouParseException("E_MATOU_TYPE", n, ftyp);
                }
                Map<String, String> gf = genres.get(openGenre);
                if (gf.containsKey(fname)) {
                    throw new MatouParseException("E_MATOU_FIELD", n,
                            "duplicate " + fname);
                }
                gf.put(fname, ftyp);
            } else {
                Matcher m = match(P_INSTANCE, l);
                if (m == null) {
                    throw new MatouParseException("E_MATOU_GENRE", n, l);
                }
                String gword = m.group(1);
                String iname = m.group(2);
                if (isReserved(gword)) {
                    throw new MatouParseException("E_MATOU_HEADER", n, l);
                }
                String decl = declOf(gword, syntax);
                if (decl == null || !genres.containsKey(decl)) {
                    throw new MatouParseException("E_MATOU_GENRE", n, gword);
                }
                if (namespace == null) {
                    throw new MatouParseException("E_MATOU_HEADER", n,
                            "instance before namespace");
                }
                if (isReserved(iname)) {
                    throw new MatouParseException("E_MATOU_RESERVED", n, iname);
                }
                for (Shell s : shells) {
                    if (s.decl.equals(decl) && s.name.equals(iname)) {
                        throw new MatouParseException("E_MATOU_FIELD", n,
                                "duplicate " + iname);
                    }
                }
                pending = new Shell(decl, iname);
                openGenre = null;
            }
            pos++;
        }
        if (pending != null) {
            closeCheck(pending, genres, lines.get(lines.size() - 1).n);
            shells.add(pending);
        }
        if (namespace == null) {
            throw new MatouParseException("E_MATOU_HEADER",
                    lines.get(lines.size() - 1).n, "missing namespace");
        }

        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Shell sh : shells) {
            Map<String, String> gf = genres.get(sh.decl);
            Map<String, Object> fields = new LinkedHashMap<String, Object>();
            for (RawField r : sh.raw) {
                fields.put(r.name, parseValue(r.raw, gf.get(r.name), r.line,
                        namespace, imports, shells));
            }
            Map<String, Object> inst = new LinkedHashMap<String, Object>();
            inst.put("decl", sh.decl);
            inst.put("fields", fields);
            inst.put("name", sh.name);
            out.add(inst);
        }
        Map<String, Object> tree = new LinkedHashMap<String, Object>();
        tree.put("syntax", syntax);
        tree.put("namespace", namespace);
        tree.put("imports", new ArrayList<String>(imports));
        tree.put("genres", genres);
        tree.put("instances", out);
        return tree;
    }

    private static void closeCheck(Shell inst,
            Map<String, Map<String, String>> genres, int endLine)
            throws MatouParseException {
        Set<String> seen = new HashSet<String>();
        for (RawField r : inst.raw) {
            seen.add(r.name);
        }
        for (String f : genres.get(inst.decl).keySet()) {
            if (!seen.contains(f)) {
                throw new MatouParseException("E_MATOU_FIELD", endLine,
                        "missing " + f);
            }
        }
    }

    private static boolean validScalarOrRef(String t, int syntax) {
        if (SCALARS_V1.contains(t) || SCALARS_V2_ONLY.contains(t)) {
            return true;
        }
        if (t.endsWith("_ref")) {
            String g = t.substring(0, t.length() - 4);
            return declOf(g, syntax) != null;
        }
        return false;
    }

    private static boolean validType(String t, int syntax) {
        if (SCALARS_V1.contains(t)) {
            return true;
        }
        if (syntax >= 2) {
            if (SCALARS_V2_ONLY.contains(t)) {
                return true;
            }
            if (t.startsWith("list<") && t.endsWith(">")) {
                return validScalarOrRef(t.substring(5, t.length() - 1), syntax);
            }
        }
        return t.endsWith("_ref")
                && declOf(t.substring(0, t.length() - 4), syntax) != null;
    }

    private static Object parseValue(String raw, String typ, int line,
            String localNs, TreeSet<String> imports,
            List<Shell> shells) throws MatouParseException {
        if (typ.startsWith("list<") && typ.endsWith(">")) {
            String inner = typ.substring(5, typ.length() - 1);
            String s = raw.trim();
            if (s.length() < 2 || !s.startsWith("[") || !s.endsWith("]")) {
                throw new MatouParseException("E_MATOU_TYPE", line, raw);
            }
            String body = s.substring(1, s.length() - 1).trim();
            List<Object> out = new ArrayList<Object>();
            if (!body.isEmpty()) {
                for (String e : splitTopLevel(body)) {
                    out.add(parseScalar(e.trim(), inner, line,
                            localNs, imports, shells));
                }
            }
            return out;
        }
        return parseScalar(raw.trim(), typ, line, localNs, imports, shells);
    }

    /** Top-level comma split; a comma inside "..." never splits. */
    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            }
            if (ch == ',' && !quoted) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        parts.add(cur.toString());
        return parts;
    }

    private static final Pattern P_INT = Pattern.compile("-?\\d+");

    private static Object parseScalar(String raw, String typ, int line,
            String localNs, TreeSet<String> imports,
            List<Shell> shells) throws MatouParseException {
        if (typ.equals("f32")) {
            try {
                return Double.valueOf(raw);
            } catch (NumberFormatException e) {
                throw new MatouParseException("E_MATOU_TYPE", line, raw);
            }
        }
        if (typ.equals("u32") || typ.equals("i32")) {
            Pattern p = typ.equals("u32") ? P_UINT : P_INT;
            if (match(p, raw) != null) {
                try {
                    return Long.valueOf(raw);
                } catch (NumberFormatException e) {
                    throw new MatouParseException("E_MATOU_TYPE", line, raw);
                }
            }
            throw new MatouParseException("E_MATOU_TYPE", line, raw);
        }
        if (typ.equals("bool")) {
            if (raw.equals("true")) {
                return Boolean.TRUE;
            }
            if (raw.equals("false")) {
                return Boolean.FALSE;
            }
            throw new MatouParseException("E_MATOU_TYPE", line, raw);
        }
        if (typ.equals("string")) {
            Matcher m = match(P_STRING, raw);
            if (m == null) {
                throw new MatouParseException("E_MATOU_TYPE", line, raw);
            }
            return m.group(1);
        }
        if (typ.equals("vec3")) {
            String[] parts = raw.split(",", -1);
            if (parts.length != 3) {
                throw new MatouParseException("E_MATOU_TYPE", line, raw);
            }
            List<Object> out = new ArrayList<Object>();
            for (String p : parts) {
                String e = p.trim();
                if (match(P_INT, e) == null) {
                    throw new MatouParseException("E_MATOU_TYPE", line, raw);
                }
                try {
                    out.add(Long.valueOf(e));
                } catch (NumberFormatException ex) {
                    throw new MatouParseException("E_MATOU_TYPE", line, raw);
                }
            }
            return out;
        }
        Matcher m = match(P_FQID, raw);
        if (m == null) {
            throw new MatouParseException("E_MATOU_TYPE", line, raw);
        }
        String rns = m.group(1);
        String rname = m.group(2);
        if (!rns.equals(localNs) && !imports.contains(rns)) {
            throw new MatouParseException("E_MATOU_UNKNOWN_REF", line, raw);
        }
        String want = typ.substring(0, typ.length() - 4);
        if (rns.equals(localNs)) {
            boolean found = false;
            for (Shell s : shells) {
                if (s.decl.toLowerCase().equals(want) && s.name.equals(rname)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new MatouParseException("E_MATOU_UNKNOWN_REF", line, raw);
            }
        }
        return raw;
    }

    // --- canonical JSON (structural compare in gate; key order free) ---
    private static String json(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof String) {
            return "\"" + ((String) o).replace("\\", "\\\\")
                    .replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        }
        if (o instanceof Double) {
            double d = (Double) o;
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                return Long.toString((long) d) + ".0";
            }
            return Double.toString(d);
        }
        if (o instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object e : (List<?>) o) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(json(e));
            }
            return sb.append("]").toString();
        }
        if (o instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(json(e.getKey().toString()));
                sb.append(": ");
                sb.append(json(e.getValue()));
            }
            return sb.append("}").toString();
        }
        return o.toString();
    }

    public static void main(String[] args) {
        try {
            System.out.println(json(parseFile(args[0])));
        } catch (MatouParseException e) {
            System.out.println(e.toString());
            System.exit(1);
        } catch (Exception e) {
            System.out.println("E_MATOU_IO:0: " + e.getMessage());
            System.exit(2);
        }
    }
}
