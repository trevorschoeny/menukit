package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.DialogLayout;
import com.trevlar.menukit.core.VanillaLook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * An element and a few controls on its first line, at its trailing edge (7.3.0).
 * Package-private; {@link ListView} makes two kinds:
 *
 * <ul>
 *   <li><b>A row with its controls</b> ({@link ListView.Builder#controlsInRows()}): the
 *       consumer's row, then a vertical divider line, then up, down and remove. The
 *       list makes one per key only when the list has controls in its rows, so any
 *       other list keeps the consumer's row as it is.</li>
 *   <li><b>The toolbar</b>: the list's title if it has one, then the search field,
 *       then the sort, add, remove and arrow buttons as its controls, a button's gap
 *       after it, no divider.</li>
 * </ul>
 *
 * <h3>Inside the row</h3>
 *
 * The row is the consumer's, whatever it is. The controls go on the row's first line
 * ({@link AbstractPanelElement#reserveTrailing}): a boxed {@link Flow} keeps them inside
 * its card, its content that much narrower; a {@link Section}, alone or in a boxed Flow,
 * keeps them on its header line, so what opens under it has the whole width. Any other
 * row (a bare text field) is narrowed by the same room and they sit beside it. Either
 * way the divider is the card's padding from the content and from the controls, so the
 * controls sit in a compartment of their own.
 *
 * <p>Each control keeps its slot whether or not it shows (a pinned row has no arrows
 * and no remove button), so every row of the list leaves the same room and the
 * controls line up in columns. They are centred on the first line: a Section's
 * header, or a row's height less what it grew by ({@link PanelElement#extraLayoutHeight}).
 * A first line shorter than the controls moves the row down until they fit.
 *
 * <p>The toolbar ({@link #toolbar}) is as wide as the room it is given, as any element:
 * placed by the mod above the list's scroll area, it reaches that area's outer edge,
 * scrollbar lane and band inset included, as the elements above it do; drawn by the
 * list at its own top, it is given the rows' width and lines up with them.
 *
 * <p>A toolbar with a title ({@link ListView.Builder#title}) starts with it at its left
 * edge, in the heading style a {@link Section}'s title has, centred on the buttons'
 * line as a label in a {@code crossAlign(CENTER)} {@link Flow} is. The search field
 * follows a button's gap after it and takes what is left; with no search the room
 * between the title and the buttons is empty. The title is read every frame, so a live
 * count grows in place, and one too long for the room (the buttons', and the search
 * field's own width when there is one) is cut with "..." keeping its styles, and shown
 * whole in a tooltip. A title given a tooltip of its own shows that one while hovered,
 * cut or not, as a {@link Section} with a tooltip does. A title with nothing else is a
 * line of its own, the text's height.
 */
final class ListRow extends AbstractPanelElement {

    /** The toolbar's: between the search field and its buttons, a button's gap. */
    static final int LANE_GAP = DialogLayout.BUTTON_GAP;
    /** Between two controls: the gap between any two MenuKit buttons side by side. */
    static final int CONTROL_GAP = DialogLayout.BUTTON_GAP;
    /** A row's buttons, all this tall and square: up, down, remove. */
    static final int CONTROL = 16;
    /** A row's divider: a card's padding on each side of its line, so the controls' compartment is as padded as the card. */
    static final int DIVIDER_PAD = Flow.BOX_PADDING;
    /** The divider's column: the Default look draws two lines (light, dark), the Panel look one. */
    static final int DIVIDER_W = 2;

    private final PanelElement row;
    private final List<PanelElement> controls;
    /** Drawn in this order, the controls over the row; input goes the other way, the controls first. */
    private final List<PanelElement> all;
    private final List<PanelElement> input;
    /** A list row's controls sit behind a divider line; the toolbar's buttons a gap after the search field. */
    private final boolean divider;
    /**
     * Whether the row keeps the controls' room on its own first line (otherwise they sit
     * beside it): asked once, on first need, since a button measures its label with the
     * game's font and a list may be built before it loads.
     */
    private @Nullable Boolean inside;

    /** The toolbar's title, read every frame, or {@code null}: rows and a toolbar without one. */
    private final @Nullable Supplier<Component> title;
    /** The title's own hover tooltip, read while hovered, or {@code null}: then a cut title shows whole. */
    private final @Nullable Supplier<Component> titleTooltip;
    /** Greys the title with the list. */
    private final BooleanSupplier titleOff;
    /** Whether the toolbar's row is a search field (it keeps its own width from the title) rather than an empty stand-in. */
    private final boolean searched;
    /** The title's slot at the last placing, so a title that changes width moves the search field with it. */
    private int placedLead = -1;

    /** The width its container gives it (the list, or the mod's layout for a placed toolbar); until the first pass, its natural width. */
    private int budget = -1;

    /** A list row and its controls, behind a divider. */
    ListRow(PanelElement row, List<PanelElement> controls) {
        this(row, controls, true, null, null, () -> false, false);
    }

    /**
     * The toolbar: the title if any, the search field if any (else an empty Flow), and
     * the buttons a gap after them, no divider. {@code off} greys the title with the list;
     * {@code titleTooltip} is the title's own hover tooltip, if any.
     */
    static ListRow toolbar(@Nullable Supplier<Component> title, @Nullable Supplier<Component> titleTooltip,
                           BooleanSupplier off, @Nullable PanelElement search, List<PanelElement> buttons) {
        PanelElement row = search != null ? search : Flow.builder().build();   // nothing there: the buttons keep the right end
        return new ListRow(row, buttons, false, title, titleTooltip, off, search != null);
    }

    private ListRow(PanelElement row, List<PanelElement> controls, boolean divider,
                    @Nullable Supplier<Component> title, @Nullable Supplier<Component> titleTooltip,
                    BooleanSupplier titleOff, boolean searched) {
        this.divider = divider;
        this.title = title;
        this.titleTooltip = titleTooltip;
        this.titleOff = titleOff;
        this.searched = searched;
        this.row = row;
        this.controls = List.copyOf(controls);
        List<PanelElement> all = new ArrayList<>(controls.size() + 1);
        all.add(row);
        all.addAll(controls);
        this.all = List.copyOf(all);
        this.input = List.copyOf(all.reversed());
    }

    /** The consumer's row inside. */
    PanelElement row() {
        return row;
    }

    /** The controls, left to right. */
    List<PanelElement> controls() {
        return controls;
    }

    // Layout

    private boolean inside() {
        if (inside == null) inside = row instanceof AbstractPanelElement r && r.reserveTrailing(room());
        return inside;
    }

    /** What the controls take from the row's line, or beside it: the divider and its padding, or the toolbar's gap, and the lane. */
    private int room() {
        return (divider ? DIVIDER_PAD + DIVIDER_W + DIVIDER_PAD : LANE_GAP) + laneWidth();
    }

    /** Every control's slot and the gaps between them, shown or not. */
    private int laneWidth() {
        int w = 0;
        for (PanelElement c : controls) w += c.getWidth();
        return w + Math.max(0, controls.size() - 1) * CONTROL_GAP;
    }

    private int laneHeight() {
        int h = 0;
        for (PanelElement c : controls) h = Math.max(h, c.getHeight());
        return h;
    }

    /** The row's first line, {@code {top, height, right}}: where it keeps the room, or its baseline height. */
    private int[] line() {
        if (inside()) return ((AbstractPanelElement) row).trailingLine();
        return new int[]{0, row.getHeight() - Math.max(0, row.extraLayoutHeight()), 0};
    }

    /** The controls' top, from the row's: centred on the first line (negative: above the row's top). */
    private int centred() {
        int[] line = line();
        return line[0] + (line[1] - laneHeight()) / 2;
    }

    /** How far the row sits below this element's top so the controls fit above its first line. */
    private int rowDrop() {
        return Math.max(0, -centred());
    }

    /** The left edge of the controls, from this element's left. */
    private int laneX() {
        return getWidth() - line()[2] - laneWidth();
    }

    // The title (toolbar only)

    private static final String ELLIPSIS = "...";

    /** The widest the title may be: the width less the buttons' room, and the search field's own width when there is one. */
    private int titleRoom() {
        int room = getWidth() - (controls.isEmpty() ? 0 : room());
        return searched ? room - row.naturalWidth() - LANE_GAP : room;
    }

    /** The title as drawn now: whole when it fits, else cut with "..." keeping its styles (a grey count stays grey). */
    FormattedText titleShown() {
        if (title == null) return FormattedText.EMPTY;
        Component t = title.get();
        if (t == null) return FormattedText.EMPTY;
        Font font = Minecraft.getInstance().font;
        int room = titleRoom();
        if (font.width(t) <= room) return t;
        int dots = font.width(ELLIPSIS);
        if (room < dots) return FormattedText.EMPTY;
        return FormattedText.composite(font.substrByWidth(t, room - dots), FormattedText.of(ELLIPSIS));
    }

    /** What the title takes from the left of the line: its width and a button's gap, or nothing. */
    private int lead() {
        int w = title == null ? 0 : Minecraft.getInstance().font.width(titleShown());
        return w > 0 ? w + LANE_GAP : 0;
    }

    /** The title's box, {@code {x, y, width, height}} in the toolbar's coordinates, or {@code null} without one. */
    int @Nullable [] titleBox() {
        if (title == null) return null;
        Font font = Minecraft.getInstance().font;
        int h = font.lineHeight;
        return new int[]{getChildX(), getChildY() + (getHeight() - h) / 2, font.width(titleShown()), h};
    }

    /**
     * What hovering the title shows now: its own tooltip when it has one, cut or not (a
     * {@code null} result shows none), as a {@link Section} with a tooltip does; else the
     * whole title when it is cut; else nothing.
     */
    @Nullable Component titleHover() {
        if (title == null) return null;
        if (titleTooltip != null) return titleTooltip.get();
        Component whole = title.get();
        return whole != null && !titleShown().getString().equals(whole.getString()) ? whole : null;
    }

    /** The buttons' box, {@code {x, y, width, height}}: their left edge, the line they share. */
    int[] laneBox() {
        int top = getChildY() + rowDrop() + centred();
        return new int[]{getChildX() + laneX(), top, laneWidth(), laneHeight()};
    }

    /**
     * A title that changed width since the toolbar was placed (a live count) moves the
     * search field after it and gives it what is left; nothing when it is the same.
     */
    private void refit() {
        if (title == null || budget <= 0 || lead() == placedLead) return;
        layoutWithin(budget);
        setChildPosition(getChildX(), getChildY());
    }

    @Override
    public int naturalWidth() {
        int titleW = title == null || title.get() == null ? 0 : Minecraft.getInstance().font.width(title.get());
        int lead = titleW > 0 ? titleW + LANE_GAP : 0;
        return lead + (inside() ? row.naturalWidth() : row.naturalWidth() + room());
    }

    /** The width it was given, as any element: the list's for a row, its container's for a placed toolbar. */
    @Override
    public int getWidth() {
        return budget > 0 ? budget : naturalWidth();
    }

    /** The row and the controls on its line; a title alone is a line of its own, the text's height. */
    @Override
    public int getHeight() {
        int drop = rowDrop();
        int h = Math.max(drop + row.getHeight(), drop + centred() + laneHeight());
        return title == null ? h : Math.max(h, Minecraft.getInstance().font.lineHeight);
    }

    /** What the consumer's row grew by, so a row inside a row still reports it. */
    @Override
    public int extraLayoutHeight() {
        return row.extraLayoutHeight();
    }

    @Override
    public void layoutWithin(int budget) {
        this.budget = Math.max(1, budget);
        int w = getWidth();
        row.layoutWithin(Math.max(1, (inside() ? w : w - room()) - lead()));
    }

    @Override
    public void fillWidth(int width) {
        layoutWithin(width);
    }

    /** Places the row at the left and the controls on its first line, ending at its trailing edge, each centred. */
    @Override
    public void setChildPosition(int x, int y) {
        super.setChildPosition(x, y);
        int drop = rowDrop();
        placedLead = lead();
        if (row instanceof AbstractPanelElement r) r.setChildPosition(x + placedLead, y + drop);
        int top = y + drop + centred(), laneH = laneHeight();
        int cx = x + laneX();
        for (PanelElement c : controls) {
            if (c instanceof AbstractPanelElement e) e.setChildPosition(cx, top + (laneH - c.getHeight()) / 2);
            cx += c.getWidth() + CONTROL_GAP;
        }
    }

    // Render and input: the row and its controls share the list's origin.

    /** The title, the row, then its divider over it, then the controls. */
    @Override
    public void render(RenderContext ctx) {
        refit();
        if (title != null) titleLine(ctx);
        ChildDispatch.render(List.of(row), ctx);
        if (divider && row.isVisible()) dividerLine(ctx);
        ChildDispatch.render(controls, ctx);
    }

    /**
     * The toolbar's title at its left edge, centred on the line, in a heading's colour
     * and shadow for the look (a {@link Section}'s title), greyed with the list. Hovered,
     * it shows {@link #titleHover()}: its own tooltip, or the whole title when cut.
     */
    private void titleLine(RenderContext ctx) {
        int[] box = titleBox();
        FormattedText shown = titleShown();
        if (box == null || box[2] == 0) return;
        var g = ctx.graphics();
        Font font = Minecraft.getInstance().font;
        int col = textColor(ctx.disabledIf(titleOff.getAsBoolean()), TextRole.HEADING);
        g.text(font, Language.getInstance().getVisualOrder(shown), ctx.originX() + box[0], ctx.originY() + box[1],
                col, ctx.kind().textShadow());
        if (ctx.hasMouseInput() && ctx.isHovered(box[0], box[1], box[2], box[3])) {
            Component tip = titleHover();
            if (tip != null) MKTooltip.queue(g, tip, ctx.mouseX(), ctx.mouseY());
        }
    }

    /**
     * The line between the row's content and the controls, in MenuKit's divider style
     * for the look: vanilla's light and dark pair in the Default look, the Panel look's
     * one grey line. It runs along the first line and the card's padding above and below
     * it, kept inside the card's border (the Default card's 1 px, the Panel card's border
     * and bevel): the card's whole inner height for a one-line card, and only the header
     * line of a card holding an open {@link Section}, whose content has the full width.
     */
    private void dividerLine(RenderContext ctx) {
        var g = ctx.graphics();
        boolean card = row instanceof Flow f && f.isBoxed();
        int pad = card ? Flow.BOX_PADDING : 0;
        int border = !card || !inside() ? 0 : ctx.kind() == ScreenKind.DEFAULT ? 1 : 2;
        int[] line = line();
        int h = row.getHeight();
        int top = Math.max(border, line[0] - pad), bottom = Math.min(h - border, line[0] + line[1] + pad);
        int x = ctx.originX() + getChildX() + laneX() - DIVIDER_PAD - DIVIDER_W;
        int y = ctx.originY() + row.getChildY();
        if (ctx.kind() == ScreenKind.DEFAULT) {
            g.fill(x, y + top, x + 1, y + bottom, VanillaLook.SEPARATOR_LIGHT);
            g.fill(x + 1, y + top, x + 2, y + bottom, VanillaLook.SEPARATOR_DARK);
        } else {
            g.fill(x + 1, y + top, x + 2, y + bottom, Divider.DEFAULT_COLOR);
        }
    }

    @Override
    public void renderCulled(RenderContext ctx) {
        ChildDispatch.renderCulled(all, ctx);
    }

    @Override
    public void renderOverlay(RenderContext ctx) {
        ChildDispatch.renderOverlay(all, ctx);
    }

    @Override public boolean isInteractive() { return true; }

    @Override
    public boolean hitTest(InputContext in) {
        return ChildDispatch.hitTest(input, in);
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        return ChildDispatch.mouseClicked(input, in, button);
    }

    @Override
    public boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        return ChildDispatch.mouseScrolled(input, in, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        ChildDispatch.mouseReleased(all, in, button);
        return false;
    }

    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        return ChildDispatch.keyPressed(input, in, keyCode, scanCode, modifiers);
    }

    @Override
    public int @Nullable [] getActiveOverlayBounds(InputContext in) {
        return ChildDispatch.activeOverlay(input, in);
    }

    @Override
    public void notifyClickOutsideOverlay(InputContext in) {
        ChildDispatch.notifyClickOutside(all, in);
    }

    @Override
    public boolean presentsSlotAt(InputContext in) {
        return ChildDispatch.presentsSlotAt(all, in);
    }

    @Override
    public int @Nullable [] takeRevealRequest() {
        return ChildDispatch.takeRevealRequest(all);
    }

    @Override public void screenKind(ScreenKind kind) { ChildDispatch.screenKind(all, kind); }
    @Override public void inDialog(boolean inDialog) { ChildDispatch.inDialog(all, inDialog); }
    @Override public void inBand(boolean inBand) { ChildDispatch.inBand(all, inBand); }
    @Override public void onAttach(Screen screen) { ChildDispatch.attach(all, screen); }
    @Override public void onDetach(Screen screen) { ChildDispatch.detach(all, screen); }
}
