package com.trevorschoeny.menukit.panel;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.widget.MKButtonDef;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A layout group that arranges its children using a specific layout mode.
 * Groups can be nested -- a column can contain a grid, which contains slots.
 *
 * <p>Layout modes:
 * <ul>
 *   <li><b>COLUMN</b> -- children stack top-to-bottom with a gap</li>
 *   <li><b>ROW</b> -- children stack left-to-right with a gap</li>
 *   <li><b>GRID</b> -- children fill a grid of fixed-size cells, wrapping
 *       into new columns after {@code maxRows} rows</li>
 * </ul>
 *
 * <p>Children are mutable -- callers can insert/remove children via
 * {@link #insertBefore}, {@link #insertAfter}, {@link #removeById}.
 * Mutation is locked during {@link #computeLayout} to prevent concurrent
 * modification.
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public class MKGroupDef {

    private final LayoutMode mode;
    private final int gap;                        // spacing between children (COLUMN/ROW)
    private final int cellSize;                   // GRID: cell width and height (typically 18 for slots)
    private final int maxRows;                    // GRID: rows per column before wrapping
    private final boolean fillRight;              // GRID: true = index 0 in rightmost column
    private final ArrayList<MKGroupChild> children;
    private final @Nullable BooleanSupplier disabledWhen;
    private final @Nullable List<MKGridTrack> columnTracks;   // null = uniform cellSize mode
    private final @Nullable List<MKGridTrack> rowTracks;      // null = uniform cellSize mode

    /** When true, mutation methods throw. Set during computeLayout. */
    private boolean mutationLocked = false;

    public MKGroupDef(
            LayoutMode mode,
            int gap,
            int cellSize,
            int maxRows,
            boolean fillRight,
            List<MKGroupChild> children,
            @Nullable BooleanSupplier disabledWhen,
            @Nullable List<MKGridTrack> columnTracks,
            @Nullable List<MKGridTrack> rowTracks) {
        this.mode = mode;
        this.gap = gap;
        this.cellSize = cellSize;
        this.maxRows = maxRows;
        this.fillRight = fillRight;
        // Always store as mutable ArrayList internally
        this.children = new ArrayList<>(children);
        this.disabledWhen = disabledWhen;
        this.columnTracks = columnTracks;
        this.rowTracks = rowTracks;
    }

    // ── Record-compatible Accessors ─────────────────────────────────────────
    // Named to match the original record accessors so all existing callers
    // (group.mode(), group.children(), etc.) continue to compile unchanged.

    public LayoutMode mode() { return mode; }
    public int gap() { return gap; }
    public int cellSize() { return cellSize; }
    public int maxRows() { return maxRows; }
    public boolean fillRight() { return fillRight; }
    /** Returns the live (mutable) children list. Prefer {@link #getChildren()} for read-only access. */
    public List<MKGroupChild> children() { return children; }
    public @Nullable BooleanSupplier disabledWhen() { return disabledWhen; }
    public @Nullable List<MKGridTrack> columnTracks() { return columnTracks; }
    public @Nullable List<MKGridTrack> rowTracks() { return rowTracks; }

    /** Returns an unmodifiable view of the children list. */
    public List<MKGroupChild> getChildren() {
        return Collections.unmodifiableList(children);
    }

    // ── Mutable Tree Operations ─────────────────────────────────────────────

    private void checkNotLocked() {
        if (mutationLocked) {
            throw new IllegalStateException(
                    "[MenuKit] Cannot mutate MKGroupDef children during computeLayout");
        }
    }

    /**
     * Inserts a new child before the child with the given ID.
     *
     * @param targetId the ID of the existing child to insert before
     * @param newChild the new child to insert
     * @throws IllegalStateException if mutation is locked (during layout)
     * @throws IllegalArgumentException if no child with targetId exists
     */
    public void insertBefore(String targetId, MKGroupChild newChild) {
        checkNotLocked();
        int idx = indexOfId(targetId);
        if (idx < 0) {
            throw new IllegalArgumentException(
                    "[MenuKit] No child with id '" + targetId + "' in this group");
        }
        children.add(idx, newChild);
    }

    /**
     * Inserts a new child after the child with the given ID.
     *
     * @param targetId the ID of the existing child to insert after
     * @param newChild the new child to insert
     * @throws IllegalStateException if mutation is locked (during layout)
     * @throws IllegalArgumentException if no child with targetId exists
     */
    public void insertAfter(String targetId, MKGroupChild newChild) {
        checkNotLocked();
        int idx = indexOfId(targetId);
        if (idx < 0) {
            throw new IllegalArgumentException(
                    "[MenuKit] No child with id '" + targetId + "' in this group");
        }
        children.add(idx + 1, newChild);
    }

    /**
     * Removes a child by its ID.
     *
     * @param childId the ID of the child to remove
     * @return true if a child was removed
     * @throws IllegalStateException if mutation is locked (during layout)
     */
    public boolean removeById(String childId) {
        checkNotLocked();
        int idx = indexOfId(childId);
        if (idx < 0) return false;
        children.remove(idx);
        return true;
    }

    /**
     * Finds a child by its ID.
     *
     * @param childId the ID to search for
     * @return the matching child, or null if not found
     */
    public @Nullable MKGroupChild findById(String childId) {
        int idx = indexOfId(childId);
        return idx >= 0 ? children.get(idx) : null;
    }

    /** Returns the index of the child with the given ID, or -1. */
    private int indexOfId(String targetId) {
        for (int i = 0; i < children.size(); i++) {
            String id = childId(children.get(i));
            if (targetId.equals(id)) return i;
        }
        return -1;
    }

    /** Extracts the ID from a child element, or null if it has no ID. */
    private static @Nullable String childId(MKGroupChild child) {
        return switch (child) {
            case MKGroupChild.Slot s -> s.id();
            case MKGroupChild.Button b -> b.id();
            case MKGroupChild.Text t -> t.id();
            case MKGroupChild.Group g -> g.id();
            case MKGroupChild.SlotGroup sg -> sg.id();
            case MKGroupChild.Spanning s -> null;
            case MKGroupChild.Dynamic d -> d.id();
            case MKGroupChild.Scroll sc -> sc.id();
            case MKGroupChild.Tabs tb -> tb.id();
        };
    }

    /** Layout modes for groups. */
    public enum LayoutMode {
        COLUMN,  // top-to-bottom
        ROW,     // left-to-right
        GRID     // cell-based 2D grid
    }

    /**
     * Backwards-compatible overload -- delegates to the panelName-aware version
     * with null panelName and no active tracking. Used by callers that don't
     * need element visibility (e.g., size estimation with dummy arrays).
     */
    public int[] computeLayout(int[][] positions, int[] counters,
                                int totalSlots, int totalButtons,
                                int offsetX, int offsetY,
                                boolean parentDisabled, boolean rightAligned) {
        return computeLayout(positions, null, counters, totalSlots, totalButtons,
                offsetX, offsetY, parentDisabled, rightAligned, null);
    }

    /**
     * Backwards-compatible overload without active array -- delegates to the
     * full version with null active array. Used by callers that only need
     * positions and don't track active state.
     */
    public int[] computeLayout(int[][] positions, int[] counters,
                                int totalSlots, int totalButtons,
                                int offsetX, int offsetY,
                                boolean parentDisabled, boolean rightAligned,
                                @Nullable String panelName) {
        return computeLayout(positions, null, counters, totalSlots, totalButtons,
                offsetX, offsetY, parentDisabled, rightAligned, panelName);
    }

    /**
     * Recursively computes positions for all children in this group.
     * Writes absolute positions (relative to the group's origin) into the
     * flat position arrays at the correct indices. Marks active elements
     * in the {@code active} array (if non-null).
     *
     * @param positions  flat array of [x, y] pairs (size = total slots + buttons + texts)
     * @param active     flat boolean array marking active elements (null = no tracking)
     * @param counters   mutable array: [slotCounter, buttonCounter, textCounter]
     * @param totalSlots total number of slots in the panel (for index offset calculation)
     * @param totalButtons total number of buttons in the panel
     * @param offsetX    X offset from parent group's origin
     * @param offsetY    Y offset from parent group's origin
     * @param parentDisabled true if any ancestor group is disabled
     * @param rightAligned   true if children should be right-aligned within the group
     * @param panelName  the panel this group belongs to (for element visibility overrides)
     * @return {width, height} of this group's content area
     */
    public int[] computeLayout(int[][] positions, @Nullable boolean[] active, int[] counters,
                                int totalSlots, int totalButtons,
                                int offsetX, int offsetY,
                                boolean parentDisabled, boolean rightAligned,
                                @Nullable String panelName) {
        boolean groupDisabled = parentDisabled
                || (disabledWhen != null && disabledWhen.getAsBoolean());

        // Lock mutations during layout to prevent concurrent modification
        mutationLocked = true;
        try {
        return switch (mode) {
            case COLUMN -> computeColumn(positions, active, counters, totalSlots, totalButtons,
                    offsetX, offsetY, groupDisabled, rightAligned, panelName);
            case ROW -> computeRow(positions, active, counters, totalSlots, totalButtons,
                    offsetX, offsetY, groupDisabled, rightAligned, panelName);
            case GRID -> (columnTracks != null || rowTracks != null)
                    ? computeMixedGrid(positions, active, counters, totalSlots, totalButtons,
                            offsetX, offsetY, groupDisabled, panelName)
                    : computeGrid(positions, active, counters, totalSlots, totalButtons,
                            offsetX, offsetY, groupDisabled, panelName);
        };
        } finally {
            mutationLocked = false;
        }
    }

    // ── COLUMN Layout ────────────────────────────────────────────────────────

    private int[] computeColumn(int[][] positions, @Nullable boolean[] active, int[] counters,
                                 int totalSlots, int totalButtons,
                                 int offsetX, int offsetY, boolean disabled,
                                 boolean rightAligned, @Nullable String panelName) {
        int cursor = 0;
        int maxWidth = 0;
        boolean hasActiveChild = false;

        // Save counter state before each child so the post-pass can find
        // which flat indices belong to each child for X-shifting.
        int[][] counterSnapshots = new int[children.size()][3];
        int[][] childSizes = new int[children.size()][2];
        boolean[] childActive = new boolean[children.size()];

        for (int i = 0; i < children.size(); i++) {
            MKGroupChild child = children.get(i);
            boolean childDisabled = disabled || child.isDisabled(panelName);

            if (childDisabled) {
                writeDisabled(child, positions, active, counters, totalSlots, totalButtons);
                childActive[i] = false;
                continue;
            }

            if (hasActiveChild) cursor += gap;
            hasActiveChild = true;

            // Snapshot counters BEFORE this child is laid out
            counterSnapshots[i] = new int[]{ counters[0], counters[1], counters[2] };

            childSizes[i] = layoutChild(child, positions, active, counters, totalSlots, totalButtons,
                    offsetX, offsetY + cursor, false, rightAligned, panelName);
            cursor += childSizes[i][1];
            maxWidth = Math.max(maxWidth, childSizes[i][0]);
            childActive[i] = true;
        }

        // ── Right-alignment post-pass ──────────────────────────────────────
        // Shift narrower children rightward so they align to the right edge.
        if (rightAligned && maxWidth > 0) {
            for (int i = 0; i < children.size(); i++) {
                if (!childActive[i]) continue;
                int shift = maxWidth - childSizes[i][0];
                if (shift <= 0) continue;

                // Shift all flat positions that belong to this child
                int startSlot = counterSnapshots[i][0];
                int endSlot = (i + 1 < children.size()) ? findNextActiveCounter(i + 1, counterSnapshots, childActive, 0) : counters[0];
                for (int s = startSlot; s < endSlot; s++) {
                    if (active != null ? active[s] : positions[s][0] != 0 || positions[s][1] != 0) positions[s][0] += shift;
                }

                int startBtn = counterSnapshots[i][1];
                int endBtn = (i + 1 < children.size()) ? findNextActiveCounter(i + 1, counterSnapshots, childActive, 1) : counters[1];
                for (int b = startBtn; b < endBtn; b++) {
                    int fi = totalSlots + b;
                    if (active != null ? active[fi] : positions[fi][0] != 0 || positions[fi][1] != 0) positions[fi][0] += shift;
                }

                int startTxt = counterSnapshots[i][2];
                int endTxt = (i + 1 < children.size()) ? findNextActiveCounter(i + 1, counterSnapshots, childActive, 2) : counters[2];
                for (int t = startTxt; t < endTxt; t++) {
                    int fi = totalSlots + totalButtons + t;
                    if (active != null ? active[fi] : positions[fi][0] != 0 || positions[fi][1] != 0) positions[fi][0] += shift;
                }
            }
        }

        return new int[]{ maxWidth, cursor };
    }

    /** Finds the counter value for the next active child, or falls back to current counters. */
    private int findNextActiveCounter(int startIdx, int[][] counterSnapshots,
                                       boolean[] childActive, int counterType) {
        for (int i = startIdx; i < childActive.length; i++) {
            if (childActive[i]) return counterSnapshots[i][counterType];
        }
        // No more active children -- shouldn't happen, but return the snapshot of startIdx
        return counterSnapshots[startIdx][counterType];
    }

    // ── ROW Layout ───────────────────────────────────────────────────────────

    private int[] computeRow(int[][] positions, @Nullable boolean[] active, int[] counters,
                              int totalSlots, int totalButtons,
                              int offsetX, int offsetY, boolean disabled,
                              boolean rightAligned, @Nullable String panelName) {
        int cursor = 0;
        int maxHeight = 0;
        boolean hasActiveChild = false;

        for (MKGroupChild child : children) {
            boolean childDisabled = disabled || child.isDisabled(panelName);

            if (childDisabled) {
                writeDisabled(child, positions, active, counters, totalSlots, totalButtons);
                continue;
            }

            if (hasActiveChild) cursor += gap;
            hasActiveChild = true;

            int[] childSize = layoutChild(child, positions, active, counters, totalSlots, totalButtons,
                    offsetX + cursor, offsetY, false, rightAligned, panelName);
            cursor += childSize[0];
            maxHeight = Math.max(maxHeight, childSize[1]);
        }

        return new int[]{ cursor, maxHeight };
    }

    // ── GRID Layout ──────────────────────────────────────────────────────────

    private int[] computeGrid(int[][] positions, @Nullable boolean[] active, int[] counters,
                               int totalSlots, int totalButtons,
                               int offsetX, int offsetY, boolean disabled,
                               @Nullable String panelName) {
        // First pass: count enabled children and assign grid positions
        int enabledCount = 0;
        // Track which children are enabled (we need two passes)
        boolean[] enabledFlags = new boolean[children.size()];

        for (int i = 0; i < children.size(); i++) {
            boolean childDisabled = disabled || children.get(i).isDisabled(panelName);
            enabledFlags[i] = !childDisabled;
            if (!childDisabled) enabledCount++;
        }

        // Compute how many columns we need
        int activeRows = Math.min(enabledCount, maxRows);
        int activeCols = enabledCount > 0 ? ((enabledCount - 1) / maxRows) + 1 : 0;

        // Second pass: assign positions
        int enabledIndex = 0;
        int[] savedCounters = { counters[0], counters[1], counters[2] };

        for (int i = 0; i < children.size(); i++) {
            MKGroupChild child = children.get(i);

            if (!enabledFlags[i]) {
                writeDisabled(child, positions, active, counters, totalSlots, totalButtons);
                continue;
            }

            int col = enabledIndex / maxRows;
            int row = enabledIndex % maxRows;

            // For fillRight: flip columns so index 0 is in the rightmost column
            int visualCol = fillRight ? (activeCols - 1 - col) : col;

            int childX = offsetX + visualCol * cellSize;
            int childY = offsetY + row * cellSize;

            layoutChild(child, positions, active, counters, totalSlots, totalButtons,
                    childX, childY, false, false, panelName);
            enabledIndex++;
        }

        // Compute size from active columns and rows
        int width = activeCols * cellSize;
        int height = Math.min(enabledCount, maxRows) * cellSize;
        return new int[]{ width, height };
    }

    // ── Mixed Grid Layout ─────────────────────────────────────────────────────

    /**
     * Lays out children using variable-width columns and/or variable-height rows.
     * Children are placed in row-major order into the first unoccupied cell that
     * fits their span. Spanning children reserve multiple cells in the occupied grid.
     *
     * <p>When {@code columnTracks} or {@code rowTracks} is null, the missing dimension
     * falls back to uniform {@code cellSize} sizing.
     */
    private int[] computeMixedGrid(int[][] positions, @Nullable boolean[] active, int[] counters,
                                    int totalSlots, int totalButtons,
                                    int offsetX, int offsetY, boolean disabled,
                                    @Nullable String panelName) {
        // Resolve grid dimensions
        int cols = columnTracks != null ? columnTracks.size()
                : (children.size() > 0 ? ((children.size() - 1) / maxRows) + 1 : 0);
        int rows = rowTracks != null ? rowTracks.size() : maxRows;

        // Compute cumulative column start positions (gap between tracks, not before first)
        int[] colStart = new int[cols + 1];
        for (int i = 0; i < cols; i++) {
            int w = columnTracks != null ? columnTracks.get(i).size() : cellSize;
            colStart[i + 1] = colStart[i] + w + gap;
        }
        // Total width = last column's start + last column's width (no trailing gap)
        int totalWidth = cols > 0
                ? colStart[cols - 1] + (columnTracks != null ? columnTracks.get(cols - 1).size() : cellSize)
                : 0;

        // Compute cumulative row start positions
        int[] rowStart = new int[rows + 1];
        for (int i = 0; i < rows; i++) {
            int h = rowTracks != null ? rowTracks.get(i).size() : cellSize;
            rowStart[i + 1] = rowStart[i] + h + gap;
        }
        // Total height = last row's start + last row's height (no trailing gap)
        int totalHeight = rows > 0
                ? rowStart[rows - 1] + (rowTracks != null ? rowTracks.get(rows - 1).size() : cellSize)
                : 0;

        // Occupied grid tracks which cells are taken (for spanning support)
        boolean[][] occupied = new boolean[cols][rows];

        // Place each child into the first available cell (row-major scan)
        for (MKGroupChild child : children) {
            boolean childDisabled = disabled || child.isDisabled(panelName);

            if (childDisabled) {
                // Unwrap Spanning if needed, then write disabled position
                MKGroupChild actual = child instanceof MKGroupChild.Spanning s ? s.inner() : child;
                writeDisabled(actual, positions, active, counters, totalSlots, totalButtons);
                continue;
            }

            // Determine span and unwrap
            int colSpan = 1, rowSpan = 1;
            MKGroupChild actual = child;
            if (child instanceof MKGroupChild.Spanning s) {
                colSpan = s.colSpan();
                rowSpan = s.rowSpan();
                actual = s.inner();
            }

            // Find first unoccupied cell that fits the span (row-major scan)
            int placedCol = -1, placedRow = -1;
            outer:
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (!occupied[c][r] && canPlace(occupied, c, r, colSpan, rowSpan, cols, rows)) {
                        placedCol = c;
                        placedRow = r;
                        break outer;
                    }
                }
            }

            if (placedCol == -1) {
                // No space in the grid -- disable this child
                writeDisabled(actual, positions, active, counters, totalSlots, totalButtons);
                MenuKit.LOGGER.warn("[MenuKit] Mixed grid: no space for child, grid full");
                continue;
            }

            // Mark all spanned cells as occupied
            for (int dc = 0; dc < colSpan; dc++) {
                for (int dr = 0; dr < rowSpan; dr++) {
                    occupied[placedCol + dc][placedRow + dr] = true;
                }
            }

            // Compute visual position, accounting for fillRight column reversal
            int visualCol;
            if (fillRight) {
                visualCol = cols - placedCol - colSpan;
            } else {
                visualCol = placedCol;
            }

            int childX = offsetX + colStart[visualCol];
            int childY = offsetY + rowStart[placedRow];

            layoutChild(actual, positions, active, counters, totalSlots, totalButtons,
                    childX, childY, false, false, panelName);
        }

        return new int[]{ totalWidth, totalHeight };
    }

    /**
     * Checks whether a spanning child can be placed at (col, row) without
     * overlapping any occupied cells or exceeding grid bounds.
     */
    private boolean canPlace(boolean[][] occupied, int col, int row,
                              int colSpan, int rowSpan, int cols, int rows) {
        for (int dc = 0; dc < colSpan; dc++) {
            for (int dr = 0; dr < rowSpan; dr++) {
                if (col + dc >= cols || row + dr >= rows) return false;
                if (occupied[col + dc][row + dr]) return false;
            }
        }
        return true;
    }

    // ── Grid Position Helper (for tree-walk methods) ────────────────────────

    /**
     * Computes the (x, y) pixel position of each child in a GRID layout,
     * mirroring the logic in {@link #computeGrid} and {@link #computeMixedGrid}.
     * Returns a 2D array where {@code result[i]} is {@code {x, y}} for child i.
     * Disabled children get {@code {offsetX, offsetY}} as a safe fallback.
     *
     * <p>Used by {@link #collectScrollRegions} and {@link #collectTabsRegions}
     * so that nested containers report correct viewport bounds in GRID groups.
     */
    private int[][] computeGridChildPositions(int offsetX, int offsetY,
                                               @Nullable String panelName) {
        int[][] result = new int[children.size()][2];
        boolean isMixed = (columnTracks != null || rowTracks != null);

        if (isMixed) {
            // ── Mixed grid: variable-width columns / variable-height rows ──
            int cols = columnTracks != null ? columnTracks.size()
                    : (children.size() > 0 ? ((children.size() - 1) / maxRows) + 1 : 0);
            int rows = rowTracks != null ? rowTracks.size() : maxRows;

            // Cumulative column start positions
            int[] colStart = new int[cols + 1];
            for (int i = 0; i < cols; i++) {
                int w = columnTracks != null ? columnTracks.get(i).size() : cellSize;
                colStart[i + 1] = colStart[i] + w + gap;
            }

            // Cumulative row start positions
            int[] rowStart = new int[rows + 1];
            for (int i = 0; i < rows; i++) {
                int h = rowTracks != null ? rowTracks.get(i).size() : cellSize;
                rowStart[i + 1] = rowStart[i] + h + gap;
            }

            // Occupied grid for spanning support
            boolean[][] occupied = new boolean[cols][rows];

            for (int i = 0; i < children.size(); i++) {
                MKGroupChild child = children.get(i);
                boolean childDisabled = (disabledWhen != null && disabledWhen.getAsBoolean())
                        || child.isDisabled(panelName);
                if (childDisabled) {
                    result[i] = new int[]{ offsetX, offsetY };
                    continue;
                }

                // Determine span
                int colSpan = 1, rowSpan = 1;
                if (child instanceof MKGroupChild.Spanning s) {
                    colSpan = s.colSpan();
                    rowSpan = s.rowSpan();
                }

                // Find first unoccupied cell (row-major scan)
                int placedCol = -1, placedRow = -1;
                outer:
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        if (!occupied[c][r] && canPlace(occupied, c, r, colSpan, rowSpan, cols, rows)) {
                            placedCol = c;
                            placedRow = r;
                            break outer;
                        }
                    }
                }

                if (placedCol == -1) {
                    result[i] = new int[]{ offsetX, offsetY };
                    continue;
                }

                // Mark occupied cells
                for (int dc = 0; dc < colSpan; dc++) {
                    for (int dr = 0; dr < rowSpan; dr++) {
                        occupied[placedCol + dc][placedRow + dr] = true;
                    }
                }

                // Compute visual position (fillRight reverses columns)
                int visualCol = fillRight ? (cols - placedCol - colSpan) : placedCol;
                result[i] = new int[]{
                        offsetX + colStart[visualCol],
                        offsetY + rowStart[placedRow]
                };
            }
        } else {
            // ── Uniform grid: fixed cellSize, column-major fill ──
            // First pass: count enabled children for column count
            int enabledCount = 0;
            boolean[] enabledFlags = new boolean[children.size()];
            for (int i = 0; i < children.size(); i++) {
                boolean childDisabled = (disabledWhen != null && disabledWhen.getAsBoolean())
                        || children.get(i).isDisabled(panelName);
                enabledFlags[i] = !childDisabled;
                if (!childDisabled) enabledCount++;
            }
            int activeCols = enabledCount > 0 ? ((enabledCount - 1) / maxRows) + 1 : 0;

            // Second pass: assign positions
            int enabledIndex = 0;
            for (int i = 0; i < children.size(); i++) {
                if (!enabledFlags[i]) {
                    result[i] = new int[]{ offsetX, offsetY };
                    continue;
                }
                int col = enabledIndex / maxRows;
                int row = enabledIndex % maxRows;
                int visualCol = fillRight ? (activeCols - 1 - col) : col;
                result[i] = new int[]{
                        offsetX + visualCol * cellSize,
                        offsetY + row * cellSize
                };
                enabledIndex++;
            }
        }

        return result;
    }

    // ── Child Layout Helpers ─────────────────────────────────────────────────

    /**
     * Positions a single child and returns its {width, height}.
     * For leaf elements, writes the position directly. For nested groups,
     * recursively computes layout.
     */
    private int[] layoutChild(MKGroupChild child, int[][] positions, @Nullable boolean[] active,
                               int[] counters, int totalSlots, int totalButtons,
                               int x, int y, boolean disabled, boolean rightAligned,
                               @Nullable String panelName) {
        return switch (child) {
            case MKGroupChild.Slot s -> {
                int idx = counters[0]++;
                // +1 offset: vanilla renders slot backgrounds at (x-1, y-1),
                // so we shift inward by 1px so the visual border doesn't clip
                // past the panel edge. This is baked in here so rendering code
                // can treat all element positions uniformly.
                positions[idx] = new int[]{ x + 1, y + 1 };
                if (active != null) active[idx] = true;
                yield new int[]{ 18, 18 };
            }
            case MKGroupChild.Button b -> {
                int idx = totalSlots + counters[1]++;
                positions[idx] = new int[]{ x, y };
                if (active != null) active[idx] = true;
                yield new int[]{ estimateButtonWidth(b.def()), estimateButtonHeight(b.def()) };
            }
            case MKGroupChild.Text t -> {
                int idx = totalSlots + totalButtons + counters[2]++;
                positions[idx] = new int[]{ x, y };
                if (active != null) active[idx] = true;
                yield new int[]{ t.def().layoutWidth(), t.def().layoutHeight() };
            }
            case MKGroupChild.Group g -> {
                // Recursive layout -- the child group computes its own children
                yield g.def().computeLayout(positions, active, counters,
                        totalSlots, totalButtons, x, y, disabled, rightAligned, panelName);
            }
            case MKGroupChild.SlotGroup sg -> {
                // Lay out the inner group just like a Group child.
                // For virtual SlotGroups (empty inner group), this returns {0, 0}.
                yield sg.group().computeLayout(positions, active, counters,
                        totalSlots, totalButtons, x, y, disabled, rightAligned, panelName);
            }
            case MKGroupChild.Spanning s -> {
                // In non-GRID modes, spanning is ignored -- delegate to inner child
                yield layoutChild(s.inner(), positions, active, counters, totalSlots, totalButtons,
                        x, y, disabled, rightAligned, panelName);
            }
            case MKGroupChild.Dynamic d -> {
                // Dynamic group -- delegate to the pre-expanded inner group
                yield d.def().expandedGroup().computeLayout(positions, active, counters,
                        totalSlots, totalButtons, x, y, disabled, rightAligned, panelName);
            }
            case MKGroupChild.Scroll sc -> {
                // Compute the FULL content layout into positions array.
                // Actual scroll offset + viewport clipping happens at render time.
                sc.def().contentGroup().computeLayout(
                        positions, active, counters, totalSlots, totalButtons,
                        x, y, disabled, rightAligned, panelName);
                // Return viewport size, not content size
                yield new int[]{ sc.def().viewportWidth(), sc.def().viewportHeight() };
            }
            case MKGroupChild.Tabs tb -> {
                MKTabsDef tabsDef = tb.def();

                // Get active tab index from panel state
                int activeTab = tabsDef.defaultTab();
                if (tb.id() != null && panelName != null) {
                    MKPanelState state = MKPanelStateRegistry.get(panelName);
                    if (state != null) {
                        activeTab = state.getActiveTab(tb.id());
                    }
                }
                // Clamp to valid range
                activeTab = Math.max(0, Math.min(activeTab, tabsDef.tabs().size() - 1));

                // Compute tab bar dimensions
                boolean isHorizontalBar = tabsDef.barPosition() == MKTabsDef.TabBarPosition.TOP
                        || tabsDef.barPosition() == MKTabsDef.TabBarPosition.BOTTOM;
                int barSize = tabsDef.barThickness();

                // Determine content offset based on bar position
                int contentOffsetX = tabsDef.barPosition() == MKTabsDef.TabBarPosition.LEFT ? barSize : 0;
                int contentOffsetY = tabsDef.barPosition() == MKTabsDef.TabBarPosition.TOP ? barSize : 0;

                // Layout ALL tabs -- active one gets real positions, inactive ones are disabled
                int maxContentWidth = 0, maxContentHeight = 0;
                for (int i = 0; i < tabsDef.tabs().size(); i++) {
                    MKGroupDef contentGroup = tabsDef.tabs().get(i).contentGroup();
                    if (i == activeTab) {
                        // Active tab -- compute real layout
                        int[] contentSize = contentGroup.computeLayout(
                                positions, active, counters, totalSlots, totalButtons,
                                x + contentOffsetX, y + contentOffsetY,
                                disabled, rightAligned, panelName);
                        maxContentWidth = Math.max(maxContentWidth, contentSize[0]);
                        maxContentHeight = Math.max(maxContentHeight, contentSize[1]);
                    } else {
                        // Inactive tab -- disable all children (positions stay at {0,0}, active stays false)
                        for (MKGroupChild nested : contentGroup.children()) {
                            writeDisabled(nested, positions, active, counters, totalSlots, totalButtons);
                        }
                    }
                }

                // Return total size: content + bar
                int totalWidth, totalHeight;
                if (isHorizontalBar) {
                    totalWidth = maxContentWidth;
                    totalHeight = maxContentHeight + barSize;
                } else {
                    totalWidth = maxContentWidth + barSize;
                    totalHeight = maxContentHeight;
                }
                yield new int[]{ totalWidth, totalHeight };
            }
        };
    }

    /**
     * Marks all elements within a disabled child as inactive.
     * Positions stay at {0, 0} (default), active entries stay false.
     * Advances the flat counters so subsequent children get correct indices.
     */
    private void writeDisabled(MKGroupChild child, int[][] positions, @Nullable boolean[] active,
                                int[] counters, int totalSlots, int totalButtons) {
        switch (child) {
            case MKGroupChild.Slot s -> {
                int idx = counters[0]++;
                // Position stays {0, 0}, active stays false — no sentinel needed
            }
            case MKGroupChild.Button b -> {
                int idx = totalSlots + counters[1]++;
                // Position stays {0, 0}, active stays false
            }
            case MKGroupChild.Text t -> {
                int idx = totalSlots + totalButtons + counters[2]++;
                // Position stays {0, 0}, active stays false
            }
            case MKGroupChild.Group g -> {
                // Recursively disable all children in nested group
                for (MKGroupChild nested : g.def().children()) {
                    writeDisabled(nested, positions, active, counters, totalSlots, totalButtons);
                }
            }
            case MKGroupChild.SlotGroup sg -> {
                // Recursively disable all children in the inner group
                for (MKGroupChild nested : sg.group().children()) {
                    writeDisabled(nested, positions, active, counters, totalSlots, totalButtons);
                }
            }
            case MKGroupChild.Spanning s -> {
                // Delegate to the wrapped inner child
                writeDisabled(s.inner(), positions, active, counters, totalSlots, totalButtons);
            }
            case MKGroupChild.Dynamic d -> {
                // Recursively disable all children in the expanded dynamic group
                for (MKGroupChild nested : d.def().expandedGroup().children()) {
                    writeDisabled(nested, positions, active, counters, totalSlots, totalButtons);
                }
            }
            case MKGroupChild.Scroll sc -> {
                // Recursively disable all children inside the scroll's content group
                for (MKGroupChild nested : sc.def().contentGroup().children()) {
                    writeDisabled(nested, positions, active, counters, totalSlots, totalButtons);
                }
            }
            case MKGroupChild.Tabs tb -> {
                // Disable all children in ALL tabs
                for (MKTabDef tab : tb.def().tabs()) {
                    for (MKGroupChild nested : tab.contentGroup().children()) {
                        writeDisabled(nested, positions, active, counters, totalSlots, totalButtons);
                    }
                }
            }
        }
    }

    // ── Button Size Estimation ───────────────────────────────────────────────

    private int estimateButtonWidth(MKButtonDef def) {
        if (def.width() > 0) return def.width();
        String label = def.label() != null ? def.label().getString() : "";
        int textWidth = label.length() * 6;
        int iconWidth = def.icon() != null ? (def.iconSize() > 0 ? def.iconSize() : 16) + 2 : 0;
        return Math.max(20, textWidth + iconWidth + 8);
    }

    private int estimateButtonHeight(MKButtonDef def) {
        if (def.height() > 0) return def.height();
        return 17;
    }

    // ── Scroll / Tab Region Collection ────────────────────────────────────────
    //
    // These methods walk the layout tree to collect metadata about scroll
    // containers and tab containers that the rendering pipeline needs.
    // Each record captures the viewport bounds (in content-relative coords)
    // and the element ID (for state lookup in MKPanelState).

    /**
     * Metadata about a scroll container's viewport within a panel's layout tree.
     * Positions are content-relative (before panel padding is added).
     *
     * @param id              element ID for scroll offset lookup in MKPanelState
     * @param viewportX       viewport left edge relative to panel content origin
     * @param viewportY       viewport top edge relative to panel content origin
     * @param viewportWidth   visible area width
     * @param viewportHeight  visible area height
     * @param contentWidth    total content width (may exceed viewport)
     * @param contentHeight   total content height (may exceed viewport)
     * @param scrollDef       the scroll definition (for scrollSpeed, showScrollbar, etc.)
     */
    public record ScrollRegion(
            String id,
            int viewportX, int viewportY,
            int viewportWidth, int viewportHeight,
            int contentWidth, int contentHeight,
            MKScrollDef scrollDef
    ) {}

    /**
     * Metadata about a tabs container within a panel's layout tree.
     * Positions are content-relative (before panel padding is added).
     *
     * @param id              element ID for active tab lookup in MKPanelState
     * @param x               tabs container left edge relative to panel content origin
     * @param y               tabs container top edge relative to panel content origin
     * @param totalWidth      total width of the tabs container (content + bar)
     * @param totalHeight     total height of the tabs container (content + bar)
     * @param tabsDef         the tabs definition (for bar position, thickness, labels, etc.)
     * @param panelName       the panel this tabs container belongs to
     */
    public record TabsRegion(
            String id,
            int x, int y,
            int totalWidth, int totalHeight,
            MKTabsDef tabsDef,
            @Nullable String panelName
    ) {}

    /**
     * Walks the layout tree and collects all scroll container regions with
     * their viewport bounds. Used by the rendering pipeline to apply scissor
     * clipping and render scrollbar indicators.
     *
     * @param offsetX  X offset of this group relative to the panel content origin
     * @param offsetY  Y offset of this group relative to the panel content origin
     * @param panelName the panel name (for state lookup)
     * @param out      list to append scroll regions to
     */
    public void collectScrollRegions(int offsetX, int offsetY,
                                      @Nullable String panelName,
                                      List<ScrollRegion> out) {
        // Track cursor for layout positioning (mirrors computeLayout logic)
        int cursor = 0;
        boolean hasActiveChild = false;

        // Pre-compute grid cell positions if in GRID mode so nested containers
        // report correct viewport bounds (not just the group origin).
        int[][] gridPositions = (mode == LayoutMode.GRID)
                ? computeGridChildPositions(offsetX, offsetY, panelName) : null;

        for (int childIdx = 0; childIdx < children.size(); childIdx++) {
            MKGroupChild child = children.get(childIdx);
            boolean childDisabled = (disabledWhen != null && disabledWhen.getAsBoolean())
                    || child.isDisabled(panelName);
            if (childDisabled) continue;

            // Compute child position based on layout mode
            int childX, childY;
            switch (mode) {
                case COLUMN -> {
                    if (hasActiveChild) cursor += gap;
                    childX = offsetX;
                    childY = offsetY + cursor;
                }
                case ROW -> {
                    if (hasActiveChild) cursor += gap;
                    childX = offsetX + cursor;
                    childY = offsetY;
                }
                default -> {
                    // GRID mode: use pre-computed cell positions
                    childX = gridPositions[childIdx][0];
                    childY = gridPositions[childIdx][1];
                }
            }
            hasActiveChild = true;

            switch (child) {
                case MKGroupChild.Scroll sc -> {
                    // Compute the content group's actual size
                    int total = countElements(sc.def().contentGroup());
                    int[][] dummyPos = new int[total][2];
                    // positions default to {0, 0} — no sentinel initialization needed
                    int[] dummyCounters = {0, 0, 0};
                    int[] contentSize = sc.def().contentGroup().computeLayout(
                            dummyPos, dummyCounters, total, 0,
                            0, 0, false, false, panelName);

                    out.add(new ScrollRegion(
                            sc.id() != null ? sc.id() : "",
                            childX, childY,
                            sc.def().viewportWidth(), sc.def().viewportHeight(),
                            contentSize[0], contentSize[1],
                            sc.def()
                    ));

                    // Also walk inside the scroll's content group for nested scrolls
                    sc.def().contentGroup().collectScrollRegions(
                            childX, childY, panelName, out);

                    // Advance cursor by viewport size (not content size)
                    if (mode == LayoutMode.COLUMN) cursor += sc.def().viewportHeight();
                    else if (mode == LayoutMode.ROW) cursor += sc.def().viewportWidth();
                }
                case MKGroupChild.Group g -> {
                    // Recurse into nested groups
                    g.def().collectScrollRegions(childX, childY, panelName, out);

                    // Advance cursor by group size
                    int[] gSize = estimateChildSize(child, panelName);
                    if (mode == LayoutMode.COLUMN) cursor += gSize[1];
                    else if (mode == LayoutMode.ROW) cursor += gSize[0];
                }
                case MKGroupChild.SlotGroup sg -> {
                    // Recurse into the inner group (same as Group)
                    sg.group().collectScrollRegions(childX, childY, panelName, out);
                    int[] sgSize = estimateChildSize(child, panelName);
                    if (mode == LayoutMode.COLUMN) cursor += sgSize[1];
                    else if (mode == LayoutMode.ROW) cursor += sgSize[0];
                }
                case MKGroupChild.Tabs tb -> {
                    // Walk inside tab content groups for nested scrolls
                    MKTabsDef tabsDef = tb.def();
                    int activeTab = tabsDef.defaultTab();
                    if (tb.id() != null && panelName != null) {
                        MKPanelState state = MKPanelStateRegistry.get(panelName);
                        if (state != null) activeTab = state.getActiveTab(tb.id());
                    }
                    activeTab = Math.max(0, Math.min(activeTab, tabsDef.tabs().size() - 1));

                    boolean isHorizontalBar = tabsDef.barPosition() == MKTabsDef.TabBarPosition.TOP
                            || tabsDef.barPosition() == MKTabsDef.TabBarPosition.BOTTOM;
                    int contentOffsetX = tabsDef.barPosition() == MKTabsDef.TabBarPosition.LEFT
                            ? tabsDef.barThickness() : 0;
                    int contentOffsetY = tabsDef.barPosition() == MKTabsDef.TabBarPosition.TOP
                            ? tabsDef.barThickness() : 0;

                    if (activeTab >= 0 && activeTab < tabsDef.tabs().size()) {
                        tabsDef.tabs().get(activeTab).contentGroup().collectScrollRegions(
                                childX + contentOffsetX, childY + contentOffsetY,
                                panelName, out);
                    }

                    int[] tSize = estimateChildSize(child, panelName);
                    if (mode == LayoutMode.COLUMN) cursor += tSize[1];
                    else if (mode == LayoutMode.ROW) cursor += tSize[0];
                }
                case MKGroupChild.Dynamic d -> {
                    d.def().expandedGroup().collectScrollRegions(childX, childY, panelName, out);
                    int[] dSize = estimateChildSize(child, panelName);
                    if (mode == LayoutMode.COLUMN) cursor += dSize[1];
                    else if (mode == LayoutMode.ROW) cursor += dSize[0];
                }
                default -> {
                    // Leaf elements (slot, button, text, spanning) -- advance cursor
                    int[] leafSize = estimateChildSize(child, panelName);
                    if (mode == LayoutMode.COLUMN) cursor += leafSize[1];
                    else if (mode == LayoutMode.ROW) cursor += leafSize[0];
                }
            }
        }
    }

    /**
     * Walks the layout tree and collects all tabs container regions with
     * their bounds. Used by the rendering pipeline to render tab bar buttons
     * and handle tab click interactions.
     *
     * @param offsetX   X offset of this group relative to the panel content origin
     * @param offsetY   Y offset of this group relative to the panel content origin
     * @param panelName the panel name (for state lookup)
     * @param out       list to append tabs regions to
     */
    public void collectTabsRegions(int offsetX, int offsetY,
                                    @Nullable String panelName,
                                    List<TabsRegion> out) {
        int cursor = 0;
        boolean hasActiveChild = false;

        // Pre-compute grid cell positions if in GRID mode so nested containers
        // report correct viewport bounds (not just the group origin).
        int[][] gridPositions = (mode == LayoutMode.GRID)
                ? computeGridChildPositions(offsetX, offsetY, panelName) : null;

        for (int childIdx = 0; childIdx < children.size(); childIdx++) {
            MKGroupChild child = children.get(childIdx);
            boolean childDisabled = (disabledWhen != null && disabledWhen.getAsBoolean())
                    || child.isDisabled(panelName);
            if (childDisabled) continue;

            int childX, childY;
            switch (mode) {
                case COLUMN -> {
                    if (hasActiveChild) cursor += gap;
                    childX = offsetX;
                    childY = offsetY + cursor;
                }
                case ROW -> {
                    if (hasActiveChild) cursor += gap;
                    childX = offsetX + cursor;
                    childY = offsetY;
                }
                default -> {
                    // GRID mode: use pre-computed cell positions
                    childX = gridPositions[childIdx][0];
                    childY = gridPositions[childIdx][1];
                }
            }
            hasActiveChild = true;

            switch (child) {
                case MKGroupChild.Tabs tb -> {
                    // Compute tabs container size
                    int[] tSize = estimateChildSize(child, panelName);

                    out.add(new TabsRegion(
                            tb.id() != null ? tb.id() : "",
                            childX, childY,
                            tSize[0], tSize[1],
                            tb.def(),
                            panelName
                    ));

                    // Walk inside active tab content for nested tabs
                    MKTabsDef tabsDef = tb.def();
                    int activeTab = tabsDef.defaultTab();
                    if (tb.id() != null && panelName != null) {
                        MKPanelState state = MKPanelStateRegistry.get(panelName);
                        if (state != null) activeTab = state.getActiveTab(tb.id());
                    }
                    activeTab = Math.max(0, Math.min(activeTab, tabsDef.tabs().size() - 1));

                    boolean isHorizontalBar = tabsDef.barPosition() == MKTabsDef.TabBarPosition.TOP
                            || tabsDef.barPosition() == MKTabsDef.TabBarPosition.BOTTOM;
                    int contentOffsetX = tabsDef.barPosition() == MKTabsDef.TabBarPosition.LEFT
                            ? tabsDef.barThickness() : 0;
                    int contentOffsetY = tabsDef.barPosition() == MKTabsDef.TabBarPosition.TOP
                            ? tabsDef.barThickness() : 0;

                    if (activeTab >= 0 && activeTab < tabsDef.tabs().size()) {
                        tabsDef.tabs().get(activeTab).contentGroup().collectTabsRegions(
                                childX + contentOffsetX, childY + contentOffsetY,
                                panelName, out);
                    }

                    if (mode == LayoutMode.COLUMN) cursor += tSize[1];
                    else if (mode == LayoutMode.ROW) cursor += tSize[0];
                }
                case MKGroupChild.Group g -> {
                    g.def().collectTabsRegions(childX, childY, panelName, out);
                    int[] gSize = estimateChildSize(child, panelName);
                    if (mode == LayoutMode.COLUMN) cursor += gSize[1];
                    else if (mode == LayoutMode.ROW) cursor += gSize[0];
                }
                case MKGroupChild.SlotGroup sg -> {
                    sg.group().collectTabsRegions(childX, childY, panelName, out);
                    int[] sgSize = estimateChildSize(child, panelName);
                    if (mode == LayoutMode.COLUMN) cursor += sgSize[1];
                    else if (mode == LayoutMode.ROW) cursor += sgSize[0];
                }
                case MKGroupChild.Scroll sc -> {
                    sc.def().contentGroup().collectTabsRegions(childX, childY, panelName, out);
                    if (mode == LayoutMode.COLUMN) cursor += sc.def().viewportHeight();
                    else if (mode == LayoutMode.ROW) cursor += sc.def().viewportWidth();
                }
                case MKGroupChild.Dynamic d -> {
                    d.def().expandedGroup().collectTabsRegions(childX, childY, panelName, out);
                    int[] dSize = estimateChildSize(child, panelName);
                    if (mode == LayoutMode.COLUMN) cursor += dSize[1];
                    else if (mode == LayoutMode.ROW) cursor += dSize[0];
                }
                default -> {
                    int[] leafSize = estimateChildSize(child, panelName);
                    if (mode == LayoutMode.COLUMN) cursor += leafSize[1];
                    else if (mode == LayoutMode.ROW) cursor += leafSize[0];
                }
            }
        }
    }

    // ── Helper: count total elements in a group tree ──────────────────────────
    //
    // Used by collectScrollRegions to create dummy position arrays for
    // computing content group sizes.

    private static int countElements(MKGroupDef group) {
        int count = 0;
        for (MKGroupChild child : group.children()) {
            count += switch (child) {
                case MKGroupChild.Slot s -> 1;
                case MKGroupChild.Button b -> 1;
                case MKGroupChild.Text t -> 1;
                case MKGroupChild.Group g -> countElements(g.def());
                case MKGroupChild.SlotGroup sg -> countElements(sg.group());
                case MKGroupChild.Spanning s -> countElements(s.inner());
                case MKGroupChild.Dynamic d -> countElements(d.def().expandedGroup());
                case MKGroupChild.Scroll sc -> countElements(sc.def().contentGroup());
                case MKGroupChild.Tabs tb -> {
                    int tabCount = 0;
                    for (MKTabDef tab : tb.def().tabs()) {
                        tabCount += countElements(tab.contentGroup());
                    }
                    yield tabCount;
                }
            };
        }
        return count;
    }

    /**
     * Collects all {@link MKButtonDef}s from the tree in depth-first traversal
     * order — the same order used by {@link #computeLayout} for index assignment.
     * This captures conditionally-injected buttons that aren't in the original
     * flat {@code buttonDefs} list.
     *
     * <p>Package-private -- called from MKPanelDef.
     */
    static void collectButtonDefs(MKGroupDef group, java.util.List<MKButtonDef> out) {
        for (MKGroupChild child : group.children()) {
            collectButtonDefsFromChild(child, out);
        }
    }

    private static void collectButtonDefsFromChild(MKGroupChild child, java.util.List<MKButtonDef> out) {
        switch (child) {
            case MKGroupChild.Slot s -> {} // not a button
            case MKGroupChild.Button b -> out.add(b.def());
            case MKGroupChild.Text t -> {} // not a button
            case MKGroupChild.Group g -> collectButtonDefs(g.def(), out);
            case MKGroupChild.SlotGroup sg -> collectButtonDefs(sg.group(), out);
            case MKGroupChild.Spanning s -> collectButtonDefsFromChild(s.inner(), out);
            case MKGroupChild.Dynamic d -> collectButtonDefs(d.def().expandedGroup(), out);
            case MKGroupChild.Scroll sc -> collectButtonDefs(sc.def().contentGroup(), out);
            case MKGroupChild.Tabs tb -> {
                for (MKTabDef tab : tb.def().tabs()) {
                    collectButtonDefs(tab.contentGroup(), out);
                }
            }
        }
    }

    /**
     * Counts leaf elements in this group tree by type: {slots, buttons, texts}.
     * Used by {@link MKPanelDef#computeFlowPositionsFromTree()} to allocate
     * correctly-sized arrays that account for conditional element insertions.
     *
     * <p>Package-private -- only called from MKPanelDef.
     */
    static int[] countElementsByType(MKGroupDef group) {
        int[] counts = new int[3]; // [slots, buttons, texts]
        countElementsByTypeRecursive(group, counts);
        return counts;
    }

    private static void countElementsByTypeRecursive(MKGroupDef group, int[] counts) {
        for (MKGroupChild child : group.children()) {
            countChildByType(child, counts);
        }
    }

    private static void countChildByType(MKGroupChild child, int[] counts) {
        switch (child) {
            case MKGroupChild.Slot s -> counts[0]++;
            case MKGroupChild.Button b -> counts[1]++;
            case MKGroupChild.Text t -> counts[2]++;
            case MKGroupChild.Group g -> countElementsByTypeRecursive(g.def(), counts);
            case MKGroupChild.SlotGroup sg -> countElementsByTypeRecursive(sg.group(), counts);
            case MKGroupChild.Spanning s -> countChildByType(s.inner(), counts);
            case MKGroupChild.Dynamic d -> countElementsByTypeRecursive(d.def().expandedGroup(), counts);
            case MKGroupChild.Scroll sc -> countElementsByTypeRecursive(sc.def().contentGroup(), counts);
            case MKGroupChild.Tabs tb -> {
                for (MKTabDef tab : tb.def().tabs()) {
                    countElementsByTypeRecursive(tab.contentGroup(), counts);
                }
            }
        }
    }

    /** Counts elements for a single child (for Spanning unwrapping). */
    private static int countElements(MKGroupChild child) {
        return switch (child) {
            case MKGroupChild.Slot s -> 1;
            case MKGroupChild.Button b -> 1;
            case MKGroupChild.Text t -> 1;
            case MKGroupChild.Group g -> countElements(g.def());
            case MKGroupChild.SlotGroup sg -> countElements(sg.group());
            case MKGroupChild.Spanning s -> countElements(s.inner());
            case MKGroupChild.Dynamic d -> countElements(d.def().expandedGroup());
            case MKGroupChild.Scroll sc -> countElements(sc.def().contentGroup());
            case MKGroupChild.Tabs tb -> {
                int tabCount = 0;
                for (MKTabDef tab : tb.def().tabs()) {
                    tabCount += countElements(tab.contentGroup());
                }
                yield tabCount;
            }
        };
    }

    // ── Helper: estimate child size without full layout computation ──────────
    //
    // Lightweight size estimation for cursor advancement during tree walking.
    // Mirrors the sizes returned by layoutChild() in computeLayout().

    private int[] estimateChildSize(MKGroupChild child, @Nullable String panelName) {
        return switch (child) {
            case MKGroupChild.Slot s -> new int[]{18, 18};
            case MKGroupChild.Button b -> new int[]{
                    estimateButtonWidth(b.def()), estimateButtonHeight(b.def())
            };
            case MKGroupChild.Text t -> new int[]{t.def().layoutWidth(), t.def().layoutHeight()};
            case MKGroupChild.Group g -> {
                // Use typed counts so computeLayout gets correct offsets
                // (buttons are at index totalSlots+i, texts at totalSlots+totalButtons+i)
                int[] typeCounts = countElementsByType(g.def());
                int total = typeCounts[0] + typeCounts[1] + typeCounts[2];
                int[][] dummyPos = new int[total][2];
                int[] counters = {0, 0, 0};
                yield g.def().computeLayout(dummyPos, counters,
                        typeCounts[0], typeCounts[1],
                        0, 0, false, false, panelName);
            }
            case MKGroupChild.SlotGroup sg -> {
                // Same as Group -- use typed counts for correct offsets
                int[] typeCounts = countElementsByType(sg.group());
                int total = typeCounts[0] + typeCounts[1] + typeCounts[2];
                int[][] dummyPos = new int[total][2];
                int[] counters = {0, 0, 0};
                yield sg.group().computeLayout(dummyPos, counters,
                        typeCounts[0], typeCounts[1],
                        0, 0, false, false, panelName);
            }
            case MKGroupChild.Spanning s -> estimateChildSize(s.inner(), panelName);
            case MKGroupChild.Dynamic d -> {
                int[] typeCounts = countElementsByType(d.def().expandedGroup());
                int total = typeCounts[0] + typeCounts[1] + typeCounts[2];
                int[][] dummyPos = new int[total][2];
                int[] counters = {0, 0, 0};
                yield d.def().expandedGroup().computeLayout(dummyPos, counters,
                        typeCounts[0], typeCounts[1],
                        0, 0, false, false, panelName);
            }
            case MKGroupChild.Scroll sc -> new int[]{
                    sc.def().viewportWidth(), sc.def().viewportHeight()
            };
            case MKGroupChild.Tabs tb -> {
                // Estimate tabs size: content size + bar
                MKTabsDef tabsDef = tb.def();
                int activeTab = tabsDef.defaultTab();
                if (tb.id() != null && panelName != null) {
                    MKPanelState state = MKPanelStateRegistry.get(panelName);
                    if (state != null) activeTab = state.getActiveTab(tb.id());
                }
                activeTab = Math.max(0, Math.min(activeTab, tabsDef.tabs().size() - 1));

                int maxContentW = 0, maxContentH = 0;
                if (activeTab >= 0 && activeTab < tabsDef.tabs().size()) {
                    MKGroupDef contentGroup = tabsDef.tabs().get(activeTab).contentGroup();
                    int[] typeCounts = countElementsByType(contentGroup);
                    int total = typeCounts[0] + typeCounts[1] + typeCounts[2];
                    int[][] dummyPos = new int[total][2];
                    int[] counters = {0, 0, 0};
                    int[] contentSize = contentGroup.computeLayout(dummyPos, counters,
                            typeCounts[0], typeCounts[1],
                            0, 0, false, false, panelName);
                    maxContentW = contentSize[0];
                    maxContentH = contentSize[1];
                }

                boolean isHorizontalBar = tabsDef.barPosition() == MKTabsDef.TabBarPosition.TOP
                        || tabsDef.barPosition() == MKTabsDef.TabBarPosition.BOTTOM;
                if (isHorizontalBar) {
                    yield new int[]{maxContentW, maxContentH + tabsDef.barThickness()};
                } else {
                    yield new int[]{maxContentW + tabsDef.barThickness(), maxContentH};
                }
            }
        };
    }
}
