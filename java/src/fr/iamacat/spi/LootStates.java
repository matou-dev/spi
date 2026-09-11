package fr.iamacat.spi;

/**
 * Loot-domain state roles (hub
 * {@code decisions/SPI_STATE_VOCABULARY.md}): the sealed snapshot contract
 * every harvest-drop subsystem honours — sealed harvests, kind-to-item
 * table, items per harvest.
 *
 * <p>Since the distinct-drops tranche (hub {@code decisions/LOOT.md})
 * the table carries one ore kind plus one {@code beast.<mob>} kind per
 * sealed mob (each mob kill pays its own mob's drop), and the count
 * carries one positive entry per table kind (each kind pays its own
 * authorial number unless the operator {@code loot.count} wins
 * uniformly). Single-mob tables keep the legacy two-entry shape
 * ({@code ore} + {@code beast}, one count).
 *
 * <p>Content-blind: only the domain roles live here, never a content
 * namespace. A content builds its vocabulary with its own namespace
 * ({@link #vocabulary}) and both its pure job and its bridge seal resolve
 * through the typed accessors below — the role literals exist exactly
 * once, on these constants. Java 8, zero deps.
 */
public final class LootStates {
    private LootStates() {}

    /** Pack scope serving the loot vocabulary (see {@link VocabularyPack}). */
    public static final String SCOPE = "loot";

    /** Sealed harvests role: harvest cell to harvest tick. */
    public static final String HARVESTED = "harvested";
    /**
     * Loot table role: harvest kind to content item ref (one ore kind
     * plus one {@code beast.<mob>} kind per sealed mob since the
     * distinct-drops tranche).
     */
    public static final String TABLE = "table";
    /**
     * Items-per-harvest role: harvest kind to positive count (one entry
     * per table kind since the distinct-drops tranche — a kind without
     * its count is refused, never defaulted).
     */
    public static final String COUNT = "count";

    /**
     * Builds the loot vocabulary for a content namespace, roles in seal
     * order (harvested, table, count) so sealed maps iterate identically
     * live and in verdict replay.
     */
    public static StateVocabulary vocabulary(String namespace) {
        if (namespace == null) {
            throw new NullPointerException(
                    "E_MATOU_VOCAB:null loot namespace");
        }
        return StateVocabulary.of(namespace, HARVESTED, TABLE, COUNT);
    }

    /** Resolves the harvests id, refusing a vocabulary without the role. */
    public static MatouId harvested(StateVocabulary vocab) {
        return role(vocab, HARVESTED);
    }

    /** Resolves the table id, refusing a vocabulary without the role. */
    public static MatouId table(StateVocabulary vocab) {
        return role(vocab, TABLE);
    }

    /** Resolves the count id, refusing a vocabulary without the role. */
    public static MatouId count(StateVocabulary vocab) {
        return role(vocab, COUNT);
    }

    private static MatouId role(StateVocabulary vocab, String role) {
        if (vocab == null) {
            throw new NullPointerException(
                    "E_MATOU_VOCAB:null loot vocabulary");
        }
        return vocab.id(role);
    }
}
