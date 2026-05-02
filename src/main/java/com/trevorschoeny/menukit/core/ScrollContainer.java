package com.trevorschoeny.menukit.core;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * A clipped viewport hosting pre-positioned {@link PanelElement}s with
 * vertical scroll input. The single-responsibility primitive for "show
 * content larger than its bounds with scroll input."
 *
 * <h3>Single-responsibility design</h3>
 *
 * ScrollContainer owns clipping + scroll offset + scrollbar UI + minimal
 * background. It does NOT compute layout, auto-size its bounds, or apply
 * padding — those are the consumer's job (compose layout via M8 helpers,
 * declare viewport dimensions explicitly, wrap in a styled Panel for
 * richer chrome).
 *
 * <p>Children are pre-positioned PanelElements with their {@code childX} /
 * {@code childY} relative to the viewport's content origin (top-left of
 * the scrollable area). ScrollContainer translates rendering by the
 * current scroll offset; children's positions are still "fixed at
 * construction" per Principle 4 — the translation is render-time only.
 *
 * <h3>The thesis reframe (M8 + scroll)</h3>
 *
 * Scroll requires runtime clipping; build-time positioned-element emission
 * (M8's helper-not-container test) can't deliver. The narrowed thesis:
 * <i>"Panel is the ceiling of layout composition. PanelElements may have
 * internal render dispatch over their own visuals OR over a viewport
 * showing externally-positioned children. The M8 helper-not-container
 * test applies to LAYOUT (build-time positioning); other runtime concerns
 * (clipping, viewport scrolling) are honest sub-passes within an Element."</i>
 * See {@code Design Docs/Elements/SCROLL_CONTAINER.md} §4.2 for the full
 * reframe.
 *
 * <h3>Scroll position state — Principle 8 lens pattern</h3>
 *
 * Scroll position is consumer state. The library reads via
 * {@link DoubleSupplier} (returning a normalized 0.0-to-1.0 value) and
 * notifies via {@link DoubleConsumer} on scroll events. Consumer mutates
 * their own state in the callback; library reads supplier next frame.
 * Out-of-range supplier values are silently clamped (matches ProgressBar
 * convention).
 *
 * <h3>Scroll input — v1 sources</h3>
 *
 * <ul>
 *   <li><b>Mouse wheel</b> within the viewport bounds. Updates offset by
 *       a fixed pixel amount per "tick" of wheel input.</li>
 *   <li><b>Click-and-drag on the scrollbar handle.</b> Click starts drag;
 *       drag updates offset proportionally to mouse Y delta. Drag end
 *       is detected by polling the mouse-button state per frame
 *       (avoids requiring {@code mouseDragged}/{@code mouseReleased}
 *       additions to {@link PanelElement} for v1).</li>
 * </ul>
 *
 * Click on items inside the viewport dispatches to those items normally.
 * Click on the scrollbar handle starts drag instead of dispatching.
 *
 * <h3>Modal-aware</h3>
 *
 * When a modal dialog is up and the cursor is inside the modal, scroll
 * inside the modal reaches the modal's elements (so a ScrollContainer
 * inside a modal works). When the cursor is outside the modal, scroll is
 * eaten by the modal mechanism. See
 * {@code Design Docs/Elements/SCROLL_CONTAINER.md} §4.9 for the dispatch
 * details.
 *
 * @see PanelElement The interface this implements
 */
public class ScrollContainer implements PanelElement {

    /** Width of the scrollbar handle sprite, in pixels. Matches vanilla
     *  {@code CreativeModeInventoryScreen.SCROLLER_WIDTH}. */
    public static final int SCROLLER_WIDTH = 12;

    /** Height of the scrollbar handle sprite, in pixels. Matches vanilla
     *  {@code CreativeModeInventoryScreen.SCROLLER_HEIGHT} — vanilla uses
     *  a FIXED-size handle (not proportional to content), positioned along
     *  the track based on scroll offset. We adopt the same convention for
     *  visual parity with creative inventory's scrollbar. */
    public static final int SCROLLER_HEIGHT = 15;

    /** Vanilla scroller-enabled sprite — same identifier
     *  {@code CreativeModeInventoryScreen.SCROLLER_SPRITE} uses. */
    public static final Identifier SCROLLER_SPRITE =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller");

    /** Vanilla scroller-disabled sprite (used when content fits viewport). */
    public static final Identifier SCROLLER_DISABLED_SPRITE =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    /** Pixels of gutter between viewport content and the scrollbar
     *  track. Visual spacing so the track doesn't butt directly against
     *  content. Library-defined; consumers who use {@link #viewportWidthFor(int)}
     *  get correctly-sized content automatically. */
    public static final int SCROLLER_GUTTER = 4;

    /** Pixels of padding inside the track around the handle. The track
     *  is wider/taller than the handle by this amount on each side, so
     *  the handle sits inside a slightly larger recessed area —
     *  matches vanilla creative inventory's scrollbar appearance. */
    public static final int SCROLLER_TRACK_PADDING = 1;

    /** Total scrollbar track width = handle width + 2× track padding. */
    public static final int TRACK_WIDTH = SCROLLER_WIDTH + 2 * SCROLLER_TRACK_PADDING;

    /** Pixels scrolled per mouse-wheel tick. Tuned for typical content. */
    public static final int SCROLL_PIXELS_PER_TICK = 10;

    private final int childX;
    private final int childY;
    private final int width;
    private final int height;
    private final List<PanelElement> content;
    private final int contentHeight;
    private final DoubleSupplier scrollOffsetSupplier;
    private final @Nullable DoubleConsumer onScrollOffsetChanged;

    /** Drag state — internal, mutable. {@code true} while user is dragging the
     *  scrollbar handle. Polled per-frame in render to detect drag end. */
    private boolean draggingHandle = false;
    /** Mouse Y at drag start, screen-space. */
    private double dragStartMouseY = 0;
    /** Scroll offset (normalized) at drag start. */
    private double dragStartScrollOffset = 0;

    /** Begins a builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the viewport width (the area children render into) for a
     * ScrollContainer of the given outer width. Consumers use this when
     * sizing content elements to fit inside the viewport without being
     * clipped by the scissor — typical pattern:
     *
     * <pre>{@code
     * int viewport = ScrollContainer.viewportWidthFor(panelWidth);
     * var col = Column.at(0, 0).spacing(2);
     * for (var item : items) {
     *     col = col.add(Button.spec(viewport, 20, item.label(), item.onClick()));
     * }
     * }</pre>
     *
     * Avoids consumers having to remember the {@code TRACK_WIDTH +
     * SCROLLER_GUTTER} subtraction themselves.
     */
    public static int viewportWidthFor(int outerWidth) {
        return outerWidth - TRACK_WIDTH - SCROLLER_GUTTER;
    }

    private ScrollContainer(int childX, int childY, int width, int height,
                             List<PanelElement> content, int contentHeight,
                             DoubleSupplier scrollOffsetSupplier,
                             @Nullable DoubleConsumer onScrollOffsetChanged) {
        this.childX = childX;
        this.childY = childY;
        this.width = width;
        this.height = height;
        this.content = List.copyOf(content);
        this.contentHeight = contentHeight;
        this.scrollOffsetSupplier = scrollOffsetSupplier;
        this.onScrollOffsetChanged = onScrollOffsetChanged;
    }

    // ── Geometry helpers ─────────────────────────────────────────────────

    /** Viewport width — element width minus the scrollbar lane (gutter +
     *  track). The track is wider than the handle by 2× track padding.
     *  Viewport right edge is at {@code sx + viewportWidth()}; the gutter
     *  occupies {@code [viewportWidth, viewportWidth + GUTTER)}; the
     *  track occupies the rightmost {@code TRACK_WIDTH} pixels. */
    private int viewportWidth() {
        return width - TRACK_WIDTH - SCROLLER_GUTTER;
    }

    /** Viewport height — full height (scrollbar runs full vertical). */
    private int viewportHeight() {
        return height;
    }

    /** Whether content overflows the viewport (i.e., scrolling is meaningful). */
    private boolean canScroll() {
        return contentHeight > viewportHeight();
    }

    /** Maximum scroll distance in pixels. Zero when content fits. */
    private int maxScrollPixels() {
        return Math.max(0, contentHeight - viewportHeight());
    }

    /** Current scroll offset clamped to [0, 1]. */
    private double scrollOffset() {
        double raw = scrollOffsetSupplier.getAsDouble();
        return Math.max(0.0, Math.min(1.0, raw));
    }

    /** Current scroll position in pixels (offset × maxScrollPixels). */
    private int scrollPixels() {
        return (int) (scrollOffset() * maxScrollPixels());
    }

    /** Scrollbar handle height — fixed-size matching vanilla's
     *  {@code SCROLLER_HEIGHT} convention. */
    private int handleHeight() {
        return SCROLLER_HEIGHT;
    }

    /** Scrollbar handle Y offset (relative to scrollbar top), in pixels.
     *  Track height = element height; usable handle range = element height
     *  minus handle height minus 2× track padding (1px from track top,
     *  1px from track bottom). Offset is scrolled fraction of that range. */
    private int handleYOffset() {
        if (!canScroll()) return SCROLLER_TRACK_PADDING;
        int handleRange = height - SCROLLER_HEIGHT - 2 * SCROLLER_TRACK_PADDING;
        if (handleRange <= 0) return SCROLLER_TRACK_PADDING;
        return SCROLLER_TRACK_PADDING + (int) (scrollOffset() * handleRange);
    }

    /** Notifies the consumer of a new scroll offset (clamped). */
    private void notifyOffset(double newOffset) {
        if (onScrollOffsetChanged == null) return;
        double clamped = Math.max(0.0, Math.min(1.0, newOffset));
        onScrollOffsetChanged.accept(clamped);
    }

    // ── PanelElement Implementation ───────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth()  { return width; }
    @Override public int getHeight() { return height; }

    @Override
    public void render(RenderContext ctx) {
        var graphics = ctx.graphics();
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;

        // Cache the screen-space origin so mouseClicked can compute
        // element-relative coords (mouseClicked doesn't get RenderContext).
        cacheRenderOrigin(sx, sy);

        // Per-frame drag offset update. While draggingHandle is true,
        // sample the current mouse Y from the RenderContext and update
        // scroll offset proportional to drag distance. Drag end is
        // signaled by mouseReleased() — set draggingHandle=false there.
        // Drag range matches the handleYOffset math: full handle range
        // is the track height minus handle height minus 2× track padding.
        if (draggingHandle && canScroll()) {
            int handleRange = height - SCROLLER_HEIGHT - 2 * SCROLLER_TRACK_PADDING;
            if (handleRange > 0) {
                double deltaY = ctx.mouseY() - dragStartMouseY;
                double newOffset = dragStartScrollOffset + (deltaY / handleRange);
                notifyOffset(newOffset);
            }
        }

        // 1. Render minimal background — a 1px frame around the viewport.
        // Consumer who wants richer chrome wraps the ScrollContainer in a
        // styled Panel. The frame here is observational ("here's where
        // the scroll region is"), not stylistic.
        // (Skipped in v1: just render content directly. Frame can fold
        // post-evidence if smoke shows the viewport boundary is unclear.)

        // 2. Enable scissor — clip subsequent draws to the viewport.
        // Vanilla pattern: enableScissor takes (x1, y1, x2, y2) inclusive
        // top-left and exclusive bottom-right.
        graphics.enableScissor(sx, sy, sx + viewportWidth(), sy + viewportHeight());

        // 3. Render each child with translation by current scroll offset.
        // The child's childX/childY remain "fixed at construction"
        // (Principle 4); we translate the rendering origin instead.
        int scrollY = scrollPixels();
        RenderContext childCtx = new RenderContext(
                graphics, sx, sy - scrollY, ctx.mouseX(), ctx.mouseY());
        for (PanelElement element : content) {
            if (!element.isVisible()) continue;
            // Skip elements entirely outside the visible scroll window for
            // a small render-cost saving on large content lists. Element
            // is visible if its Y range intersects [scrollY, scrollY+viewportHeight].
            int top = element.getChildY();
            int bottom = top + element.getHeight();
            if (bottom < scrollY) continue;
            if (top >= scrollY + viewportHeight()) continue;
            element.render(childCtx);
        }

        // 4. Disable scissor before drawing the scrollbar.
        graphics.disableScissor();

        // 5. Render scrollbar — inset track + handle. Track is the
        // rightmost TRACK_WIDTH pixels, full element height. Handle is
        // SCROLLER_WIDTH × SCROLLER_HEIGHT, positioned 1px inside the
        // track on each side (so the inset shows around the handle).
        // Slot-style inset (subtle 1px shadow/highlight + gray fill)
        // matches vanilla's scrollbar track exactly — heavier
        // PanelStyle.INSET would frame too aggressively.
        int trackX = sx + width - TRACK_WIDTH;
        PanelRendering.renderInsetRect(graphics, trackX, sy,
                TRACK_WIDTH, height);

        // Handle on top of the inset track, padded inside.
        int handleX = trackX + SCROLLER_TRACK_PADDING;
        int handleY = sy + handleYOffset();
        int hHeight = handleHeight();
        Identifier sprite = canScroll() ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                sprite, handleX, handleY, SCROLLER_WIDTH, hHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false; // Left-click only for v1.

        // Compute screen-space bounds of the viewport (no offset translation
        // here — render context isn't available at click time, so we rely
        // on the adapter's hit-test having already determined cursor is
        // inside our element bounds).

        // Note: PanelElement.mouseClicked is dispatched by the adapter
        // after hit-testing the element's getChildX/Y/Width/Height. So
        // (mouseX, mouseY) is guaranteed inside our element bounds. We
        // re-test for sub-region (scrollbar vs viewport) using the
        // element's known position, but we don't have RenderContext
        // available here for screen-space coords. Use mouseX/mouseY
        // directly — they're already in screen-space per
        // PanelElement.mouseClicked's coord-space contract.

        // Hit-test the scrollbar gutter via screen-space comparison.
        // We need to know our element's screen-space top-left, but we
        // only have childX/childY (panel-local). The adapter's hit-test
        // already verified mouseX/Y is inside [contentX + childX,
        // contentX + childX + width] etc. — we just don't know
        // contentX/Y here. Instead, hit-test using the screen-space
        // mouseX/Y against a bounding box that covers the scrollbar
        // sub-region. Computed as: mouseX is in the rightmost
        // SCROLLER_WIDTH pixels of our element (regardless of where in
        // screen space we are).
        //
        // Trick: the adapter's hit-test ensured mouseX is inside
        // [contentX+childX, contentX+childX+width). The scrollbar gutter
        // is the rightmost SCROLLER_WIDTH of that. So mouseX is in the
        // scrollbar gutter iff (mouseX - elementLeftEdgeScreenSpace) >=
        // viewportWidth. We don't have elementLeftEdgeScreenSpace
        // directly, but mouseX - mouseX_within_element = elementLeftEdge.
        // The simpler approach: track (mouseX, mouseY) relative to our
        // element, given the adapter passed real screen coords. Compute
        // from element's internal-relative position, where the adapter
        // has guaranteed mouse is inside [0, width) × [0, height).
        //
        // Simpler still: ask the question "is the cursor in the scrollbar
        // region of our element?" via the cursor position relative to our
        // screen-space top-left. We don't know our screen-space top-left
        // at click time; we compute it from the parent screen via
        // Minecraft.getInstance().screen. Hmm, that's also complex.
        //
        // PRAGMATIC v1 ANSWER: store the most-recent screen-space top-left
        // computed in render(). render runs every frame; click happens
        // shortly after. The cached origin is fresh.

        // (Track most-recent screen-space origin from render; defer logic
        //  via the cached field below.)
        if (cachedRenderOriginValid) {
            double localX = mouseX - cachedRenderOriginX;
            double localY = mouseY - cachedRenderOriginY;
            // Scrollbar track region: rightmost TRACK_WIDTH pixels.
            // The gutter pixels between viewport and track are inert
            // (consumed silently — clicks there don't fall through).
            int trackLeftEdge = width - TRACK_WIDTH;
            if (localX >= trackLeftEdge && localX < width
                    && localY >= 0 && localY < height) {
                // Click in track region. If on the handle (which is padded
                // inside the track), start drag. Otherwise consume silently.
                int handleTop = handleYOffset();
                int handleBot = handleTop + SCROLLER_HEIGHT;
                if (canScroll() && localY >= handleTop && localY < handleBot) {
                    draggingHandle = true;
                    dragStartMouseY = mouseY;
                    dragStartScrollOffset = scrollOffset();
                    return true;
                }
                // Click in scrollbar track (above or below handle, or
                // inside track padding) — defer jump-scroll for now;
                // consume the click.
                return true;
            }
            // Click in gutter — inert. Consume silently so it doesn't fall
            // through to vanilla.
            if (localX >= viewportWidth() && localX < trackLeftEdge
                    && localY >= 0 && localY < height) {
                return true;
            }
            // Click in viewport — dispatch to children at scroll-translated coords.
            if (localX >= 0 && localX < viewportWidth()
                    && localY >= 0 && localY < viewportHeight()) {
                // Translate mouseY back to content-space: virtual content Y
                // is the actual mouseY plus the scrolled amount.
                double contentMouseX = mouseX;
                double contentMouseY = mouseY + scrollPixels();
                for (PanelElement element : content) {
                    if (!element.isVisible()) continue;
                    int childAbsX = (int) cachedRenderOriginX + element.getChildX();
                    int childAbsY = (int) cachedRenderOriginY + element.getChildY();
                    if (contentMouseX < childAbsX
                            || contentMouseX >= childAbsX + element.getWidth()) continue;
                    if (contentMouseY < childAbsY
                            || contentMouseY >= childAbsY + element.getHeight()) continue;
                    if (element.mouseClicked(contentMouseX, contentMouseY, button)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        if (!canScroll()) return false;
        // Wheel up (scrollY > 0) scrolls content up (offset decreases).
        // Wheel down (scrollY < 0) scrolls content down (offset increases).
        int max = maxScrollPixels();
        if (max == 0) return false;
        double delta = -scrollY * SCROLL_PIXELS_PER_TICK / (double) max;
        notifyOffset(scrollOffset() + delta);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // End scrollbar drag on any mouse release. Adapter dispatches
        // release to all elements regardless of cursor position, so this
        // fires even when the user dragged the cursor off the element.
        if (draggingHandle && button == 0) {
            draggingHandle = false;
        }
        return false; // Don't claim consumption — release is observational.
    }

    // ── Render-origin cache (used for click hit-testing) ──────────────────
    //
    // The mouseClicked hook receives screen-space coords but doesn't have a
    // RenderContext (no graphics + origin). render() does. We cache the
    // screen-space top-left from the most-recent render frame, so click
    // dispatch can compute element-relative coords without recomputing
    // adapter origin. Slightly hacky but bounded — render runs each frame
    // before any click; cache is at most one frame stale.

    private double cachedRenderOriginX = 0;
    private double cachedRenderOriginY = 0;
    private boolean cachedRenderOriginValid = false;

    /** Updates the render-origin cache. Called from render(); only valid
     *  after the first frame this element renders. */
    private void cacheRenderOrigin(int sx, int sy) {
        cachedRenderOriginX = sx;
        cachedRenderOriginY = sy;
        cachedRenderOriginValid = true;
    }

    // ── Builder ───────────────────────────────────────────────────────────

    /** Builder for ScrollContainer. */
    public static final class Builder {
        private int childX = 0;
        private int childY = 0;
        private int width = -1;
        private int height = -1;
        private @Nullable List<PanelElement> content = null;
        private int contentHeightOverride = -1;
        private @Nullable DoubleSupplier scrollOffsetSupplier = null;
        private @Nullable DoubleConsumer onScrollOffsetChanged = null;

        Builder() {}

        /** Sets the panel-local origin for this ScrollContainer. */
        public Builder at(int childX, int childY) {
            this.childX = childX;
            this.childY = childY;
            return this;
        }

        /** Sets the viewport dimensions (full width includes scrollbar gutter). */
        public Builder size(int width, int height) {
            int minWidth = TRACK_WIDTH + SCROLLER_GUTTER + 1; // gutter + track + 1px viewport
            if (width <= minWidth) {
                throw new IllegalArgumentException(
                        "ScrollContainer width must be > " + minWidth +
                        " (track + gutter + at least 1px viewport)");
            }
            if (height <= 0) {
                throw new IllegalArgumentException("ScrollContainer height must be > 0");
            }
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Sets the pre-positioned content list. Children's
         * {@code childX} / {@code childY} are relative to the viewport's
         * top-left (the scrollable area's content origin). Required.
         *
         * <p>Default {@link #contentHeight(int)} is auto-computed from
         * {@code max(child.childY + child.height)} unless explicitly
         * overridden.
         */
        public Builder content(List<PanelElement> content) {
            this.content = Objects.requireNonNull(content, "content must not be null");
            return this;
        }

        /**
         * Explicit override for the total content height. By default,
         * ScrollContainer auto-computes content height from the children
         * passed to {@link #content}. Override when you want a different
         * value — typically: trailing padding for comfortable scroll-past-end,
         * capped scroll extent smaller than children would give, or
         * supplier-driven content where auto-compute can't see the real
         * size.
         */
        public Builder contentHeight(int contentHeight) {
            if (contentHeight < 0) {
                throw new IllegalArgumentException(
                        "contentHeight must be >= 0, got " + contentHeight);
            }
            this.contentHeightOverride = contentHeight;
            return this;
        }

        /**
         * Sets the scroll position state via a supplier (read each frame)
         * and an optional callback (fired on scroll events). Required.
         *
         * <p>Per Principle 8 (lens not store): consumer holds the state,
         * library reads via the supplier and notifies via the callback.
         * Pass {@code null} for the callback if you want a read-only
         * scroll display (no scroll input is consumed).
         */
        public Builder scrollOffset(DoubleSupplier supplier,
                                     @Nullable DoubleConsumer callback) {
            this.scrollOffsetSupplier = Objects.requireNonNull(supplier,
                    "scrollOffset supplier must not be null");
            this.onScrollOffsetChanged = callback;
            return this;
        }

        /** Builds the configured ScrollContainer. */
        public ScrollContainer build() {
            if (width < 0 || height < 0) {
                throw new IllegalStateException(
                        "ScrollContainer.Builder: size(width, height) must be set before build()");
            }
            if (content == null) {
                throw new IllegalStateException(
                        "ScrollContainer.Builder: content(...) must be set before build()");
            }
            if (scrollOffsetSupplier == null) {
                throw new IllegalStateException(
                        "ScrollContainer.Builder: scrollOffset(supplier, callback) must be set before build()");
            }
            int finalContentHeight = (contentHeightOverride >= 0)
                    ? contentHeightOverride
                    : autoComputeContentHeight(content);
            return new ScrollContainer(childX, childY, width, height,
                    content, finalContentHeight,
                    scrollOffsetSupplier, onScrollOffsetChanged);
        }

        /** Auto-computes content height from max(childY + height) over children. */
        private static int autoComputeContentHeight(List<PanelElement> content) {
            int max = 0;
            for (PanelElement el : content) {
                int bottom = el.getChildY() + el.getHeight();
                if (bottom > max) max = bottom;
            }
            return max;
        }
    }
}
