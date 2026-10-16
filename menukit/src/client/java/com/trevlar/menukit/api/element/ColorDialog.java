package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.core.DialogLayout;
import com.trevlar.menukit.core.PressedTracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The dialog a {@link ColorField} opens: a modal panel with a Basic tab (the dyes)
 * and a Custom tab, Cancel and Done. Built of MenuKit's own elements ({@link Tabs},
 * {@link TextField}, {@link Button}) plus the two drawn here, the dye grid and the
 * picker, laid out as every MenuKit dialog is ({@link DialogLayout}), so it draws in
 * the kind of screen it opens on like any dialog. The dialog is as wide and as tall
 * as the shown tab ({@link Tabs.Builder#fitShownTab}): Basic is small, Custom
 * larger, and switching resizes it, the buttons following.
 * Package-private: consumers reach it through {@link ColorField}.
 *
 * <p>The colour lives with the consumer, through the field's lens. The dialog keeps
 * only its view state: the tab shown, the hue, saturation and brightness the picker
 * is at (a grey or black colour has no hue of its own, so the picker remembers
 * where its handle was), and the hex text being typed.
 */
final class ColorDialog {

    private static final AtomicLong IDS = new AtomicLong();

    /** The picker's saturation and brightness square, and its bars' length. */
    static final int SQUARE = 100;
    static final int BAR = 10;
    static final int GAP = 4;
    /** A preview swatch (before, now) and the hex field's row. */
    static final int ROW_HEIGHT = 18;
    static final int PREVIEW = 24;
    /**
     * The dyes as the Basic tab shows them: the rainbow in rows of six, then the four
     * neutrals, centred under them.
     */
    static final List<List<DyeColor>> DYE_ROWS = List.of(
            List.of(DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.GREEN, DyeColor.CYAN),
            List.of(DyeColor.LIGHT_BLUE, DyeColor.BLUE, DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.PINK, DyeColor.BROWN),
            List.of(DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BLACK));
    static final int DYE_COLUMNS = 6;
    /** From one slot to the next: slots a little apart. */
    static final int DYE_STEP = ItemPicker.SIZE + 2;
    /** Room around the grid for the current dye's ring, which is a pixel outside its slot. */
    static final int RING = 1;

    private final ColorField field;
    private final Panel panel;
    private final TextField hex;
    private final DyeGrid grid;
    private final Picker picker;

    // View state
    private String tab = "basic";
    private double hue, saturation, brightness;
    private int alpha = 0xFF;
    /** The colour the picker's handles were last set from, so a change from elsewhere moves them. */
    private int pickerSynced;
    private String hexDraft = "";
    /** The colour the hex text was last set from, so a change from elsewhere replaces it. */
    private int hexSynced;

    ColorDialog(ColorField field) {
        this.field = field;
        this.grid = new DyeGrid();
        this.picker = new Picker();
        // The hex field ends where the picker's last bar does, so Custom is no wider
        // than its picker.
        int hexX = PREVIEW * 2 + GAP;
        this.hex = TextField.builder().at(hexX, SQUARE + 6).size(picker.getWidth() - hexX, ROW_HEIGHT)
                .maxLength(9)
                .label(Component.literal("Hex"))
                .state(this::hexText, this::typeHex)
                .build();

        // No fixed size: the tabs are as wide and as tall as the shown one, so the
        // dialog fits its content on either tab.
        Tabs tabs = Tabs.builder()
                .state(() -> tab, id -> tab = id)
                .fitShownTab()
                .tab("basic", Component.literal("Basic"), List.of(grid))
                .tab("custom", Component.literal("Custom"), List.of(picker, hex))
                .build();

        // Every dialog's layout: centred, modal (opaque, dims behind, takes every
        // click and key), Escape cancels. Shown while the field says it is open.
        this.panel = DialogLayout.panel("menukit:color-dialog-" + IDS.incrementAndGet(), field::cancel,
                        DialogLayout.centred(TextLabel.builder().text(field.label()).build()),
                        tabs,
                        DialogLayout.cancelDone(field::cancel, field::done))
                .visibleWhen(field::dialogShown);
    }

    Panel panel() {
        return panel;
    }

    /** Shows a tab, as a click on it does. */
    void showTab(String id) {
        tab = id;
    }

    /**
     * What the shown tab draws, as {@code {x, y, width, height}} from its body's
     * top-left: each dye's slot with its ring, or the picker and the hex field.
     */
    List<int[]> shownCells() {
        if (!tab.equals("basic")) {
            return List.of(new int[]{picker.getChildX(), picker.getChildY(), picker.getWidth(), picker.getHeight()},
                    new int[]{hex.getChildX(), hex.getChildY(), hex.getWidth(), hex.getHeight()});
        }
        List<int[]> cells = new ArrayList<>();
        for (List<DyeColor> row : DYE_ROWS) {
            for (DyeColor dye : row) {
                int[] at = grid.slot(dye);
                cells.add(new int[]{grid.getChildX() + at[0] - RING, grid.getChildY() + at[1] - RING,
                        ItemPicker.SIZE + 2 * RING, ItemPicker.SIZE + 2 * RING});
            }
        }
        return cells;
    }

    /** A dye's name, as its tooltip shows it: vanilla's translated colour ("Light Blue"). */
    static Component dyeName(DyeColor dye) {
        return Component.translatable("color.minecraft." + dye.getName());
    }

    /** Opened: Basic first, the picker and the hex text from the colour as it is now. */
    void opened() {
        tab = "basic";
        syncPicker(true);
        hexDraft = ColorField.formatHex(field.value(), field.hasAlpha());
        hexSynced = field.value();
    }

    // Hex

    String hexText() {
        int c = field.value();
        if (c != hexSynced) {
            hexDraft = ColorField.formatHex(c, field.hasAlpha());
            hexSynced = c;
        }
        return hexDraft;
    }

    void typeHex(String text) {
        hexDraft = text;
        Integer parsed = ColorField.parseHex(text, field.hasAlpha());
        if (parsed != null) {
            // Six digits on a field with alpha keep the transparency it has.
            String digits = text.trim().startsWith("#") ? text.trim().substring(1) : text.trim();
            if (field.hasAlpha() && digits.length() == 6) parsed = (field.value() & 0xFF000000) | (parsed & 0xFFFFFF);
            field.write(parsed);
        }
        // What was typed stands for the colour as it is now, valid or not: only a later
        // change from elsewhere replaces it.
        hexSynced = field.value();
    }

    /** Red while the text is not a colour; back to the colour's hex once the field loses focus. */
    private void checkHex() {
        EditBox box = hex.widget();
        boolean valid = ColorField.parseHex(hexDraft, field.hasAlpha()) != null;
        if (box != null) {
            box.setTextColor(valid ? TextField.TEXT_VALID : TextField.TEXT_INVALID);
            if (!valid && !box.isFocused()) {
                hexDraft = ColorField.formatHex(field.value(), field.hasAlpha());
                hexSynced = field.value();
            }
        }
    }

    // The picker's handles

    /** Moves the handles to the colour when it changed from elsewhere (or always, on open). */
    private void syncPicker(boolean force) {
        int c = field.value();
        if (!force && c == pickerSynced) return;
        float[] hsv = toHsv(c);
        // Grey has no hue and black no saturation: keep where those handles were.
        if (hsv[1] > 0 && hsv[2] > 0) hue = hsv[0];
        if (hsv[2] > 0) saturation = hsv[1];
        brightness = hsv[2];
        alpha = field.hasAlpha() ? (c >>> 24) : 0xFF;
        pickerSynced = c;
    }

    private void writePicker() {
        int rgb = fromHsv(hue, saturation, brightness);
        field.write((alpha << 24) | rgb);
        pickerSynced = field.value();
    }

    // Colour maths

    /** {@code {hue 0..1, saturation 0..1, brightness 0..1}} of an RGB colour (alpha ignored). */
    static float[] toHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) / 6f;
        else if (max == g) h = ((b - r) / d + 2) / 6f;
        else h = ((r - g) / d + 4) / 6f;
        if (h < 0) h += 1;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    /** The RGB ({@code 0xRRGGBB}, no alpha) of a hue, saturation and brightness, each 0..1. */
    static int fromHsv(double h, double s, double v) {
        double hh = (h - Math.floor(h)) * 6;
        int sector = (int) hh % 6;
        double f = hh - Math.floor(hh);
        double p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        double r, g, b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255);
    }

    /** Light and mid grey squares, four pixels each: what transparency is shown over. */
    static void checkerboard(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFFFFFFF);
        for (int cy = 0; cy < h; cy += 4) {
            for (int cx = (cy / 4) % 2 * 4; cx < w; cx += 8) {
                g.fill(x + cx, y + cy, x + Math.min(cx + 4, w), y + Math.min(cy + 4, h), 0xFFBFBFBF);
            }
        }
    }

    private static double clamp01(double d) {
        return d < 0 ? 0 : Math.min(d, 1);
    }

    /** Whether the left button is still held. With no game running it counts as held. */
    private static boolean stillHeld() {
        return Minecraft.getInstance() == null || PressedTracker.isLeftMouseDown();
    }

    // The Basic tab

    /**
     * The sixteen dyes, by their items, in {@link #DYE_ROWS}. Clicking one writes its
     * colour. The grid's bounds include {@link #RING} round the slots, so the current
     * dye's ring (a pixel outside its slot) is inside them: the tab's body clips to
     * its own box, and a ring on the top row would otherwise lose its top edge. Given
     * more width than it needs (the dialog's button row is wider), it centres the
     * slots in it.
     */
    private final class DyeGrid extends AbstractPanelElement {

        DyeGrid() {
            this.width = this.authoredWidth = DYE_COLUMNS * DYE_STEP - 2 + 2 * RING;
            this.height = DYE_ROWS.size() * DYE_STEP - 2 + 2 * RING;
        }

        /** As wide as the body, so the slots centre in it; never narrower than they need. */
        @Override public void layoutWithin(int budget) { width = Math.max(authoredWidth, budget); }
        @Override public void fillWidth(int width) {}
        @Override public boolean isInteractive() { return true; }

        /** A dye's slot, {@code {x, y}} from the grid's top-left; a short row is centred under the full ones. */
        int[] slot(DyeColor dye) {
            for (int r = 0; r < DYE_ROWS.size(); r++) {
                List<DyeColor> row = DYE_ROWS.get(r);
                int c = row.indexOf(dye);
                if (c < 0) continue;
                int x = (width - authoredWidth) / 2 + RING + (DYE_COLUMNS - row.size()) * DYE_STEP / 2 + c * DYE_STEP;
                return new int[]{x, RING + r * DYE_STEP};
            }
            throw new IllegalArgumentException("not in the grid: " + dye);
        }

        private @Nullable DyeColor dyeAt(InputContext in) {
            double lx = in.mouseX() - (in.originX() + childX), ly = in.mouseY() - (in.originY() + childY);
            for (DyeColor dye : DyeColor.values()) {
                int[] at = slot(dye);
                if (lx >= at[0] && lx < at[0] + ItemPicker.SIZE && ly >= at[1] && ly < at[1] + ItemPicker.SIZE) return dye;
            }
            return null;
        }

        @Override
        public void render(RenderContext ctx) {
            GuiGraphicsExtractor g = ctx.graphics();
            int gx = ctx.originX() + childX, gy = ctx.originY() + childY;
            DyeColor hovered = ctx.hasMouseInput() ? dyeAt(ctx.input()) : null;
            int current = field.value() & 0xFFFFFF;
            for (DyeColor dye : DyeColor.values()) {
                int[] at = slot(dye);
                int x = gx + at[0], y = gy + at[1];
                ItemPicker.drawSlot(g, ctx.kind(), x, y, Items.DYE.pick(dye), dye == hovered, false);
                // The current colour's dye: a white ring around its slot.
                if ((ColorField.dyeColor(dye) & 0xFFFFFF) == current) g.outline(x - 1, y - 1, ItemPicker.SIZE + 2, ItemPicker.SIZE + 2, 0xFFFFFFFF);
            }
            if (hovered != null) MKTooltip.queue(g, dyeName(hovered), ctx.mouseX(), ctx.mouseY());
        }

        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if (button != Click.LEFT || disabled(in)) return false;
            DyeColor dye = dyeAt(in);
            if (dye == null) return false;
            ElementWidget.playClickSound();
            field.pickDye(dye);
            return true;
        }
    }

    // The Custom tab

    /** Which handle a press is dragging. */
    private enum Drag { NONE, SQUARE, HUE, ALPHA }

    /**
     * Saturation and brightness square, hue bar, transparency bar (with alpha), and
     * the colour before and now. Each handle follows a held press, as a scroll
     * container's handle does.
     */
    private final class Picker extends AbstractPanelElement {

        private Drag drag = Drag.NONE;

        Picker() {
            this.width = this.authoredWidth = SQUARE + GAP + BAR + (field.hasAlpha() ? GAP + BAR : 0);
            this.height = SQUARE + 6 + ROW_HEIGHT;
        }

        @Override public void layoutWithin(int budget) {}
        @Override public void fillWidth(int width) {}
        @Override public boolean isInteractive() { return true; }

        private int hueX() { return SQUARE + GAP; }
        private int alphaX() { return hueX() + BAR + GAP; }

        @Override
        public void render(RenderContext ctx) {
            syncPicker(false);
            checkHex();
            int x = ctx.originX() + childX, y = ctx.originY() + childY;
            if (drag != Drag.NONE) {
                if (!stillHeld()) drag = Drag.NONE;
                else if (ctx.hasMouseInput()) follow(x, y, ctx.mouseX(), ctx.mouseY());
            }
            GuiGraphicsExtractor g = ctx.graphics();

            // The square: each column a gradient from its full-brightness colour down to
            // black, which is exactly brightness scaling the RGB.
            for (int col = 0; col < SQUARE; col++) {
                int top = 0xFF000000 | fromHsv(hue, col / (double) (SQUARE - 1), 1);
                g.fillGradient(x + col, y, x + col + 1, y + SQUARE, top, 0xFF000000);
            }
            int sx = x + (int) Math.round(saturation * (SQUARE - 1)), sy = y + (int) Math.round((1 - brightness) * (SQUARE - 1));
            g.outline(sx - 2, sy - 2, 5, 5, brightness > 0.5 ? 0xFF000000 : 0xFFFFFFFF);

            // The hue bar: six straight runs between the primaries and secondaries.
            int hx = x + hueX();
            for (int i = 0; i < 6; i++) {
                int y0 = y + i * SQUARE / 6, y1 = y + (i + 1) * SQUARE / 6;
                g.fillGradient(hx, y0, hx + BAR, y1, 0xFF000000 | fromHsv(i / 6.0, 1, 1),
                        0xFF000000 | fromHsv((i + 1) / 6.0, 1, 1));
            }
            marker(g, hx, y + (int) Math.round(hue * (SQUARE - 1)));

            // The transparency bar, over the checkerboard: opaque at the top.
            int rgb = fromHsv(hue, saturation, brightness);
            if (field.hasAlpha()) {
                int ax = x + alphaX();
                checkerboard(g, ax, y, BAR, SQUARE);
                g.fillGradient(ax, y, ax + BAR, y + SQUARE, 0xFF000000 | rgb, rgb);
                marker(g, ax, y + (int) Math.round((1 - alpha / 255.0) * (SQUARE - 1)));
            }

            // Before and now, side by side, where the hex field's row starts.
            int py = y + SQUARE + 6;
            swatch(g, x, py, field.before());
            swatch(g, x + PREVIEW, py, field.drawColor());
            if (ctx.isHovered(childX, childY + SQUARE + 6, PREVIEW * 2, ROW_HEIGHT)) {
                MKTooltip.queue(g, Component.literal("Before and now"), ctx.mouseX(), ctx.mouseY());
            }
        }

        private void swatch(GuiGraphicsExtractor g, int x, int y, int argb) {
            g.fill(x, y, x + PREVIEW, y + ROW_HEIGHT, 0xFF000000);
            if (field.hasAlpha()) checkerboard(g, x + 1, y + 1, PREVIEW - 2, ROW_HEIGHT - 2);
            g.fill(x + 1, y + 1, x + PREVIEW - 1, y + ROW_HEIGHT - 1, argb);
        }

        private void marker(GuiGraphicsExtractor g, int x, int y) {
            g.fill(x - 1, y - 1, x + BAR + 1, y + 2, 0xFF000000);
            g.fill(x - 1, y, x + BAR + 1, y + 1, 0xFFFFFFFF);
        }

        /** A press on a handle's area takes that handle and moves it to the mouse. */
        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if (button != Click.LEFT || disabled(in)) return false;
            int x = in.originX() + childX, y = in.originY() + childY;
            double lx = in.mouseX() - x, ly = in.mouseY() - y;
            if (ly < 0 || ly >= SQUARE) return false;
            if (lx >= 0 && lx < SQUARE) drag = Drag.SQUARE;
            else if (lx >= hueX() && lx < hueX() + BAR) drag = Drag.HUE;
            else if (field.hasAlpha() && lx >= alphaX() && lx < alphaX() + BAR) drag = Drag.ALPHA;
            else return false;
            syncPicker(false);
            follow(x, y, in.mouseX(), in.mouseY());
            return true;
        }

        @Override
        public boolean mouseReleased(InputContext in, int button) {
            if (button != Click.LEFT || drag == Drag.NONE) return false;
            drag = Drag.NONE;
            return true;
        }

        /** The dragged handle to the mouse at {@code (mx, my)}, the picker's top-left at {@code (x, y)}; written through. */
        private void follow(int x, int y, double mx, double my) {
            double fy = clamp01((my - y) / (SQUARE - 1));
            switch (drag) {
                case SQUARE -> {
                    saturation = clamp01((mx - x) / (SQUARE - 1));
                    brightness = 1 - fy;
                }
                case HUE -> hue = Math.min(fy, 0.999);
                case ALPHA -> alpha = (int) Math.round((1 - fy) * 255);
                case NONE -> { return; }
            }
            writePicker();
        }
    }
}
