package fr.iamacat.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *
 * <p>Combat is per-mob since syntax {@code spec/SYNTAX-V6.md}: every
 * weakspot funds one {@code (mob, bone)} pair, so the combat views come
 * in per-mob accessors plus the legacy sole-mob views (which refuse
 * unless exactly one mob is sealed).
 *
 * <p>Loot is per-mob since the distinct-drops tranche (hub
 * {@code decisions/LOOT.md}): every mob funds its own drop item ref plus
 * its own authorial items-per-kill count, so the loot views come in
 * per-mob accessors plus the legacy sole-mob views below (which content
 * implements; their contract is unchanged here — they refuse unless
 * exactly one mob is sealed).
 *
 * <p>Spawn is per-mob since the second-beast tranche (hub
 * {@code decisions/VIRTUAL_HITBOXES.md}, spawn policy hub
 * {@code decisions/SPAWN.md}): every mob funds its own hp/cap/budget/y
 * band, so the spawn views come in per-mob accessors plus the legacy
 * sole-mob views below (which content implements; their contract is
 * unchanged here).
 */
public interface PolicyPack extends VocabularyPack {
    /**
     * Loot table: harvest kind to content item ref (the content item, never
     * a landable name — the bridge resolves the carrier). Insertion-ordered,
     * unmodifiable, never null, never empty; both served kinds paid.
     * Sole-mob view: refuses unless exactly one mob is sealed (use
     * {@link #lootDrop(String)} per mob — the ore harvest then pays the
     * first sealed mob, each mob kill pays its own mob).
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
     * Sole-mob view: refuses unless exactly one mob is sealed (use
     * {@link #lootBeastKind(String)} per mob).
     */
    String lootBeastKind();

    /**
     * Loot mobs: short instance names of every sealed content mob, in
     * file order (hub {@code decisions/LOOT.md}, distinct-drops tranche,
     * mirroring {@link #spawnMobs()} and {@link #combatMobs()}).
     * Unmodifiable, never null, never empty.
     */
    Set<String> lootMobs();

    /**
     * Content drop item ref funding one mob's kills (hub
     * {@code decisions/LOOT.md}, distinct-drops tranche). Never null,
     * never empty. Loud on null/unknown mob — never defaulted.
     */
    String lootDrop(String mob);

    /**
     * Harvest kind one mob's kills are recorded under (hub
     * {@code decisions/LOOT.md}, distinct-drops tranche — the kill hook
     * records the victim's mob identity, the ore harvest keeps
     * {@link #lootOreKind()}). Never null, never empty; always paid by
     * the per-mob table. Loud on null/unknown mob — never defaulted.
     */
    String lootBeastKind(String mob);

    /**
     * Authorial items paid per due harvest (positive, never defaulted — the
     * bridge transports it into the seal unless the operator count wins).
     * Sole-mob view: refuses unless exactly one mob is sealed (use
     * {@link #lootCount(String)} per mob).
     */
    long lootCount();

    /**
     * Authorial items paid per due harvest of one mob's kills (positive,
     * never defaulted — the bridge transports it into the seal unless the
     * operator {@code loot.count} wins uniformly, hub
     * {@code decisions/LOOT.md}, distinct-drops tranche). Loud on
     * null/unknown mob — never defaulted.
     */
    long lootCount(String mob);

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
     * Spawn mobs: short instance names of every sealed content mob, in
     * file order (hub {@code decisions/SPAWN.md}, second-beast tranche
     * of hub {@code decisions/VIRTUAL_HITBOXES.md}, mirroring
     * {@link #combatMobs()}). Unmodifiable, never null, never empty.
     */
    Set<String> spawnMobs();

    /**
     * Qualified content mob ref ({@code "ns:name"}) for one sealed mob,
     * in the table's namespace (hub {@code decisions/SPAWN.md},
     * qualified-view tranche — the bridge qualifies sealed shorts
     * through this view instead of reading the file namespace off the
     * loot drop refs). Never null, never empty. Loud on null/unknown
     * mob — never defaulted.
     */
    String spawnMobRef(String mob);

    /**
     * Spec hp for one mob (positive), landed on that beast's max health
     * (hub {@code decisions/SPAWN.md} hp tranche, second-beast tranche
     * of hub {@code decisions/VIRTUAL_HITBOXES.md}). Never defaulted.
     * Loud on null/unknown mob. No operator override in v1 (damage
     * balance is content, same split as {@link #spawnHp()}).
     */
    long spawnHp(String mob);

    /**
     * Authorial living cap for one mob (positive): a full census lands
     * nothing (hub {@code decisions/SPAWN.md}, second-beast tranche of
     * hub {@code decisions/VIRTUAL_HITBOXES.md}). Never defaulted. Loud
     * on null/unknown mob.
     */
    long spawnCap(String mob);

    /**
     * Authorial landings per tick for one mob while room remains
     * (positive; hub {@code decisions/SPAWN.md}, second-beast tranche
     * of hub {@code decisions/VIRTUAL_HITBOXES.md}). Never defaulted.
     * Loud on null/unknown mob.
     */
    long spawnBudget(String mob);

    /**
     * Authorial spawn ordinate floor for one mob (0 {@code <=} yMin
     * {@code <=} yMax; hub {@code decisions/SPAWN.md}, second-beast
     * tranche of hub {@code decisions/VIRTUAL_HITBOXES.md}). Never
     * defaulted. Loud on null/unknown mob.
     */
    long spawnYMin(String mob);

    /**
     * Authorial spawn ordinate ceiling for one mob (0 {@code <=} yMin
     * {@code <=} yMax; hub {@code decisions/SPAWN.md}, second-beast
     * tranche of hub {@code decisions/VIRTUAL_HITBOXES.md}). Never
     * defaulted. Loud on null/unknown mob.
     */
    long spawnYMax(String mob);

    /**
     * Combat mobs: short instance names of every sealed content mob, in
     * file order. Unmodifiable, never null, never empty.
     */
    Set<String> combatMobs();

    /**
     * Combat weakspot table for one mob: bone name to damage multiplier,
     * served from the content weakspot rows funding {@code mob} (hub
     * {@code decisions/VIRTUAL_HITBOXES.md} combat-policy tranche, syntax
     * {@code spec/SYNTAX-V6.md}). Insertion-ordered, unmodifiable, never
     * null; multipliers positive and finite. Loud on null/unknown mob —
     * never defaulted.
     */
    Map<String, Float> combatWeakspots(String mob);

    /**
     * Combat reach attribute for one mob: eye-to-hitVec cutoff for the
     * bone ray-test, served from that mob's {@code reach} field.
     * Positive and finite, never defaulted. Loud on null/unknown mob.
     * No operator override in v1 (same split as {@link #spawnHp()}).
     */
    double combatReach(String mob);

    /**
     * Combat weakspot table: bone name to damage multiplier, served from
     * the content weakspot table (hub {@code decisions/VIRTUAL_HITBOXES.md}
     * combat-policy tranche, syntax {@code spec/SYNTAX-V5.md}). The
     * instance name is the bone, so keys never shadow another name.
     * Insertion-ordered, unmodifiable, never null, never empty; multipliers
     * positive and finite (a table nobody pays would be a silent no-op —
     * the bridge refines delivered hurts by these, it never defaults).
     * Sole-mob view: refuses unless exactly one mob is sealed (use
     * {@link #combatWeakspots(String)} per mob).
     */
    Map<String, Float> combatWeakspots();

    /**
     * Combat reach attribute: eye-to-hitVec cutoff for the bone ray-test,
     * served from the content mob {@code reach} field (replaces the
     * bridge-local {@code COMBAT_REACH} constant). Positive and finite,
     * never defaulted. No operator override in v1 (same split as
     * {@link #spawnHp()}: the spec field ships with a live reader, the
     * override is a named follow-up, never a quiet fallback).
     * Sole-mob view: refuses unless exactly one mob is sealed (use
     * {@link #combatReach(String)} per mob).
     */
    double combatReach();

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
