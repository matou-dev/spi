package fr.iamacat.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * T3 shared state vocabulary (hub {@code decisions/SPI_STATE_VOCABULARY.md}):
 * a generic, content-blind descriptor naming the snapshot states one sealed
 * subsystem shares between its pure job and its bridge seal.
 *
 * <p>The pure jobs and the bridge seals agree on snapshot ids without either
 * side importing the other: the content builds the vocabulary once from its
 * namespace (parse-once, beside its tables) and both sides resolve
 * {@link MatouId} through it. No content name ever enters this class — only
 * the mechanism lives here; namespaces, role names and scopes stay with
 * their owners (the spawn/loot domain roles in {@link SpawnStates} /
 * {@link LootStates}, the content namespaces in the packs).
 *
 * <p>Explicit provision, never a static registry: a global mutable registry
 * would couple seal reads to class-init order across jars (fragile) and leak
 * state between gate batteries (untestable in isolation). The vocabulary is
 * built once, carried explicitly (pack {@code vocabulary(scope)}, seal
 * first parameter), resolved often. Unknown names, duplicate names, empty
 * vocabularies and nulls are refused loudly, never defaulted. Immutable,
 * Java 8, zero deps.
 */
public final class StateVocabulary {
    private final String namespace;
    private final List<String> names;
    private final Map<String, MatouId> ids;

    private StateVocabulary(String namespace, List<String> names) {
        this.namespace = namespace;
        this.names = Collections.unmodifiableList(names);
        Map<String, MatouId> built = new LinkedHashMap<String, MatouId>();
        for (String name : names) {
            built.put(name, MatouId.of(namespace, name));
        }
        this.ids = Collections.unmodifiableMap(built);
    }

    /**
     * Builds the vocabulary for a namespace over ordered distinct state
     * names. Insertion order is kept throughout, so sealed maps iterate
     * identically live and in verdict replay.
     */
    public static StateVocabulary of(String namespace, String... names) {
        if (namespace == null) {
            throw new NullPointerException(
                    "E_MATOU_VOCAB:null namespace");
        }
        if (names == null) {
            throw new NullPointerException("E_MATOU_VOCAB:null names in <"
                    + namespace + ">");
        }
        List<String> seen = new ArrayList<String>();
        for (String name : names) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException(
                        "E_MATOU_VOCAB:bad name <" + name + "> in <"
                                + namespace + "> (want MatouId names)");
            }
            // Loud on malformed namespace or name alike — MatouId owns
            // the shape, this class never re-states its patterns.
            MatouId.of(namespace, name);
            if (seen.contains(name)) {
                throw new IllegalArgumentException(
                        "E_MATOU_VOCAB:dup <" + name + "> in <"
                                + namespace + "> (one id per state)");
            }
            seen.add(name);
        }
        if (seen.isEmpty()) {
            throw new IllegalArgumentException("E_MATOU_VOCAB:empty <"
                    + namespace + "> (a vocabulary names at least one "
                    + "state)");
        }
        return new StateVocabulary(namespace, seen);
    }

    /** Owning namespace, e.g. {@code "example1.spawn"}. */
    public String namespace() {
        return namespace;
    }

    /** State names in declaration order, never null entries. */
    public List<String> names() {
        return names;
    }

    /**
     * Resolves one state id. Unknown names are refused loudly — a seal
     * reading a vocabulary that does not carry its roles fails here,
     * never seals under a null id.
     */
    public MatouId id(String name) {
        if (name == null) {
            throw new NullPointerException("E_MATOU_VOCAB:null name in <"
                    + namespace + ">");
        }
        MatouId id = ids.get(name);
        if (id == null) {
            throw new IllegalArgumentException("E_MATOU_VOCAB:unknown <"
                    + name + "> in <" + namespace + "> (want " + names
                    + ")");
        }
        return id;
    }
}
