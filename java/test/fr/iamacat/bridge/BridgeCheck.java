package fr.iamacat.bridge;

import fr.iamacat.spi.ConfigurablePack;
import fr.iamacat.spi.ContentPack;
import fr.iamacat.spi.Cell;
import fr.iamacat.spi.Counts;
import fr.iamacat.spi.MatouId;
import fr.iamacat.spi.MatouJob;
import fr.iamacat.spi.Snapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M1 bridge self-test (no JUnit on this gate): any violation prints
 * {@code FAIL bridge-skeleton : ...} and exits 1. Run by tools/check.sh
 * against the {@code ../spi} sibling checkout.
 */
public final class BridgeCheck {
    private BridgeCheck() {}

    /** Plain stub pack for the configured-loader gate. */
    public static class StubPlain implements ContentPack {
        public StubPlain() {}
        public String namespace() {
            return "stub";
        }
        public Map<MatouId, Object> states(long tick) {
            return Collections.<MatouId, Object>emptyMap();
        }
        public List<MatouJob<List<String>>> jobs() {
            return Collections.<MatouJob<List<String>>>emptyList();
        }
    }

    /** Configurable stub pack recording its operator args. */
    public static class StubConfigured implements ConfigurablePack {
        Map<String, String> seen;
        public StubConfigured() {}
        public String namespace() {
            return "stubcfg";
        }
        public void configure(Map<String, String> args) {
            seen = args;
        }
        public Map<MatouId, Object> states(long tick) {
            return Collections.<MatouId, Object>emptyMap();
        }
        public List<MatouJob<List<String>>> jobs() {
            return Collections.<MatouJob<List<String>>>emptyList();
        }
    }

    private static void check(boolean cond, String what) {
        if (!cond) {
            System.out.println("FAIL bridge-skeleton : " + what);
            System.exit(1);
        }
        System.out.println("ok bridge-skeleton : " + what);
    }

    private static void checkMsg(String got, String want, String what) {
        if (!want.equals(got)) {
            System.out.println("FAIL bridge-skeleton : " + what
                    + " got <" + got + "> want <" + want + ">");
            System.exit(1);
        }
        System.out.println("ok bridge-skeleton : " + what);
    }

    public static void main(String[] args) {
        SpiBridge bridge = new SpiBridge();
        MatouJob<String> yes = new MatouJob<String>() {
            public String decide(Snapshot snap) {
                return "tick-" + snap.tick();
            }
        };
        MatouJob<String> none = new MatouJob<String>() {
            public String decide(Snapshot snap) {
                return null;
            }
        };
        Snapshot s1 = new Snapshot(1L,
                Collections.<MatouId, Object>emptyMap());
        Snapshot s2 = new Snapshot(2L,
                Collections.<MatouId, Object>emptyMap());

        bridge.tick(s1, yes);
        bridge.tick(s2, none); // null decision applies nothing
        bridge.tick(s2, yes);
        if (!bridge.applied().toString().equals("[tick-1, tick-2]")) {
            System.out.println("FAIL bridge-skeleton : applied="
                    + bridge.applied());
            System.exit(1);
        }
        System.out.println("ok bridge-skeleton : decide-apply walk");

        try {
            bridge.applied().add("x");
            System.out.println("FAIL bridge-skeleton : applied mutable");
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            System.out.println("ok bridge-skeleton : applied immutable");
        }

        try {
            bridge.tick(null, yes);
            System.out.println("FAIL bridge-skeleton : null snapshot");
            System.exit(1);
        } catch (NullPointerException e) {
            System.out.println("ok bridge-skeleton : null refused (" + e.getMessage() + ")");
        }

        int[] pos = ForgeCells.parseCell("3,7");
        if (pos[0] != 3 || pos[1] != 7) {
            System.out.println("FAIL bridge-forge : parseCell=3,7");
            System.exit(1);
        }
        System.out.println("ok bridge-forge : parseCell");
        for (String bad : new String[]{"3", "a,b", "1,2,3", ""}) {
            try {
                ForgeCells.parseCell(bad);
                System.out.println("FAIL bridge-forge : parseCell <" + bad + ">");
                System.exit(1);
            } catch (IllegalArgumentException e) {
                System.out.println("ok bridge-forge : refused <" + bad + "> (" + e.getMessage() + ")");
            }
        }
        try {
            ForgeCells.parseCell(null);
            System.out.println("FAIL bridge-forge : parseCell null");
            System.exit(1);
        } catch (NullPointerException e) {
            System.out.println("ok bridge-forge : null refused (" + e.getMessage() + ")");
        }

        final List<String> landed = new ArrayList<String>();
        CellSink rec = new CellSink() {
            public void setCell(int x, int z) {
                landed.add(x + "," + z);
            }
        };
        List<String> cells = new ArrayList<String>();
        cells.add("1,2");
        cells.add("3,4");
        ForgeCells.applyCells(cells, rec);
        if (!landed.toString().equals("[1,2, 3,4]")) {
            System.out.println("FAIL bridge-forge : landed=" + landed);
            System.exit(1);
        }
        System.out.println("ok bridge-forge : apply verbatim");
        try {
            List<String> withNull = new ArrayList<String>();
            withNull.add(null);
            ForgeCells.applyCells(withNull, rec);
            System.out.println("FAIL bridge-forge : null entry");
            System.exit(1);
        } catch (NullPointerException e) {
            System.out.println("ok bridge-forge : null entry refused (" + e.getMessage() + ")");
        }

        ForgeCells.BlockCell vol = ForgeCells.parseBlockCell(
                "3,65,4:minecraft:stone");
        if (vol.x != 3 || vol.y != 65 || vol.z != 4
                || !vol.block.equals("minecraft:stone")) {
            System.out.println("FAIL bridge-forge : parseBlockCell=3,65,4");
            System.exit(1);
        }
        System.out.println("ok bridge-forge : parseBlockCell");
        for (String bad : new String[]{"3,65", "a,65,4:minecraft:stone",
                "3,65,4:", "3,65,4", "1,2,3,4:minecraft:stone"}) {
            try {
                ForgeCells.parseBlockCell(bad);
                System.out.println("FAIL bridge-forge : parseBlockCell <" + bad + ">");
                System.exit(1);
            } catch (IllegalArgumentException e) {
                System.out.println("ok bridge-forge : refused <" + bad + "> (" + e.getMessage() + ")");
            }
        }
        try {
            ForgeCells.parseBlockCell(null);
            System.out.println("FAIL bridge-forge : parseBlockCell null");
            System.exit(1);
        } catch (NullPointerException e) {
            System.out.println("ok bridge-forge : null refused (" + e.getMessage() + ")");
        }

        final List<String> landed3d = new ArrayList<String>();
        CellSink rec3d = new CellSink() {
            public void setCell(int x, int z) {
                landed3d.add(x + "," + z);
            }
            @Override
            public void setBlock(int x, int y, int z, String block) {
                landed3d.add(x + "," + y + "," + z + ":" + block);
            }
        };
        List<String> mixed = new ArrayList<String>();
        mixed.add("1,2");
        mixed.add("3,65,4:minecraft:stone");
        ForgeCells.applyCells(mixed, rec3d);
        if (!landed3d.toString().equals("[1,2, 3,65,4:minecraft:stone]")) {
            System.out.println("FAIL bridge-forge : landed3d=" + landed3d);
            System.exit(1);
        }
        System.out.println("ok bridge-forge : mixed apply dispatch");
        try {
            List<String> volOnly = new ArrayList<String>();
            volOnly.add("3,65,4:minecraft:stone");
            ForgeCells.applyCells(volOnly, rec);
            System.out.println("FAIL bridge-forge : 2d sink swallows 3d");
            System.exit(1);
        } catch (UnsupportedOperationException e) {
            System.out.println("ok bridge-forge : 2d sink refuses 3d (" + e.getMessage() + ")");
        }

        Map<MatouId, Object> states = new LinkedHashMap<MatouId, Object>();
        states.put(MatouId.parse("matou:tick"), Long.valueOf(9L));
        Snapshot sealed = ForgeSnapshot.snapshot(9L, states);
        if (sealed.tick() != 9L) {
            System.out.println("FAIL bridge-forge : snapshot tick");
            System.exit(1);
        }
        System.out.println("ok bridge-forge : snapshot sealed");
        try {
            ForgeSnapshot.snapshot(-1L, states);
            System.out.println("FAIL bridge-forge : negative tick");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.out.println("ok bridge-forge : tick refused (" + e.getMessage() + ")");
        }

        Cell plane = Cell.parse("3,7");
        check(!plane.isVolume() && plane.x == 3 && plane.z == 7,
                "cell plane parsed");
        check(plane.render().equals("3,7")
                && plane.posKey().equals("3,7")
                && plane.equals(Cell.of(3, 7))
                && plane.hashCode() == Cell.of(3, 7).hashCode(),
                "cell plane codec");
        Cell volCell = Cell.parse("3,65,4:minecraft:stone");
        check(volCell.isVolume() && volCell.x == 3 && volCell.y == 65
                && volCell.z == 4
                && volCell.block.equals("minecraft:stone"),
                "cell volume parsed");
        check(volCell.render().equals("3,65,4:minecraft:stone")
                && volCell.posKey().equals("3,65,4"),
                "cell volume codec");
        check(Cell.of(1, 2).toString().equals("1,2")
                && !Cell.of(1, 2).equals(Cell.of(1, 2, 3, "minecraft:stone")),
                "cell plane-volume distinct");
        check(Cell.parse("3,65").equals(Cell.of(3, 65))
                && !Cell.parse("3,65").isVolume()
                && Cell.parse("3,65,4:minecraft:stone").isVolume(),
                "cell dispatch on colon");
        for (String bad : new String[]{"3", "a,b", "1,2,3", "",
                "3,65,4", "3,65,4:", "a,65,4:minecraft:stone"}) {
            try {
                Cell.parse(bad);
                System.out.println("FAIL bridge-skeleton : cell <" + bad + ">");
                System.exit(1);
            } catch (IllegalArgumentException e) {
                System.out.println("ok bridge-skeleton : cell refused <"
                        + bad + "> (" + e.getMessage() + ")");
            }
        }
        try {
            Cell.parse(null);
            System.out.println("FAIL bridge-skeleton : cell null");
            System.exit(1);
        } catch (NullPointerException e) {
            System.out.println("ok bridge-skeleton : cell null refused ("
                    + e.getMessage() + ")");
        }
        try {
            Cell.of(1, 2, 3, null);
            System.out.println("FAIL bridge-skeleton : cell null block");
            System.exit(1);
        } catch (NullPointerException e) {
            System.out.println("ok bridge-skeleton : cell null block ("
                    + e.getMessage() + ")");
        }
        try {
            Cell.of(1, 2, 3, "");
            System.out.println("FAIL bridge-skeleton : cell empty block");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.out.println("ok bridge-skeleton : cell empty block ("
                    + e.getMessage() + ")");
        }

        try {
            ForgeCells.parseCell("a,b");
            System.out.println("FAIL bridge-forge : recode lost");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            checkMsg(e.getMessage(),
                    "E_BRIDGE_CELL:shape <a,b> (want \"x,z\")",
                    "seam keeps bridge codes");
        }
        try {
            ForgeCells.parseBlockCell("3,65");
            System.out.println("FAIL bridge-forge : recode lost");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            checkMsg(e.getMessage(),
                    "E_BRIDGE_CELL:shape <3,65> (want \"x,y,z:ns:block\")",
                    "seam keeps bridge volume codes");
        }
        ForgeCells.BlockCell built = new ForgeCells.BlockCell(
                3, 65, 4, "minecraft:stone");
        check(built.x == 3 && built.block.equals("minecraft:stone"),
                "seam block cell public");

        Map<MatouId, Object> typed = new LinkedHashMap<MatouId, Object>();
        typed.put(MatouId.parse("matou:name"), "here");
        typed.put(MatouId.parse("matou:n"), Long.valueOf(9L));
        Map<String, String> table = new LinkedHashMap<String, String>();
        table.put("0,0", "#");
        typed.put(MatouId.parse("matou:cells"), table);
        Snapshot tsnap = new Snapshot(4L, typed);
        check(tsnap.require(MatouId.parse("matou:name")).equals("here"),
                "snapshot require");
        check(tsnap.stringOf(MatouId.parse("matou:name")).equals("here"),
                "snapshot string");
        check(tsnap.longOf(MatouId.parse("matou:n")) == 9L,
                "snapshot long");
        check(tsnap.mapOf(MatouId.parse("matou:cells")).equals(table),
                "snapshot map");
        try {
            tsnap.require(MatouId.parse("matou:nope"));
            System.out.println("FAIL bridge-skeleton : require silent");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            checkMsg(e.getMessage(),
                    "E_MATOU_SNAPSHOT:missing <matou:nope>",
                    "snapshot missing named");
        }
        try {
            tsnap.stringOf(MatouId.parse("matou:n"));
            System.out.println("FAIL bridge-skeleton : stringOf silent");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.out.println("ok bridge-skeleton : stringOf refused ("
                    + e.getMessage() + ")");
        }
        try {
            tsnap.mapOf(MatouId.parse("matou:name"));
            System.out.println("FAIL bridge-skeleton : mapOf silent");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.out.println("ok bridge-skeleton : mapOf refused ("
                    + e.getMessage() + ")");
        }
        try {
            tsnap.longOf(null);
            System.out.println("FAIL bridge-skeleton : longOf null id");
            System.exit(1);
        } catch (NullPointerException e) {
            System.out.println("ok bridge-skeleton : longOf null id ("
                    + e.getMessage() + ")");
        }

        MatouId countId = MatouId.parse("matou:count");
        check(Counts.positive(Long.valueOf(8L), countId, "E_T_COUNT") == 8,
                "counts positive");
        try {
            Counts.positive(null, countId, "E_T_COUNT");
            System.out.println("FAIL bridge-skeleton : counts null");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            checkMsg(e.getMessage(), "E_T_COUNT:missing <matou:count>",
                    "counts missing named");
        }
        try {
            Counts.positive("eight", countId, "E_T_COUNT");
            System.out.println("FAIL bridge-skeleton : counts type");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            checkMsg(e.getMessage(),
                    "E_T_COUNT:type <eight> (want u32 number)",
                    "counts type named");
        }
        try {
            Counts.positive(Long.valueOf(0L), countId, "E_T_COUNT");
            System.out.println("FAIL bridge-skeleton : counts range");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            checkMsg(e.getMessage(), "E_T_COUNT:range <0> (want > 0)",
                    "counts range named");
        }
        try {
            Counts.positive(Long.valueOf(1L), countId, null);
            System.out.println("FAIL bridge-skeleton : counts null code");
            System.exit(1);
        } catch (NullPointerException e) {
            System.out.println("ok bridge-skeleton : counts null code ("
                    + e.getMessage() + ")");
        }

        Map<String, String> noArgs = new LinkedHashMap<String, String>();
        ContentPack plain = Packs.loadConfigured(
                "fr.iamacat.bridge.BridgeCheck$StubPlain", noArgs);
        check(plain.namespace().equals("stub"), "loadConfigured plain");
        try {
            Map<String, String> extra = new LinkedHashMap<String, String>();
            extra.put("k", "v");
            Packs.loadConfigured(
                    "fr.iamacat.bridge.BridgeCheck$StubPlain", extra);
            System.out.println("FAIL bridge-skeleton : stray args");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.out.println("ok bridge-skeleton : stray args refused ("
                    + e.getMessage() + ")");
        }
        try {
            Packs.loadConfigured(
                    "fr.iamacat.bridge.BridgeCheck$StubPlain", null);
            System.out.println("FAIL bridge-skeleton : null args");
            System.exit(1);
        } catch (NullPointerException e) {
            System.out.println("ok bridge-skeleton : null args ("
                    + e.getMessage() + ")");
        }
        Map<String, String> cfgArgs = new LinkedHashMap<String, String>();
        cfgArgs.put("ownedFile", "a");
        ContentPack cfg = Packs.loadConfigured(
                "fr.iamacat.bridge.BridgeCheck$StubConfigured", cfgArgs);
        check(cfg.namespace().equals("stubcfg")
                && ((StubConfigured) cfg).seen.equals(cfgArgs),
                "loadConfigured wires args");
        Packs.PackSpec spec = new Packs.PackSpec(
                "fr.iamacat.example1.ExamplePack", 64, "minecraft:stone",
                noArgs);
        check(spec.y == 64, "pack spec public");

        System.out.println("ok bridge-skeleton : all");
    }
}
