package fr.iamacat.spi;

/**
 * Render-domain state roles (hub
 * {@code decisions/GPU_INSTANCING.md}): the sealed snapshot contract
 * every frustum-cull render planner honours — camera eye, row-major
 * view-projection matrix, instance records.
 *
 * <p>Content-blind: only the domain roles live here, never a content
 * namespace. The bridge harness builds its vocabulary from its own
 * namespace ({@link #vocabulary}) and both its pure job and its seal
 * resolve through the typed accessors below — the role literals exist
 * exactly once, on these constants. Java 8, zero deps.
 *
 * <p>Sealed shapes (owned by the job/seal pair, named here so every
 * bridge seals the same bytes): {@code eye} is a {@code double[3]}
 * camera world position; {@code matrix} is a {@code float[16]}
 * row-major view-projection matrix (see
 * {@link fr.iamacat.spi.render.ViewProjection}); {@code recs} is a
 * {@link java.util.List} of
 * {@link fr.iamacat.spi.render.InstanceBucket.Rec}.
 */
public final class RenderStates {
    private RenderStates() {}

    /** Pack scope serving the render vocabulary (see {@link VocabularyPack}). */
    public static final String SCOPE = "render";

    /** Camera-eye role: {@code double[3]} world position. */
    public static final String EYE = "eye";
    /** View-projection role: {@code float[16]} row-major matrix. */
    public static final String MATRIX = "matrix";
    /** Instance-record role: list of render records. */
    public static final String RECS = "recs";

    /**
     * Builds the render vocabulary for a namespace, roles in seal
     * order (eye, matrix, recs) so sealed maps iterate identically
     * live and in verdict replay.
     */
    public static StateVocabulary vocabulary(String namespace) {
        if (namespace == null) {
            throw new NullPointerException(
                    "E_MATOU_VOCAB:null render namespace");
        }
        return StateVocabulary.of(namespace, EYE, MATRIX, RECS);
    }

    /** Resolves the eye id, refusing a vocabulary without the role. */
    public static MatouId eye(StateVocabulary vocab) {
        return role(vocab, EYE);
    }

    /** Resolves the matrix id, refusing a vocabulary without the role. */
    public static MatouId matrix(StateVocabulary vocab) {
        return role(vocab, MATRIX);
    }

    /** Resolves the recs id, refusing a vocabulary without the role. */
    public static MatouId recs(StateVocabulary vocab) {
        return role(vocab, RECS);
    }

    private static MatouId role(StateVocabulary vocab, String role) {
        if (vocab == null) {
            throw new NullPointerException(
                    "E_MATOU_VOCAB:null render vocabulary");
        }
        return vocab.id(role);
    }
}
