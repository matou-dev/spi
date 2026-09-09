package fr.iamacat.spi;

import java.util.List;
import java.util.Map;

/**
 * B2 content contract: a pack exposes plain-data states per tick plus the
 * pure jobs deciding cells — {@code "x,z"} plane cells (feature jobs) and
 * {@code "x,y,z:ns:block"} volume cells (V3 structure jobs, carrying their
 * own y and block). The bridge seals states into a Snapshot, runs every
 * job, merges owned-first, and lands cells on the world. Packs never see
 * the live world; loading is reflective (no bridge-to-content compile
 * edge, Q2). Java 8, zero deps.
 */
public interface ContentPack {
    /** Pack namespace, e.g. {@code "example1"}; never null. */
    String namespace();

    /**
     * Plain-data states for a tick (counts, configs — never world handles).
     * Same tick in, equal map out. Never null.
     */
    Map<MatouId, Object> states(long tick);

    /**
     * Pure cell jobs, first = owned backend, rest = late additives.
     * Never null, no null entries.
     */
    List<MatouJob<List<String>>> jobs();
}
