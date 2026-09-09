package fr.iamacat.spi;

/**
 * Execution seam (M1): the pure side decides, the bridge applies.
 *
 * <p>Contract for implementations: {@link #decide} MUST be a pure function of
 * the snapshot — no IO, no Minecraft, no clock, no unaddressed random, no
 * mutable statics. Same snapshot in, equal decision out. The bridge calls
 * decide on tick and owns every side effect.
 *
 * @param <D> decision type: plain data, never a live world handle.
 */
public interface MatouJob<D> {
    D decide(Snapshot snap);
}
