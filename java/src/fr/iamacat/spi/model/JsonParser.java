package fr.iamacat.spi.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser for the Bedrock geometry subset (objects, arrays,
 * strings with escapes, numbers, true/false/null). Pure Java 8, zero
 * dependency — org.json/Gson cannot enter matou-spi. Numbers decode as
 * Double, objects keep document order (LinkedHashMap).
 */
public final class JsonParser {
    private final String src;
    private int pos;

    private JsonParser(String src) {
        this.src = src;
        this.pos = 0;
    }

    public static Object parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("E_MODEL_JSON:empty (want a JSON document)");
        }
        JsonParser p = new JsonParser(text);
        Object v = p.value();
        p.ws();
        if (p.pos != p.src.length()) {
            throw new IllegalArgumentException("E_MODEL_JSON:syntax <trailing characters at " + p.pos + ">");
        }
        return v;
    }

    private IllegalArgumentException syntax(String want) {
        return new IllegalArgumentException("E_MODEL_JSON:syntax <" + want + " at " + pos + ">");
    }

    private void ws() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                return;
            }
        }
    }

    private Object value() {
        ws();
        if (pos >= src.length()) {
            throw syntax("value");
        }
        char c = src.charAt(pos);
        if (c == '{') {
            return object();
        }
        if (c == '[') {
            return array();
        }
        if (c == '"') {
            return string();
        }
        if (c == 't' || c == 'f' || c == 'n') {
            return literal();
        }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        pos++;
        ws();
        if (pos < src.length() && src.charAt(pos) == '}') {
            pos++;
            return out;
        }
        while (true) {
            ws();
            if (pos >= src.length() || src.charAt(pos) != '"') {
                throw syntax("object key");
            }
            String key = string();
            ws();
            if (pos >= src.length() || src.charAt(pos) != ':') {
                throw syntax("':' after key");
            }
            pos++;
            out.put(key, value());
            ws();
            if (pos >= src.length()) {
                throw syntax("',' or '}'");
            }
            char c = src.charAt(pos);
            if (c == ',') {
                pos++;
            } else if (c == '}') {
                pos++;
                return out;
            } else {
                throw syntax("',' or '}'");
            }
        }
    }

    private List<Object> array() {
        List<Object> out = new ArrayList<Object>();
        pos++;
        ws();
        if (pos < src.length() && src.charAt(pos) == ']') {
            pos++;
            return out;
        }
        while (true) {
            out.add(value());
            ws();
            if (pos >= src.length()) {
                throw syntax("',' or ']'");
            }
            char c = src.charAt(pos);
            if (c == ',') {
                pos++;
            } else if (c == ']') {
                pos++;
                return out;
            } else {
                throw syntax("',' or ']'");
            }
        }
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        pos++;
        while (true) {
            if (pos >= src.length()) {
                throw syntax("closing '\"'");
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= src.length()) {
                    throw syntax("escape");
                }
                char e = src.charAt(pos++);
                if (e == '"') {
                    sb.append('"');
                } else if (e == '\\') {
                    sb.append('\\');
                } else if (e == '/') {
                    sb.append('/');
                } else if (e == 'n') {
                    sb.append('\n');
                } else if (e == 't') {
                    sb.append('\t');
                } else if (e == 'r') {
                    sb.append('\r');
                } else if (e == 'b') {
                    sb.append('\b');
                } else if (e == 'f') {
                    sb.append('\f');
                } else if (e == 'u') {
                    if (pos + 4 > src.length()) {
                        throw syntax("unicode escape");
                    }
                    try {
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw syntax("unicode escape");
                    }
                    pos += 4;
                } else {
                    throw syntax("escape");
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Object literal() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw syntax("literal");
    }

    private Double number() {
        int start = pos;
        if (pos < src.length() && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) {
            pos++;
        }
        while (pos < src.length() && (Character.isDigit(src.charAt(pos))
                || src.charAt(pos) == '.' || src.charAt(pos) == 'e'
                || src.charAt(pos) == 'E' || src.charAt(pos) == '+'
                || src.charAt(pos) == '-')) {
            pos++;
        }
        if (start == pos) {
            throw syntax("number");
        }
        try {
            return Double.valueOf(src.substring(start, pos));
        } catch (NumberFormatException e) {
            throw syntax("number");
        }
    }
}
