package fr.iamacat.spi;

/**
 * Addressed deterministic RNG: same address yields the same stream on every
 * JVM, every run. Seed = FNV-1a 64 over the address parts, expanded by
 * SplitMix64. No hidden state, no clock, no platform hash. Java 8, zero deps.
 */
public final class MatouRng {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    private MatouRng(long seed) {
        this.state = seed;
    }

    /** Pure seed for an address; distinct addresses give distinct seeds. */
    public static long addressSeed(String... parts) {
        if (parts == null) {
            throw new NullPointerException("E_MATOU_RNG:null address");
        }
        long h = FNV_OFFSET;
        for (String p : parts) {
            if (p == null) {
                throw new NullPointerException("E_MATOU_RNG:null address part");
            }
            for (int i = 0; i < p.length(); i++) {
                h ^= p.charAt(i);
                h *= FNV_PRIME;
            }
            h ^= 0xFF; // separator: ("ab","c") != ("a","bc")
            h *= FNV_PRIME;
        }
        return splitMix64(h);
    }

    public static MatouRng forAddress(String... parts) {
        return new MatouRng(addressSeed(parts));
    }

    /** Independent child stream; parent state is left untouched. */
    public MatouRng fork(String scope) {
        if (scope == null) {
            throw new NullPointerException("E_MATOU_RNG:null fork scope");
        }
        return new MatouRng(splitMix64(state + addressSeed(scope)));
    }

    public long nextLong() {
        state += GAMMA;
        return splitMix64(state);
    }

    /**
     * Uniform-ish int in {@code [0, bound)}. Modulo bias is negligible for
     * small worldgen bounds; not for crypto (out of scope by design).
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException(
                    "E_MATOU_RNG_BOUND:" + bound + " (want > 0)");
        }
        return (int) Long.remainderUnsigned(nextLong() >>> 1, bound);
    }

    private static long splitMix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
