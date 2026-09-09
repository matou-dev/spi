package fr.iamacat.bridge;

import fr.iamacat.spi.Cell;
import java.util.List;

/**
 * B1 pure cell helpers: parse decision cells and apply them verbatim to a
 * {@link CellSink}. Two shapes: {@code "x,z"} plane cells (wire y/block)
 * and {@code "x,y,z:ns:block"} volume cells (own y/block). Pure Java,
 * zero Minecraft: the FML side owns the live world, this side only
 * refuses loudly. Java 8, zero deps.
 *
 * <p>The codec itself lives in {@link Cell}; these wrappers only recode
 * its refusals to the bridge {@code E_BRIDGE_CELL} names the forge side
 * and its gates match on.
 */
public final class ForgeCells {
    private ForgeCells() {}

    /** One parsed volume cell: position plus the block name to place. */
    public static final class BlockCell {
        public final int x;
        public final int y;
        public final int z;
        public final String block;

        public BlockCell(int x, int y, int z, String block) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
        }
    }

    /**
     * @return int[2] {x, z}, never null.
     * @throws NullPointerException when cell is null.
     * @throws IllegalArgumentException when cell is not {@code "x,z"} ints.
     */
    public static int[] parseCell(String cell) {
        if (cell == null) {
            throw new NullPointerException("E_BRIDGE_CELL:null (want \"x,z\")");
        }
        try {
            Cell parsed = Cell.parsePlane(cell);
            return new int[]{parsed.x, parsed.z};
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "E_BRIDGE_CELL:shape <" + cell + "> (want \"x,z\")");
        }
    }

    /**
     * @return parsed volume cell, never null.
     * @throws NullPointerException when cell is null.
     * @throws IllegalArgumentException when cell is not
     *         {@code "x,y,z:ns:block"} (ints plus a non-empty block name).
     *         The y range is owned FML-side ({@code WorldCellSink}).
     */
    public static BlockCell parseBlockCell(String cell) {
        if (cell == null) {
            throw new NullPointerException(
                    "E_BRIDGE_CELL:null (want \"x,y,z:ns:block\")");
        }
        try {
            Cell parsed = Cell.parseVolume(cell);
            return new BlockCell(parsed.x, parsed.y, parsed.z,
                    parsed.block);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "E_BRIDGE_CELL:shape <" + cell
                            + "> (want \"x,y,z:ns:block\")");
        }
    }

    /**
     * Applies every cell in order, no dedup, no replace: merge policy lives
     * in the jobs (Q3-Q4), the bridge only lands data. A cell holding a
     * {@code :} is a volume cell ({@link #setBlock} path), anything else a
     * plane cell ({@link #setCell} path) — both refuse loudly on bad
     * shape. Null entries refused.
     *
     * @throws NullPointerException when cells, sink, or any entry is null.
     */
    public static void applyCells(List<String> cells, CellSink sink) {
        if (cells == null) {
            throw new NullPointerException("E_BRIDGE_CELLS:null");
        }
        if (sink == null) {
            throw new NullPointerException("E_BRIDGE_SINK:null");
        }
        for (String cell : cells) {
            if (cell == null) {
                throw new NullPointerException("E_BRIDGE_CELL:null entry");
            }
            if (cell.indexOf(':') < 0) {
                int[] pos = parseCell(cell);
                sink.setCell(pos[0], pos[1]);
            } else {
                BlockCell vol = parseBlockCell(cell);
                sink.setBlock(vol.x, vol.y, vol.z, vol.block);
            }
        }
    }
}
