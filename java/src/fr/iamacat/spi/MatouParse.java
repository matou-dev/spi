package fr.iamacat.spi;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reference parser for SYNTAX-V1 (spec: spec/SYNTAX-V1.md).
 * Java port of parser/matou_parse.py — the 9 goldens are the shared oracle:
 * both implementations must agree. Zero Minecraft imports. Java 8 bytecode.
 */
public final class MatouParse {
    private static final String[] GENRES_V1 = {"block", "item", "mob", "feature"};
    private static final String[] RESERVED = {"syntax", "namespace", "from",
            "use", "genre", "field", "true", "false"};

    private MatouParse() {}

    private static boolean isReserved(String w) {
        for (String r : RESERVED) {
            if (r.equals(w)) {
                return true;
            }
        }
        return false;
    }

    private static String declOf(String gword) {
        for (String g : GENRES_V1) {
            if (g.equals(gword)) {
                return Character.toUpperCase(g.charAt(0)) + g.substring(1);
            }
        }
        return null;
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

    private static Matcher full(String re, String s) {
        Matcher m = Pattern.compile(re).matcher(s);
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
        if (full("syntax 1", first.text) == null) {
            throw new MatouParseException("E_MATOU_VERSION", first.n, first.text);
        }

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
                Matcher m = full("  ([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+)", l);
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
                Matcher m = full("namespace ([A-Za-z_][A-Za-z0-9_.]*)", l);
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
                Matcher m = full("from ([A-Za-z_][A-Za-z0-9_.]*) use (.+)", l);
                if (m == null) {
                    throw new MatouParseException("E_MATOU_HEADER", n, l);
                }
                imports.add(m.group(1));
                openGenre = null;
            } else if (l.startsWith("genre ")) {
                Matcher m = full("genre ([A-Za-z_][A-Za-z0-9_]*) : Data", l);
                if (m == null) {
                    throw new MatouParseException("E_MATOU_GENRE", n, l);
                }
                String decl = m.group(1);
                if (declOf(decl.toLowerCase()) == null
                        || !declOf(decl.toLowerCase()).equals(decl)) {
                    throw new MatouParseException("E_MATOU_GENRE", n, decl);
                }
                if (genres.containsKey(decl)) {
                    throw new MatouParseException("E_MATOU_GENRE", n,
                            "duplicate " + decl);
                }
                genres.put(decl, new LinkedHashMap<String, String>());
                openGenre = decl;
            } else if (l.startsWith("field ")) {
                Matcher m = full("field ([A-Za-z_][A-Za-z0-9_]*) : (\\S+)", l);
                if (m == null || openGenre == null) {
                    throw new MatouParseException("E_MATOU_FIELD", n, l);
                }
                String fname = m.group(1);
                String ftyp = m.group(2);
                if (isReserved(fname)) {
                    throw new MatouParseException("E_MATOU_RESERVED", n, fname);
                }
                if (!validType(ftyp)) {
                    throw new MatouParseException("E_MATOU_TYPE", n, ftyp);
                }
                Map<String, String> gf = genres.get(openGenre);
                if (gf.containsKey(fname)) {
                    throw new MatouParseException("E_MATOU_FIELD", n,
                            "duplicate " + fname);
                }
                gf.put(fname, ftyp);
            } else {
                Matcher m = full("([A-Za-z_][A-Za-z0-9_]*) ([A-Za-z_][A-Za-z0-9_]*)", l);
                if (m == null) {
                    throw new MatouParseException("E_MATOU_GENRE", n, l);
                }
                String gword = m.group(1);
                String iname = m.group(2);
                if (isReserved(gword)) {
                    throw new MatouParseException("E_MATOU_HEADER", n, l);
                }
                String decl = declOf(gword);
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
                        namespace, imports, namespace, shells));
            }
            Map<String, Object> inst = new LinkedHashMap<String, Object>();
            inst.put("decl", sh.decl);
            inst.put("fields", fields);
            inst.put("name", sh.name);
            out.add(inst);
        }
        Map<String, Object> tree = new LinkedHashMap<String, Object>();
        tree.put("syntax", 1);
        tree.put("namespace", namespace);
        tree.put("imports", new ArrayList<String>(imports));
        tree.put("genres", genres);
        tree.put("instances", out);
        return tree;
    }

    private static void closeCheck(Shell inst,
            Map<String, Map<String, String>> genres, int endLine)
            throws MatouParseException {
        List<String> seen = new ArrayList<String>();
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

    private static boolean validType(String t) {
        if (t.equals("f32") || t.equals("u32") || t.equals("bool")
                || t.equals("string")) {
            return true;
        }
        if (t.endsWith("_ref")) {
            String g = t.substring(0, t.length() - 4);
            return declOf(g) != null;
        }
        return false;
    }

    private static Object parseValue(String raw, String typ, int line,
            String localNs, TreeSet<String> imports, String ns,
            List<Shell> shells) throws MatouParseException {
        if (typ.equals("f32")) {
            try {
                return Double.valueOf(raw);
            } catch (NumberFormatException e) {
                throw new MatouParseException("E_MATOU_TYPE", line, raw);
            }
        }
        if (typ.equals("u32")) {
            if (full("\\d+", raw) != null) {
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
            Matcher m = full("\"(.*)\"", raw);
            if (m == null) {
                throw new MatouParseException("E_MATOU_TYPE", line, raw);
            }
            return m.group(1);
        }
        Matcher m = full("([A-Za-z_][A-Za-z0-9_.]*):([A-Za-z_][A-Za-z0-9_]*)", raw);
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
