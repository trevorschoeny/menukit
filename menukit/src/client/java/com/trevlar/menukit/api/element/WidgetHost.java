package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.Focus;
import com.trevlar.menukit.core.OverlayDraws;
import com.trevlar.menukit.core.PressedTracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.util.Util;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Hosts a vanilla-style widget (anything that is a {@link GuiEventListener} and a
 * {@link Renderable}) inside a panel, at the element's own rectangle. It is the one
 * way MenuKit hosts a vanilla widget: {@link TextField} hosts vanilla's
 * {@code EditBox} and {@link Slider} vanilla's slider through it, and a mod hosts
 * a widget another library hands it (a config library's custom control) the same way.
 *
 * <pre>{@code
 * WidgetHost.builder()
 *         .widget(() -> library.makeWidget())
 *         .size(150, 20)
 *         .build();
 * }</pre>
 *
 * <h3>What the host does every frame</h3>
 * <ul>
 *   <li><b>Places it.</b> A widget that is a vanilla {@link LayoutElement} is moved to
 *       the element's rectangle ({@code setX}, {@code setY}, and {@code setWidth} and
 *       {@code setHeight} on an {@link AbstractWidget}). Anything else is placed by
 *       the consumer's {@link Builder#place} hook, which also wins over the
 *       {@code LayoutElement} path when given. The rectangle follows the panel, its
 *       scroll and any layout that moves the element.</li>
 *   <li><b>Draws it,</b> clipped to the element's rectangle (and, inside a
 *       {@link ScrollContainer}, to its viewport), with the mouse when the panel owns
 *       the pointer and none when another panel covers it or the host is disabled.</li>
 * </ul>
 *
 * <h3>Input</h3>
 *
 * The host routes the mouse itself, in screen coordinates, as an element: a click
 * (with vanilla's double-click rule and the modifier keys held), the release of a
 * press it took, the drag in between (followed every frame while the button is
 * held) and the wheel. So the widget works wherever its panel is: on a standalone
 * screen, on a panel over a vanilla screen, inside a scroll area or a tab. A click
 * the widget takes gives it keyboard focus, as vanilla does.
 *
 * <p>Keys, typed characters, Tab and arrow-key focus moves and narration come from
 * vanilla's focus: a stand-in for the widget is one of the screen's children
 * ({@code Focus}), and forwards them to the widget while it holds focus. The
 * stand-in is live only while the host is drawn and enabled, so a widget whose
 * panel is hidden, whose tab is not shown or that is disabled is skipped by Tab and
 * gives up focus on the next key, rather than typing unseen.
 *
 * <h3>Overflow</h3>
 *
 * A widget that opens a popup (a dropdown list) would be cut off at its rectangle.
 * With {@link Builder#overflow()}, while the widget holds keyboard focus it draws
 * on top of the screen's panels, after the dialogs' layer and unclipped, and takes
 * clicks anywhere on the screen: a click the widget does not take (outside it and
 * its popup) ends its focus, which closes the popup. A host under a modal it does
 * not belong to draws in place.
 *
 * <h3>Inert, disabled, kinds</h3>
 *
 * Like any element: under another panel's claim it gets no clicks and draws with
 * no mouse; disabled (its own {@code disabledWhen} or its panel's) it takes no input
 * and the stand-in is not focusable. The widget draws itself, so it looks the same on
 * every kind of screen; an element built on the host (a {@link TextField}) chooses
 * how its widget looks.
 *
 * <h3>Lifecycle</h3>
 *
 * The widget is made from the supplier on first need (the screen's init, in
 * practice), not at build, since a vanilla widget may need the game's font.
 *
 * @param <W> the hosted widget's type
 */
public class WidgetHost<W extends GuiEventListener & Renderable> extends AbstractPanelElement {

    /**
     * Places a widget that is not a vanilla {@link LayoutElement}, or places one in
     * the consumer's own way: called every frame and before every input event with
     * the element's rectangle in screen coordinates.
     */
    @FunctionalInterface
    public interface Placer<W> {
        void place(W widget, int x, int y, int width, int height);
    }

    /** Vanilla's double-click window ({@code MouseHandler}). */
    private static final long DOUBLE_CLICK_MS = 250;

    private final Supplier<? extends W> supplier;
    private final @Nullable Placer<? super W> placer;
    private final boolean overflow;

    private @Nullable W widget;
    private final StandIn standIn = new StandIn();
    private @Nullable Screen screen;

    // Interaction state, from a press to its release: which button the widget took a
    // press with (-1 for none) and where the mouse was last, so the drag can follow.
    private int pressed = -1;
    private double dragX, dragY;
    private long lastClickAt = Long.MIN_VALUE / 2;
    private int lastClickButton = -1;

    // Where the host was last drawn, and whether it was enabled then: the stand-in is
    // focusable only while the host is drawn and enabled.
    private long renderedAt = Long.MIN_VALUE / 2;
    private boolean enabled = true;
    private int lastX, lastY;

    protected WidgetHost(Builder<W> b) {
        super(b);
        this.supplier = Objects.requireNonNull(b.widget);
        this.placer = b.placer;
        this.overflow = b.overflow;
    }

    /** Starts a host. Call {@link Builder#widget} first: the builder takes the widget's type from it. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Builder<?> builder() {
        return new Builder();
    }

    /** The hosted widget, or {@code null} before it is first needed. */
    public @Nullable W widget() {
        return widget;
    }

    /**
     * The widget's stand-in among the screen's children: what holds vanilla's
     * keyboard focus for it. For the checks and for MenuKit's own elements.
     */
    @ApiStatus.Internal
    public GuiEventListener focusTarget() {
        return standIn;
    }

    /** The widget, made on first need. A widget MenuKit cannot place is a build mistake, and says so. */
    private W ensure() {
        if (widget != null) return widget;
        W made = Objects.requireNonNull(supplier.get(), "WidgetHost: the widget supplier returned null");
        if (placer == null && !(made instanceof LayoutElement)) {
            throw new IllegalStateException("WidgetHost: " + made.getClass().getName()
                    + " is not a vanilla LayoutElement, so MenuKit cannot move it; give the builder .place(...)");
        }
        widget = made;
        return made;
    }

    /** Moves the widget to the element's rectangle at {@code (x, y)}, by the hook or as a LayoutElement. */
    private void place(W w, int x, int y) {
        lastX = x;
        lastY = y;
        if (placer != null) {
            placer.place(w, x, y, width, height);
            return;
        }
        LayoutElement le = (LayoutElement) w;   // ensure() refused anything else without a placer
        le.setX(x);
        le.setY(y);
        if (w instanceof AbstractWidget aw) {
            aw.setWidth(width);
            aw.setHeight(height);
        }
    }

    @Override public boolean isInteractive() { return true; }

    // Render

    @Override
    public void render(RenderContext ctx) {
        W w = ensure();
        int x = ctx.originX() + childX;
        int y = ctx.originY() + childY;
        place(w, x, y);
        boolean disabled = disabled(ctx);
        enabled = !disabled;
        renderedAt = Util.getMillis();
        adoptScreenFocus(w);
        if (disabled) pressed = -1;
        followDrag(w, ctx);

        int mx = !disabled && ctx.hasMouseInput() ? ctx.mouseX() : -1;
        int my = !disabled && ctx.hasMouseInput() ? ctx.mouseY() : -1;
        // Overflowing with its popup open: on top of everything, unclipped. Falls back
        // to the clipped draw where nothing composes a top layer (or under a dialog).
        if (!(popupOpen() && OverlayDraws.defer(top -> w.extractRenderState(top, mx, my, 0f)))) {
            GuiGraphicsExtractor g = ctx.graphics();
            g.enableScissor(x, y, x + width, y + height);
            try {
                w.extractRenderState(g, mx, my, 0f);
            } finally {
                g.disableScissor();
            }
        }
        queueTooltip(ctx);
    }

    /**
     * A screen that focused the widget itself ({@code screen.setFocused(field.widget())})
     * gets the stand-in focused instead, so Tab moves on from it in screen order.
     */
    private void adoptScreenFocus(W w) {
        if (screen != null && screen.getFocused() == w) screen.setFocused(standIn);
    }

    /**
     * While a press the widget took is held, hands it every mouse move as a drag, as
     * vanilla does for the focused widget. Read from the game's cursor, so a drag
     * that leaves the panel keeps going; ended by the release, or here when the
     * release went somewhere that never told the host.
     */
    private void followDrag(W w, RenderContext ctx) {
        if (pressed < 0) return;
        Minecraft mc = Minecraft.getInstance();
        double mx, my;
        if (mc != null && mc.getWindow() != null) {
            if (pressed == Click.LEFT && !PressedTracker.isLeftMouseDown()) {
                pressed = -1;
                return;
            }
            mx = mc.mouseHandler.getScaledXPos(mc.getWindow());
            my = mc.mouseHandler.getScaledYPos(mc.getWindow());
        } else {
            if (!ctx.hasMouseInput()) return;
            mx = ctx.mouseX();
            my = ctx.mouseY();
        }
        if (mx == dragX && my == dragY) return;
        w.mouseDragged(event(mx, my, pressed), mx - dragX, my - dragY);
        dragX = mx;
        dragY = my;
    }

    // Input

    /** A vanilla mouse event at {@code (x, y)} with the modifier keys held at the click. */
    private static MouseButtonEvent event(double x, double y, int button) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, PressedTracker.lastModifiers()));
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        if (disabled(in)) return false;
        W w = ensure();
        place(w, in.originX() + childX, in.originY() + childY);
        boolean wasOpen = popupOpen();
        long now = Util.getMillis();
        boolean doubleClick = now - lastClickAt < DOUBLE_CLICK_MS && lastClickButton == button;
        if (!w.mouseClicked(event(in.mouseX(), in.mouseY(), button), doubleClick)) {
            // Outside the widget and its popup, while it was open: the popup closes,
            // as a native one does on a click elsewhere.
            if (wasOpen) blur(w);
            return false;
        }
        lastClickAt = now;
        lastClickButton = button;
        pressed = button;
        dragX = in.mouseX();
        dragY = in.mouseY();
        if (w.shouldTakeFocusAfterInteraction()) focus(w);
        return true;
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        // Releases reach every element; only the end of a press the widget took is its.
        if (button != pressed || widget == null) return false;
        pressed = -1;
        place(widget, in.originX() + childX, in.originY() + childY);
        widget.mouseReleased(event(in.mouseX(), in.mouseY(), button));
        return true;
    }

    @Override
    public boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        if (disabled(in)) return false;
        W w = ensure();
        place(w, in.originX() + childX, in.originY() + childY);
        return w.mouseScrolled(in.mouseX(), in.mouseY(), scrollX, scrollY);
    }

    /** While an overflowing widget's popup is open it may take a click anywhere: the whole screen. */
    @Override
    public int @Nullable [] getActiveOverlayBounds(InputContext in) {
        if (!popupOpen() || !standIn.live()) return null;
        int w = screen != null ? screen.width : Short.MAX_VALUE;
        int h = screen != null ? screen.height : Short.MAX_VALUE;
        return new int[]{0, 0, w, h};
    }

    /** Whether the widget is overflowing now: built with {@code overflow()} and holding focus. */
    private boolean popupOpen() {
        return overflow && widget != null && widget.isFocused();
    }

    /**
     * Gives the widget keyboard focus from code, as a click it took does (7.3.0, a
     * {@link TextField}'s {@code focus()}), making the widget first if it was not yet.
     */
    void takeFocus() {
        focus(ensure());
    }

    /** Takes keyboard focus from the widget, when it has it. */
    void releaseFocus() {
        if (widget != null && widget.isFocused()) blur(widget);
    }

    private void focus(W w) {
        if (screen != null) {
            if (screen.getFocused() != standIn) screen.setFocused(standIn);
        } else {
            w.setFocused(true);   // no screen (a build-time check): the widget alone
        }
    }

    private void blur(W w) {
        if (screen != null && screen.getFocused() == standIn) screen.setFocused(null);
        else w.setFocused(false);
    }

    // Lifecycle

    @Override
    public void onAttach(Screen screen) {
        if (this.screen == screen) return;
        if (this.screen != null) onDetach(this.screen);
        this.screen = screen;
        Focus.addHosted(screen, standIn, ensure());
    }

    @Override
    public void onDetach(Screen screen) {
        if (this.screen != screen) return;
        if (widget != null) Focus.removeHosted(screen, standIn, widget);
        if (screen.getFocused() == standIn) screen.setFocused(null);
        this.screen = null;
        pressed = -1;
    }

    /**
     * The widget's place among the screen's children: vanilla's focus, keys, typed
     * characters and narration reach the widget through it. Deaf to the mouse (the
     * host routes the mouse), and live only while the host is drawn and enabled.
     */
    private final class StandIn implements GuiEventListener, NarratableEntry {

        /** Drawn within the last {@link ElementWidget#LIVE_MS} and enabled then. */
        boolean live() {
            return enabled && Util.getMillis() - renderedAt < ElementWidget.LIVE_MS;
        }

        /** Hidden or disabled while holding focus: let it go, so keys stop reaching it unseen. */
        private boolean liveOrDrop() {
            if (live()) return true;
            if (screen != null && screen.getFocused() == this) screen.setFocused(null);
            else if (widget != null && widget.isFocused()) widget.setFocused(false);
            return false;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            return liveOrDrop() && widget != null && widget.keyPressed(event);
        }

        @Override
        public boolean keyReleased(KeyEvent event) {
            return liveOrDrop() && widget != null && widget.keyReleased(event);
        }

        @Override
        public boolean charTyped(CharacterEvent event) {
            return liveOrDrop() && widget != null && widget.charTyped(event);
        }

        @Override
        public boolean preeditUpdated(@Nullable PreeditEvent event) {
            return liveOrDrop() && widget != null && widget.preeditUpdated(event);
        }

        @Override
        public void setFocused(boolean focused) {
            if (widget != null) widget.setFocused(focused);
        }

        @Override
        public boolean isFocused() {
            return widget != null && widget.isFocused();
        }

        /**
         * A focused text field captures input: the screen then leaves the inventory key,
         * the hotbar keys and the like to it ({@code Screen.isInputCaptured}).
         */
        @Override
        public boolean capturesInput() {
            return live() && widget != null && widget.capturesInput();
        }

        @Override
        public boolean shouldTakeFocusAfterInteraction() {
            return widget == null || widget.shouldTakeFocusAfterInteraction();
        }

        /**
         * Over the widget, or anywhere while its popup is open: what MenuKit's focus
         * janitor asks before blurring on a click ({@code Focus.blurOnOutsideBounds}).
         * Mouse events themselves never come through here.
         */
        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            if (!live() || widget == null) return false;
            return popupOpen() || widget.isMouseOver(mouseX, mouseY);
        }

        /** Tab and the arrow keys: the widget's own answer, while the host is live. */
        @Override
        public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent event) {
            if (!live() || widget == null) return null;
            return widget.nextFocusPath(event) != null ? ComponentPath.leaf(this) : null;
        }

        @Override
        public ScreenRectangle getRectangle() {
            if (widget instanceof LayoutElement le) return le.getRectangle();
            return new ScreenRectangle(lastX, lastY, width, height);
        }

        @Override
        public int getTabOrderGroup() {
            return widget != null ? widget.getTabOrderGroup() : 0;
        }

        @Override
        public NarrationPriority narrationPriority() {
            if (!live() || !(widget instanceof NarratableEntry n)) return NarrationPriority.NONE;
            return n.narrationPriority();
        }

        @Override
        public void updateNarration(NarrationElementOutput output) {
            if (widget instanceof NarratableEntry n) n.updateNarration(output);
        }

        @Override
        public boolean isActive() {
            return live() && (!(widget instanceof NarratableEntry n) || n.isActive());
        }
    }

    /**
     * Builds a {@link WidgetHost}. {@link #widget} and {@link #size} are required; call
     * {@code widget} first, since it fixes the type {@link #place} is typed by.
     */
    public static final class Builder<W extends GuiEventListener & Renderable>
            extends AbstractPanelElement.Builder<WidgetHost<W>, Builder<W>> {

        private @Nullable Supplier<? extends W> widget;
        private @Nullable Placer<? super W> placer;
        private boolean overflow;

        Builder() {}

        @Override protected Builder<W> self() { return this; }

        /**
         * Required: the widget, made on first need (the screen's init). One widget per
         * host: the supplier runs once.
         */
        @SuppressWarnings("unchecked")
        public <X extends GuiEventListener & Renderable> Builder<X> widget(Supplier<? extends X> widget) {
            Builder<X> typed = (Builder<X>) (Object) this;
            typed.widget = Objects.requireNonNull(widget, "widget");
            return typed;
        }

        /** Required: the rectangle the widget gets, in pixels. */
        @Override
        public Builder<W> size(int width, int height) {
            return super.size(width, height);
        }

        /**
         * How to put the widget at the element's rectangle, for a widget that is not a
         * vanilla {@link LayoutElement} (or to place one in your own way). Called every
         * frame and before every input event, in screen coordinates.
         */
        public Builder<W> place(Placer<? super W> placer) {
            this.placer = Objects.requireNonNull(placer, "placer");
            return self();
        }

        /**
         * Lets the widget draw past its rectangle while it holds focus (a dropdown's
         * open list): on top of the screen's panels, taking clicks anywhere until a
         * click it does not take ends its focus.
         */
        public Builder<W> overflow() {
            this.overflow = true;
            return self();
        }

        @Override
        public WidgetHost<W> build() {
            require(widget != null, "widget(supplier) is required");
            require(width > 0 && height > 0, "size(w, h) is required");
            return new WidgetHost<>(this);
        }
    }
}
