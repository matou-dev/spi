package fr.iamacat.spi;

/**
 * Spawn-domain state roles (hub
 * {@code decisions/SPI_STATE_VOCABULARY.md}): the sealed snapshot contract
 * every budgeted-spawn subsystem honours — living census, content mob
 * table, living cap, per-tick budget, ordinate band.
 *
 * <p>Content-blind: only the domain roles live here, never a content
 * namespace. A content builds its vocabulary with its own namespace
 * ({@link #vocabulary}) and both its pure job and its bridge seal resolve
 * through the typed accessors below — the role literals exist exactly
 * once, on these constants. Java 8, zero deps.
 */
public final class SpawnStates {
    private SpawnStates() {}

    /** Pack scope serving the spawn vocabulary (see {@link VocabularyPack}). */
    public static final String SCOPE = "spawn";

    /** Living census role: entity id to spawn cell. */
    public static final String CENSUS = "census";
    /**
     * Spawn table role: the content mob ref every spawn carries.
     * Sole-mob seals carry the single qualified ref string; per-mob
     * seals carry an ordered list of qualified mob refs (hub
     * {@code decisions/SPAWN.md}, second-beast tranche of hub
     * {@code decisions/VIRTUAL_HITBOXES.md}). The single-string shape
     * stays valid for sole-mob seals.
     */
    public static final String TABLE = "table";
    /** Living cap role: census at cap means no spawn. */
    public static final String CAP = "cap";
    /** Per-tick budget role: spawns landed while room remains. */
    public static final String BUDGET = "budget";
    /** Ordinate band role: {@code [yMin, yMax]} longs, inclusive. */
    public static final String Y = "y";

    /**
     * Builds the spawn vocabulary for a content namespace, roles in seal
     * order (census, table, cap, budget, y) so sealed maps iterate
     * identically live and in verdict replay.
     */
    public static StateVocabulary vocabulary(String namespace) {
        if (namespace == null) {
            throw new NullPointerException(
                    "E_MATOU_VOCAB:null spawn namespace");
        }
        return StateVocabulary.of(namespace, CENSUS, TABLE, CAP, BUDGET,
                Y);
    }

    /** Resolves the census id, refusing a vocabulary without the role. */
    public static MatouId census(StateVocabulary vocab) {
        return role(vocab, CENSUS);
    }

    /** Resolves the table id, refusing a vocabulary without the role. */
    public static MatouId table(StateVocabulary vocab) {
        return role(vocab, TABLE);
    }

    /** Resolves the cap id, refusing a vocabulary without the role. */
    public static MatouId cap(StateVocabulary vocab) {
        return role(vocab, CAP);
    }

    /** Resolves the budget id, refusing a vocabulary without the role. */
    public static MatouId budget(StateVocabulary vocab) {
        return role(vocab, BUDGET);
    }

    /** Resolves the band id, refusing a vocabulary without the role. */
    public static MatouId y(StateVocabulary vocab) {
        return role(vocab, Y);
    }

    private static MatouId role(StateVocabulary vocab, String role) {
        if (vocab == null) {
            throw new NullPointerException(
                    "E_MATOU_VOCAB:null spawn vocabulary");
        }
        return vocab.id(role);
    }
}
