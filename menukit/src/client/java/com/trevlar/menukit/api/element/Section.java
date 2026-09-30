package com.trevlar.menukit.api.element;

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
 * An arrow ({@code ▶} closed, {@code ▼} open), an optional 8x8 colour swatch, the
 * title, and a grey summary read every frame, cut with "..." when it runs out of
 * room. A click anywhere on the header row toggles the section.
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
 * <h3>Disabled look (§0066)</h3>
 *
 * While disabled (its own {@code disabledWhen}, or its panel's or container's),
 * the arrow, title and summary draw in the disabled grey, the header takes no
 * clicks, and everything in the section is disabled too.
 */
public final class Section extends AbstractPanelElement {

    /** Height of the header row. */
    public static final int HEADER_HEIGHT = 12;
    /** Space between the header and the content when open. */
    public static final int CONTENT_GAP = 2;
    /** Default title colour: dark, for a raised panel. */
    public static final int DEFAULT_TITLE_COLOR = ElementConstants.TEXT_DARK;
    /** Default summary colour. */
    public static final int DEFAULT_SUMMARY_COLOR = 0xFF8B8B8B;

    private static final String ARROW_CLOSED = "▶ ";
    private static final String ARROW_OPEN = "▼ ";
    private static final String ELLIPSIS = "...";
    private static final int SWATCH = 8;
    private static final int SWATCH_GAP = 4;
    private static final int SUMMARY_GAP = 8;
    /** A faint darkening under the header while the mouse is on it. */
    private static final int HOVER_FILL = 0x18000000;

    private final Component title;
    private final @Nullable Integer swatch;
    private final @Nullable Supplier<Component> summary;
    private final int titleColor;
    private final int summaryColor;
    private final List<PanelElement> children;
    private final BooleanSupplier openGet;
    private final Consumer<Boolean> openSet;

    /** Whether the section is open, when the consumer does not own it (view state). */
    private boolean ownOpen;

    private int resolvedWidth = -1;   // from layoutWithin; -1 until the first pass

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

    public static Builder builder(Component title) {
        return new Builder(title);
    }

    /** Whether the section is open right now. */
    public boolean isOpen() {
        return openGet.getAsBoolean();
    }

    // ── Layout ─────────────────────────────────────────────────────────

    private int contentTop() {
        return childY + HEADER_HEIGHT + CONTENT_GAP;
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
        if (!isOpen()) return HEADER_HEIGHT;
        relayout();
        return contentHeight > 0 ? HEADER_HEIGHT + CONTENT_GAP + contentHeight : HEADER_HEIGHT;
    }

    /** Everything below the header: the panel pushes later rows down by this much. */
    @Override
    public int extraLayoutHeight() {
        return getHeight() - HEADER_HEIGHT;
    }

    /** The whole header on one line, or the widest child, whichever is wider. */
    @Override
    public int naturalWidth() {
        Font font = Minecraft.getInstance().font;
        int header = font.width(ARROW_OPEN) + (swatch != null ? SWATCH + SWATCH_GAP : 0) + font.width(title);
        if (summary != null) header += SUMMARY_GAP + font.width(summary.get());
        int widest = 0;
        for (PanelElement e : children) {
            if (e.isVisible()) widest = Math.max(widest, e.getChildX() + e.naturalWidth());
        }
        return Math.max(header, widest);
    }

    @Override public boolean isInteractive() { return true; }

    // ── Render ─────────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
        var g = ctx.graphics();
        Font font = Minecraft.getInstance().font;
        boolean open = isOpen();
        boolean disabled = disabled(ctx);
        int w = getWidth();
        int hx = ctx.originX() + childX, hy = ctx.originY() + childY;
        int titleCol = disabled ? ElementConstants.TEXT_DISABLED : titleColor;
        int summaryCol = disabled ? ElementConstants.TEXT_DISABLED : summaryColor;

        if (!disabled && ctx.isHovered(childX, childY, w, HEADER_HEIGHT)) {
            g.fill(hx, hy, hx + w, hy + HEADER_HEIGHT, HOVER_FILL);
        }
        int textY = Text.centeredTextY(hy, hy + HEADER_HEIGHT);
        int x = hx + 1;
        String arrow = open ? ARROW_OPEN : ARROW_CLOSED;
        g.text(font, arrow, x, textY, titleCol, false);
        x += font.width(arrow);
        if (swatch != null) {
            int sy = hy + (HEADER_HEIGHT - SWATCH) / 2;
            g.fill(x, sy, x + SWATCH, sy + SWATCH, swatch);
            x += SWATCH + SWATCH_GAP;
        }
        g.text(font, title.getString(), x, textY, titleCol, false);
        x += font.width(title);
        if (summary != null) {
            x += SUMMARY_GAP;
            int room = hx + w - x;
            String text = summary.get().getString();
            if (room > 0 && font.width(text) > room) {
                text = font.plainSubstrByWidth(text, Math.max(0, room - font.width(ELLIPSIS))) + ELLIPSIS;
            }
            if (room > 0) g.text(font, text, x, textY, summaryCol, false);
        }
        queueTooltip(ctx);

        if (!open) return;
        relayout();
        ChildDispatch.render(children, contentRender(ctx));
    }

    @Override
    public void renderOverlay(RenderContext ctx) {
        if (isOpen()) ChildDispatch.renderOverlay(children, contentRender(ctx));
    }

    /** The children's render context: origin at the content area's top-left, the disabled cascade added. */
    private RenderContext contentRender(RenderContext ctx) {
        return ctx.at(ctx.originX() + childX, ctx.originY() + contentTop()).disabledIf(ownDisabled());
    }

    /** The children's input context, the same way. */
    private InputContext content(InputContext in) {
        return in.at(in.originX() + childX, in.originY() + contentTop()).disabledIf(ownDisabled());
    }

    // ── Input ──────────────────────────────────────────────────────────

    private boolean onHeader(InputContext in) {
        return in.isOver(childX, childY, getWidth(), HEADER_HEIGHT);
    }

    /** The header, and when open any child under the point. Gaps in the content claim nothing. */
    @Override
    public boolean hitTest(InputContext in) {
        if (onHeader(in)) return true;
        if (!isOpen()) return false;
        relayout();
        return ChildDispatch.hitTest(children, content(in));
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

    /** Every child attaches, open or not, as a hidden panel's elements do. */
    @Override
    public void onAttach(Screen screen) {
        ChildDispatch.attach(children, screen);
    }

    @Override
    public void onDetach(Screen screen) {
        ChildDispatch.detach(children, screen);
    }

    // ── Builder ────────────────────────────────────────────────────────

    public static final class Builder extends AbstractPanelElement.Builder<Section, Builder> {
        private final Component title;
        private @Nullable Integer swatch;
        private @Nullable Supplier<Component> summary;
        private int titleColor = DEFAULT_TITLE_COLOR;
        private int summaryColor = DEFAULT_SUMMARY_COLOR;
        private final List<PanelElement> children = new ArrayList<>();
        private boolean open = false;
        private @Nullable BooleanSupplier openGet;
        private @Nullable Consumer<Boolean> openSet;

        private Builder(Component title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        @Override protected Builder self() { return this; }

        /** An 8x8 colour swatch after the arrow (ARGB). */
        public Builder swatch(int argb) {
            this.swatch = argb;
            return this;
        }

        /** The grey text after the title, read every frame. */
        public Builder summary(Supplier<Component> summary) {
            this.summary = Objects.requireNonNull(summary, "summary");
            return this;
        }

        /** Title and summary colours (ARGB), for a dark panel. A disabled section draws both grey on its own. */
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
