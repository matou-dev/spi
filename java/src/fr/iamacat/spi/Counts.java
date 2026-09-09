package fr.iamacat.spi;

/**
 * One positive-count rule shared by content jobs: snapshot counts
 * (feature occurrences, scatter amounts) are backend-owned data, so an
 * absent or malformed count is refused loudly with the <i>job's own</i>
 * code — never defaulted, never shared silently. The rule (null = missing,
 * non-number = type, {@code <= 0} = range) used to live copy-pasted in
 * every job; it lives here once, parameterized by the refusal code so each
 * job keeps its named errors byte for byte. Pure, Java 8, zero deps.
 */
public final class Counts {
    private Counts() {}

    /**
     * @param raw snapshot value (or parsed content value), may be null.
     * @param id owning feature id, shown verbatim in refusals.
     * @param code refusal prefix, e.g. {@code "E_EXAMPLE_COUNT"}.
     * @return positive int content of {@code raw}.
     * @throws NullPointerException when {@code code} is null.
     * @throws IllegalArgumentException as {@code <code>:missing},
     *         {@code <code>:type} or {@code <code>:range} otherwise.
     */
    public static int positive(Object raw, MatouId id, String code) {
        if (code == null) {
            throw new NullPointerException("E_MATOU_COUNT:null code");
        }
        if (raw == null) {
            throw new IllegalArgumentException(
                    code + ":missing <" + id + ">");
        }
        if (!(raw instanceof Number)) {
            throw new IllegalArgumentException(
                    code + ":type <" + raw + "> (want u32 number)");
        }
        int count = ((Number) raw).intValue();
        if (count <= 0) {
            throw new IllegalArgumentException(
                    code + ":range <" + raw + "> (want > 0)");
        }
        return count;
    }
}
