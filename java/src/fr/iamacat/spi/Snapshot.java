package fr.iamacat.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable tick snapshot that pure jobs read. The bridge builds one per tick
 * and owns the apply; jobs never see the live world. Java 8, zero deps.
 */
public final class Snapshot {
    private final long tick;
    private final Map<MatouId, Object> states;

    public Snapshot(long tick, Map<MatouId, Object> states) {
        if (states == null) {
            throw new NullPointerException("E_MATOU_SNAPSHOT:null states");
        }
        this.tick = tick;
        this.states = Collections.unmodifiableMap(
                new LinkedHashMap<MatouId, Object>(states));
    }

    public long tick() {
        return tick;
    }

    /** State for an id, or null when unknown. Unknown reads null, never throws. */
    public Object get(MatouId id) {
        if (id == null) {
            throw new NullPointerException("E_MATOU_SNAPSHOT:null id");
        }
        return states.get(id);
    }

    /**
     * Required state: unknown ids are refused loudly
     * ({@code E_MATOU_SNAPSHOT:missing}) instead of surfacing as null.
     * Prefer this over {@link #get} unless the absence itself is data.
     */
    public Object require(MatouId id) {
        Object value = get(id);
        if (value == null) {
            throw new IllegalArgumentException(
                    "E_MATOU_SNAPSHOT:missing <" + id + ">");
        }
        return value;
    }

    /** Required string state; missing or non-string is refused loudly. */
    public String stringOf(MatouId id) {
        Object value = require(id);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(
                    "E_MATOU_SNAPSHOT:type <" + value + "> (want String)");
        }
        return (String) value;
    }

    /**
     * Required numeric state, narrowed the way content counts narrow
     * (see {@link Counts}): truncation, never rounding, never default.
     */
    public long longOf(MatouId id) {
        Object value = require(id);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(
                    "E_MATOU_SNAPSHOT:type <" + value + "> (want number)");
        }
        return ((Number) value).longValue();
    }

    /** Required map state (cell tables, configs); missing or not-a-map refused. */
    public Map<?, ?> mapOf(MatouId id) {
        Object value = require(id);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException(
                    "E_MATOU_SNAPSHOT:type <" + value + "> (want map)");
        }
        return (Map<?, ?>) value;
    }

    public Set<MatouId> ids() {
        return states.keySet();
    }
}
