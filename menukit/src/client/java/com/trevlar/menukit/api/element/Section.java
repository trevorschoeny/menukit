package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.api.panel.Panel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A collapsible section: a header row that opens and closes the elements under it.
 *
 * <pre>{@code
 * Section.builder(Component.literal("Shift-click"))
 *         .summary(() -> Component.literal(onCount() + " of 18 on"))
 *         .add(TextLabel.builder().text(Component.literal("Moves a stack to the other side.")).build())
 *         .add(checkboxes)
 *         .build();
 * }</pre>
 *
 * <h3>Header</h3>
 *
 * An arrow ({@code ▶} closed, {@code ▼} open), an optional 8x8 color swatch, the
 * title, and a gray summary, cut with "..." when it runs out of room. A click
 * anywhere on the header row toggles the section.
 *
 * <p>The title and swatch are fixed or read every frame ({@code builder(Supplier)},
 * {@code swatch(IntSupplier)}, 7.3.0), and the summary is always read every frame. So
 * a section built once, such as a keyed {@link ListView} row, shows a rename or a
 * recolor in place, with no rebuild:
 * {@code Section.builder(() -> Component.literal(group.name)).swatch(() -> group.color)}.
 * The header measures the title as it is now. The summary takes what room the title
 * leaves, and a title too long for the header (a long rename) is cut with "..."
 * before the trailing edge, or a list row's controls, and shown whole in a tooltip.
 *
 * <p>In the Default look ({@link ScreenKind#DEFAULT}) the header takes the
 * category header's colors from vanilla's Controls screen: the title in white
 * with a shadow, the summary in vanilla's gray. It reads from the left as in the
 * Panel look, not centered as vanilla's is, so it lines up with the content
 * under it.
 *
 * <h3>Content</h3>
 *
 * Children are positioned from the top-left of the content area, which starts
 * {@link #CONTENT_GAP} below the header. They are laid out to the section's width
 * the way a panel lays out its own elements (a label wraps, a {@link Flow} wraps,
 * and growth pushes the children below down), and they receive input through
 * {@link ChildDispatch} exactly as they would on the panel. A closed section's
 * children are not drawn and take no clicks, wheel or keys; releases still reach
 * them, so a drag begun before the section closed can finish.
 *
 * <h3>Moving what is below it</h3>
 *
 * Position the element after a section as if the section were closed. Open, the
 * section reports its content as {@link #extraLayoutHeight()}, so the panel's
 * reflow pushes every later row down by exactly that much. Sections nest.
 *
 * <h3>Open state: view state, or the consumer's</h3>
 *
 * By default whether the section is open is its own view state, like a
 * {@link Tabs} body's scroll: closed unless {@code open(true)}, and kept for the
 * life of the element (a tab's body is built once per menu, so switching tabs
 * keeps it). {@code state(get, set)} hands it to the consumer instead: {@code get}
 * is read every frame and a header click hands the new value to {@code set}.
 *
 * <h3>Disabled look</h3>
 *
 * While disabled (its own {@code disabledWhen}, or its panel's or container's),
 * the arrow, title and summary draw in the disabled gray, the header takes no
 * clicks, and everything in the section is disabled too.
 */
public final class Section extends AbstractPanelElement {

    /** Height of the header row. */
    public static final int HEADER_HEIGHT = 12;
    /** Space between the header and the content when open. */
    public static final int CONTENT_GAP = 2;
    /** The title's color in the Panel look ({@link TextRole#HEADING}): dark, for a raised panel. */
    public static final int DEFAULT_TITLE_COLOR = ElementConstants.TEXT_DARK;
    /** The summary's color in the Panel look ({@link TextRole#MUTED}). */
    public static final int DEFAULT_SUMMARY_COLOR = ElementConstants.TEXT_MUTED;

    private static final String ARROW_CLOSED = "▶ ";
    private static final String ARROW_OPEN = "▼ ";
    private static final String ELLIPSIS = "...";
    private static final int SWATCH = 8;
    private static final int SWATCH_GAP = 4;
    private static final int SUMMARY_GAP = 8;
    /** The arrow's distance from the header's left edge, in both looks. */
    private static final int HEADER_INSET = 1;
    /** A faint darkening under the header while the mouse is on it. */
    private static final int HOVER_FILL = 0x18000000;
    /** The same, lightening, on vanilla's dark backdrop. */
    private static final int HOVER_FILL_VANILLA = 0x18FFFFFF;

    private final Supplier<Component> title;
    private final @Nullable IntSupplier swatch;
    private final @Nullable Supplier<Component> summary;
    /** Fixed colors from {@code colors(...)}, or null to take the kind's heading and muted colors. */
    private final @Nullable Integer titleColor;
    private final @Nullable Integer summaryColor;
    private final List<PanelElement> children;
    private final BooleanSupplier openGet;
    private final Consumer<Boolean> openSet;

    /** Whether the section is open, when the consumer does not own it (view state). */
    private boolean ownOpen;

    private int resolvedWidth = -1;   // from layoutWithin; -1 until the first pass

    /** Room kept free at the header's trailing edge for a list row's controls ({@link #reserveTrailing}). */
    private int trailing = 0;

    // The content's height at one width, and the visibility and geometry signature
    // it was measured at (after layout, so a settled layout does not relayout).
    private int contentHeight = 0;
    private int layoutWidth = -1;
    private int layoutSig = 0;

    private Section(Builder b) {
        super(b);
        this.title = b.title;
        this.swatch = b.swatch;
        this.summary = b.summary;
        this.titleColor = b.titleColor;
        this.summaryColor = b.summaryColor;
        this.children = List.copyOf(b.children);
        this.ownOpen = b.open;
        if (b.openGet != null) {
            this.openGet = b.openGet;
            this.openSet = b.openSet;
        } else {
            this.openGet = () -> ownOpen;
            this.openSet = v -> ownOpen = v;
        }
    }

    /** A section with a fixed title. */
    public static Builder builder(Component title) {
        Objects.requireNonNull(title, "title");
        return new Builder(() -> title);
    }

    /** A section whose title is read every frame (7.3.0): a rename shows at once, on the same element. */
    public static Builder builder(Supplier<Component> title) {
        return new Builder(Objects.requireNonNull(title, "title"));
    }

    /** Whether the section is open right now. */
    public boolean isOpen() {
        return openGet.getAsBoolean();
    }

    // Layout

    private int contentTop() {
        return childY + headerHeight() + CONTENT_GAP;
    }

    /** Lays the children out to the current width when it, or any child's size or visibility, changed. */
    private void relayout() {
        int w = getWidth();
        if (w == layoutWidth && signature() == layoutSig) return;
        Panel.layoutElementsWithin(children, w);
        Panel.reflowForWrap(children);
        contentHeight = Panel.contentHeightOf(children);
        layoutWidth = w;
        layoutSig = signature();
    }

    private int signature() {
        int sig = 1;
        for (PanelElement e : children) {
            boolean vis = e.isVisible();
            sig = sig * 31 + (vis ? 1 : 0);
            if (vis) {
                sig = sig * 31 + e.getChildX() + e.getWidth();
                sig = sig * 31 + e.getChildY() + e.getHeight();
            }
        }
        return sig;
    }

    /** As wide as the room its panel gives it. */
    @Override
    public void layoutWithin(int budget) {
        resolvedWidth = Math.max(1, budget);
        relayout();
    }

    @Override
    public void fillWidth(int width) {
        resolvedWidth = Math.max(1, width);
    }

    @Override
    public int getWidth() {
        return resolvedWidth >= 0 ? resolvedWidth : naturalWidth();
    }

    /** The header plus, when open, the content. */
    @Override
    public int getHeight() {
        if (!isOpen()) return headerHeight();
        relayout();
        return contentHeight > 0 ? headerHeight() + CONTENT_GAP + contentHeight : headerHeight();
    }

    /** Everything below the header: the panel pushes later rows down by this much. */
    @Override
    public int extraLayoutHeight() {
        return getHeight() - headerHeight();
    }

    /**
     * A list row's controls go on the header line, at its trailing edge: the header
     * (its summary, its hover, its click) stops short of them, and the content under it
     * keeps the whole width. The header line grows to the controls' height.
     */
    @Override
    boolean reserveTrailing(int px) {
        trailing = Math.max(0, px);
        return true;
    }

    @Override
    int[] trailingLine() {
        return new int[]{0, headerHeight(), 0};
    }

    /**
     * The header's height: {@link #HEADER_HEIGHT}, or as tall as a list row's controls
     * when it holds them, so they sit within the header line and clear what opens under it.
     */
    private int headerHeight() {
        return trailing > 0 ? Math.max(HEADER_HEIGHT, ListRow.CONTROL) : HEADER_HEIGHT;
    }

    /** The header's width: the section's, less the room kept for a list row's controls. */
    private int headerWidth() {
        return Math.max(1, getWidth() - trailing);
    }

    /** The whole header on one line, or the widest child, whichever is wider. */
    @Override
    public int naturalWidth() {
        Font font = Minecraft.getInstance().font;
        int header = font.width(ARROW_OPEN) + (swatch != null ? SWATCH + SWATCH_GAP : 0) + font.width(title.get());
        if (summary != null) header += SUMMARY_GAP + font.width(summary.get());
        int widest = 0;
        for (PanelElement e : children) {
            if (e.isVisible()) widest = Math.max(widest, e.getChildX() + e.naturalWidth());
        }
        return Math.max(header + trailing, widest);
    }

    @Override public boolean isInteractive() { return true; }

    // Rendering

    @Override
    public void render(RenderContext ctx) {
        var g = ctx.graphics();
        Font font = Minecraft.getInstance().font;
        boolean open = isOpen();
        boolean disabled = disabled(ctx);
        int w = headerWidth();
        int hx = ctx.originX() + childX, hy = ctx.originY() + childY;
        ScreenKind kind = ctx.kind();
        boolean vanilla = kind == ScreenKind.DEFAULT;
        boolean shadow = kind.textShadow();
        int titleCol = disabled || titleColor == null ? textColor(ctx, TextRole.HEADING) : titleColor;
        int summaryCol = disabled || summaryColor == null ? textColor(ctx, TextRole.MUTED) : summaryColor;

        if (!disabled && ctx.isHovered(childX, childY, w, headerHeight())) {
            g.fill(hx, hy, hx + w, hy + headerHeight(), vanilla ? HOVER_FILL_VANILLA : HOVER_FILL);
        }
        int textY = Text.centeredTextY(hy, hy + headerHeight());
        String arrow = open ? ARROW_OPEN : ARROW_CLOSED;
        // The header reads from the left in both looks, where the section's content
        // starts. Vanilla's category header is centered (KeyBindsList.CategoryEntry), but
        // over a settings page of labels and controls a centered heading floats away
        // from what it heads.
        int x = hx + HEADER_INSET;
        g.text(font, arrow, x, textY, titleCol, shadow);
        x += font.width(arrow);
        if (swatch != null) {
            int sy = hy + (headerHeight() - SWATCH) / 2;
            g.fill(x, sy, x + SWATCH, sy + SWATCH, swatch.getAsInt());
            x += SWATCH + SWATCH_GAP;
        }
        // The title and summary share what the header has left after the arrow and
        // swatch (and short of a list row's controls). The summary gives way first: it
        // takes only what the whole title leaves, then the title itself is cut.
        Component t = title.get();
        int room = hx + w - x;
        String titleText = fit(font, t.getString(), room);
        boolean titleCut = !titleText.equals(t.getString());
        g.text(font, titleText, x, textY, titleCol, shadow);
        x += font.width(titleText) + SUMMARY_GAP;
        if (summary != null) {
            String text = fit(font, summary.get().getString(), hx + w - x);
            if (!text.isEmpty()) g.text(font, text, x, textY, summaryCol, shadow);
        }
        // A cut title shows whole in a tooltip on the header, as a cut tab label does,
        // unless the consumer gave the section a tooltip of its own.
        if (titleCut && tooltipSupplier() == null && ctx.hasMouseInput()
                && ctx.isHovered(childX, childY, w, headerHeight())) {
            MKTooltip.queue(g, t, ctx.mouseX(), ctx.mouseY());
        } else {
            queueTooltip(ctx);
        }

        if (!open) return;
        relayout();
        ChildDispatch.render(children, contentRender(ctx));
    }

    /** {@code text} cut with "..." to {@code room}; empty when not even the "..." fits. */
    private static String fit(Font font, String text, int room) {
        if (font.width(text) <= room) return text;
        if (room < font.width(ELLIPSIS)) return "";
        return font.plainSubstrByWidth(text, room - font.width(ELLIPSIS)) + ELLIPSIS;
    }

    /** Culled while open: its content is held out of view with it. Closed, its content is hidden and gets nothing. */
    @Override
    public void renderCulled(RenderContext ctx) {
        if (isOpen()) ChildDispatch.renderCulled(children, contentRender(ctx));
    }

    @Override
    public void renderOverlay(RenderContext ctx) {
        if (isOpen()) ChildDispatch.renderOverlay(children, contentRender(ctx));
    }

    /** The children's render context: origin at the content area's top-left, the disabled cascade added. */
    private RenderContext contentRender(RenderContext ctx) {
        return childContext(ctx.at(ctx.originX() + childX, ctx.originY() + contentTop()));
    }

    /** The children's input context, the same way. */
    private InputContext content(InputContext in) {
        return in.at(in.originX() + childX, in.originY() + contentTop()).disabledIf(ownDisabled());
    }

    // Input

    private boolean onHeader(InputContext in) {
        return in.isOver(childX, childY, headerWidth(), headerHeight());
    }

    /** The header, and when open any child under the point. Gaps in the content claim nothing. */
    @Override
    public boolean hitTest(InputContext in) {
        if (onHeader(in)) return true;
        if (!isOpen()) return false;
        relayout();
        return ChildDispatch.hitTest(children, content(in));
    }

    /** A slot in the content, while open. */
    @Override
    public boolean presentsSlotAt(InputContext in) {
        if (!isOpen()) return false;
        relayout();
        return ChildDispatch.presentsSlotAt(children, content(in));
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        boolean open = isOpen();
        InputContext inner = content(in);
        // An open popover in the content claims the click anywhere in its bounds.
        if (open && ChildDispatch.overlayOwner(children, inner) != null) {
            return ChildDispatch.mouseClicked(children, inner, button);
        }
        if (onHeader(in)) {
            if (button == Click.LEFT && !disabled(in)) openSet.accept(!open);
            return true;                                   // the header eats its own clicks
        }
        if (!open) return false;
        relayout();
        return ChildDispatch.mouseClicked(children, inner, button);
    }

    @Override
    public boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        if (!isOpen()) return false;
        relayout();
        return ChildDispatch.mouseScrolled(children, content(in), scrollX, scrollY);
    }

    /** Broadcast even when closed, so a drag or capture begun before closing can end. */
    @Override
    public boolean mouseReleased(InputContext in, int button) {
        ChildDispatch.mouseReleased(children, content(in), button);
        return false;
    }

    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        return isOpen() && ChildDispatch.keyPressed(children, content(in), keyCode, scanCode, modifiers);
    }

    @Override
    public int @Nullable [] getActiveOverlayBounds(InputContext in) {
        return isOpen() ? ChildDispatch.activeOverlay(children, content(in)) : null;
    }

    @Override
    public void notifyClickOutsideOverlay(InputContext in) {
        ChildDispatch.notifyClickOutside(children, content(in));
    }

    @Override
    public void screenKind(ScreenKind kind) {
        ChildDispatch.screenKind(children, kind);
    }

    @Override
    public void inDialog(boolean inDialog) {
        ChildDispatch.inDialog(children, inDialog);
    }

    @Override
    public void inBand(boolean inBand) {
        ChildDispatch.inBand(children, inBand);
    }

    /** Every child attaches, open or not, as a hidden panel's elements do. */
    @Override
    public void onAttach(Screen screen) {
        ChildDispatch.attach(children, screen);
    }

    @Override
    public void onDetach(Screen screen) {
        ChildDispatch.detach(children, screen);
    }

    public static final class Builder extends AbstractPanelElement.Builder<Section, Builder> {
        private final Supplier<Component> title;
        private @Nullable IntSupplier swatch;
        private @Nullable Supplier<Component> summary;
        private @Nullable Integer titleColor;
        private @Nullable Integer summaryColor;
        private final List<PanelElement> children = new ArrayList<>();
        private boolean open = false;
        private @Nullable BooleanSupplier openGet;
        private @Nullable Consumer<Boolean> openSet;

        private Builder(Supplier<Component> title) {
            this.title = title;
        }

        @Override protected Builder self() { return this; }

        /** An 8x8 color swatch after the arrow (ARGB). */
        public Builder swatch(int argb) {
            return swatch(() -> argb);
        }

        /** An 8x8 color swatch after the arrow (ARGB), read every frame (7.3.0): a recolor shows at once. */
        public Builder swatch(IntSupplier argb) {
            this.swatch = Objects.requireNonNull(argb, "argb");
            return this;
        }

        /** The gray text after the title, read every frame. */
        public Builder summary(Supplier<Component> summary) {
            this.summary = Objects.requireNonNull(summary, "summary");
            return this;
        }

        /**
         * Fixed title and summary colors (ARGB), on every kind of screen. Without them the
         * kind decides ({@link TextRole#HEADING} and {@link TextRole#MUTED}). A
         * disabled section draws both in the disabled color on its own.
         */
        public Builder colors(int title, int summary) {
            this.titleColor = title;
            this.summaryColor = summary;
            return this;
        }

        /** Adds a child, positioned from the content area's top-left. */
        public Builder add(PanelElement child) {
            children.add(Objects.requireNonNull(child, "child"));
            return this;
        }

        /** Adds children, each positioned from the content area's top-left. */
        public Builder content(List<? extends PanelElement> content) {
            for (PanelElement e : content) add(e);
            return this;
        }

        /** Starts open (view state). Default closed. */
        public Builder open(boolean open) {
            this.open = open;
            return this;
        }

        /**
         * The consumer owns whether it is open: {@code get} is read every frame, and
         * a header click hands the new value to {@code set}.
         */
        public Builder state(BooleanSupplier get, Consumer<Boolean> set) {
            this.openGet = Objects.requireNonNull(get, "get");
            this.openSet = Objects.requireNonNull(set, "set");
            return this;
        }

        @Override
        public Section build() {
            return new Section(this);
        }
    }
}
