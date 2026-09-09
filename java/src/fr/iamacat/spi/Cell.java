package fr.iamacat.spi;

/**
 * Pure cell value type shared by content jobs, client jobs and the bridge
 * seam: one codec for both decided shapes — {@code "x,z"} plane cells and
 * {@code "x,y,z:ns:block"} volume cells (V3, carrying their own y and
 * block). The wire strings stay the contract (packs decide
 * {@code List<String>}, the bridge lands them); this type only parses and
 * renders them in one place so jobs stop re-splitting strings. The accept
 * set is exactly the bridge's ({@code ForgeCells}): same splits, same
 * integer rule, same loud refusals with {@code E_MATOU_CELL} codes.
 * Immutable, Java 8, zero deps.
 */
public final class Cell {
    /** Plane abscissa (block X on the bridge side). */
    public final int x;
    /** Volume ordinate (block Y); 0 on plane cells, unset by contract. */
    public final int y;
    /** Plane ordinate / volume depth (block Z on the bridge side). */
    public final int z;
    /** Volume block ({@code ns:name}); null on plane cells. */
    public final String block;

    private Cell(int x, int y, int z, String block) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.block = block;
    }

    /** One plane cell at {@code (x, z)}. */
    public static Cell of(int x, int z) {
        return new Cell(x, 0, z, null);
    }

    /**
     * One volume cell. The y range is owned bridge-side; here y is only an
     * integer and the block only a non-empty name.
     */
    public static Cell of(int x, int y, int z, String block) {
        if (block == null) {
            throw new NullPointerException("E_MATOU_CELL:null block");
        }
        if (block.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_MATOU_CELL:bad block <> (want ns:name)");
        }
        return new Cell(x, y, z, block);
    }

    /** True for volume cells ({@link #block} set), false for plane cells. */
    public boolean isVolume() {
        return block != null;
    }

    /**
     * Parses one plane cell ({@code "x,z"} ints, no trim, no default).
     * Never null.
     */
    public static Cell parsePlane(String raw) {
        if (raw == null) {
            throw new NullPointerException(
                    "E_MATOU_CELL:null (want \"x,z\")");
        }
        String[] parts = raw.split(",", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "E_MATOU_CELL:shape <" + raw + "> (want \"x,z\")");
        }
        try {
            return of(Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "E_MATOU_CELL:shape <" + raw + "> (want \"x,z\")");
        }
    }

    /**
     * Parses one volume cell ({@code "x,y,z:ns:block"}: ints plus a
     * non-empty block name; the block itself may hold {@code :}). Never
     * null.
     */
    public static Cell parseVolume(String raw) {
        if (raw == null) {
            throw new NullPointerException(
                    "E_MATOU_CELL:null (want \"x,y,z:ns:block\")");
        }
        int cut = raw.indexOf(':');
        String head = cut < 0 ? raw : raw.substring(0, cut);
        String name = cut < 0 ? "" : raw.substring(cut + 1);
        String[] parts = head.split(",", -1);
        if (parts.length != 3 || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "E_MATOU_CELL:shape <" + raw
                            + "> (want \"x,y,z:ns:block\")");
        }
        try {
            return of(Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    name);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "E_MATOU_CELL:shape <" + raw
                            + "> (want \"x,y,z:ns:block\")");
        }
    }

    /**
     * Parses either shape: a cell holding a {@code :} is a volume cell,
     * anything else a plane cell — the same dispatch the bridge applies.
     * Never null.
     */
    public static Cell parse(String raw) {
        if (raw == null) {
            throw new NullPointerException("E_MATOU_CELL:null");
        }
        if (raw.indexOf(':') < 0) {
            return parsePlane(raw);
        }
        return parseVolume(raw);
    }

    /** Wire form: {@code "x,z"} plane, {@code "x,y,z:block"} volume. */
    public String render() {
        if (block == null) {
            return x + "," + z;
        }
        return x + "," + y + "," + z + ":" + block;
    }

    /**
     * Position part only ({@code "x,z"} plane, {@code "x,y,z"} volume):
     * the key position-keyed merges dedup on.
     */
    public String posKey() {
        if (block == null) {
            return x + "," + z;
        }
        return x + "," + y + "," + z;
    }

    @Override
    public String toString() {
        return render();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Cell)) {
            return false;
        }
        Cell other = (Cell) o;
        if (x != other.x || y != other.y || z != other.z) {
            return false;
        }
        if (block == null) {
            return other.block == null;
        }
        return block.equals(other.block);
    }

    @Override
    public int hashCode() {
        int h = 31 * (31 * x + y) + z;
        return block == null ? h : 31 * h + block.hashCode();
    }
}
