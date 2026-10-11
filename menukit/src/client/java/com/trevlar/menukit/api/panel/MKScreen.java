package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.RegionConstants;
import com.trevlar.menukit.inject.LayerPlan;
import com.trevlar.menukit.inject.PanelHost;
import com.trevlar.menukit.inject.ScreenPanelRegistry;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Base class for standalone screens built with MenuKit: full-screen, client-local,
 * interactive UIs not tied to a container menu.
 *
 * <p>Extends vanilla's {@link Screen} directly; a MenuKit standalone screen
 * <em>is</em> a vanilla Screen, so ecosystem mixins into {@code Screen} affect it
 * identically.
 *
 * <h3>Its panels live in one host</h3>
 * The screen's panels go into a standalone {@link PanelHost}, attached to
 * {@link ScreenPanelRegistry} so global questions (what claims a point, is a modal
 * up) see them alongside anything injected onto this screen. The host places each
 * panel by its {@link PanelPosition}:
 * <ul>
 *   <li>{@code main()}: the screen's frame, centred (one per screen);</li>
 *   <li>{@code region(OutsideRegion)}: around the main panel;</li>
 *   <li>{@code screenAnchor(InsideRegion)}: screen chrome (a back button, a title);</li>
 *   <li>{@code center()}: an overlay, drawn on top (dialogs);</li>
 *   <li>{@code pixel(...)}: exact coordinates.</li>
 * </ul>
 * An unplaced panel takes the standalone default
 * ({@link PanelPosition#standaloneDefaults}): the first becomes {@code main()}, later
 * ones stack below it. A {@code region(...)} panel with no main to anchor to is
 * rejected at construction.
 *
 * <p>Rendering follows the {@link LayerPlan} (FLOW, POPOVER, dim, OVERLAY), composed by the
 * registry with any injected hosts on this screen. Input: the screen routes its own
 * panels (topmost first, modal-aware), then vanilla widgets, then eats a click its
 * panels claim.
 *
 * <h3>Its kind (6.1.0)</h3>
 * A standalone screen declares what kind of screen it is once, when it is created
 * ({@link ScreenKind}): {@code super(title, ScreenKind.OPTIONS, panels)} for a
 * settings screen that should read like the ones reached from the pause menu.
 * Every element on it draws for that kind, its panels' backgrounds too, and so
 * does anything injected onto it or opened from it (a dialog, an item picker's
 * chooser). Without a kind it is {@link ScreenKind#CONTAINER}, MenuKit's grey
 * look, which is how every standalone screen looked before 6.1.0. On an options
 * screen a panel usually has no background ({@link PanelStyle#NONE}), so
 * vanilla's backdrop (the blurred world, or the panorama) shows behind it.
 *
 * @see com.trevlar.menukit.api.panel.ScreenPanelAdapter for panels on vanilla screens
 */
public class MKScreen extends Screen {

    /**
     * Padding inside each styled panel (pixels from panel edge to content). The
     * padding applied is style-conditional via {@link Panel#interiorPadding()}:
     * this value for styled panels, {@code 0} for {@link PanelStyle#NONE}.
     */
    protected static final int PANEL_PADDING = 7;

    private final List<Panel> panels;

    /** The kind of screen this is, declared at creation; never changes. */
    private final ScreenKind screenKind;

    /** This screen's panels, placed and routed. */
    private final PanelHost host;

    /** Whether the title is drawn at the screen top with a band reserved for it. See {@link #hideTitle()}. */
    private boolean titleBand = true;

    /**
     * Optional action run on close (Escape) instead of returning to the game. Set it
     * when this screen was opened over something to restore on exit, above all a
     * live server-synced container (a {@code CustomContainerScreen}): opening a
     * plain Screen over a container makes vanilla close that container on the
     * server, so going back means opening it again. Null for the default close.
     */
    private @Nullable Runnable returnAction;

    /**
     * A container-kind screen ({@link ScreenKind#CONTAINER}): MenuKit's grey look,
     * the look every standalone screen had before 6.1.0.
     *
     * @param title  the screen title (drawn at the top unless {@link #hideTitle()}; always narrated)
     * @param panels the screen's panels, bottom to top
     * @throws IllegalArgumentException if a panel's position cannot be placed here
     *         (a second {@code main()}, or a {@code region(...)} with no main)
     */
    protected MKScreen(Component title, List<Panel> panels) {
        this(title, ScreenKind.CONTAINER, panels);
    }

    /**
     * A screen of the given kind: {@link ScreenKind#OPTIONS} for a settings screen
     * in the pause-menu look, {@link ScreenKind#CONTAINER} for MenuKit's own.
     *
     * @param title  the screen title (drawn at the top unless {@link #hideTitle()}; always narrated)
     * @param kind   what kind of screen this is, which decides how everything on it draws
     * @param panels the screen's panels, bottom to top
     * @throws IllegalArgumentException if a panel's position cannot be placed here
     *         (a second {@code main()}, or a {@code region(...)} with no main)
     */
    protected MKScreen(Component title, ScreenKind kind, List<Panel> panels) {
        super(title);
        this.screenKind = java.util.Objects.requireNonNull(kind, "kind");
        this.panels = List.copyOf(panels);
        this.host = PanelHost.standalone(kind,
                () -> new PanelHost.Frame(this.width, this.height, null),
                PanelHost.TitleBand.SCREEN_TOP);
        // An element-only main is always safe to auto-fit its height (it scrolls
        // instead of running off a small screen): the host's default.
        List<PanelPosition> positions = PanelPosition.standaloneDefaults(
                this.panels.stream().map(Panel::getPosition).toList());
        boolean hasMain = positions.stream().anyMatch(p -> p.mode() == PanelPosition.Mode.MAIN);
        for (int i = 0; i < this.panels.size(); i++) {
            Panel panel = this.panels.get(i);
            PanelPosition position = positions.get(i);
            if (position.mode() == PanelPosition.Mode.REGION && !hasMain) {
                throw new IllegalArgumentException("MenuKit: panel '" + panel.id()
                        + "' is " + position.describe() + " on a screen with no main() panel to anchor to.");
            }
            // One author, one screen: declaration order is the sequence tie-break.
            host.add(panel, position, panel.interiorPadding(), "", i);
        }
    }

    /**
     * No title band: the title is not drawn and no room is kept for it, so the
     * main panel can reach the top of the screen. The title still names the
     * screen for narration. Call from the subclass constructor.
     */
    protected final void hideTitle() {
        this.titleBand = false;
        host.titleBand(PanelHost.TitleBand.NONE);
    }

    /** The kind of screen this is, as declared when it was created. */
    public final ScreenKind screenKind() {
        return screenKind;
    }

    /** Whether the title band shows (false after {@link #hideTitle()}). */
    public boolean showsTitle() {
        return titleBand;
    }

    /**
     * Sets the action run when this screen closes via Escape (see
     * {@link #returnAction}). Subclasses also call {@link #runReturnAction()}
     * from their own "Back" chrome so Back and Escape agree.
     */
    protected void setReturnAction(@Nullable Runnable returnAction) {
        this.returnAction = returnAction;
    }

    /** Whether a return action is set (Back chrome can branch on this). */
    protected boolean hasReturnAction() {
        return returnAction != null;
    }

    /**
     * Runs the return action if one is set and returns true; otherwise false so the
     * caller can fall back to its default navigation.
     */
    protected boolean runReturnAction() {
        if (returnAction == null) return false;
        returnAction.run();
        return true;
    }

    @Override
    public void onClose() {
        if (runReturnAction()) return;
        super.onClose();
    }

    @Override
    protected void init() {
        super.init();
        // Attach the host so the registry composes it (FLOW, dim, OVERLAY) with
        // whatever is injected here, and so claims and modals see its panels.
        ScreenPanelRegistry.attachOwnHost(this, host);
        // Widget-wrapping elements (TextField, Slider) register their vanilla widget.
        // Detach-then-attach, so a resize (which clears widgets without removed())
        // re-registers them.
        host.attach(this);
        // The title draws under the panels: this renderable is added before the
        // registry's panel renderable (added right after init, on AFTER_INIT).
        this.addRenderableOnly((graphics, mouseX, mouseY, partialTick) -> {
            if (titleBand) {
                graphics.centeredText(this.font, this.title,
                        this.width / 2, RegionConstants.SCREEN_EDGE_MARGIN, 0xFFFFFFFF);
            }
        });
    }

    @Override
    public void removed() {
        host.detach(this);
        super.removed();
    }

    // Input

    /** Whether one of this screen's own panels is a shown modal. */
    private boolean ownModalUp() {
        return host.topmostModal() != null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        // An open Dropdown closes when the click falls outside it,
        // even if something else consumes the click.
        host.notifyOutsideClick(event.x(), event.y());
        boolean modal = ownModalUp();
        if (host.mouseClicked(event.x(), event.y(), event.button(), modal)) {
            return true;
        }
        // A modal claims everything: nothing behind it takes the click.
        if (modal) return true;
        // Vanilla widgets registered by the panels (Slider, TextField) need the
        // initiating click, so vanilla routing runs before the claim eat.
        if (super.mouseClicked(event, flag)) {
            return true;
        }
        // Nothing consumed it: a click one of this screen's panels claims (an opaque
        // panel's empty space, a transparent panel's solid element) stops here.
        return host.claimAt(event.x(), event.y()) != null;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean modal = ownModalUp();
        if (host.keyPressed(event.key(), event.keycode(), event.modifiers(), modal)) {
            return true;
        }
        // Escape with a modal up dismisses the topmost modal (its onEscape) instead
        // of closing the screen out from under it; eaten either way.
        if (event.key() == InputConstants.KEY_ESCAPE) {
            Panel top = host.topmostModal();
            if (top != null) {
                Runnable escape = top.getEscapeAction();
                if (escape != null) escape.run();
                return true;
            }
        }
        // Panel toggle keys: a panel built with .toggleKey(key) flips itself
        // (client-side; a standalone screen has no server menu to sync).
        for (Panel p : panels) {
            if (p.getToggleKey() >= 0 && p.getToggleKey() == event.key()) {
                p.setVisible(!p.isVisible());
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (host.mouseScrolled(mouseX, mouseY, scrollX, scrollY, ownModalUp())) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        // Every shown element hears the release (drag end), modal or not.
        host.mouseReleased(event.x(), event.y(), event.button());
        return super.mouseReleased(event);
    }

    // Panel access

    /** The panels in order, unmodifiable. */
    public List<Panel> getPanels() { return panels; }

    /** This frame's placement of {@code panel} on this screen, or {@code null} when it is not placed. */
    public PanelHost.@Nullable Placed placementOf(Panel panel) {
        return host.placedOf(panel);
    }
}
