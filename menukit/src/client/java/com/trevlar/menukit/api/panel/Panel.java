package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.MKTooltip;
import com.trevlar.menukit.core.RegionMath;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.element.Tabs;
import com.trevlar.menukit.api.element.TextLabel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The basic unit of composition. Every element in MenuKit lives inside a Panel,
 * because the panel is what shows and hides, and showing and hiding is what makes
 * menus dynamic.
 *
 * <p>A Panel holds an ordered list of {@link PanelElement}s (buttons, text labels,
 * anything implementing the interface), plus a style, a position, a toggle key and
 * a visibility flag. Visibility can change; the element list is fixed once built.
 *
 * <p>The same Panel type is used in inventory menus, on the HUD and on standalone
 * screens. What is specific to each (slot groups, HUD anchoring, a standalone
 * screen's lifecycle) lives in whatever holds the panel, not in the panel.
 *
 * <p>A Panel is client-only: it is how a panel looks. On a MenuKit: Containers
 * custom menu the server's half of a panel (its id, its slot groups, whether it is
 * shown) belongs to the menu; the screen builds a Panel for each and shows it while
 * the server says so.
 *
 * <h3>Visibility: imperative or supplier-driven</h3>
 *
 * Panel visibility can be controlled two ways:
 * <ul>
 *   <li><b>Imperative</b> via {@link #setVisible(boolean)}. The Panel holds
 *       the boolean.</li>
 *   <li><b>Supplier-driven</b> via {@link #visibleWhen(java.util.function.BooleanSupplier)}. Consumer holds
 *       the state; Panel reads via the supplier on each {@code isVisible()}
 *       call. The usual choice for injected panels, HUDs and standalone
 *       screens, like a {@code Toggle}'s {@code state(get, set)}.</li>
 * </ul>
 * Only one applies at a time; see {@link #visibleWhen(java.util.function.BooleanSupplier)}
 * for which wins.
 */
public class Panel {

    // Interior padding: the room the screen (MKScreen, CustomContainerScreen,
    // ScreenPanelAdapter) leaves between the panel background and its elements.
    // INTERIOR_PADDING is the value for styled panels (RAISED, DARK, INSET), which
    // need space between the visible frame and the elements. A NONE panel has no
    // frame, so interiorPadding() returns 0 for it. Rendering code calls
    // interiorPadding(); the constant is for mods that want the styled value.
    public static final int INTERIOR_PADDING = 7;

    private final String id;
    private final List<PanelElement> elements;
    private final PanelStyle style;
    private final PanelPosition position;
    private final int toggleKey; // InputConstants.KEY_* code that toggles visibility, or -1 for none
    private boolean visible;

    /**
     * Optional tooltip for the whole panel, shown while the cursor is over the
     * panel's outer bounds whichever child is hovered. For tooltips on specific
     * children, put them on those children. When both queue, the last call wins
     * (vanilla's {@code setTooltipForNextFrame}), and the panel tooltip is queued
     * after the elements, so it wins. See {@link #maybeQueueTooltip}.
     */
    private @Nullable Supplier<Component> tooltipSupplier;

    // Size pinning. When pinnedWidth >= 0, getWidth() returns it whatever the
    // elements' visibility; the same for pinnedHeight. For panels whose elements
    // come and go, so their size doesn't drop to zero and shift the panels stacked
    // after them in a region.
    //
    // Pinned sizes also turn on wrapping and scrolling:
    //   - pinnedWidth set: every child TextLabel wraps to the content width
    //     (pinnedWidth - 2 * INTERIOR_PADDING, less the scrollbar when
    //     pinnedHeight is also set).
    //   - pinnedHeight set and the content taller than it: getElements() returns
    //     one internal ScrollContainer around the elements, with wheel scrolling
    //     and a scrollbar.
    // Both always apply to a bounded panel; there is no flag to turn them off.
    private int pinnedWidth = -1;
    private int pinnedHeight = -1;

    // Wrapping at the screen edge. effectiveContentWidth is the most content width
    // the screen leaves the panel, set every frame by the placement code
    // (PanelHost) through setAvailableContentWidth. -1 means no limit: the panel
    // grows to its content. It is kept apart from pinnedWidth so a limit MenuKit
    // computed never looks like a size the mod pinned (getPinnedWidth() stays
    // accurate for MKC's pinned-versus-auto layout).
    //
    // The limit only applies to an unpinned panel whose content is wider than it;
    // then TextLabels wrap to it, as they do for a pinned width. lastFitAvail
    // makes setAvailableContentWidth mark the configuration dirty only when the
    // width actually changes, so the per-frame calls cost nothing and the
    // auto-scroll ScrollContainer isn't rebuilt every frame (which would reset
    // its drag state).
    private int effectiveContentWidth = -1;
    private int lastFitAvail = Integer.MIN_VALUE;

    // Scrolling at the screen edge: the height counterpart of
    // effectiveContentWidth, set every frame by PanelHost through
    // setAvailableContentHeight; -1 means no limit. An unpinned panel whose content
    // is taller than the limit scrolls within it instead of running off the
    // screen, using the same ScrollContainer as pinnedHeight. Content that fits
    // changes nothing (no scrolling, no scrollbar room). lastFitAvailHeight works
    // like lastFitAvail.
    private int effectiveContentHeight = -1;
    private int lastFitAvailHeight = Integer.MIN_VALUE;

    // The frame band (6.1.0): the content height from the panel's top down to where
    // the frame beside it ends, fed per frame by the host for a panel in a side
    // region (setFrameHeight). -1 = no frame beside the panel. Handed down to every
    // element as its wrap height (PanelElement.wrapHeight), so a vertical Flow
    // wraps into columns at the frame's bottom. Distinct from the height ceiling
    // above on purpose: the ceiling is where the panel scrolls (the screen's edge),
    // the band is where a column wraps (the frame's edge).
    private int frameHeight = -1;

    // The kind of screen the panel is on, fed by its host before each layout
    // (setScreenKind). Only the auto-scroll path reads it: on an options screen the
    // wrapping ScrollContainer is the list band, which insets its content, so the
    // elements are laid out that much narrower.
    private ScreenKind screenKind = ScreenKind.CONTAINER;

    // Auto-scroll position, in pixels from the top. Pixels rather than a fraction,
    // so what is on screen stays put when the content changes height
    // (ScrollContainer.Builder.state is in pixels). The panel keeps it because the
    // inner ScrollContainer is internal; the mod never sees it. Updated by the
    // ScrollContainer's callback when the player scrolls.
    private double scrollOffset = 0.0;

    // The internal ScrollContainer for auto-scroll. Built on the first
    // getElements() call once the content overflows, then reused across frames so
    // its drag state and cached render origin stay stable. Rebuilt only when the
    // configuration changes.
    private @Nullable ScrollContainer cachedScrollContainer;

    // Whether the configuration pass (handing down wrap widths, building the
    // scroll container) needs to run again. It is cheap, but skipping it when
    // nothing changed saves a walk over the elements every frame.
    private boolean configurationDirty = true;

    // A signature of the elements' sizes at the last configuration pass. When an
    // element changes size by itself (a SlotFlowElement growing rows when a slot
    // is revealed, say), the new signature differs and forces a reconfigure.
    // MIN_VALUE until the first pass, so the first ensureConfigured always runs.
    private int lastLayoutSig = Integer.MIN_VALUE;

    // Supplier-driven visibility. When non-null it wins over the `visible` field:
    // isVisible() reads the supplier and setVisible(...) does nothing. Clear it
    // with visibleWhen(null) to go back to setVisible. The mod holds the state and
    // the panel reads it, like a Toggle's state lens. A BooleanSupplier, the same
    // type as the elements' visibleWhen, disabledWhen and revealWhen.
    private @Nullable BooleanSupplier visibilitySupplier;

    // Disabling the whole panel. While it holds, every element draws its disabled
    // look and takes no clicks, wheel or keys: the host gives the elements
    // disabled contexts. The panel is still there and still claims its area; only
    // what is inside is inert.
    private @Nullable BooleanSupplier disabledSupplier;

    // Three independent flags make up "modal", so other kinds of panel (popovers,
    // dropdowns) can take only the parts they need.
    //
    // - `opaque` (default true): while the panel is visible, input inside its
    //   bounds goes to the panel and vanilla underneath never sees it, so clicks
    //   never pass through a visible panel. Transparent overlays opt out with
    //   `opaque(false)`.
    //
    // - `dimsBehind` (default false): while the panel is visible, the LayerPlan
    //   draws a translucent black fill over the screen before drawing it. Real
    //   modals set it; other opaque panels (decoration, popups) don't.
    //
    // - `tracksAsModal` (default false): while the panel is visible, MenuKit locks
    //   the cursor shape (no clickable feedback over vanilla widgets) and eats
    //   keys other than Escape. Real modals set it; other opaque panels leave the
    //   cursor and keyboard working normally outside their bounds.
    //
    // Panel.modal() sets all three. The separate setters are for other
    // combinations (popovers, click blockers and so on).
    private boolean opaque = true;
    private boolean dimsBehind = false;
    private boolean tracksAsModal = false;

    // Popover layer (6.1.0), declared once on the builder (Builder.popover()). A
    // popover panel draws, and claims, above every ordinary panel of every host on
    // the screen, below the modal overlay: the LayerPlan's POPOVER layer.
    private boolean popover = false;

    // Optional Escape action. When this is the topmost visible tracksAsModal panel
    // and the player presses Escape, the host (MKScreen, or ScreenPanelRegistry's
    // key listener for injected panels) runs it to dismiss the modal instead of
    // closing the screen under an open dialog. ConfirmDialog and AlertDialog put
    // their onCancel / onAcknowledge here. With none set, the host still eats
    // Escape while a modal is up so it can't close the screen.
    private @Nullable Runnable escapeAction;

    /**
     * The full constructor. Internal: build panels with {@link #builder(String)},
     * which names only the fields you set and hides the {@code -1} no-toggle-key
     * value behind {@link Builder#toggleKey(int)}. It stays public only so the
     * builder and MenuKit's other packages can reach it.
     *
     * @param id        unique identifier within the screen
     * @param elements  panel elements, buttons, text labels, etc. (immutable after construction)
     * @param visible   initial visibility state
     * @param style     visual style for panel background rendering
     * @param position  how this panel is positioned in the layout
     * @param toggleKey key code ({@code InputConstants.KEY_*}) that toggles this panel's visibility, or -1 for none
     */
    @ApiStatus.Internal
    public Panel(String id, List<PanelElement> elements,
                 boolean visible, PanelStyle style, PanelPosition position,
                 int toggleKey) {
        this.id = id;
        this.elements = List.copyOf(elements);
        this.visible = visible;
        this.style = style;
        this.position = position;
        this.toggleKey = toggleKey;
    }

    /**
     * Default style (RAISED), no position ({@link PanelPosition#UNPLACED}), no
     * toggle key. Internal; use {@link #builder(String)}.
     */
    @ApiStatus.Internal
    public Panel(String id, List<PanelElement> elements, boolean visible) {
        this(id, elements, visible, PanelStyle.RAISED, PanelPosition.UNPLACED, -1);
    }

    /**
     * A visible panel with the default style and position. Internal; use
     * {@link #builder(String)}.
     */
    @ApiStatus.Internal
    public Panel(String id, List<PanelElement> elements) {
        this(id, elements, true);
    }

    // Builder

    // "No toggle key". Callers leave Builder.toggleKey unset instead of passing -1.
    static final int NO_TOGGLE_KEY = -1;

    /**
     * Starts a builder for a Panel with the given id.
     *
     * <pre>{@code
     * Panel p = Panel.builder("settings")
     *     .add(Button.builder().label(Component.literal("OK")).onClick(this::ok).build())
     *     .add(Toggle.builder().state(cfg::on, cfg::setOn).label(Component.literal("On")).build())
     *     .style(PanelStyle.RAISED)
     *     .position(PanelPosition.region(OutsideRegion.RIGHT_ALIGN_TOP))
     *     .build();
     * }</pre>
     *
     * @param id unique identifier within the screen
     * @return a new {@link Builder}
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Builds a {@link Panel}. Defaults: visible, {@link PanelStyle#RAISED},
     * {@link PanelPosition#UNPLACED}, and no toggle key unless
     * {@link #toggleKey(int)} is called.
     */
    public static final class Builder {
        private final String id;
        private final List<PanelElement> elements = new java.util.ArrayList<>();
        private boolean visible = true;
        private PanelStyle style = PanelStyle.RAISED;
        private PanelPosition position = PanelPosition.UNPLACED;
        private int toggleKey = NO_TOGGLE_KEY;
        private @Nullable BooleanSupplier disabledWhen;
        private @Nullable BooleanSupplier visibleWhen;
        private boolean popover = false;

        private Builder(String id) {
            this.id = id;
        }

        /**
         * Replaces the element list so far with the given one; later
         * {@link #add(PanelElement)} calls append to it.
         */
        public Builder elements(List<? extends PanelElement> elements) {
            this.elements.clear();
            this.elements.addAll(elements);
            return this;
        }

        /** Appends an element. */
        public Builder add(PanelElement element) {
            this.elements.add(element);
            return this;
        }

        /** Sets the initial visibility state. Default {@code true}. */
        public Builder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        /**
         * Shows the panel only while {@code condition} holds, read every frame: the
         * same name every element's builder uses. See
         * {@link Panel#visibleWhen}.
         */
        public Builder visibleWhen(BooleanSupplier condition) {
            this.visibleWhen = java.util.Objects.requireNonNull(condition, "condition");
            return this;
        }

        /** Sets the panel's visual background style. Default {@link PanelStyle#RAISED}. */
        public Builder style(PanelStyle style) {
            this.style = style;
            return this;
        }

        /**
         * Sets where the panel sits; every host reads this.
         * Default {@link PanelPosition#UNPLACED}: adapters and the HUD reject an
         * unplaced panel; {@code MKScreen} and {@code CustomContainerMenu} make the first
         * unplaced panel their {@code main()} and stack later ones below it.
         */
        public Builder position(PanelPosition position) {
            this.position = position;
            return this;
        }

        /**
         * Sets the key code ({@code InputConstants.KEY_*}) that toggles this panel's
         * visibility. Leave it unset for no toggle key.
         */
        public Builder toggleKey(int keyCode) {
            this.toggleKey = keyCode;
            return this;
        }

        /**
         * Disables the whole panel while {@code condition} holds, read every frame:
         * every element draws greyed and takes no input. See
         * {@link Panel#disabledWhen}.
         */
        public Builder disabledWhen(BooleanSupplier condition) {
            this.disabledWhen = java.util.Objects.requireNonNull(condition, "condition");
            return this;
        }

        /**
         * Makes the panel a popover: something that pops up over the screen while it
         * is shown (a row of pockets over a hovered hotbar slot, a flyout from a
         * button). It draws above every ordinary panel on the screen, whichever host
         * holds it or them (a container screen's adapters, a slot group's, a
         * standalone screen's own) and whichever registered first, and below a modal
         * and its dim. Input follows the picture: a click, the wheel, hover or a
         * tooltip over a popover reach it, never a panel drawn under it. Its own slots
         * stay live under it as any panel's do.
         *
         * <p>Where it sits is still its {@link #position}; only its layer changes.
         * Among popovers, and among ordinary panels, the sorted order (priority, then
         * mod, then registration) still decides which is on top. Default: an
         * ordinary panel.
         */
        public Builder popover() {
            this.popover = true;
            return this;
        }

        /** Builds the configured Panel. */
        public Panel build() {
            Panel panel = new Panel(id, elements, visible, style, position, toggleKey);
            panel.disabledSupplier = disabledWhen;
            panel.popover = popover;
            if (visibleWhen != null) panel.visibleWhen(visibleWhen);
            return panel;
        }
    }

    // Identity

    /** This panel's id, unique within the screen. */
    public String id() { return id; }

    // Style and position

    /** The panel's background style. */
    public PanelStyle getStyle() { return style; }

    /**
     * The padding, in pixels, between the panel's outer (background) bounds and
     * where its elements start.
     *
     * <ul>
     *   <li>{@link PanelStyle#NONE}: {@code 0}. With no visible frame, the element
     *       edge is the panel edge, and hover, click and tooltip bounds match the
     *       elements.</li>
     *   <li>Every other style ({@link PanelStyle#RAISED}, {@link PanelStyle#DARK},
     *       {@link PanelStyle#INSET}): {@link #INTERIOR_PADDING} ({@code 7}), room
     *       between the visible frame and the elements.</li>
     * </ul>
     *
     * <p>Used wherever outer bounds are worked out from the elements or the other
     * way round: {@code MKScreen}, {@code CustomContainerScreen}, and the
     * default-padding constructors of
     * {@link com.trevlar.menukit.api.panel.ScreenPanelAdapter} and
     * {@link com.trevlar.menukit.api.panel.SlotGroupPanelAdapter}. The
     * explicit-padding constructors use the padding they are given.
     */
    public int interiorPadding() {
        return style == PanelStyle.NONE ? 0 : INTERIOR_PADDING;
    }

    /** Where this panel sits. */
    public PanelPosition getPosition() { return position; }

    /** The key code ({@code InputConstants.KEY_*}) that toggles this panel's visibility, or -1 for none. */
    public int getToggleKey() { return toggleKey; }

    // Elements

    /**
     * The elements to lay out, render and send input to.
     *
     * <p>Usually the panel's own elements. When the content is taller than the
     * panel's viewport (a pinned height, or the room the screen leaves), this is a
     * single internal {@link ScrollContainer} holding them, which passes render,
     * clicks, scrolling and releases on to its children. To the screen it is just
     * another element.
     *
     * <p>{@link ScrollContainer} also passes {@code onAttach}, {@code onDetach} and
     * {@code keyPressed} on, so widget-wrapping elements (Slider, TextField) inside
     * a scrolling panel still register their vanilla widgets and get key input.
     */
    public List<PanelElement> getElements() {
        ensureConfigured();
        if (cachedScrollContainer != null) {
            return List.of(cachedScrollContainer);
        }
        return elements;
    }

    /**
     * The elements the panel was built with, never the auto-scroll wrapper. For
     * the configuration pass and the rare caller that needs the raw list.
     */
    public List<PanelElement> getRawElements() {
        return elements;
    }

    // Configuration pass

    /**
     * Runs the wrap and scroll configuration pass when something it depends on
     * has changed. Cheap, and safe to call from any size or element getter.
     *
     * <p>{@code pinnedWidth} and {@code pinnedHeight} are the panel's
     * <i>content</i> size, what {@link #getWidth()} and {@link #getHeight()}
     * return. The screen adds its padding on top for the outer (background) size,
     * so {@code pinnedWidth=80} gives 80 px of content plus 2 x 7 px of padding,
     * 94 px outer.
     *
     * <ol>
     *   <li><b>Wrapping</b>: works out one content width (the pinned width, or
     *       the natural width capped by the room the screen leaves) and hands each
     *       element its share, so text wraps to it.</li>
     *   <li><b>Scrolling</b>: when the content, measured after wrapping, is taller
     *       than the viewport, lays it out again narrower to clear the scrollbar
     *       and wraps every element in an internal {@link ScrollContainer}, which
     *       {@link #getElements()} then returns.</li>
     * </ol>
     */
    private void ensureConfigured() {
        // An element can change its own size without any limit changing (a
        // SlotFlowElement gaining rows when a slot is revealed). Without this the
        // overflow check below would go stale, and a reveal that overflows the
        // height limit wouldn't build the ScrollContainer. The signature is cheap,
        // allocates nothing, and stays the same until an element's size changes.
        int sig = layoutSignature();
        if (sig != lastLayoutSig) configurationDirty = true;
        if (!configurationDirty) return;
        configurationDirty = false;

        // Step 1: one content width, which every element works from (width flows
        // down from the panel). It is the widest element's unconstrained extent,
        // capped by the room the screen leaves so the panel never runs past the
        // screen-edge margin.
        //
        //   pinnedWidth set: contentWidth = pinnedWidth.
        //   otherwise:       contentWidth = min(naturalWidth, effectiveContentWidth),
        //                    or naturalWidth with no limit. A panel that fits keeps
        //                    hugging its content; the limit only matters when it
        //                    doesn't fit.
        int naturalW = 0;
        for (PanelElement e : elements) {
            if (!e.isVisible()) continue;
            int right = e.getChildX() + e.naturalWidth();
            if (right > naturalW) naturalW = right;
        }
        int contentWidth;
        if (pinnedWidth >= 0) {
            contentWidth = pinnedWidth;
        } else {
            int ceiling = (effectiveContentWidth >= 0)
                    ? effectiveContentWidth : Integer.MAX_VALUE;
            contentWidth = Math.min(naturalW, ceiling);
        }

        // Steps 2 to 4: hand out widths, measure, and only on overflow reserve the
        // scrollbar and scroll.
        //
        // The viewport is pinnedHeight when set, else the height the screen leaves
        // (effectiveContentHeight). The panel scrolls only when its content is
        // taller than the viewport. A pinned height always gives a viewport; the
        // screen's limit only does when it leaves a useful one. A region panel
        // squeezed to a sliver against a tall frame would get a uselessly tiny
        // scrollbar, so below MIN_SCROLL_VIEWPORT it draws at natural height and
        // resolveMenu's on-screen clamp handles it, as for an over-wide panel.
        int viewportHeight;
        if (pinnedHeight >= 0) {
            viewportHeight = pinnedHeight;
        } else if (effectiveContentHeight >= MIN_SCROLL_VIEWPORT) {
            viewportHeight = effectiveContentHeight;
        } else {
            viewportHeight = -1;
        }

        // Lay out against the full content width (no scrollbar room) and reflow,
        // then measure the content height. Scrollbar room is taken only if the
        // panel overflows; otherwise a panel that fits would narrow its content
        // (wrapping text early) for a scrollbar that never appears. That is also
        // what lets PanelHost give every region panel a height limit without
        // shrinking the ones that fit.
        layoutElementsWithin(contentWidth);
        reflowForWrap();
        // Height flows down to the elements that fill it, like the width above: a
        // filling element takes the viewport from its top
        // edge down, so its bottom lands exactly on the viewport's and the panel
        // never scrolls it. With no viewport it is released (-1) to its natural
        // height. Only elements that declare fillsHeight() are touched, so every
        // other element's size is exactly what it was before this step existed.
        for (PanelElement e : elements) {
            if (!e.fillsHeight()) continue;
            e.fillHeight(viewportHeight > 0 ? Math.max(1, viewportHeight - e.getChildY()) : -1);
        }
        // The frame band flows down the same way, to every element, as the room from
        // its top edge to the frame's bottom (or -1, no frame beside the panel). Only a
        // wrapping element (a vertical Flow) reads it; it lays out against it lazily,
        // before the height is measured just below.
        for (PanelElement e : elements) {
            e.wrapHeight(frameHeight >= 0 ? Math.max(1, frameHeight - e.getChildY()) : -1);
        }
        int naturalContentHeight = aggregateRawContentHeight();

        cachedScrollContainer = null;
        boolean overflow = viewportHeight > 0 && naturalContentHeight > viewportHeight;
        // Everything fits a real viewport, so the scroll is at the top; reset it, as
        // the container would write back its clamp. Otherwise the old offset would
        // come back the moment the panel overflowed again (a section closed so all
        // fits, then reopened), jumping from the top to where it once was. Tabs'
        // bodies do the same.
        if (!overflow && viewportHeight > 0) scrollOffset = 0;
        if (overflow) {
            // Lay out again with room for the scrollbar so wrapped content clears
            // the track, reflow, then wrap everything in a ScrollContainer the size
            // of the viewport. Its width is the content area: pinnedWidth when set,
            // the capped contentWidth when the screen limits width, otherwise the
            // children's width plus the reserve. The reserve is the scrollbar lane
            // plus, on an options screen, the list band's left inset
            // (ScrollContainer.viewportWidthFor decides it).
            int reserve = contentWidth - ScrollContainer.viewportWidthFor(contentWidth, screenKind);
            layoutElementsWithin(contentWidth - reserve);
            reflowForWrap();
            int outerWidth;
            if (pinnedWidth >= 0) {
                outerWidth = pinnedWidth;
            } else if (effectiveContentWidth >= 0) {
                outerWidth = contentWidth;
            } else {
                outerWidth = aggregateRawContentWidth() + reserve;
            }
            // ScrollContainer.Builder.size() rejects widths <= track + gutter + 1
            // (17). When there isn't that much room, skip scrolling: the content
            // overflows, but nothing crashes.
            int minScrollWidth = reserve + 1;
            if (outerWidth > minScrollWidth) {
                cachedScrollContainer = ScrollContainer.builder()
                        .at(0, 0)
                        .size(outerWidth, viewportHeight)
                        .content(elements)
                        .state(() -> scrollOffset, v -> scrollOffset = v)
                        .build();
            }
        }

        // Take the signature after reflow, which just moved each element's childY,
        // so next frame's signature from the settled positions matches and the
        // pass is skipped. A real change (visibility, size, a move) still changes
        // it.
        lastLayoutSig = layoutSignature();
    }

    /**
     * Step 2 of {@link #ensureConfigured}: gives each element the room from its left
     * edge to the content's right edge ({@code contentWidth} minus its x). Each
     * element works out its width, and for wrapped text its height, from that,
     * reversibly, so a wider budget later restores its natural size. A separate
     * method because overflow lays out twice: at full width to detect it, then
     * narrower for the scrollbar.
     */
    private void layoutElementsWithin(int contentWidth) {
        layoutElementsWithin(elements, contentWidth);
    }

    /** {@link #layoutElementsWithin(int)} over any element list; shared with {@link Tabs}' bodies. */
    @ApiStatus.Internal
    public static void layoutElementsWithin(List<PanelElement> elements, int contentWidth) {
        for (PanelElement e : elements) {
            int budget = contentWidth - e.getChildX();
            if (budget < MIN_ELEMENT_WIDTH) budget = MIN_ELEMENT_WIDTH;
            e.layoutWithin(budget);
        }
    }

    /**
     * A cheap, allocation-free hash of the elements' visibility, position and size,
     * which triggers a reconfigure when an element changes by itself (see
     * {@link #ensureConfigured}). Stable from frame to frame unless an element
     * actually resizes or moves.
     */
    private int layoutSignature() {
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

    // The least width any element is given, so a tight gutter can't squeeze content
    // to nothing.
    private static final int MIN_ELEMENT_WIDTH = 8;

    // The smallest screen-imposed height worth scrolling within. A region panel
    // squeezed against a tall main frame can be left a sliver, and a scrollbar in
    // fewer pixels than this is worse than drawing at natural height and relying on
    // the on-screen clamp. A pinnedHeight is always honoured.
    private static final int MIN_SCROLL_VIEWPORT = 24;

    /**
     * Re-stacks the elements' childY from their baselines, so a wrapped (taller)
     * element pushes everything below it down by exactly the extra height and
     * fixed-childY layouts don't overlap when text wraps.
     *
     * <p>The baseline is each element's declared or explicitly moved Y, kept on the
     * element ({@link AbstractPanelElement#reflowBaselineY()}, captured lazily and
     * updated by {@code at}/{@code setChildPosition}). Reflow sets childY to
     * {@code baseline + push} through {@link AbstractPanelElement#applyReflowedY},
     * which leaves the baseline alone. So the push is reversible (a wider frame
     * that un-wraps gives back {@code baseline + 0}), and a move at runtime re-bases
     * the stack instead of being overwritten. A snapshot taken once on the first
     * pass would never see runtime moves, and reflow would put the old Y back.
     *
     * <p>Nothing changes when nothing wrapped. Rendering and input read
     * {@code getChildY()} every frame, so reflow needs nothing else.
     *
     * <p>Only library elements ({@link AbstractPanelElement}) can be repositioned;
     * a bare custom {@link PanelElement} keeps its position (it doesn't wrap, so it
     * never causes a reflow; it could only be pushed, which it can't be here). Elements sharing a baseline childY form one row: the row's downward
     * push is the tallest wrap within it, so side-by-side cells (e.g. a slot grid
     * row) don't double-count.
     */
    private void reflowForWrap() {
        reflowForWrap(elements);
    }

    /** {@link #reflowForWrap()} over any element list; shared with {@link Tabs}' bodies. */
    @ApiStatus.Internal
    public static void reflowForWrap(List<PanelElement> elements) {
        if (elements.isEmpty()) return;
        int n = elements.size();

        // Each element's baseline Y (declared or explicitly moved). A bare custom
        // element can't be moved, so its baseline is its current childY.
        int[] baseline = new int[n];
        for (int i = 0; i < n; i++) {
            PanelElement e = elements.get(i);
            baseline[i] = (e instanceof AbstractPanelElement ape)
                    ? ape.reflowBaselineY() : e.getChildY();
        }

        // Visit in baseline-Y (top-to-bottom) order so a wrapped row pushes only
        // the rows below it.
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order,
                java.util.Comparator.comparingInt(i -> baseline[i]));

        int cumulative = 0;            // extra pushed down by wrapped rows above
        int rowExtra = 0;              // tallest wrap in the current row
        int rowY = Integer.MIN_VALUE;  // current row's baseline Y
        for (int idx : order) {
            int dY = baseline[idx];
            if (dY != rowY) {
                cumulative += rowExtra; // bank the previous row's push
                rowExtra = 0;
                rowY = dY;
            }
            PanelElement e = elements.get(idx);
            if (e instanceof AbstractPanelElement ape) {
                // Live Y = baseline + push; baseline untouched (reversible).
                ape.applyReflowedY(dY + cumulative);
            }
            if (e.isVisible()) {
                // Any element that grew under layoutWithin (a wrapped TextLabel,
                // a multi-line Button) pushes the rows below down by its extra
                // height.
                rowExtra = Math.max(rowExtra, e.extraLayoutHeight());
            }
        }
    }

    // Content height of the raw elements, for the overflow check. Not
    // getElements(), which would recurse into the configuration pass.
    private int aggregateRawContentHeight() {
        return contentHeightOf(elements);
    }

    /** The max {@code childY + height} over the visible elements; shared with {@link Tabs}' bodies. */
    @ApiStatus.Internal
    public static int contentHeightOf(List<PanelElement> elements) {
        int max = 0;
        for (PanelElement e : elements) {
            if (!e.isVisible()) continue;
            int bottom = e.getChildY() + e.getHeight();
            if (bottom > max) max = bottom;
        }
        return max;
    }

    // Content width of the raw elements: the ScrollContainer's width when it
    // scrolls with no pinned or limited width.
    private int aggregateRawContentWidth() {
        int max = 0;
        for (PanelElement e : elements) {
            if (!e.isVisible()) continue;
            int right = e.getChildX() + e.getWidth();
            if (right > max) max = right;
        }
        return max;
    }

    // Size

    /**
     * Pins this panel's width and height, overriding the size {@link #getWidth()}
     * and {@link #getHeight()} would compute from the elements. Use it when the
     * content is supplier-driven and its size would change from frame to frame,
     * so the panels stacked after it in the same region don't jump. Text wraps to
     * the pinned width and content taller than the pinned height scrolls.
     *
     * @param w pinned width in pixels (must be non-negative)
     * @param h pinned height in pixels (must be non-negative)
     * @return this panel, for method chaining
     */
    public Panel size(int w, int h) {
        this.pinnedWidth = w;
        this.pinnedHeight = h;
        // Pinned sizes feed wrapping and scrolling, so reconfigure on next access.
        this.configurationDirty = true;
        this.cachedScrollContainer = null;
        return this;
    }

    /**
     * Pins only the width; the height still follows the content. Text wraps to the
     * pinned width and the panel grows to fit it.
     */
    public Panel pinnedWidth(int w) {
        this.pinnedWidth = w;
        this.configurationDirty = true;
        this.cachedScrollContainer = null;
        return this;
    }

    /**
     * The width set with {@link #size(int,int)} or {@link #pinnedWidth(int)}, or
     * {@code -1} if none. For layout code that has to tell a pinned width from an
     * automatic one, such as MKC's {@code CustomContainerScreen.computePanelSize},
     * which uses the pinned width when set and otherwise the widest of its slots
     * and elements. {@link #getWidth()} only counts elements, so it can't tell.
     */
    public int getPinnedWidth() {
        return pinnedWidth;
    }

    /**
     * The height set with {@link #size(int,int)} or {@link #pinnedHeight(int)}, or
     * {@code -1} if none. See {@link #getPinnedWidth()}.
     */
    public int getPinnedHeight() {
        return pinnedHeight;
    }

    /**
     * Pins only the height; the width still follows the content. Content taller
     * than the pinned height scrolls; text doesn't wrap, and long lines are
     * clipped.
     */
    public Panel pinnedHeight(int h) {
        this.pinnedHeight = h;
        this.configurationDirty = true;
        this.cachedScrollContainer = null;
        return this;
    }

    /**
     * Sets the content width the screen leaves this panel. PanelHost calls it every
     * frame, before {@link #getWidth()}, with the room left before the screen-edge
     * margin at the panel's anchor. An unpinned panel whose content is wider wraps
     * its TextLabels to it (and sizes a scroll viewport to it) instead of running
     * off the screen, so every panel wraps at the screen edge without opting in.
     *
     * <p>Only a changed value marks the configuration dirty and drops the scroll
     * container. Since this runs every frame, it would otherwise either keep a
     * stale wrap or rebuild the scroll container each frame, resetting its drag
     * state. A window resize re-fits; a stable frame costs nothing.
     *
     * <p>Internal: it carries screen geometry the panel itself doesn't know.
     * Values below a small minimum are raised to it, so a degenerate frame can't
     * wrap to zero.
     *
     * @param avail content-width room before the screen-edge margin, in pixels
     */
    @ApiStatus.Internal
    public void setAvailableContentWidth(int avail) {
        int clamped = Math.max(MIN_FIT_WIDTH, avail);
        if (clamped == lastFitAvail) return; // unchanged: keep the last pass
        lastFitAvail = clamped;
        this.effectiveContentWidth = clamped;
        this.configurationDirty = true;
        this.cachedScrollContainer = null;
    }

    // Minimum for setAvailableContentWidth, so a tiny or negative value can't wrap
    // content to nothing.
    private static final int MIN_FIT_WIDTH = 16;

    /**
     * Sets the content height the screen leaves this panel, the height counterpart
     * of {@link #setAvailableContentWidth}. PanelHost works out the room the
     * panel's anchor leaves toward the screen edge
     * ({@link RegionMath#availableMenuHeight}); when the content is taller, the
     * panel scrolls within it (the same ScrollContainer as {@code pinnedHeight})
     * instead of running off the screen. Content that fits is unaffected: no
     * scrolling, no scrollbar room.
     *
     * <p>Like {@link #setAvailableContentWidth}, only a changed value triggers a
     * reconfigure. Internal.
     *
     * @param avail content-height room before the screen-edge margin, in pixels
     */
    @ApiStatus.Internal
    public void setAvailableContentHeight(int avail) {
        int clamped = Math.max(MIN_FIT_HEIGHT, avail);
        if (clamped == lastFitAvailHeight) return; // unchanged: keep the last pass
        lastFitAvailHeight = clamped;
        this.effectiveContentHeight = clamped;
        this.configurationDirty = true;
        this.cachedScrollContainer = null;
    }

    // Minimum for setAvailableContentHeight, so a degenerate frame can't collapse
    // the scroll viewport to nothing.
    private static final int MIN_FIT_HEIGHT = 16;

    /**
     * Sets the frame band: the content height from the panel's content top down to
     * where the frame beside it ends, or {@code -1} when no frame is beside it. The
     * host feeds it every frame, before measuring, for a panel in a side region
     * ({@code RIGHT_ALIGN_*}, {@code LEFT_ALIGN_*}) on a container screen, on a slot
     * group (whose frame is still the container screen's) and beside a standalone
     * screen's main panel; every other placement gets {@code -1}. Each element
     * receives it as its wrap height ({@link PanelElement#wrapHeight}).
     *
     * <p>Not a scroll ceiling: a panel taller than its band does not scroll (that is
     * {@link #setAvailableContentHeight}, at the screen's edge). Like those, it
     * only re-lays out when the value changes. Internal.
     *
     * @param height content-height room down to the frame's bottom, or {@code -1}
     */
    @ApiStatus.Internal
    public void setFrameHeight(int height) {
        int value = height < 0 ? -1 : height;
        if (value == frameHeight) return;
        this.frameHeight = value;
        this.configurationDirty = true;
        this.cachedScrollContainer = null;
    }

    /**
     * The kind of screen the panel is on, fed by its host before each layout (the
     * host fixes it from its screen, {@link ScreenKind#of}). An auto-scrolling panel
     * on an options screen lays its elements out inside the list band's inset
     * ({@link ScrollContainer#viewportWidthFor(int, ScreenKind)}). Re-lays out only
     * when it changes, like the size limits. Internal.
     */
    @ApiStatus.Internal
    public void setScreenKind(ScreenKind kind) {
        if (kind == screenKind) return;
        this.screenKind = kind;
        this.configurationDirty = true;
        this.cachedScrollContainer = null;
    }

    /**
     * The panel's content width: the pinned width if one was set, otherwise the
     * extent of the visible elements (the largest {@code childX + width}).
     *
     * <p>A panel whose elements are all supplier-gated should pin its size: with
     * every element hidden the width drops to zero, and the panels after it shift
     * for a frame until the elements come back.
     */
    public int getWidth() {
        if (pinnedWidth >= 0) return pinnedWidth;
        // getElements(), so a scrolling panel reports the ScrollContainer's width,
        // scrollbar included.
        int extent = 0;
        for (PanelElement e : getElements()) {
            if (!e.isVisible()) continue;
            int right = e.getChildX() + e.getWidth();
            if (right > extent) extent = right;
        }
        return extent + backgroundPadding();
    }

    /**
     * The panel's content height, worked out like {@link #getWidth()}.
     */
    public int getHeight() {
        if (pinnedHeight >= 0) return pinnedHeight;
        // getElements(), as in getWidth().
        int extent = 0;
        for (PanelElement e : getElements()) {
            if (!e.isVisible()) continue;
            int bottom = e.getChildY() + e.getHeight();
            if (bottom > extent) extent = bottom;
        }
        return extent + backgroundPadding();
    }

    // Extra pixels a background adds beyond the elements. 0 for every style for
    // now; one value rather than per-edge insets because every style is
    // symmetric. Change it if a styled frame ever clips.
    private int backgroundPadding() {
        return 0;
    }

    // Visibility

    /**
     * Whether this panel is visible: the supplier's value if one is set with
     * {@link #visibleWhen(java.util.function.BooleanSupplier)}, otherwise what
     * {@link #setVisible(boolean)} last set.
     */
    public boolean isVisible() {
        if (visibilitySupplier != null) {
            return visibilitySupplier.getAsBoolean();
        }
        return visible;
    }

    /**
     * Sets this panel's visibility.
     *
     * <p><b>Ignored while a supplier is set.</b> After
     * {@link #visibleWhen(java.util.function.BooleanSupplier)} with a non-null
     * supplier, the supplier alone decides, so unrelated code can't override it.
     * Call {@code visibleWhen(null)} first to go back to {@code setVisible}.
     */
    public void setVisible(boolean visible) {
        if (visibilitySupplier != null) return; // the supplier decides
        if (this.visible == visible) return;
        this.visible = visible;
    }

    /**
     * Makes a supplier decide this panel's visibility. {@link #isVisible()} reads
     * it on every call and {@link #setVisible(boolean)} is ignored.
     *
     * <p>Like an element's {@code state(get, set)}: you hold the state and MenuKit
     * reads it, so there is no second copy to fall out of sync.
     *
     * <h4>Precedence</h4>
     * <ul>
     *   <li>Calling {@code visibleWhen(supplier)} replaces any prior
     *       {@link #setVisible(boolean)} state. The imperative {@code visible}
     *       field is ignored while the supplier is active.</li>
     *   <li>To go back to {@code setVisible}, call
     *       {@code visibleWhen(null)}. The prior {@code setVisible} state is not
     *       restored; visibility resets to the default ({@code true}) until
     *       the consumer calls {@code setVisible} again.</li>
     * </ul>
     *
     * <h4>Sync</h4>
     *
     * A panel's visibility is a client-side rendering decision. Where it must
     * drive slot inertness and sync (a MenuKit: Containers custom menu), the menu holds
     * it on the server and the screen reads it through this supplier.
     *
     * @param supplier the visibility condition, or {@code null} to go back to
     *                 {@code setVisible}.
     * @return this panel, for method chaining.
     */
    public Panel visibleWhen(@Nullable BooleanSupplier supplier) {
        this.visibilitySupplier = supplier;
        if (supplier == null) {
            // Back to visible by default; the earlier setVisible state is not restored.
            this.visible = true;
        }
        return this;
    }

    // Disabled

    /**
     * Disables the whole panel while {@code condition} holds, read every frame
     * (pass {@code null} to clear). Every element draws its disabled look (text
     * and section headers grey, controls dark) and takes no clicks, wheel or
     * keys; containers pass it down to their children. The panel itself stays
     * present: it still claims its area, so nothing behind it reacts either.
     */
    public Panel disabledWhen(@Nullable BooleanSupplier condition) {
        this.disabledSupplier = condition;
        return this;
    }

    /** Whether the panel is disabled right now. */
    public boolean isDisabled() {
        return disabledSupplier != null && disabledSupplier.getAsBoolean();
    }

    // Opacity, dim and modal tracking

    /**
     * Sets whether this panel takes all input over its bounds.
     *
     * <p>When {@code true} and the panel is visible, input inside its bounding box
     * (clicks, hover, tooltips) goes to the panel and never reaches vanilla widgets
     * underneath. Empty space in the panel eats input, tooltips for items behind
     * it are suppressed, and slot hover returns null. Clicks never pass through a
     * visible panel unless it opts out, as transparent overlays do.
     *
     * <p>The claim rule: an opaque panel claims its
     * whole bounding box, regardless of {@link PanelStyle}, with no holes; a
     * transparent panel ({@code opaque(false)}) claims only its solid elements
     * (shown, opaque, interactive). {@code PanelStyle.NONE + opaque(true)} is the
     * "click blocker" pattern (invisible but blocks input). A panel's own slots
     * stay live under its own claim.
     *
     * <p><b>Every host:</b> panels on container and vanilla screens, slot-group
     * panels, and a standalone screen's own panels all claim by this rule.
     *
     * <p>Default: {@code true}.
     *
     * @param isOpaque {@code true} to make this panel interaction-opaque
     * @return this panel, for chaining
     */
    public Panel opaque(boolean isOpaque) {
        this.opaque = isOpaque;
        return this;
    }

    /** Whether this panel takes all input over its bounds. See {@link #opaque(boolean)}. */
    public boolean isOpaque() {
        return opaque;
    }

    /**
     * Sets whether the screen dims behind this panel while it is visible.
     *
     * <p>When {@code true} and the panel is visible, a translucent black fill is
     * drawn over the screen before this panel. Modal dialogs use it to stand apart
     * from ordinary decoration.
     *
     * <p>Independent of {@link #opaque(boolean)}: a panel can be opaque without
     * dimming (popovers, dropdowns), or in principle transparent with a dim.
     * Independent of {@link #tracksAsModal(boolean)} too: the dim is only visual,
     * while modal tracking governs the cursor and keyboard.
     *
     * <p>Default: {@code false}.
     *
     * @param dims {@code true} to dim the screen behind this panel
     * @return this panel, for chaining
     */
    public Panel dimsBehind(boolean dims) {
        this.dimsBehind = dims;
        return this;
    }

    /** Whether this panel dims the screen behind it. See {@link #dimsBehind(boolean)}. */
    public boolean dimsBehind() {
        return dimsBehind;
    }

    /**
     * Sets whether this panel acts as a modal for the whole window.
     *
     * <p>When {@code true} and the panel is visible:
     * <ul>
     *   <li><b>Cursor lock:</b> {@code Window.setAllowCursorChanges(false)} every
     *       tick, so the cursor stays DEFAULT whatever vanilla widgets ask for
     *       (creative tabs and so on).</li>
     *   <li><b>Keys:</b> keys the panels don't take never reach the screen
     *       underneath. Escape runs the panel's {@link #onEscape} action instead
     *       of closing the screen.</li>
     *   <li><b>Clicks anywhere:</b> a visible modal claims every point, so clicks
     *       outside it are eaten too.</li>
     * </ul>
     *
     * <p>Pointer-based suppression (slot hover, tooltips) comes from
     * {@link #opaque(boolean)} within the panel's bounds and doesn't need
     * {@code tracksAsModal}. The pointer has a position, so that can be local;
     * the cursor shape and keyboard belong to the whole window, so only modals
     * take them over.
     *
     * <p>Default: {@code false}.
     *
     * @param tracks {@code true} to participate in modal tracking
     * @return this panel, for chaining
     */
    public Panel tracksAsModal(boolean tracks) {
        this.tracksAsModal = tracks;
        return this;
    }

    /** Whether this panel acts as a modal. See {@link #tracksAsModal(boolean)}. */
    public boolean tracksAsModal() {
        return tracksAsModal;
    }

    /**
     * Whether this panel is a screen-centred overlay rather than part of the
     * normal layout: true when it declares {@link PanelPosition#center()}, or is
     * {@link #dimsBehind()} or {@link #tracksAsModal()}.
     *
     * <p>Hosts use this one check for three things: centring the panel on the
     * screen, leaving it out of the normal layout, and drawing it in the overlay
     * layer on top. Counting {@code dimsBehind} and {@code tracksAsModal} means a
     * dialog made with {@code modal()} is centred without calling
     * {@code center()}, while a non-dim, non-modal overlay opts in with
     * {@code position(PanelPosition.center())}.
     *
     * <p>This is about position only. The dim is still drawn only for
     * {@link #dimsBehind()}, so an overlay can be centred on top without
     * dimming.
     */
    public boolean isOverlayPositioned() {
        return getPosition().mode() == PanelPosition.Mode.CENTER
                || dimsBehind()
                || tracksAsModal();
    }

    /**
     * Whether this panel is a popover ({@link Builder#popover()}): it draws and
     * claims above every ordinary panel on the screen, below the modal overlay.
     */
    public boolean isPopover() {
        return popover;
    }

    /**
     * Sets all three modal flags, the same as
     * {@code opaque(true).dimsBehind(true).tracksAsModal(true)}: a real modal in
     * one call. Popovers, dropdowns and click blockers use the separate setters.
     *
     * <p><b>Undefined:</b> {@code opaque(false)} with {@code tracksAsModal(true)}
     * makes no sense (clicks pass through while the cursor and keys are locked)
     * and is almost certainly a bug. It isn't rejected, but its behaviour is not
     * defined.
     *
     * @return this panel, for chaining
     */
    public Panel modal() {
        return opaque(true).dimsBehind(true).tracksAsModal(true);
    }

    /**
     * Sets the action run when the player presses Escape while this panel is the
     * topmost visible {@link #tracksAsModal() modal}. The host ({@code MKScreen},
     * or the key listener for injected panels) runs it and eats the key, so Escape
     * dismisses the modal instead of closing the screen under it.
     *
     * <p>The dialog builders set it for you:
     * {@link com.trevlar.menukit.api.dialog.ConfirmDialog} to its {@code onCancel}
     * and {@link com.trevlar.menukit.api.dialog.AlertDialog} to its
     * {@code onAcknowledge}, so Escape does what the button does. For a modal you
     * build yourself, pass your dismiss callback here.
     *
     * <p>Without an action, a visible {@code tracksAsModal} panel still makes the
     * host eat Escape, so it can't close the screen.
     *
     * @param action the dismiss callback, or {@code null} to clear it
     * @return this panel, for chaining
     */
    public Panel onEscape(@Nullable Runnable action) {
        this.escapeAction = action;
        return this;
    }

    /**
     * The action set with {@link #onEscape(Runnable)}, or {@code null}.
     */
    public @Nullable Runnable getEscapeAction() {
        return escapeAction;
    }

    // Tooltip

    /**
     * Sets a tooltip shown whenever the cursor is over the panel's outer bounds,
     * say to explain what a collapsible or configurable panel is for.
     *
     * <p>For tooltips on specific children, put them on the children. Child
     * tooltips are queued while the elements render and the panel's after, and
     * the last one queued wins, so over a child with both, the panel's shows.
     */
    public Panel tooltip(Component text) {
        return tooltip(() -> text);
    }

    /**
     * Sets a tooltip whose text comes from a supplier, read every frame while the
     * panel is hovered.
     */
    public Panel tooltip(Supplier<Component> supplier) {
        this.tooltipSupplier = supplier;
        return this;
    }

    /** The tooltip supplier, or null. */
    public @Nullable Supplier<Component> getTooltipSupplier() {
        return tooltipSupplier;
    }

    /**
     * Queues the panel's tooltip, if it has one, when the cursor is inside the
     * given outer rectangle. The host calls it after rendering the elements.
     *
     * <p>Skipped when {@code hasMouseInput} is {@code false}. The HUD passes
     * {@code mouseX = -1}, which would miss the hit test anyway; the check makes
     * that explicit and saves the supplier call.
     */
    public void maybeQueueTooltip(GuiGraphicsExtractor graphics,
                                  int panelX, int panelY,
                                  int panelWidth, int panelHeight,
                                  int mouseX, int mouseY,
                                  boolean hasMouseInput) {
        if (tooltipSupplier == null || !hasMouseInput) return;
        if (mouseX < panelX || mouseX >= panelX + panelWidth) return;
        if (mouseY < panelY || mouseY >= panelY + panelHeight) return;
        Component text = tooltipSupplier.get();
        if (text == null) return;
        MKTooltip.queue(graphics, text, mouseX, mouseY);
    }
}
