package fr.iamacat.spi;

import java.util.HashMap;
import java.util.Map;

/**
 * M1 skeleton self-test (no JUnit on this gate): every violation prints
 * {@code FAIL skeleton : ...} and exits 1. Run by tools/check.sh.
 */
public final class SkeletonCheck {
    private SkeletonCheck() {}

    private static void check(boolean cond, String what) {
        if (!cond) {
            System.out.println("FAIL skeleton : " + what);
            System.exit(1);
        }
        System.out.println("ok skeleton : " + what);
    }

    private static void expectRefused(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException e) {
            System.out.println("ok skeleton : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL skeleton : accepted " + what);
        System.exit(1);
    }

    public static void main(String[] args) {
        // --- MatouId ---
        final MatouId id = MatouId.parse("example1.common:my_ore");
        check(id.namespace.equals("example1.common")
                && id.name.equals("my_ore"), "id parse");
        check(id.equals(MatouId.of("example1.common", "my_ore"))
                && id.hashCode() == MatouId.of("example1.common", "my_ore")
                        .hashCode(), "id equals+hash");
        check(id.toString().equals("example1.common:my_ore"), "id roundtrip");
        expectRefused(new Runnable() {
            public void run() {
                MatouId.parse("my_ore");
            }
        }, "bare ident");
        expectRefused(new Runnable() {
            public void run() {
                MatouId.parse("a:b:c");
            }
        }, "double colon");
        expectRefused(new Runnable() {
            public void run() {
                MatouId.of("example1.common", "9bad");
            }
        }, "bad name");

        // --- MatouRng: same address, same stream ---
        MatouRng a1 = MatouRng.forAddress("slot", "3", "x", "y");
        MatouRng a2 = MatouRng.forAddress("slot", "3", "x", "y");
        boolean same = true;
        for (int i = 0; i < 4; i++) {
            same &= a1.nextLong() == a2.nextLong();
        }
        check(same, "rng deterministic");
        check(MatouRng.addressSeed("slot", "3")
                != MatouRng.addressSeed("slot", "4"), "rng seeds differ");
        check(MatouRng.addressSeed("ab", "c")
                != MatouRng.addressSeed("a", "bc"), "rng separator");
        MatouRng f1 = MatouRng.forAddress("w").fork("child");
        MatouRng f2 = MatouRng.forAddress("w").fork("child");
        check(f1.nextLong() == f2.nextLong(), "rng fork deterministic");
        expectRefused(new Runnable() {
            public void run() {
                MatouRng.forAddress("w").nextInt(0);
            }
        }, "rng bound 0");
        int v = MatouRng.forAddress("w").nextInt(64);
        check(v >= 0 && v < 64, "rng bound range");

        // --- Snapshot immutability + Job purity ---
        Map<MatouId, Object> src = new HashMap<MatouId, Object>();
        src.put(id, Long.valueOf(8L));
        final Snapshot snap = new Snapshot(12L, src);
        src.put(id, Long.valueOf(999L)); // late write must not leak in
        check(Long.valueOf(8L).equals(snap.get(id)), "snapshot frozen");
        check(snap.tick() == 12L, "snapshot tick");
        try {
            snap.ids().clear();
            check(false, "snapshot ids immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok skeleton : snapshot ids immutable");
        }
        MatouJob<String> job = new MatouJob<String>() {
            public String decide(Snapshot s) {
                return s.tick() + "=" + s.get(id);
            }
        };
        check(job.decide(snap).equals(job.decide(snap)), "job pure");
        check(job.decide(snap).equals("12=8"), "job value");
        System.out.println("ok skeleton : all");
    }
}
