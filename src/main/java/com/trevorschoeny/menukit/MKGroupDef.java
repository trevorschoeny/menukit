package com.trevorschoeny.menukit;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A layout group that arranges its children using a specific layout mode.
 * Groups can be nested — a column can contain a grid, which contains slots.
 *
 * <p>Layout modes:
 * <ul>
 *   <li><b>COLUMN</b> — children stack top-to-bottom with a gap</li>
 *   <li><b>ROW</b> — children stack left-to-right with a gap</li>
 *   <li><b>GRID</b> — children fill a grid of fixed-size cells, wrapping
 *       into new columns after {@code maxRows} rows</li>
 * </ul>
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public record MKGroupDef(
        LayoutMode mode,
        int gap,                        // spacing between children (COLUMN/ROW)
        int cellSize,                   // GRID: cell width and height (typically 18 for slots)
        int maxRows,                    // GRID: rows per column before wrapping
        boolean fillRight,              // GRID: true = index 0 in rightmost column
        List<MKGroupChild> children,
        @Nullable BooleanSupplier disabledWhen
) {

    /** Layout modes for groups. */
    public enum LayoutMode {
        COLUMN,  // top-to-bottom
        ROW,     // left-to-right
        GRID     // cell-based 2D grid
    }

    /**
     * Recursively computes positions for all children in this group.
     * Writes absolute positions (relative to the group's origin) into the
     * flat position arrays at the correct indices.
     *
     * @param positions  flat array of [x, y] pairs (size = total slots + buttons + texts)
     * @param counters   mutable array: [slotCounter, buttonCounter, textCounter]
     * @param totalSlots total number of slots in the panel (for index offset calculation)
     * @param totalButtons total number of buttons in the panel
     * @param offsetX    X offset from parent group's origin
     * @param offsetY    Y offset from parent group's origin
     * @param parentDisabled true if any ancestor group is disabled
     * @param rightAligned   true if children should be right-aligned within the group
     * @return {width, height} of this group's content area
     */
    public int[] computeLayout(int[][] positions, int[] counters,
                                int totalSlots, int totalButtons,
                                int offsetX, int offsetY,
                                boolean parentDisabled, boolean rightAligned) {
        boolean groupDisabled = parentDisabled
                || (disabledWhen != null && disabledWhen.getAsBoolean());

        return switch (mode) {
            case COLUMN -> computeColumn(positions, counters, totalSlots, totalButtons,
                    offsetX, offsetY, groupDisabled, rightAligned);
            case ROW -> computeRow(positions, counters, totalSlots, totalButtons,
                    offsetX, offsetY, groupDisabled, rightAligned);
            case GRID -> computeGrid(positions, counters, totalSlots, totalButtons,
                    offsetX, offsetY, groupDisabled);
        };
    }

    // ── COLUMN Layout ────────────────────────────────────────────────────────

    private int[] computeColumn(int[][] positions, int[] counters,
                                 int totalSlots, int totalButtons,
                                 int offsetX, int offsetY, boolean disabled,
                                 boolean rightAligned) {
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
            boolean childDisabled = disabled || child.isDisabled();

            if (childDisabled) {
                writeDisabled(child, positions, counters, totalSlots, totalButtons);
                childActive[i] = false;
                continue;
            }

            if (hasActiveChild) cursor += gap;
            hasActiveChild = true;

            // Snapshot counters BEFORE this child is laid out
            counterSnapshots[i] = new int[]{ counters[0], counters[1], counters[2] };

            childSizes[i] = layoutChild(child, positions, counters, totalSlots, totalButtons,
                    offsetX, offsetY + cursor, false, rightAligned);
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
                    if (positions[s][0] != -9999) positions[s][0] += shift;
                }

                int startBtn = counterSnapshots[i][1];
                int endBtn = (i + 1 < children.size()) ? findNextActiveCounter(i + 1, counterSnapshots, childActive, 1) : counters[1];
                for (int b = startBtn; b < endBtn; b++) {
                    int fi = totalSlots + b;
                    if (positions[fi][0] != -9999) positions[fi][0] += shift;
                }

                int startTxt = counterSnapshots[i][2];
                int endTxt = (i + 1 < children.size()) ? findNextActiveCounter(i + 1, counterSnapshots, childActive, 2) : counters[2];
                for (int t = startTxt; t < endTxt; t++) {
                    int fi = totalSlots + totalButtons + t;
                    if (positions[fi][0] != -9999) positions[fi][0] += shift;
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
        // No more active children — shouldn't happen, but return the snapshot of startIdx
        return counterSnapshots[startIdx][counterType];
    }

    // ── ROW Layout ───────────────────────────────────────────────────────────

    private int[] computeRow(int[][] positions, int[] counters,
                              int totalSlots, int totalButtons,
                              int offsetX, int offsetY, boolean disabled,
                              boolean rightAligned) {
        int cursor = 0;
        int maxHeight = 0;
        boolean hasActiveChild = false;

        for (MKGroupChild child : children) {
            boolean childDisabled = disabled || child.isDisabled();

            if (childDisabled) {
                writeDisabled(child, positions, counters, totalSlots, totalButtons);
                continue;
            }

            if (hasActiveChild) cursor += gap;
            hasActiveChild = true;

            int[] childSize = layoutChild(child, positions, counters, totalSlots, totalButtons,
                    offsetX + cursor, offsetY, false, rightAligned);
            cursor += childSize[0];
            maxHeight = Math.max(maxHeight, childSize[1]);
        }

        return new int[]{ cursor, maxHeight };
    }

    // ── GRID Layout ──────────────────────────────────────────────────────────

    private int[] computeGrid(int[][] positions, int[] counters,
                               int totalSlots, int totalButtons,
                               int offsetX, int offsetY, boolean disabled) {
        // First pass: count enabled children and assign grid positions
        int enabledCount = 0;
        // Track which children are enabled (we need two passes)
        boolean[] enabledFlags = new boolean[children.size()];

        for (int i = 0; i < children.size(); i++) {
            boolean childDisabled = disabled || children.get(i).isDisabled();
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
                writeDisabled(child, positions, counters, totalSlots, totalButtons);
                continue;
            }

            int col = enabledIndex / maxRows;
            int row = enabledIndex % maxRows;

            // For fillRight: flip columns so index 0 is in the rightmost column
            int visualCol = fillRight ? (activeCols - 1 - col) : col;

            int childX = offsetX + visualCol * cellSize;
            int childY = offsetY + row * cellSize;

            layoutChild(child, positions, counters, totalSlots, totalButtons,
                    childX, childY, false, false);
            enabledIndex++;
        }

        // Compute size from active columns and rows
        int width = activeCols * cellSize;
        int height = Math.min(enabledCount, maxRows) * cellSize;
        return new int[]{ width, height };
    }

    // ── Child Layout Helpers ─────────────────────────────────────────────────

    /**
     * Positions a single child and returns its {width, height}.
     * For leaf elements, writes the position directly. For nested groups,
     * recursively computes layout.
     */
    private int[] layoutChild(MKGroupChild child, int[][] positions, int[] counters,
                               int totalSlots, int totalButtons,
                               int x, int y, boolean disabled, boolean rightAligned) {
        return switch (child) {
            case MKGroupChild.Slot s -> {
                int idx = counters[0]++;
                // +1 offset: vanilla renders slot backgrounds at (x-1, y-1),
                // so we shift inward by 1px so the visual border doesn't clip
                // past the panel edge. This is baked in here so rendering code
                // can treat all element positions uniformly.
                positions[idx] = new int[]{ x + 1, y + 1 };
                yield new int[]{ 18, 18 };
            }
            case MKGroupChild.Button b -> {
                int idx = totalSlots + counters[1]++;
                positions[idx] = new int[]{ x, y };
                yield new int[]{ estimateButtonWidth(b.def()), estimateButtonHeight(b.def()) };
            }
            case MKGroupChild.Text t -> {
                int idx = totalSlots + totalButtons + counters[2]++;
                positions[idx] = new int[]{ x, y };
                yield new int[]{ t.def().estimateWidth(), MKTextDef.TEXT_HEIGHT };
            }
            case MKGroupChild.Group g -> {
                // Recursive layout — the child group computes its own children
                yield g.def().computeLayout(positions, counters,
                        totalSlots, totalButtons, x, y, disabled, rightAligned);
            }
        };
    }

    /**
     * Writes -9999 positions for all elements within a disabled child.
     * Advances the flat counters so subsequent children get correct indices.
     */
    private void writeDisabled(MKGroupChild child, int[][] positions, int[] counters,
                                int totalSlots, int totalButtons) {
        switch (child) {
            case MKGroupChild.Slot s -> {
                int idx = counters[0]++;
                positions[idx] = new int[]{ -9999, -9999 };
            }
            case MKGroupChild.Button b -> {
                int idx = totalSlots + counters[1]++;
                positions[idx] = new int[]{ -9999, -9999 };
            }
            case MKGroupChild.Text t -> {
                int idx = totalSlots + totalButtons + counters[2]++;
                positions[idx] = new int[]{ -9999, -9999 };
            }
            case MKGroupChild.Group g -> {
                // Recursively disable all children in nested group
                for (MKGroupChild nested : g.def().children()) {
                    writeDisabled(nested, positions, counters, totalSlots, totalButtons);
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
}
