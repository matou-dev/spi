package fr.iamacat.spi;

/**
 * T3 load-time contract for packs serving sealed-subsystem vocabularies
 * (hub {@code decisions/SPI_STATE_VOCABULARY.md}): the bridge resolves the
 * state ids it seals from the reflectively loaded pack at wire time
 * (parse-once, never on the tick path), so the seal shares the job's
 * vocabulary with no bridge-to-content compile edge (Q2 — the same
 * reflective pattern as {@link ContentPack} loading in
 * {@code fr.iamacat.bridge.Packs}). Scopes are opaque strings the pack
 * owns ({@link SpawnStates#SCOPE}, {@link LootStates#SCOPE}); unknown
 * scopes are refused loudly by the pack, never defaulted. Java 8, zero
 * deps.
 */
public interface VocabularyPack extends ContentPack {
    /**
     * Serves the state vocabulary for a scope. Never null on a known
     * scope; unknown or null scopes are refused loudly.
     */
    StateVocabulary vocabulary(String scope);
}
