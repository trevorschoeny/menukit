package com.trevlar.menukit.core;

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
 *         .add(new TextLabel(0, 0, Component.literal("Moves a stack to the other side.")))
 *         .add(Flow.of(checkboxes).at(0, 12))
 *         .build();
 * }</pre>
 *
 * <h3>Header</h3>
 *
 * An arrow ({@code ▶} closed, {@code ▼} open), an optional 8×8 colour swatch, the
 * title, and a grey summary read every frame, cut with "..." when it runs out of
 * room. A click anywhere on the header row toggles the section.
 *
 * <h3>Content</h3>
 *
 * Children are positioned from the top-left of the content area, which starts
 * {@link #CONTENT_GAP} below the header. They are laid out to the section's width
 * the way a panel lays out its own elements: a label wraps, a {@link Flow} wraps,
 * and the growth pushes the children below it down. They get clicks, the wheel,
 * keys and overlays (a {@link Dropdown} popover) exactly as they would directly on
 * the panel. A closed section's children are inert: not drawn, not hovered, and
 * offered no clicks, wheel or keys. Mouse releases still reach them, so a drag or
 * a key capture that started before the section closed can finish.
 *
 * <h3>Moving what's below it</h3>
 *
 * Position the element after a section as if the section were closed: its top at
 * the section's {@code childY + HEADER_HEIGHT} plus whatever gap you want. When
 * the section opens, it reports its content as {@link #extraLayoutHeight()}, the
 * seam {@link Flow} and a wrapped {@link TextLabel} use, so the panel's reflow
 * pushes every later row down by exactly that much, and the panel's (or a
 * {@link Tabs} body's) scroll height follows. Sections nest the same way.
 *
 * <h3>Open state</h3>
 *
 * Owned by the element by default, closed unless {@link Builder#open(boolean)}
 * says otherwise, and kept for the life of the element: switching {@link Tabs}
 * away and back keeps it, because a tab's body is built once per menu. For state
 * the consumer owns, {@link Builder#linked(BooleanSupplier, Consumer)} reads it
 * every frame and writes the new value on a click, like {@link Checkbox#linked}.
 *
 * <p>Known limit, shared with {@link Tabs}: a widget-wrapping element (a
 * {@link TextField}) inside a closed section is still registered with the screen,
 * so it can keep keyboard focus. See {@link TextField}.
 */
public final class Section extends AbstractPanelElement<Section> {

    @Override protected Section self() { return this; }

    /** Height of the header row. */
    public static final int HEADER_HEIGHT = 12;
    /** Space between the header and the content when open. */
    public static final int CONTENT_GAP = 2;
    /** Default title colour: dark, for a raised panel. */
    public static final int DEFAULT_TITLE_COLOR = TextLabel.COLOR_DARK;
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

    // ── Declared structure (fixed at construction) ─────────────────────

    private final Component title;
    private final @Nullable Integer swatch;
    private final @Nullable Supplier<Component> summary;
    private final int titleColor;
    private final int summaryColor;
    private final List<PanelElement> children;
    private final @Nullable BooleanSupplier linkedOpen;
    private final @Nullable Consumer<Boolean> onToggle;

    // ── State ──────────────────────────────────────────────────────────

    /** Element-owned open state; unused when linked. */
    private boolean open;

    private int resolvedWidth = -1;   // from layoutWithin; -1 until the first pass

    // Content layout cache: the children's height at one width, and the
    // visibility + geometry signature it was measured at (after layout, so a
    // settled layout doesn't re-lay itself out every call).
    private int contentHeight = 0;
    private int layoutWidth = -1;
    private int layoutSig = 0;

    // Screen-space panel-content origin from the last render/hitTest; input hooks
    // get no RenderContext (the Flow / ScrollContainer idiom).
    private int cachedContentX, cachedContentY;
    private boolean cachedOriginValid = false;

    private Section(Builder b) {
        this.childX = b.childX;
        this.childY = b.childY;
        this.title = b.title;
        this.swatch = b.swatch;
        this.summary = b.summary;
        this.titleColor = b.titleColor;
        this.summaryColor = b.summaryColor;
        this.children = List.copyOf(b.children);
        this.linkedOpen = b.linkedOpen;
        this.onToggle = b.onToggle;
        this.open = b.open;
    }

    public static Builder builder(Component title) {
        return new Builder(title);
    }

    /** Whether the section is open right now. */
    public boolean isOpen() {
        return linkedOpen != null ? linkedOpen.getAsBoolean() : open;
    }

    /** Opens or closes it, as a header click does. */
    public void setOpen(boolean value) {
        if (linkedOpen != null) {
            if (onToggle != null) onToggle.accept(value);
        } else {
            open = value;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Layout
    // ════════════════════════════════════════════════════════════════════

    /** The content's top-left, relative to the panel content origin. */
    private int contentLeft() {
        return childX;
    }

    private int contentTop() {
        return childY + HEADER_HEIGHT + CONTENT_GAP;
    }

    /** Lays the children out to the current width when it, or any child's size or visibility, changed. */
    private void relayout() {
        int w = getWidth();
        if (w == layoutWidth && signature(children) == layoutSig) return;
        Panel.layoutElementsWithin(children, w);
        Panel.reflowForWrap(children);
        contentHeight = Panel.contentHeightOf(children);
        layoutWidth = w;
        layoutSig = signature(children);
    }

    /** Visibility and live geometry of the children: changes when one resizes, moves, or toggles. */
    private static int signature(List<PanelElement> elements) {
        int sig = 1;
        for (PanelElement e : elements) {
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

    // ════════════════════════════════════════════════════════════════════
    // Render
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void render(RenderContext ctx) {
        var g = ctx.graphics();
        Font font = Minecraft.getInstance().font;
        cacheContentOrigin(ctx.originX(), ctx.originY());
        boolean isOpen = isOpen();
        int w = getWidth();
        int hx = ctx.originX() + childX, hy = ctx.originY() + childY;

        // ── Header ──
        if (ctx.isHovered(childX, childY, w, HEADER_HEIGHT)) {
            g.fill(hx, hy, hx + w, hy + HEADER_HEIGHT, HOVER_FILL);
        }
        int textY = hy + (HEADER_HEIGHT - font.lineHeight) / 2 + 1;
        int x = hx + 1;
        String arrow = isOpen ? ARROW_OPEN : ARROW_CLOSED;
        g.text(font, arrow, x, textY, titleColor, false);
        x += font.width(arrow);
        if (swatch != null) {
            int sy = hy + (HEADER_HEIGHT - SWATCH) / 2;
            g.fill(x, sy, x + SWATCH, sy + SWATCH, swatch);
            x += SWATCH + SWATCH_GAP;
        }
        g.text(font, title.getString(), x, textY, titleColor, false);
        x += font.width(title);
        if (summary != null) {
            x += SUMMARY_GAP;
            int room = hx + w - x;
            String text = summary.get().getString();
            if (room > 0 && font.width(text) > room) {
                text = font.plainSubstrByWidth(text, Math.max(0, room - font.width(ELLIPSIS))) + ELLIPSIS;
            }
            if (room > 0) g.text(font, text, x, textY, summaryColor, false);
        }
        queueTooltip(ctx);

        // ── Content ──
        if (!isOpen) return;
        relayout();
        RenderContext contentCtx = contentContext(ctx);
        for (PanelElement e : children) {
            if (e.isVisible()) e.render(contentCtx);
        }
    }

    @Override
    public void renderOverlay(RenderContext ctx) {
        if (!isOpen()) return;
        RenderContext contentCtx = contentContext(ctx);
        for (PanelElement e : children) {
            if (e.isVisible()) e.renderOverlay(contentCtx);
        }
    }

    /** The context the children draw in: origin at the content area's top-left. */
    private RenderContext contentContext(RenderContext ctx) {
        return new RenderContext(ctx.graphics(), ctx.originX() + contentLeft(), ctx.originY() + contentTop(),
                ctx.mouseX(), ctx.mouseY());
    }

    // ════════════════════════════════════════════════════════════════════
    // Input
    // ════════════════════════════════════════════════════════════════════

    private void cacheContentOrigin(int contentX, int contentY) {
        cachedContentX = contentX;
        cachedContentY = contentY;
        cachedOriginValid = true;
    }

    /** The children's origin in screen space. */
    private int childOriginX() {
        return cachedContentX + contentLeft();
    }

    private int childOriginY() {
        return cachedContentY + contentTop();
    }

    private boolean onHeader(double mouseX, double mouseY) {
        double lx = mouseX - cachedContentX - childX, ly = mouseY - cachedContentY - childY;
        return lx >= 0 && lx < getWidth() && ly >= 0 && ly < HEADER_HEIGHT;
    }

    /** The header, and when open, any child the point is on. Gaps in the content claim nothing. */
    @Override
    public boolean hitTest(double mouseX, double mouseY, int contentX, int contentY) {
        cacheContentOrigin(contentX, contentY);
        if (onHeader(mouseX, mouseY)) return true;
        if (!isOpen()) return false;
        relayout();
        for (PanelElement e : children) {
            if (e.isVisible() && e.hitTest(mouseX, mouseY, childOriginX(), childOriginY())) return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!cachedOriginValid) return false;
        boolean isOpen = isOpen();
        // An open popover in the content claims the click anywhere in its bounds.
        if (isOpen) {
            PanelElement owner = overlayOwnerAt(mouseX, mouseY);
            if (owner != null) return owner.mouseClicked(mouseX, mouseY, button);
        }
        if (onHeader(mouseX, mouseY)) {
            if (button == 0) setOpen(!isOpen);
            return true;                                   // the header eats its own clicks
        }
        if (!isOpen) return false;
        relayout();
        for (PanelElement e : children) {
            if (!e.isVisible()) continue;
            if (e.hitTest(mouseX, mouseY, childOriginX(), childOriginY()) && e.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!cachedOriginValid || !isOpen()) return false;
        PanelElement owner = overlayOwnerAt(mouseX, mouseY);
        if (owner != null) return owner.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        relayout();
        for (PanelElement e : children) {
            if (!e.isVisible()) continue;
            if (e.hitTest(mouseX, mouseY, childOriginX(), childOriginY())
                    && e.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    /** The child whose open overlay covers this point, or null. */
    private @Nullable PanelElement overlayOwnerAt(double mouseX, double mouseY) {
        for (PanelElement e : children) {
            if (!e.isVisible()) continue;
            int[] ov = e.getActiveOverlayBounds();
            if (ov != null && mouseX >= ov[0] && mouseX < ov[0] + ov[2] && mouseY >= ov[1] && mouseY < ov[1] + ov[3]) {
                return e;
            }
        }
        return null;
    }

    /** Broadcast even when closed, so a drag or capture begun before closing can end. */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (PanelElement e : children) {
            if (e.isVisible()) e.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isOpen()) return false;
        for (PanelElement e : children) {
            if (e.isVisible() && e.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    @Override
    public int @Nullable [] getActiveOverlayBounds() {
        if (!isOpen()) return null;
        for (PanelElement e : children) {
            if (!e.isVisible()) continue;
            int[] ov = e.getActiveOverlayBounds();
            if (ov != null) return ov;
        }
        return null;
    }

    @Override
    public void notifyClickOutsideOverlay(double mouseX, double mouseY) {
        for (PanelElement e : children) {
            if (e.isVisible()) e.notifyClickOutsideOverlay(mouseX, mouseY);
        }
    }

    /** Every child attaches, open or not, as a hidden panel's elements do. */
    @Override
    public void onAttach(Screen screen) {
        for (PanelElement e : children) e.onAttach(screen);
    }

    @Override
    public void onDetach(Screen screen) {
        for (PanelElement e : children) e.onDetach(screen);
    }

    // ════════════════════════════════════════════════════════════════════
    // Builder
    // ════════════════════════════════════════════════════════════════════

    public static final class Builder {
        private final Component title;
        private int childX, childY;
        private @Nullable Integer swatch;
        private @Nullable Supplier<Component> summary;
        private int titleColor = DEFAULT_TITLE_COLOR;
        private int summaryColor = DEFAULT_SUMMARY_COLOR;
        private final List<PanelElement> children = new ArrayList<>();
        private boolean open = false;
        private @Nullable BooleanSupplier linkedOpen;
        private @Nullable Consumer<Boolean> onToggle;

        private Builder(Component title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        /** Panel-local position. Default (0, 0). */
        public Builder at(int x, int y) {
            this.childX = x;
            this.childY = y;
            return this;
        }

        /** An 8×8 colour swatch after the arrow (ARGB). */
        public Builder swatch(int argb) {
            this.swatch = argb;
            return this;
        }

        /** The grey text after the title, read every frame. */
        public Builder summary(Supplier<Component> summary) {
            this.summary = Objects.requireNonNull(summary, "summary");
            return this;
        }

        /** Title and summary colours (ARGB), for a dark panel or a greyed-out section. */
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

        /** Starts open (element-owned state). Default closed. */
        public Builder open(boolean open) {
            this.open = open;
            return this;
        }

        /**
         * Consumer-owned open state: {@code state} is read every frame, and
         * {@code onToggle} gets the new value when the header is clicked.
         */
        public Builder linked(BooleanSupplier state, Consumer<Boolean> onToggle) {
            this.linkedOpen = Objects.requireNonNull(state, "state");
            this.onToggle = Objects.requireNonNull(onToggle, "onToggle");
            return this;
        }

        public Section build() {
            return new Section(this);
        }
    }
}
