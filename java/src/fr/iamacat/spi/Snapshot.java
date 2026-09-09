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

    public Set<MatouId> ids() {
        return states.keySet();
    }
}
