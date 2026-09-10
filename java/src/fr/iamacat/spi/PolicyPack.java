package fr.iamacat.spi;

import java.util.List;
import java.util.Map;

/**
 * T4 pack-driven policy contract (hub
 * {@code decisions/SPI_STATE_VOCABULARY.md}): a pack serving sealed-subsystem
 * policies as plain data plus the pure jobs deciding them, so the forge
 * wire stays content-blind at build time (Q2) — no bridge-to-content
 * compile edge, not even for tables, jobs or harvest kinds.
 *
 * <p>Content-blind like {@link VocabularyPack}: only the mechanism lives
 * here, never a content namespace or kind. The pack owns its namespaces
 * (T3 vocabularies), its kind names and its numbers; the bridge classifies
 * live events into the served kinds, applies the operator overrides on top
 * of the served numbers, and runs the served jobs. Serving time is wire
 * time (parse-once, beside the tables — never on the tick path). Java 8,
 * zero deps.
 */
public interface PolicyPack extends VocabularyPack {
    /**
     * Loot table: harvest kind to content item ref (the content item, never
     * a landable name — the bridge resolves the carrier). Insertion-ordered,
     * unmodifiable, never null, never empty; both served kinds paid.
     */
    Map<String, String> lootDrops();

    /**
     * Harvest kind a wire-block harvest is recorded under. Never null, never
     * empty; always a key of {@link #lootDrops()} (a kind nobody pays would
     * be a silent no-drop).
     */
    String lootOreKind();

    /**
     * Harvest kind a mob kill is recorded under. Never null, never empty;
     * always a key of {@link #lootDrops()}.
     */
    String lootBeastKind();

    /**
     * Authorial items paid per due harvest (positive, never defaulted — the
     * bridge transports it into the seal unless the operator count wins).
     */
    long lootCount();

    /**
     * Qualified content mob ref ({@code "ns:name"} — content never names
     * vanilla). Never null, never empty.
     */
    String spawnMob();

    /** Spec hp (positive), landed on the beast's max health. */
    long spawnHp();

    /** Authorial living cap (positive): a full census lands nothing. */
    long spawnCap();

    /** Authorial landings per tick while room remains (positive). */
    long spawnBudget();

    /** Authorial spawn ordinate floor (0 {@code <=} yMin {@code <=} yMax). */
    long spawnYMin();

    /** Authorial spawn ordinate ceiling (0 {@code <=} yMin {@code <=} yMax). */
    long spawnYMax();

    /**
     * Pure loot job deciding drops over the sealed harvests (fresh
     * equivalent per call or shared stateless instance — the job is pure,
     * so either honours the seam). Never null.
     */
    MatouJob<List<String>> lootJob();

    /**
     * Pure spawn job deciding landings over the sealed census. Never null.
     */
    MatouJob<List<String>> spawnJob();
}
