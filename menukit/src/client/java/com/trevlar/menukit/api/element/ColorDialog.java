package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.layout.Column;
import com.trevlar.menukit.api.layout.CrossAlign;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.core.PressedTracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The dialog a {@link ColorField} opens: a modal panel with a Dyes tab and a Custom
 * tab, Cancel and Done. Built of MenuKit's own elements ({@link Tabs},
 * {@link TextField}, {@link Button}) plus the two drawn here, the dye grid and the
 * picker, so it draws in the kind of screen it opens on like any dialog.
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
    static final int HEX_WIDTH = 82;
    /** The dye grid: eight dyes a row, two rows, slots a little apart. */
    static final int DYE_COLUMNS = 8;
    static final int DYE_STEP = ItemPicker.SIZE + 2;

    /** The hex field's text colour, vanilla's EditBox default, and red while the text is not a colour. */
    private static final int HEX_OK = 0xFFE0E0E0;
    private static final int HEX_BAD = 0xFFFF5555;

    private final ColorField field;
    private final Panel panel;
    private final TextField hex;

    // View state
    private String tab = "dyes";
    private double hue, saturation, brightness;
    private int alpha = 0xFF;
    /** The colour the picker's handles were last set from, so a change from elsewhere moves them. */
    private int pickerSynced;
    private String hexDraft = "";
    /** The colour the hex text was last set from, so a change from elsewhere replaces it. */
    private int hexSynced;

    ColorDialog(ColorField field) {
        this.field = field;
        this.hex = TextField.builder().at(PREVIEW * 2 + GAP, SQUARE + 6).size(HEX_WIDTH, ROW_HEIGHT)
                .maxLength(9)
                .label(Component.literal("Hex"))
                .state(this::hexText, this::typeHex)
                .build();

        Tabs tabs = Tabs.builder()
                .size(196, 168)
                .state(() -> tab, id -> tab = id)
                .tab("dyes", Component.literal("Dyes"), List.of(new DyeGrid()))
                .tab("custom", Component.literal("Custom"), List.of(new Picker(), hex))
                .build();

        List<PanelElement> elements = Column.at(8, 8)
                .spacing(6)
                .crossAlign(CrossAlign.CENTER)
                .add(TextLabel.builder().text(field.label()).build())
                .add(tabs)
                .addRow(r -> r.spacing(4)
                        .add(Button.builder().label(Component.literal("Cancel")).size(60, 20)
                                .onClick(field::cancel).build())
                        .add(Button.builder().label(Component.literal("Done")).size(60, 20)
                                .onClick(field::done).build()))
                .build();

        // A dialog like ConfirmDialog's: centred, modal (opaque, dims behind, takes
        // every click and key), Escape cancels. Shown while the field says it is open.
        this.panel = Panel.builder("menukit:color-dialog-" + IDS.incrementAndGet())
                .elements(elements)
                .style(PanelStyle.RAISED)
                .position(PanelPosition.center())
                .visibleWhen(field::dialogShown)
                .build()
                .modal()
                .onEscape(field::cancel);
    }

    Panel panel() {
        return panel;
    }

    /** Opened: Dyes first, the picker and the hex text from the colour as it is now. */
    void opened() {
        tab = "dyes";
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
            box.setTextColor(valid ? HEX_OK : HEX_BAD);
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

    /** The opaque-free RGB ({@code 0xRRGGBB}) of a hue, saturation and brightness, each 0..1. */
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

    /** Whether the left button is still held: false once it is released, when the game says so. */
    private static boolean stillHeld() {
        return Minecraft.getInstance() == null || PressedTracker.isLeftMouseDown();
    }

    // The Dyes tab

    /** The sixteen dyes, by their items, in vanilla's order. Clicking one writes its colour. */
    private final class DyeGrid extends AbstractPanelElement {

        DyeGrid() {
            this.width = DYE_COLUMNS * DYE_STEP - 2;
            this.height = (DyeColor.values().length + DYE_COLUMNS - 1) / DYE_COLUMNS * DYE_STEP - 2;
        }

        @Override public void layoutWithin(int budget) {}
        @Override public void fillWidth(int width) {}
        @Override public boolean isInteractive() { return true; }

        private @org.jspecify.annotations.Nullable DyeColor dyeAt(InputContext in) {
            double lx = in.mouseX() - (in.originX() + childX), ly = in.mouseY() - (in.originY() + childY);
            if (lx < 0 || ly < 0) return null;
            int col = (int) (lx / DYE_STEP), row = (int) (ly / DYE_STEP);
            if (col >= DYE_COLUMNS || lx - col * DYE_STEP >= ItemPicker.SIZE || ly - row * DYE_STEP >= ItemPicker.SIZE) {
                return null;
            }
            int i = row * DYE_COLUMNS + col;
            return i < DyeColor.values().length ? DyeColor.values()[i] : null;
        }

        @Override
        public void render(RenderContext ctx) {
            GuiGraphicsExtractor g = ctx.graphics();
            int gx = ctx.originX() + childX, gy = ctx.originY() + childY;
            DyeColor hovered = ctx.hasMouseInput() ? dyeAt(ctx.input()) : null;
            int current = field.value() & 0xFFFFFF;
            for (DyeColor dye : DyeColor.values()) {
                int i = dye.ordinal();
                int x = gx + (i % DYE_COLUMNS) * DYE_STEP, y = gy + (i / DYE_COLUMNS) * DYE_STEP;
                ItemPicker.drawSlot(g, ctx.kind(), x, y, Items.DYE.pick(dye), dye == hovered, false);
                // The current colour's dye: a white ring around its slot.
                if ((ColorField.dyeColor(dye) & 0xFFFFFF) == current) g.outline(x - 1, y - 1, ItemPicker.SIZE + 2, ItemPicker.SIZE + 2, 0xFFFFFFFF);
            }
            if (hovered != null) {
                MKTooltip.queue(g, new ItemStack(Items.DYE.pick(hovered)).getHoverName(), ctx.mouseX(), ctx.mouseY());
            }
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
            this.width = SQUARE + GAP + BAR + (field.hasAlpha() ? GAP + BAR : 0);
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
