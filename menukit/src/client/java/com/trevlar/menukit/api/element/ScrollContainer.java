package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.PanelRendering;
import com.trevlar.menukit.core.VanillaLook;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * A clipped viewport over pre-positioned elements, scrolled vertically: the
 * single primitive for "show content larger than its bounds".
 *
 * <pre>{@code
 * ScrollContainer.builder().size(140, 80)
 *         .content(rows)                      // positioned from the viewport's top-left
 *         .build();
 * }</pre>
 *
 * <h3>Single responsibility</h3>
 *
 * It owns clipping, the scroll position, the scrollbar and routing input to its
 * children. It does not lay out: children are placed from the viewport's
 * top-left (compose them with {@code Row}/{@code Column} or give positions), and
 * the viewport's size is declared. Width reserved for the scrollbar lane is
 * {@link #viewportWidthFor}.
 *
 * <h3>Scroll position: view state, or the consumer's</h3>
 *
 * By default the scroll is the container's own view state, as a {@link Tabs}
 * body's is. {@code state(get, set)} hands it to the consumer instead, in pixels
 * from the top: {@code get} is read every frame, {@code set} receives each change.
 * Pixels, not a fraction: when the content changes height while shown (a section
 * opening), what is on screen stays where it is and only the range changes. A
 * position past the new end is clamped, and the clamp written back.
 *
 * <h3>Input</h3>
 *
 * The wheel over the viewport scrolls it (after any child that scrolls itself, a
 * nested container, had the wheel); dragging the handle scrolls it; clicks inside
 * the viewport reach the children at their scrolled positions; the scrollbar lane
 * eats its own clicks. An open popover of a child (a {@link Dropdown}) claims its
 * whole area, even the part hanging below the viewport, and draws unclipped on
 * the overlay pass. Disabled (its own {@code disabledWhen} or its panel's), it and
 * everything in it are greyed and inert.
 *
 * <p>Every child is dispatched through {@link ChildDispatch}, with the context
 * moved to the scrolled content origin: the same order a panel uses.
 *
 * <h3>On an options screen</h3>
 *
 * On an options screen ({@link ScreenKind#OPTIONS}) it is the band every Options
 * screen has: vanilla's darker list
 * background behind the content, scrolling with it, vanilla's separator along
 * the top and the bottom, drawn in the 2 px just outside the container as vanilla
 * draws them, and vanilla's thin list scroller ({@code widget/scroller} on
 * {@code widget/scroller_background}) at the right of the lane, hidden while
 * everything fits. The lane, the handle's travel and the input are the same on
 * both kinds of screen.
 *
 * <p>The band also insets its content as vanilla's lists do: the children's
 * origin is {@link #OPTIONS_INSET_X} in from the left and {@link #OPTIONS_INSET_Y}
 * down, with as much room past the last child, scrolling with the content. The
 * right needs no inset of its own: the scrollbar lane already keeps the content
 * clear of the scroller. So the content is narrower on an options screen: size it
 * with {@link #viewportWidthFor(int, ScreenKind)}. A {@code Tabs} page and a
 * panel's own auto-scroll do that for their children.
 */
public class ScrollContainer extends AbstractPanelElement {

    /** Width of the handle sprite: vanilla's creative-inventory scroller. */
    public static final int SCROLLER_WIDTH = 12;
    /** Height of the handle: a fixed-size handle, as vanilla's creative inventory. */
    public static final int SCROLLER_HEIGHT = 15;
    /** Vanilla's scroller sprite. */
    public static final Identifier SCROLLER_SPRITE =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    /** Vanilla's disabled scroller sprite (content fits, or disabled). */
    public static final Identifier SCROLLER_DISABLED_SPRITE =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    /** Space between the content and the scrollbar track. */
    public static final int SCROLLER_GUTTER = 4;
    /** Padding inside the track around the handle. */
    public static final int SCROLLER_TRACK_PADDING = 1;
    /** Scrollbar track width: the handle plus its padding. */
    public static final int TRACK_WIDTH = SCROLLER_WIDTH + 2 * SCROLLER_TRACK_PADDING;
    /** Pixels scrolled per wheel notch. */
    public static final int SCROLL_PIXELS_PER_TICK = 10;

    /**
     * On an options screen, how far in from the list band's left edge its content
     * starts (and, in a {@code Tabs} page that does not scroll, how far from its
     * right edge it ends): vanilla's gap between a list row's content and the
     * list's scrollbar. 26.2's {@code AbstractSelectionList.scrollBarX()} is
     * {@code getRowRight() + scrollbarWidth() + 2} (6 + 2), and a row's content
     * ends {@code Entry.CONTENT_PADDING} (2) inside the row, so 10; Controls'
     * {@code KeyBindsList.KeyEntry} ends its last button at {@code scrollBarX() - 10}.
     * MenuKit adds 4 to that: a settings page of labels, headings and wrapped text
     * read cramped at vanilla's 10, whose lists hold only buttons (Trev, 2026-10-03).
     */
    public static final int OPTIONS_INSET_X = 14;
    /**
     * On an options screen, the room above the band's first child and below its
     * last: vanilla's list padding ({@code AbstractSelectionList.getFirstEntryY()}
     * is the list's top + 2, {@code contentHeight()} the rows + 4) plus a row's
     * {@code Entry.CONTENT_PADDING} (2), which is 4. MenuKit uses 10: at 4 the first
     * heading sat on the band's top separator (Trev, 2026-10-03). It scrolls with the
     * content, as vanilla's does.
     */
    public static final int OPTIONS_INSET_Y = 10;

    // Vanilla's list scrollbar (AbstractScrollArea, 26.2): 6 px wide.
    private static final Identifier VANILLA_SCROLLER = Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier VANILLA_SCROLLER_TRACK = Identifier.withDefaultNamespace("widget/scroller_background");
    private static final int VANILLA_SCROLLER_WIDTH = 6;

    private final List<PanelElement> content;
    /** The scrollable height given to the builder, or -1 to follow the children's extent every frame. */
    private final int fixedContentHeight;
    private final DoubleSupplier scrollGet;
    private final DoubleConsumer scrollSet;
    /** Whether an options screen draws the list band; off where a host draws it (a Tabs body). */
    private final boolean band;

    /** The scroll, in pixels, when the consumer does not own it (view state). */
    private double ownScroll = 0;

    // The handle drag: interaction state from press to release, not a frame cache.
    private boolean dragging = false;
    private double dragStartMouseY = 0;
    private double dragStartFraction = 0;

    protected ScrollContainer(Builder b) {
        super(b);
        this.content = List.copyOf(b.content);
        this.fixedContentHeight = b.contentHeight;
        this.band = b.band;
        if (b.scrollGet != null) {
            this.scrollGet = b.scrollGet;
            this.scrollSet = b.scrollSet;
        } else {
            this.scrollGet = () -> ownScroll;
            this.scrollSet = v -> ownScroll = v;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The content width that fits a container {@code outerWidth} wide on a
     * container screen: its width minus the scrollbar lane. On an options screen
     * use {@link #viewportWidthFor(int, ScreenKind)}.
     */
    public static int viewportWidthFor(int outerWidth) {
        return outerWidth - TRACK_WIDTH - SCROLLER_GUTTER;
    }

    /**
     * The content width that fits a container {@code outerWidth} wide on a screen
     * of {@code kind}: its width minus the scrollbar lane and, on an options
     * screen, the list band's {@link #OPTIONS_INSET_X}.
     */
    public static int viewportWidthFor(int outerWidth, ScreenKind kind) {
        return viewportWidthFor(outerWidth) - (kind == ScreenKind.OPTIONS ? OPTIONS_INSET_X : 0);
    }

    private static int autoContentHeight(List<PanelElement> content) {
        int max = 0;
        for (PanelElement e : content) max = Math.max(max, e.getChildY() + e.getHeight());
        return max;
    }

    // ── Geometry ───────────────────────────────────────────────────────

    private int viewportWidth() {
        return width - TRACK_WIDTH - SCROLLER_GUTTER;
    }

    // The list band's inset, by the kind of screen the context says it is on:
    // only where this container draws the band (a Tabs page insets its own).
    private int insetX(ScreenKind kind) {
        return band && kind == ScreenKind.OPTIONS ? OPTIONS_INSET_X : 0;
    }

    private int insetY(ScreenKind kind) {
        return band && kind == ScreenKind.OPTIONS ? OPTIONS_INSET_Y : 0;
    }

    /**
     * The scrollable height: the builder's {@code contentHeight}, else the children's
     * extent, read live, so a child that grows or shrinks while shown (a section
     * opening, an item chooser's grid narrowing to a search) changes the range.
     * The band's inset above and below is part of it.
     */
    private int contentHeight(ScreenKind kind) {
        int children = fixedContentHeight >= 0 ? fixedContentHeight : autoContentHeight(content);
        return children + 2 * insetY(kind);
    }

    private boolean canScroll(ScreenKind kind) {
        return contentHeight(kind) > height;
    }

    private int maxScroll(ScreenKind kind) {
        return Math.max(0, contentHeight(kind) - height);
    }

    /** The scroll in pixels, clamped to the current range. */
    private double scroll(ScreenKind kind) {
        return Math.max(0.0, Math.min(maxScroll(kind), scrollGet.getAsDouble()));
    }

    private double fraction(ScreenKind kind) {
        int max = maxScroll(kind);
        return max == 0 ? 0.0 : scroll(kind) / max;
    }

    private void setScroll(ScreenKind kind, double pixels) {
        scrollSet.accept(Math.max(0.0, Math.min(maxScroll(kind), pixels)));
    }

    private int handleTravel() {
        return height - SCROLLER_HEIGHT - 2 * SCROLLER_TRACK_PADDING;
    }

    private int handleOffset(ScreenKind kind) {
        if (!canScroll(kind) || handleTravel() <= 0) return SCROLLER_TRACK_PADDING;
        return SCROLLER_TRACK_PADDING + (int) (fraction(kind) * handleTravel());
    }

    /** Where the children's origin is: the viewport's top-left (inset in the band), moved up by the scroll. */
    private InputContext childInput(InputContext in) {
        ScreenKind k = in.kind();
        return in.at(in.originX() + childX + insetX(k), in.originY() + childY + insetY(k) - (int) scroll(k))
                .disabledIf(ownDisabled());
    }

    private RenderContext childRender(RenderContext ctx) {
        ScreenKind k = ctx.kind();
        return childContext(ctx.at(ctx.originX() + childX + insetX(k), ctx.originY() + childY + insetY(k) - (int) scroll(k)));
    }

    // ── PanelElement ───────────────────────────────────────────────────

    /** Column fill widens the viewport; floored so the scrollbar lane always fits. */
    @Override
    public void fillWidth(int width) {
        super.fillWidth(Math.max(width, TRACK_WIDTH + SCROLLER_GUTTER + 1));
    }

    /** A fixed viewport: the panel does not narrow it. */
    @Override
    public void layoutWithin(int budget) {}

    @Override public boolean isInteractive() { return true; }

    @Override
    public void render(RenderContext ctx) {
        var g = ctx.graphics();
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        boolean disabled = disabled(ctx);
        ScreenKind kind = ctx.kind();
        if (disabled) dragging = false;

        // The container's own tooltip first, so a hovered child's wins.
        queueTooltip(ctx);

        // A consumer-owned position past the end (the content got shorter) is
        // clamped when read; write the clamp back, so a later growth does not jump.
        double raw = scrollGet.getAsDouble();
        if (raw != scroll(kind)) scrollSet.accept(scroll(kind));

        // Follow a held handle with the mouse.
        if (dragging && canScroll(kind) && handleTravel() > 0 && ctx.hasMouseInput()) {
            double f = dragStartFraction + (ctx.mouseY() - dragStartMouseY) / handleTravel();
            setScroll(kind, f * maxScroll(kind));
        }

        // On an options screen: the list band behind the children, scrolling with them as
        // vanilla's does, and its separators just outside the top and bottom edges.
        boolean vanilla = kind == ScreenKind.OPTIONS;
        if (vanilla && band) {
            VanillaLook.listBackground(g, sx, sy, width, height, sx, sy + (int) scroll(kind));
            VanillaLook.headerSeparator(g, sx, sy - VanillaLook.SEPARATOR_HEIGHT, width);
            VanillaLook.footerSeparator(g, sx, sy + height, width);
        }

        // The children, clipped to the viewport, at their scrolled positions. A
        // child entirely outside the window is skipped, unless its popover is open
        // (the popover draws on the overlay pass from the same origin).
        g.enableScissor(sx, sy, sx + viewportWidth(), sy + height);
        RenderContext inner = childRender(ctx);
        int top = (int) scroll(kind) - insetY(kind);  // the window's top, in the children's coordinates
        InputContext probe = inner.input();
        for (PanelElement e : content) {
            if (!e.isVisible()) continue;
            boolean open = e.getActiveOverlayBounds(probe) != null;
            if (!open && (e.getChildY() + e.getHeight() < top || e.getChildY() >= top + height)) continue;
            e.render(inner);
        }
        g.disableScissor();

        if (vanilla) {
            // Vanilla's thin list scrollbar at the lane's right, the handle where
            // MenuKit's would be (same travel, same drag); none while everything fits.
            if (!canScroll(kind)) return;
            int barX = sx + width - VANILLA_SCROLLER_WIDTH;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, VANILLA_SCROLLER_TRACK, barX, sy, VANILLA_SCROLLER_WIDTH, height);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, VANILLA_SCROLLER, barX, sy + handleOffset(kind),
                    VANILLA_SCROLLER_WIDTH, SCROLLER_HEIGHT);
            return;
        }

        // The scrollbar: an inset track and vanilla's handle.
        int trackX = sx + width - TRACK_WIDTH;
        PanelRendering.renderInsetRect(g, trackX, sy, TRACK_WIDTH, height);
        Identifier sprite = !disabled && canScroll(kind) ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, trackX + SCROLLER_TRACK_PADDING,
                sy + handleOffset(kind), SCROLLER_WIDTH, SCROLLER_HEIGHT);
    }

    /** The children's overlays, unclipped, at their scrolled positions. */
    @Override
    public void renderOverlay(RenderContext ctx) {
        ChildDispatch.renderOverlay(content, childRender(ctx));
    }

    @Override
    public int @Nullable [] getActiveOverlayBounds(InputContext in) {
        return ChildDispatch.activeOverlay(content, childInput(in));
    }

    @Override
    public void notifyClickOutsideOverlay(InputContext in) {
        ChildDispatch.notifyClickOutside(content, childInput(in));
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        if (disabled(in)) return false;
        InputContext inner = childInput(in);
        // A child's open popover claims its whole area, even below the viewport.
        if (ChildDispatch.overlayOwner(content, inner) != null) return ChildDispatch.mouseClicked(content, inner, button);

        double lx = in.mouseX() - (in.originX() + childX);
        double ly = in.mouseY() - (in.originY() + childY);
        int trackLeft = width - TRACK_WIDTH;
        if (lx >= viewportWidth()) {
            // The gutter and the track eat their own clicks; a left press on the
            // handle starts a drag.
            ScreenKind kind = in.kind();
            if (button == Click.LEFT && lx >= trackLeft && canScroll(kind)
                    && ly >= handleOffset(kind) && ly < handleOffset(kind) + SCROLLER_HEIGHT) {
                dragging = true;
                dragStartMouseY = in.mouseY();
                dragStartFraction = fraction(kind);
            }
            return true;
        }
        return ChildDispatch.mouseClicked(content, inner, button);
    }

    @Override
    public boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        if (disabled(in)) return false;
        InputContext inner = childInput(in);
        if (ChildDispatch.overlayOwner(content, inner) != null) {
            return ChildDispatch.mouseScrolled(content, inner, scrollX, scrollY);
        }
        // A child that scrolls itself (a nested container) goes first, if the
        // mouse is inside the viewport.
        double lx = in.mouseX() - (in.originX() + childX);
        if (lx < viewportWidth() && ChildDispatch.mouseScrolled(content, inner, scrollX, scrollY)) return true;
        ScreenKind kind = in.kind();
        if (!canScroll(kind)) return false;
        setScroll(kind, scroll(kind) - scrollY * SCROLL_PIXELS_PER_TICK);
        return true;
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        if (button == Click.LEFT) dragging = false;
        ChildDispatch.mouseReleased(content, childInput(in), button);
        return false;
    }

    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        return ChildDispatch.keyPressed(content, childInput(in), keyCode, scanCode, modifiers);
    }

    @Override
    public void onAttach(net.minecraft.client.gui.screens.Screen screen) {
        ChildDispatch.attach(content, screen);
    }

    @Override
    public void onDetach(net.minecraft.client.gui.screens.Screen screen) {
        ChildDispatch.detach(content, screen);
    }

    // ── Builder ────────────────────────────────────────────────────────

    public static class Builder extends AbstractPanelElement.Builder<ScrollContainer, Builder> {
        private List<PanelElement> content = List.of();
        private int contentHeight = -1;
        private @Nullable DoubleSupplier scrollGet;
        private @Nullable DoubleConsumer scrollSet;
        private boolean band = true;

        protected Builder() {}

        /** No list band on an options screen: for a host that draws one around it (a Tabs body). */
        Builder bandless() {
            this.band = false;
            return this;
        }

        @Override protected Builder self() { return this; }

        /** Required: the outer size, scrollbar lane included. */
        @Override
        public Builder size(int width, int height) {
            int min = TRACK_WIDTH + SCROLLER_GUTTER + 1;
            if (width <= min) {
                throw new IllegalArgumentException("ScrollContainer width must be > " + min
                        + " (track + gutter + at least 1px of viewport)");
            }
            if (height <= 0) throw new IllegalArgumentException("ScrollContainer height must be > 0");
            return super.size(width, height);
        }

        /** Required: the children, positioned from the viewport's top-left. */
        public Builder content(List<? extends PanelElement> content) {
            this.content = List.copyOf(Objects.requireNonNull(content, "content"));
            return this;
        }

        /** The scrollable height, when not the children's extent (room past the end, say). */
        public Builder contentHeight(int pixels) {
            if (pixels < 0) throw new IllegalArgumentException("contentHeight must be >= 0, got " + pixels);
            this.contentHeight = pixels;
            return this;
        }

        /**
         * The consumer owns the scroll, in pixels from the top: {@code get} is read
         * every frame, {@code set} receives each change. Without it the scroll is
         * the container's own view state.
         */
        public Builder state(DoubleSupplier get, DoubleConsumer set) {
            this.scrollGet = Objects.requireNonNull(get, "get");
            this.scrollSet = Objects.requireNonNull(set, "set");
            return this;
        }

        @Override
        public ScrollContainer build() {
            require(width > 0 && height > 0, "size(w, h) is required");
            return new ScrollContainer(this);
        }
    }
}
