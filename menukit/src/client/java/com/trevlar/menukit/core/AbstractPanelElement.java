package com.trevlar.menukit.core;

import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Base of MenuKit's own elements: built once by a builder, immutable after
 * (§0066, applying §0022 to elements).
 *
 * <h2>One vocabulary</h2>
 *
 * Every element's builder extends {@link Builder}, so the same idea has the same
 * name on every element:
 * <ul>
 *   <li>{@code at(x, y)}: position in the panel's content area.</li>
 *   <li>{@code size(w, h)}: on the elements that take a size (the rest size
 *       themselves from their content).</li>
 *   <li>{@code visibleWhen(supplier)}: shown while it holds, read every frame.</li>
 *   <li>{@code disabledWhen(supplier)}: greyed and inert while it holds.</li>
 *   <li>{@code tooltip(text)}: the hover tooltip.</li>
 *   <li>{@code state(get, set)}: the lens onto the consumer's value, on every
 *       element that shows one (§0026). An element never stores the value: it
 *       reads {@code get} every frame and hands a change to {@code set}.</li>
 *   <li>{@code onClick(Runnable)}: what a press does.</li>
 *   <li>{@code style(ControlStyle)}: MenuKit's look or vanilla's.</li>
 *   <li>{@code opaque(boolean)}: whether it is solid on a transparent panel.</li>
 *   <li>{@code declId(id)}: a stable id for the window's address.</li>
 * </ul>
 *
 * <h2>What an instance keeps</h2>
 *
 * Only what a container needs: {@link #setChildPosition} (a {@link Flow} or a
 * {@code Row}/{@code Column} places its children), and the layout protocol a
 * panel drives every pass ({@link #layoutWithin}, {@link #fillWidth}). Nothing
 * else about an element changes after {@code build()}; what varies at runtime is
 * read from suppliers.
 *
 * <h2>The one width-cap helper</h2>
 *
 * An element given a {@code size} keeps its authored width as its
 * {@link #naturalWidth()}; the panel's layout pass caps the live width to the room
 * it has ({@code min(authored, budget)}), reversibly, and a column's
 * {@code CrossAlign.FILL} re-authors it ({@link #fillWidth}). Elements that size
 * themselves from content, or never shrink (an icon, a checkbox square), override
 * these.
 *
 * <h2>Disabled</h2>
 *
 * {@link #disabled(RenderContext)} and {@link #disabled(InputContext)} answer
 * "should this element look and act disabled now": its own {@code disabledWhen},
 * or the disabled panel or container around it (the cascade the contexts carry).
 *
 * <h2>Consumer custom elements</h2>
 *
 * A consumer may extend this class for its own element, with its own builder
 * extending {@link Builder} (and the protected constructor taking it), or with
 * the no-argument constructor and its own fields; or implement
 * {@link PanelElement} directly.
 */
public abstract class AbstractPanelElement implements PanelElement {

    // ── Position (mutable only through the container protocol) ─────────

    /** Element X within the panel content area. */
    protected int childX;

    /**
     * Element Y within the panel content area: the LIVE render Y, the
     * {@link #reflowBaselineY() baseline} plus any push the panel's wrap reflow
     * applied.
     */
    protected int childY;

    // ── Size: the one width-cap helper ─────────────────────────────────

    /** The resolved (live) width: the authored width, capped by the last layout pass. */
    protected int width;

    /** The authored height. Elements whose label wraps grow past it in {@link #getHeight()}. */
    protected int height;

    /** The authored width, the cap's target; re-authored by {@link #fillWidth}. */
    protected int authoredWidth;

    // ── Declared once, by the builder ──────────────────────────────────

    private final @Nullable BooleanSupplier visibleWhen;
    private final @Nullable BooleanSupplier disabledWhen;
    private final @Nullable Supplier<Component> tooltip;
    private final boolean opaque;
    private final @Nullable String declId;

    // ── Wrap-reflow baseline ───────────────────────────────────────────
    // The panel's reflow pushes elements down when an element above wraps. To stay
    // reversible it needs each element's BASELINE Y (the authored or explicitly
    // moved position), distinct from the live childY (baseline + push). A builder
    // sets it at construction; a custom element that assigns childY in its own
    // constructor has it captured lazily on the first reflow read. A move through
    // setChildPosition re-bases it, so reflow never clobbers an explicit move.
    private int baselineY;
    private boolean baselineCaptured;

    /** Builds from {@code b}: position, size and the shared vocabulary. */
    protected AbstractPanelElement(Builder<?, ?> b) {
        this.childX = b.x;
        this.childY = b.y;
        this.width = Math.max(0, b.width);
        this.authoredWidth = this.width;
        this.height = Math.max(0, b.height);
        this.visibleWhen = b.visibleWhen;
        this.disabledWhen = b.disabledWhen;
        this.tooltip = b.tooltip;
        this.opaque = b.opaque;
        this.declId = b.declId;
        this.baselineY = b.y;
        this.baselineCaptured = true;
    }

    /**
     * For a consumer's custom element without a builder: at {@code (0, 0)}, no
     * size, always shown, never disabled, solid, no tooltip. The subclass assigns
     * {@link #childX}/{@link #childY} and reports its own size.
     */
    protected AbstractPanelElement() {
        this.visibleWhen = null;
        this.disabledWhen = null;
        this.tooltip = null;
        this.opaque = true;
        this.declId = null;
    }

    // ── Position ───────────────────────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }

    /**
     * Moves this element (the container protocol): a {@link Flow} places its
     * children with it every layout pass, and {@code Row}/{@code Column} place a
     * built element with it. Render and input read the position every frame, so
     * a move takes effect on the next frame. Client-side presentation only; the
     * element's identity is unchanged.
     */
    public void setChildPosition(int x, int y) {
        this.childX = x;
        this.childY = y;
        this.baselineY = y;
        this.baselineCaptured = true;
    }

    /** The baseline Y the panel's reflow stacks from. Package-private: {@code Panel}'s reflow reads it. */
    int reflowBaselineY() {
        if (!baselineCaptured) {
            baselineY = childY;
            baselineCaptured = true;
        }
        return baselineY;
    }

    /** Sets the live Y to baseline plus the reflow push, leaving the baseline alone. Package-private. */
    void applyReflowedY(int y) {
        this.childY = y;
    }

    // ── Size ───────────────────────────────────────────────────────────

    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }

    /** The authored width: what the element asks for with room to spare. */
    @Override public int naturalWidth() { return authoredWidth; }

    /** Caps the live width to the room the panel gives, reversibly. */
    @Override public void layoutWithin(int budget) { this.width = Math.min(authoredWidth, budget); }

    /** Column fill: the column's widest extent becomes this element's authored width. */
    @Override public void fillWidth(int width) {
        this.authoredWidth = width;
        this.width = width;
    }

    // ── The shared vocabulary, read ────────────────────────────────────

    @Override
    public boolean isVisible() {
        return visibleWhen == null || visibleWhen.getAsBoolean();
    }

    @Override
    public @Nullable Supplier<Component> tooltipSupplier() {
        return tooltip;
    }

    @Override
    public boolean isElementOpaque() {
        return opaque;
    }

    @Override
    public @Nullable String getElementDeclId() {
        return declId;
    }

    /** Whether this element's own {@code disabledWhen} holds (not the cascade). */
    protected final boolean ownDisabled() {
        return disabledWhen != null && disabledWhen.getAsBoolean();
    }

    /** Whether this element should look disabled this frame: its own predicate, or its panel's or container's. */
    protected final boolean disabled(RenderContext ctx) {
        return ctx.disabled() || ownDisabled();
    }

    /** Whether this element should act disabled for this event: its own predicate, or its panel's or container's. */
    protected final boolean disabled(InputContext in) {
        return in.disabled() || ownDisabled();
    }

    // ════════════════════════════════════════════════════════════════════
    // Builder
    // ════════════════════════════════════════════════════════════════════

    /**
     * The builder every MenuKit element's builder extends: the shared vocabulary,
     * in one place, with the same names everywhere. A concrete builder adds its
     * element's own settings, validates in {@code build()}, and makes {@link #size}
     * public when its element takes one.
     *
     * @param <E> the element built
     * @param <B> the concrete builder, so a chain stays typed end to end
     */
    public abstract static class Builder<E extends PanelElement, B extends Builder<E, B>> {

        /** Position within the panel's content area. Default {@code (0, 0)}. */
        protected int x, y;
        /** Authored size, or {@code -1} when not given. */
        protected int width = -1, height = -1;
        protected @Nullable BooleanSupplier visibleWhen;
        protected @Nullable BooleanSupplier disabledWhen;
        protected @Nullable Supplier<Component> tooltip;
        protected boolean opaque = true;
        protected @Nullable String declId;

        protected Builder() {}

        /** Returns {@code this} as the concrete builder. */
        protected abstract B self();

        /** Builds the element. Throws {@link IllegalStateException} when a required setting is missing. */
        public abstract E build();

        /** Panel-local position: where the element sits in its panel's content area. Default {@code (0, 0)}. */
        public B at(int x, int y) {
            this.x = x;
            this.y = y;
            return self();
        }

        /**
         * The element's size in pixels. Protected here: a builder whose element
         * takes a size overrides this as public; an element that sizes itself from
         * its content has none to take.
         */
        protected B size(int width, int height) {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("size must not be negative, got " + width + "x" + height);
            }
            this.width = width;
            this.height = height;
            return self();
        }

        /** Shown only while {@code condition} holds, read every frame. A hidden element is fully inert. */
        public B visibleWhen(BooleanSupplier condition) {
            this.visibleWhen = Objects.requireNonNull(condition, "condition");
            return self();
        }

        /**
         * Greyed and inert while {@code condition} holds, read every frame. On a
         * container (a {@link Section}, {@link Tabs}, {@link ScrollContainer},
         * {@link Flow}) it disables everything inside as well.
         */
        public B disabledWhen(BooleanSupplier condition) {
            this.disabledWhen = Objects.requireNonNull(condition, "condition");
            return self();
        }

        /** A hover tooltip with fixed text. */
        public B tooltip(Component text) {
            Objects.requireNonNull(text, "text");
            this.tooltip = () -> text;
            return self();
        }

        /** A hover tooltip read every frame while hovered; a {@code null} result shows none. */
        public B tooltip(Supplier<Component> text) {
            this.tooltip = Objects.requireNonNull(text, "text");
            return self();
        }

        /**
         * Whether the element is solid on a transparent panel ({@code Panel.opaque(false)}):
         * there only solid, interactive elements claim input, so a non-opaque one lets
         * clicks, hover and tooltips through to what is behind it. On an opaque panel
         * it makes no difference (the panel claims its whole rectangle, §0065).
         * Default {@code true}. Was {@code setElementOpaque}.
         */
        public B opaque(boolean opaque) {
            this.opaque = opaque;
            return self();
        }

        /**
         * A stable id within the panel, for the window's address of this element,
         * used instead of its position in the panel's element list. Use it when
         * the list's order is not a reliable handle (elements included
         * conditionally).
         */
        public B declId(String id) {
            this.declId = Objects.requireNonNull(id, "id");
            return self();
        }

        /** Throws {@link IllegalStateException} naming the builder when {@code ok} is false. */
        protected final void require(boolean ok, String message) {
            if (ok) return;
            Class<?> owner = getClass().getEnclosingClass();
            String name = owner != null ? owner.getSimpleName() + ".Builder" : getClass().getSimpleName();
            throw new IllegalStateException(name + ": " + message);
        }
    }
}
