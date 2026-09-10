package fr.iamacat.spi;

import java.util.Arrays;

/**
 * T3 vocabulary gate (no JUnit): the generic {@link StateVocabulary}
 * mechanism plus the content-blind spawn/loot domain roles. Any violation
 * prints {@code FAIL spi-vocab : ...} and exits 1. Run by tools/check.sh.
 */
public final class VocabularyCheck {
    private VocabularyCheck() {}

    private static void check(boolean cond, String what) {
        if (!cond) {
            System.out.println("FAIL spi-vocab : " + what);
            System.exit(1);
        }
        System.out.println("ok spi-vocab : " + what);
    }

    private static void expectIAE(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException e) {
            System.out.println("ok spi-vocab : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL spi-vocab : accepted " + what);
        System.exit(1);
    }

    private static void expectNPE(Runnable r, String what) {
        try {
            r.run();
        } catch (NullPointerException e) {
            System.out.println("ok spi-vocab : refused " + what
                    + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("FAIL spi-vocab : accepted " + what);
        System.exit(1);
    }

    public static void main(String[] args) {
        StateVocabulary vocab = StateVocabulary.of("example1.spawn",
                "census", "table", "cap", "budget", "y");
        check("example1.spawn".equals(vocab.namespace()),
                "vocabulary keeps its namespace");
        check(vocab.names().equals(Arrays.asList("census", "table",
                "cap", "budget", "y")), "vocabulary keeps name order");
        check(MatouId.of("example1.spawn", "census").equals(
                vocab.id("census")), "vocabulary resolves its ids");
        try {
            vocab.names().add("sneak");
            check(false, "vocabulary names immutable");
        } catch (UnsupportedOperationException e) {
            System.out.println("ok spi-vocab : names immutable");
        }

        // Refusals: never a defaulted id.
        expectNPE(new Runnable() {
            @Override public void run() {
                StateVocabulary.of(null, "census");
            }
        }, "null namespace");
        expectNPE(new Runnable() {
            @Override public void run() {
                StateVocabulary.of("example1.spawn", (String[]) null);
            }
        }, "null names");
        expectIAE(new Runnable() {
            @Override public void run() {
                StateVocabulary.of("example1.spawn");
            }
        }, "empty vocabulary");
        expectIAE(new Runnable() {
            @Override public void run() {
                StateVocabulary.of("example1.spawn", "census", "census");
            }
        }, "duplicate name");
        expectIAE(new Runnable() {
            @Override public void run() {
                StateVocabulary.of("example1.spawn", "bare name");
            }
        }, "malformed name");
        expectIAE(new Runnable() {
            @Override public void run() {
                StateVocabulary.of("bad namespace!", "census");
            }
        }, "malformed namespace");
        final StateVocabulary closed = vocab;
        expectNPE(new Runnable() {
            @Override public void run() {
                closed.id(null);
            }
        }, "null id name");
        expectIAE(new Runnable() {
            @Override public void run() {
                closed.id("capricorn");
            }
        }, "unknown id name");

        // Domain roles: content-blind factories over caller namespaces.
        StateVocabulary spawn = SpawnStates.vocabulary("example1.spawn");
        check("spawn".equals(SpawnStates.SCOPE), "spawn scope named");
        check(spawn.names().equals(Arrays.asList("census", "table",
                "cap", "budget", "y")), "spawn roles seal-ordered");
        check(MatouId.of("example1.spawn", "y").equals(
                SpawnStates.y(spawn)), "spawn resolvers agree");
        check(MatouId.of("example1.spawn", "census").equals(
                SpawnStates.census(spawn)), "spawn census resolves");
        StateVocabulary loot = LootStates.vocabulary("example1.loot");
        check("loot".equals(LootStates.SCOPE), "loot scope named");
        check(loot.names().equals(Arrays.asList("harvested", "table",
                "count")), "loot roles seal-ordered");
        check(MatouId.of("example1.loot", "count").equals(
                LootStates.count(loot)), "loot resolvers agree");

        // Cross-domain reads refuse: a loot vocabulary carries no spawn
        // roles, so a spawn seal fed the wrong vocabulary fails here.
        final StateVocabulary foreign = loot;
        expectIAE(new Runnable() {
            @Override public void run() {
                SpawnStates.census(foreign);
            }
        }, "spawn role on loot vocabulary");
        expectNPE(new Runnable() {
            @Override public void run() {
                SpawnStates.census(null);
            }
        }, "null spawn vocabulary");
        expectNPE(new Runnable() {
            @Override public void run() {
                LootStates.count(null);
            }
        }, "null loot vocabulary");
        expectNPE(new Runnable() {
            @Override public void run() {
                SpawnStates.vocabulary(null);
            }
        }, "null spawn namespace");
        expectNPE(new Runnable() {
            @Override public void run() {
                LootStates.vocabulary(null);
            }
        }, "null loot namespace");
        System.out.println("ok spi-vocab : all");
    }
}
