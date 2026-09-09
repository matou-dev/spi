package fr.iamacat.spi;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SPI identity: {@code namespace:name} (spec/SYNTAX-V1.md section 2).
 * Bare idents are refused, never defaulted. Immutable, Java 8, zero deps.
 */
public final class MatouId implements Comparable<MatouId> {
    private static final Pattern NS =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");
    private static final Pattern NAME =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern FQID = Pattern.compile(
            "([A-Za-z_][A-Za-z0-9_.]*):([A-Za-z_][A-Za-z0-9_]*)");

    public final String namespace;
    public final String name;

    private MatouId(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    public static MatouId of(String namespace, String name) {
        if (namespace == null || !NS.matcher(namespace).matches()) {
            throw new IllegalArgumentException(
                    "E_MATOU_ID:bad namespace <" + namespace + ">");
        }
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "E_MATOU_ID:bad name <" + name + ">");
        }
        return new MatouId(namespace, name);
    }

    /** Parse {@code namespace:name}; anything else is refused loudly. */
    public static MatouId parse(String raw) {
        if (raw == null) {
            throw new NullPointerException("E_MATOU_ID:null");
        }
        Matcher m = FQID.matcher(raw);
        if (!m.matches()) {
            throw new IllegalArgumentException("E_MATOU_ID:bare-or-malformed <"
                    + raw + "> (want namespace:name)");
        }
        return new MatouId(m.group(1), m.group(2));
    }

    @Override
    public String toString() {
        return namespace + ":" + name;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MatouId)) {
            return false;
        }
        MatouId other = (MatouId) o;
        return namespace.equals(other.namespace) && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return 31 * namespace.hashCode() + name.hashCode();
    }

    @Override
    public int compareTo(MatouId other) {
        int c = namespace.compareTo(other.namespace);
        return c != 0 ? c : name.compareTo(other.name);
    }
}
