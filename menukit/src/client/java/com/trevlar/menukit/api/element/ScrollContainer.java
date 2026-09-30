package com.trevlar.menukit.api.element;

import com.trevlar.menukit.core.PanelRendering;
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

    private final List<PanelElement> content;
    private final int contentHeight;
    private final DoubleSupplier scrollGet;
    private final DoubleConsumer scrollSet;

    /** The scroll, in pixels, when the consumer does not own it (view state). */
    private double ownScroll = 0;

    // The handle drag: interaction state from press to release, not a frame cache.
    private boolean dragging = false;
    private double dragStartMouseY = 0;
    private double dragStartFraction = 0;

    protected ScrollContainer(Builder b) {
        super(b);
        this.content = List.copyOf(b.content);
        this.contentHeight = b.contentHeight >= 0 ? b.contentHeight : autoContentHeight(content);
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

    /** The content width that fits a container {@code outerWidth} wide: its width minus the scrollbar lane. */
    public static int viewportWidthFor(int outerWidth) {
        return outerWidth - TRACK_WIDTH - SCROLLER_GUTTER;
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

    private boolean canScroll() {
        return contentHeight > height;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - height);
    }

    /** The scroll in pixels, clamped to the current range. */
    private double scroll() {
        return Math.max(0.0, Math.min(maxScroll(), scrollGet.getAsDouble()));
    }

    private double fraction() {
        int max = maxScroll();
        return max == 0 ? 0.0 : scroll() / max;
    }

    private void setScroll(double pixels) {
        scrollSet.accept(Math.max(0.0, Math.min(maxScroll(), pixels)));
    }

    private int handleTravel() {
        return height - SCROLLER_HEIGHT - 2 * SCROLLER_TRACK_PADDING;
    }

    private int handleOffset() {
        if (!canScroll() || handleTravel() <= 0) return SCROLLER_TRACK_PADDING;
        return SCROLLER_TRACK_PADDING + (int) (fraction() * handleTravel());
    }

    /** Where the children's origin is: the viewport's top-left, moved up by the scroll. */
    private InputContext childInput(InputContext in) {
        return in.at(in.originX() + childX, in.originY() + childY - (int) scroll()).disabledIf(ownDisabled());
    }

    private RenderContext childRender(RenderContext ctx) {
        return ctx.at(ctx.originX() + childX, ctx.originY() + childY - (int) scroll()).disabledIf(ownDisabled());
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
        if (disabled) dragging = false;

        // The container's own tooltip first, so a hovered child's wins.
        queueTooltip(ctx);

        // A consumer-owned position past the end (the content got shorter) is
        // clamped when read; write the clamp back, so a later growth does not jump.
        double raw = scrollGet.getAsDouble();
        if (raw != scroll()) scrollSet.accept(scroll());

        // Follow a held handle with the mouse.
        if (dragging && canScroll() && handleTravel() > 0 && ctx.hasMouseInput()) {
            double f = dragStartFraction + (ctx.mouseY() - dragStartMouseY) / handleTravel();
            setScroll(f * maxScroll());
        }

        // The children, clipped to the viewport, at their scrolled positions. A
        // child entirely outside the window is skipped, unless its popover is open
        // (the popover draws on the overlay pass from the same origin).
        g.enableScissor(sx, sy, sx + viewportWidth(), sy + height);
        RenderContext inner = childRender(ctx);
        int top = (int) scroll();
        InputContext probe = inner.input();
        for (PanelElement e : content) {
            if (!e.isVisible()) continue;
            boolean open = e.getActiveOverlayBounds(probe) != null;
            if (!open && (e.getChildY() + e.getHeight() < top || e.getChildY() >= top + height)) continue;
            e.render(inner);
        }
        g.disableScissor();

        // The scrollbar: an inset track and vanilla's handle.
        int trackX = sx + width - TRACK_WIDTH;
        PanelRendering.renderInsetRect(g, trackX, sy, TRACK_WIDTH, height);
        Identifier sprite = !disabled && canScroll() ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, trackX + SCROLLER_TRACK_PADDING,
                sy + handleOffset(), SCROLLER_WIDTH, SCROLLER_HEIGHT);
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
            if (button == Click.LEFT && lx >= trackLeft && canScroll()
                    && ly >= handleOffset() && ly < handleOffset() + SCROLLER_HEIGHT) {
                dragging = true;
                dragStartMouseY = in.mouseY();
                dragStartFraction = fraction();
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
        if (!canScroll()) return false;
        setScroll(scroll() - scrollY * SCROLL_PIXELS_PER_TICK);
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

        protected Builder() {}

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
