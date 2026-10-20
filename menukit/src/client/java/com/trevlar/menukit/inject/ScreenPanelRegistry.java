package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.ChildDispatch;
import com.trevlar.menukit.api.panel.Anchor;
import com.trevlar.menukit.api.panel.AnchorPanelAdapter;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.menukit.api.panel.VanillaScreenPanelAdapter;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.Focus;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.mixin.ScreenAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.Event;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The one registry of panel placement: which panels are declared, which
 * {@link PanelHost}s exist on each open screen, and every global question about
 * them.
 *
 * <h2>Declarations</h2>
 * The three adapters ({@link ScreenPanelAdapter}, {@link VanillaScreenPanelAdapter},
 * {@link AnchorPanelAdapter}) declare here. Declaring, targeting and unregistering
 * all freeze with the rest of MenuKit's declarations ({@code Declarations.requireOpen}):
 * after client start they throw. Order is never registration order: each host sorts
 * its entries by {@code (priority, modId, sequence)}.
 *
 * <h2>Hosts per screen</h2>
 * When a screen opens, it gets up to three kinds of host, bottom to top:
 * <ol>
 *   <li><b>own</b>: a standalone screen's own panels ({@code MKScreen},
 *       a library's own screen), attached by the screen itself
 *       ({@link #attachOwnHost});</li>
 *   <li><b>context</b>: the adapters targeting this screen (container adapters on a
 *       container screen, vanilla-screen adapters on any other);</li>
 *   <li><b>anchors</b> (any screen): one host per targeted {@link Anchor} whose box is
 *       on the screen;</li>
 *   <li><b>dialogs</b> (7.2.0): the dialogs elements on the screen own, attached by
 *       the elements themselves ({@link #attachDialog}).</li>
 * </ol>
 *
 * <h2>Global questions, one answer each</h2>
 * {@link #claimAt} is the one "who takes this point" answer every suppressor and
 * dispatcher asks: a shown modal claims everything; otherwise the topmost claiming
 * panel in the one draw order read top down ({@link LayerPlan#topDown}: OVERLAY,
 * then POPOVER, then FLOW, upper host before lower), by
 * {@link PanelHost#claimsPoint}. A background panel (7.4.0) is never in that order: it
 * claims nothing, and its input comes last ({@link #dispatchBackgroundClick},
 * {@link #listenLast}). Slot hover, widget hover, list and tab hover,
 * tooltips and the MouseHandler-level input eat all reduce to it, so a new host (an
 * anchor host, a standalone host) cannot fall through a per-site gap.
 *
 * <p>Internal: consumers use the adapters; the mixins call in here.
 */
@ApiStatus.Internal
public final class ScreenPanelRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private ScreenPanelRegistry() {}

    // Declarations

    private static final List<ScreenPanelAdapter> CONTAINER = new CopyOnWriteArrayList<>();
    private static final List<VanillaScreenPanelAdapter> VANILLA = new CopyOnWriteArrayList<>();
    private static final List<AnchorPanelAdapter> ANCHORED = new CopyOnWriteArrayList<>();

    public static void declare(ScreenPanelAdapter adapter) { CONTAINER.add(adapter); }
    public static void declare(VanillaScreenPanelAdapter adapter) { VANILLA.add(adapter); }
    public static void declare(AnchorPanelAdapter adapter) { ANCHORED.add(adapter); }

    public static void withdraw(ScreenPanelAdapter adapter) { CONTAINER.remove(adapter); }
    public static void withdraw(VanillaScreenPanelAdapter adapter) { VANILLA.remove(adapter); }
    public static void withdraw(AnchorPanelAdapter adapter) { ANCHORED.remove(adapter); }

    // Hosts per screen

    /** The hosts on one open screen. */
    private static final class ScreenHosts {
        @Nullable PanelHost own;
        @Nullable PanelHost context;
        final Map<Identifier, PanelHost> anchors = new LinkedHashMap<>();
        /**
         * The dialogs elements on this screen open (7.2.0): a {@code ColorField}'s or
         * an {@code ItemPicker}'s. Topmost, made when the first one attaches
         * ({@link #attachDialog}).
         */
        @Nullable PanelHost dialogs;

        /** Every host, bottom to top. */
        List<PanelHost> all() {
            List<PanelHost> out = new ArrayList<>(3 + anchors.size());
            if (own != null) out.add(own);
            if (context != null) out.add(context);
            out.addAll(anchors.values());
            if (dialogs != null) out.add(dialogs);
            return out;
        }

        /** The hosts the registry routes input to (not own: the screen routes its own). */
        List<PanelHost> routed() {
            List<PanelHost> out = new ArrayList<>(2 + anchors.size());
            out.addAll(declared());
            if (dialogs != null) out.add(dialogs);
            return out;
        }

        /** The hosts built from declared adapters: context, then anchors. */
        List<PanelHost> declared() {
            List<PanelHost> out = new ArrayList<>(1 + anchors.size());
            if (context != null) out.add(context);
            out.addAll(anchors.values());
            return out;
        }
    }

    /**
     * Shows {@code dialog} on {@code screen}'s dialog host, the topmost host, so an
     * element can own a dialog (a {@code ColorField}'s, an {@code ItemPicker}'s)
     * without the consumer declaring it: the element attaches it from its own
     * {@code onAttach}, at the screen's init, and the dialog's {@code visibleWhen}
     * opens and closes it. Its elements are attached to the screen too. Idempotent
     * per panel.
     *
     * <p>This is not a declaration. The element was declared, and this runs per
     * screen, as a widget registers. The host is placed against the screen and draws in its
     * kind ({@link ScreenKind#of}); a {@code center()} dialog is an overlay there.
     */
    public static void attachDialog(Screen screen, Panel dialog) {
        ScreenHosts hosts = HOSTS.computeIfAbsent(screen, s -> new ScreenHosts());
        if (hosts.dialogs == null) {
            hosts.dialogs = PanelHost.vanillaScreen(ScreenKind.of(screen),
                    () -> new PanelHost.Frame(screen.width, screen.height, null));
        }
        PanelHost host = hosts.dialogs;
        boolean present = host.entries().stream().anyMatch(e -> e.panel() == dialog);
        if (!present) host.add(dialog, dialog.getPosition(), dialog.interiorPadding(), "menukit", PanelHost.nextSeq());
        ChildDispatch.detach(dialog.getElements(), screen);
        ChildDispatch.attach(dialog.getElements(), screen);
    }

    /** Undoes {@link #attachDialog}: detaches the dialog's elements and takes it off the screen. */
    public static void detachDialog(Screen screen, Panel dialog) {
        ScreenHosts hosts = HOSTS.get(screen);
        if (hosts == null || hosts.dialogs == null) return;
        ChildDispatch.detach(dialog.getElements(), screen);
        hosts.dialogs.remove(dialog);
    }

    /** Weak on the screen: a closed screen's hosts go with it. */
    private static final Map<Screen, ScreenHosts> HOSTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Attaches a standalone screen's own host, so global questions (claims, modals)
     * see its panels. Called by {@code MKScreen} and a library's own screen from
     * their {@code init}; idempotent.
     */
    public static void attachOwnHost(Screen screen, PanelHost host) {
        HOSTS.computeIfAbsent(screen, s -> new ScreenHosts()).own = host;
    }

    /** The screen's own host, or {@code null}. */
    public static @Nullable PanelHost ownHost(@Nullable Screen screen) {
        if (screen == null) return null;
        ScreenHosts h = HOSTS.get(screen);
        return h == null ? null : h.own;
    }

    /** Every host on {@code screen}, bottom to top (empty for none). */
    public static List<PanelHost> hostsOn(@Nullable Screen screen) {
        if (screen == null) return List.of();
        ScreenHosts h = HOSTS.get(screen);
        return h == null ? List.of() : h.all();
    }

    /** The hosts the registry routes input to on {@code screen}, bottom to top. */
    private static List<PanelHost> routedOn(@Nullable Screen screen) {
        if (screen == null) return List.of();
        ScreenHosts h = HOSTS.get(screen);
        return h == null ? List.of() : h.routed();
    }

    /** The context host on {@code screen} (the adapters targeting it), or {@code null}. */
    public static @Nullable PanelHost contextHost(@Nullable Screen screen) {
        if (screen == null) return null;
        ScreenHosts h = HOSTS.get(screen);
        return h == null ? null : h.context;
    }

    private static @Nullable Screen currentScreen() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : mc.gui.screen();
    }

    // Screen open

    private static volatile boolean checkpointRun = false;

    /** Registers the one {@code AFTER_INIT} listener. Called once from {@code MenuKitClient}. */
    public static void init() {
        ScreenEvents.AFTER_INIT.register(ScreenPanelRegistry::onScreenInit);
    }

    /**
     * Builds the screen's context and anchor hosts from the declared adapters,
     * attaches their elements, and wires render (non-container screens; container
     * screens render through {@link ContainerScreenLayers}) and input.
     */
    private static void onScreenInit(Minecraft client, Screen screen, int w, int h) {
        if (!checkpointRun) {
            checkpointRun = true;
            requireTargeting();
        }

        ScreenHosts hosts = HOSTS.computeIfAbsent(screen, s -> new ScreenHosts());
        hosts.anchors.clear();
        if (screen instanceof AbstractContainerScreen<?> acs) {
            PanelHost context = PanelHost.container(() -> containerFrame(acs));
            for (ScreenPanelAdapter a : CONTAINER) {
                if (a.matches(acs)) context.add(a.getPanel(), a.getPanel().getPosition(), a.getPadding(), a.modId(), a.seq());
            }
            hosts.context = context.isEmpty() ? null : context;
        } else {
            // Any other screen: a Default-look screen when it is vanilla's, the declared
            // kind when it is an MKScreen (ScreenKind.of, the one rule).
            PanelHost context = PanelHost.vanillaScreen(ScreenKind.of(screen),
                    () -> new PanelHost.Frame(screen.width, screen.height, null));
            for (VanillaScreenPanelAdapter a : VANILLA) {
                if (a.matches(screen)) context.add(a.getPanel(), a.getPanel().getPosition(), a.getPadding(), a.modId(), a.seq());
            }
            hosts.context = context.isEmpty() ? null : context;
        }
        // Anchors, on any screen: one host per targeted anchor, placed around the box
        // its library supplies this frame (none while the box is absent).
        ScreenKind kind = ScreenKind.of(screen);
        for (AnchorPanelAdapter a : ANCHORED) {
            if (a.getTargets() == null) continue;
            for (Anchor anchor : a.getTargets()) {
                PanelHost host = hosts.anchors.computeIfAbsent(anchor.id(),
                        id -> PanelHost.anchor(kind, () -> anchorFrame(screen, anchor)));
                host.add(a.getPanel(), a.getPanel().getPosition(), a.getPadding(), a.modId(), a.seq());
            }
        }

        // Element lifecycle for the hosts the registry builds (the own host attaches in
        // its screen's init). Widget-wrapping elements register their vanilla widget,
        // and an element that owns a dialog puts it on the dialog host, so the routed
        // list is read after this.
        List<PanelHost> declared = hosts.declared();
        for (PanelHost host : declared) host.attach(screen);
        ScreenEvents.remove(screen).register(removed -> {
            for (PanelHost host : declared) host.detach(removed);
        });

        List<PanelHost> routed = hosts.routed();
        if (routed.isEmpty() && hosts.own == null) return;

        // Render: container screens compose through ContainerScreenLayers (the slot
        // pass sits between FLOW and OVERLAY). Every other screen composes here, in
        // one renderable, so its own panels and injected ones share one LayerPlan.
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            Renderable composite = (graphics, mouseX, mouseY, partialTick) ->
                    renderAll(screen, graphics, mouseX, mouseY);
            ((ScreenAccessor) screen).mk$addRenderableOnly(composite);
        }

        if (routed.isEmpty()) return;

        // Input the MouseHandler-level claim did not take: element dispatch for points
        // no panel claims (a transparent panel's click-through elements), plus closing
        // any popover the click fell outside of. A consumed click is eaten from vanilla.
        // Both walk the panels in the one draw order read top down, across the
        // routed hosts, so a popover on a lower host is offered the click first.
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
            for (PanelHost host : routed) host.notifyOutsideClick(event.x(), event.y());
            if (PanelHost.mouseClicked(routed, event.x(), event.y(), event.button(), modalUpOn(s))) {
                Focus.blurOnOutsideBounds(s, event.x(), event.y());
                return false;
            }
            return true;
        });
        ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, hAmount, vAmount) ->
                !PanelHost.mouseScrolled(routed, mouseX, mouseY, hAmount, vAmount, modalUpOn(s)));
        // Last of all (7.4.0): a turn nobody used goes to the background panels' elements,
        // then scrolls the container region. A click the screen's widgets declined goes to
        // the background panels too: on a container screen from its own mouseClicked
        // (MKBackgroundClickMixin, which always answers "used"), elsewhere here.
        listenLast(ScreenMouseEvents.afterMouseScroll(screen));
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            ScreenMouseEvents.afterMouseClick(screen).register((s, event, consumed) ->
                    consumed || dispatchBackgroundClick(s, event.x(), event.y(), event.button()));
        }
        ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
            for (PanelHost host : routed) host.mouseReleased(event.x(), event.y(), event.button());
            return true;
        });
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyEvent) -> {
            boolean modal = modalUpOn(s);
            for (int i = routed.size() - 1; i >= 0; i--) {
                if (routed.get(i).keyPressed(keyEvent.key(), keyEvent.keycode(), keyEvent.modifiers(), modal)) {
                    return false;
                }
            }
            // Escape with an injected modal up dismisses the topmost modal (its
            // onEscape; ConfirmDialog/AlertDialog wire theirs) instead of closing the
            // screen out from under it. Eaten either way while the modal is up.
            Panel topModal = topmostModal(routed);
            if (topModal == null) return true;
            if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
                Runnable escape = topModal.getEscapeAction();
                if (escape != null) escape.run();
                return false;
            }
            // Under an injected modal a key the panels did not take goes to the
            // screen's focused widget when MenuKit placed it (a dialog's text field, a
            // control's focus stand-in), then is eaten, so the screen under the modal
            // never acts on it. Vanilla's global keys (F11, F3 combinations, the
            // narrator hotkey, screenshots) are handled outside Screen.keyPressed and
            // are never gated.
            var focused = s.getFocused();
            if (focused != null && Focus.isManaged(s, focused)) focused.keyPressed(keyEvent);
            return false;
        });
    }

    /**
     * Vanilla-screen and anchor adapters must name their targets (an everywhere
     * default makes no sense on the title screen, and "any anchor" is not a target).
     * Checked once, at the first screen open, and loud.
     */
    private static void requireTargeting() {
        List<String> missing = new ArrayList<>();
        for (VanillaScreenPanelAdapter a : VANILLA) {
            if (!a.isTargetingDeclared()) missing.add("VanillaScreenPanelAdapter " + a.getPanel().id());
        }
        for (AnchorPanelAdapter a : ANCHORED) {
            if (!a.isTargetingDeclared()) missing.add("AnchorPanelAdapter " + a.getPanel().id());
        }
        if (missing.isEmpty()) return;
        String message = "MenuKit: adapters constructed but never targeted (.on(...)): "
                + String.join(", ", missing) + ". Add the missing .on(...) call(s).";
        LOGGER.error("[ScreenPanelRegistry] {}", message);
        throw new IllegalStateException(message);
    }

    // Frames

    /**
     * A container screen's frame: the menu frame, extended by the screen's chrome
     * (creative tab rows, an open recipe book), so "the top of the menu" means the
     * top of what the player sees. Read per frame: leftPos/topPos move on resize and
     * recipe-book toggle.
     */
    private static PanelHost.Frame containerFrame(AbstractContainerScreen<?> screen) {
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        MenuChrome.ChromeExtents chrome = MenuChrome.of(screen);
        Reference frame = new Reference(
                acc.mk$getLeftPos() - chrome.left(),
                acc.mk$getTopPos() - chrome.top(),
                acc.mk$getImageWidth() + chrome.left() + chrome.right(),
                acc.mk$getImageHeight() + chrome.top() + chrome.bottom());
        return new PanelHost.Frame(screen.width, screen.height, frame, screenFrame(acc));
    }

    /**
     * The container screen's own frame, without chrome: {@code leftPos, topPos,
     * imageWidth, imageHeight}. A side-region panel's frame band runs to its edge
     * ({@code topPos + imageHeight} for a top-aligned one), on the container host and
     * on every anchor host alike.
     */
    private static Reference screenFrame(AbstractContainerScreenAccessor acc) {
        return new Reference(acc.mk$getLeftPos(), acc.mk$getTopPos(),
                acc.mk$getImageWidth(), acc.mk$getImageHeight());
    }

    /**
     * One anchor's frame: the box its library supplies on this screen this frame, or
     * {@code null} when the anchor is not there. On a container screen a side-region
     * panel's band runs to the screen's frame; elsewhere there is no frame to run to.
     */
    private static PanelHost.@Nullable Frame anchorFrame(Screen screen, Anchor anchor) {
        Reference box = anchor.box(screen);
        if (box == null) return null;
        Reference band = screen instanceof AbstractContainerScreen<?> acs
                ? screenFrame((AbstractContainerScreenAccessor) acs) : null;
        return new PanelHost.Frame(screen.width, screen.height, box, band);
    }

    // Rendering

    /** A non-container screen's whole LayerPlan: FLOW, POPOVER, dim, OVERLAY for every host. */
    private static void renderAll(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        LayerPlan.compose(hostsOn(screen), graphics, mouseX, mouseY, screen.width, screen.height,
                pointerPolicy(screen, mouseX, mouseY));
    }

    /**
     * The BACKGROUND layer of the registry's hosts (7.4.0), in the screen's background
     * pass ({@link ScreenBackground}): before anything else of the frame, so everything
     * draws over it. Its slots are placed here and drawn in vanilla's slot pass.
     */
    public static void renderBackground(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<PanelHost> routed = routedOn(screen);
        if (routed.isEmpty()) return;
        renderBackground(routed, graphics, mouseX, mouseY, pointerPolicy(screen, mouseX, mouseY));
    }

    /** {@link #renderBackground(Screen, GuiGraphicsExtractor, int, int)} over {@code hosts} (bottom to top). */
    public static void renderBackground(List<PanelHost> hosts, GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                        PanelHost.PointerPolicy pointer) {
        PresentedSlots.background(true);
        try {
            LayerPlan.render(hosts, LayerPlan.Layer.BACKGROUND, graphics, mouseX, mouseY, pointer);
        } finally {
            PresentedSlots.background(false);
        }
    }

    /**
     * Layer 2 of {@link ContainerScreenLayers}: the FLOW layer of the registry's
     * hosts (context, then anchors), then the POPOVER layer of every host on the
     * screen, the screen's own included. The screen's own FLOW (a library's own
     * screen's panels) drew earlier, under vanilla's slots.
     * Popovers draw here, before the created slots, so a popover's own slots draw on
     * its background as a FLOW panel's do.
     */
    public static void renderFlow(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                  int mouseX, int mouseY) {
        PanelHost.PointerPolicy pointer = pointerPolicy(screen, mouseX, mouseY);
        LayerPlan.render(routedOn(screen), LayerPlan.Layer.FLOW, graphics, mouseX, mouseY, pointer);
        LayerPlan.render(hostsOn(screen), LayerPlan.Layer.POPOVER, graphics, mouseX, mouseY, pointer);
    }

    /**
     * Every panel {@link ContainerScreenLayers} draws after vanilla's slot pass, in the
     * order it draws them: the registry hosts' FLOW, then every host's POPOVER, then
     * every host's OVERLAY. What its place pass lays out before the slot pass (7.3.0).
     */
    public static List<LayerPlan.Drawn> drawnAfterSlots(AbstractContainerScreen<?> screen) {
        List<LayerPlan.Drawn> out = new ArrayList<>(LayerPlan.drawOrder(routedOn(screen), LayerPlan.Layer.FLOW));
        out.addAll(LayerPlan.drawOrder(hostsOn(screen), LayerPlan.Layer.POPOVER));
        out.addAll(LayerPlan.drawOrder(hostsOn(screen), LayerPlan.Layer.OVERLAY));
        return out;
    }

    /** Layer 4 of {@link ContainerScreenLayers}: the dim, then OVERLAY for every host on the screen. */
    public static void renderOverlay(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                     int mouseX, int mouseY) {
        LayerPlan.dimAndOverlay(hostsOn(screen), graphics, mouseX, mouseY, screen.width, screen.height,
                pointerPolicy(screen, mouseX, mouseY));
    }

    /**
     * Who owns the pointer this frame: a panel renders live (hover, tooltips) when
     * no modal it does not belong to is up and nothing above it claims the cursor.
     * Everything else renders with the inert {@code -1} sentinel.
     */
    public static PanelHost.PointerPolicy pointerPolicy(@Nullable Screen screen, double mouseX, double mouseY) {
        Claim claim = claimAt(screen, mouseX, mouseY);
        boolean modal = modalUpOn(screen);
        return (host, placed) -> {
            if (modal && !placed.panel().tracksAsModal()) return false;
            if (claim == null || claim.placed() == null) return true;
            return claim.host() == host && claim.placed().entry() == placed.entry();
        };
    }

    // The one claim answer

    /**
     * What takes a screen point.
     *
     * @param host          the host of the claiming panel
     * @param placed        the claiming panel's placement, or {@code null} when a shown
     *                      modal claims the point without being over it (the point is
     *                      inert and goes to nobody)
     * @param yieldsToSlot  the claimant's own live slot is under the point, so the
     *                      point goes to vanilla's slot machinery for that slot
     */
    public record Claim(PanelHost host, PanelHost.@Nullable Placed placed, boolean yieldsToSlot) {}

    /** The one claim answer for a point on {@code screen}: {@link #claimAt(List, double, double)} over its hosts. */
    public static @Nullable Claim claimAt(@Nullable Screen screen, double mouseX, double mouseY) {
        return claimAt(hostsOn(screen), mouseX, mouseY);
    }

    /**
     * The one claim answer for a point over {@code hosts} (bottom to top). A shown
     * modal (the topmost {@code tracksAsModal} panel) claims every point: its own
     * where it is placed and claims, otherwise an inert claim. Without a modal: the
     * topmost claiming panel in the draw order read top down
     * ({@link LayerPlan#topDown}): OVERLAY above POPOVER above FLOW, and within a
     * layer an upper host above a lower one and a later entry above an earlier one.
     * What is drawn on top claims, since both read the same order.
     */
    public static @Nullable Claim claimAt(List<PanelHost> hosts, double mouseX, double mouseY) {
        if (hosts.isEmpty()) return null;

        for (int i = hosts.size() - 1; i >= 0; i--) {
            PanelHost host = hosts.get(i);
            Panel modal = host.topmostModal();
            if (modal == null) continue;
            PanelHost.Placed placed = host.placedOf(modal);
            if (placed != null && host.claimsPoint(placed, mouseX, mouseY)) {
                return new Claim(host, placed, host.yieldsToSlot(placed, mouseX, mouseY));
            }
            return new Claim(host, null, false);
        }

        for (LayerPlan.Drawn d : LayerPlan.topDown(hosts)) {
            PanelHost host = d.host();
            if (host.claimsPoint(d.placed(), mouseX, mouseY)) {
                return new Claim(host, d.placed(), host.yieldsToSlot(d.placed(), mouseX, mouseY));
            }
        }
        return null;
    }

    /**
     * Whether a slot under the point is inert: something claims the point, and the
     * claimant's own slot is not what is there. Asked by the {@code getHoveredSlot}
     * hook before any slot resolution runs, so a panel over a slot blocks its hover,
     * its click (vanilla routes the click to the hovered slot) and its tooltip.
     */
    public static boolean slotInertAt(@Nullable Screen screen, double mouseX, double mouseY) {
        Claim claim = claimAt(screen, mouseX, mouseY);
        return claim != null && !claim.yieldsToSlot();
    }

    /**
     * Whether vanilla content at the point that belongs to the screen itself (its
     * widgets, list rows, creative tabs, tooltips) is inert. Same claim, one
     * exception: a standalone screen's own panels do not make that screen's own
     * widgets inert. They are one author's layout; the screen routes its own input.
     * A modal still claims everything.
     */
    public static boolean screenContentInertAt(@Nullable Screen screen, double mouseX, double mouseY) {
        Claim claim = claimAt(screen, mouseX, mouseY);
        if (claim == null || claim.yieldsToSlot()) return false;
        if (claim.placed() != null && claim.host() == ownHost(screen)) return false;
        return true;
    }

    // Modal and dim

    private static @Nullable Panel topmostModal(List<PanelHost> hosts) {
        for (int i = hosts.size() - 1; i >= 0; i--) {
            Panel modal = hosts.get(i).topmostModal();
            if (modal != null) return modal;
        }
        return null;
    }

    /** Whether a shown {@code tracksAsModal} panel is on {@code screen}, in any host. */
    public static boolean modalUpOn(@Nullable Screen screen) {
        return topmostModal(hostsOn(screen)) != null;
    }

    /** {@link #modalUpOn} for the current screen. */
    public static boolean hasAnyVisibleModalTracking() {
        return modalUpOn(currentScreen());
    }

    /**
     * Whether an injected modal gates window-level input on the current screen (the
     * cursor lock; the keyboard gate is the {@code allowKeyPress} listener above). A
     * standalone screen's own modal is left to that screen, which routes keys to the
     * modal itself (a dialog's text field must still type).
     */
    public static boolean modalGatesInput() {
        return topmostModal(routedOn(currentScreen())) != null;
    }

    // MouseHandler-level dispatch (claimed input)

    /**
     * Press at the MouseHandler, before any screen sees it. When a panel from the
     * registry's hosts claims the point, the click goes to that panel's elements and
     * vanilla never sees it. Returns {@code false} (vanilla proceeds) when nothing
     * claims, when the claimant is the screen's own host (the screen routes its own
     * panels, and its vanilla widgets need the click), or when the claimant's own
     * slot is under the point.
     *
     * @return whether to eat the press
     */
    public static boolean dispatchCoveredClick(Screen screen, double mouseX, double mouseY, int button) {
        Claim claim = claimAt(screen, mouseX, mouseY);
        if (claim == null || claim.host() == ownHost(screen) || claim.yieldsToSlot()) return false;
        for (PanelHost host : routedOn(screen)) host.notifyOutsideClick(mouseX, mouseY);
        if (claim.placed() != null) {
            claim.host().mouseClicked(claim.placed(), mouseX, mouseY, button);
            // Vanilla never sees this click, so the natural focus hand-over can't
            // run: blur a focused MenuKit widget the click landed outside of. (Outside a
            // modal, placed is null and the modal's own widgets keep focus.)
            Focus.blurOnOutsideBounds(screen, mouseX, mouseY);
        }
        return true;
    }

    /**
     * Release, symmetric with the press: eaten exactly when the press would have
     * been, and then offered to every routed element (not hit-tested) so a drag
     * ends wherever the cursor is. Vanilla's creative screen picks tabs on release,
     * which is why a modal must eat releases too.
     */
    public static boolean dispatchCoveredRelease(Screen screen, double mouseX, double mouseY, int button) {
        Claim claim = claimAt(screen, mouseX, mouseY);
        if (claim == null || claim.host() == ownHost(screen) || claim.yieldsToSlot()) return false;
        for (PanelHost host : routedOn(screen)) host.mouseReleased(mouseX, mouseY, button);
        return true;
    }

    /**
     * Scroll: routed to the claiming panel and eaten, under the same rule as a press. A
     * turn the claimant's elements do not use is offered to the container region straight
     * away (7.4.0, {@link ContainerRegions}); the region's own panels claim nothing, so over
     * them a turn goes on to the screen and every other handler first ({@link #listenLast}).
     */
    public static boolean dispatchCoveredScroll(Screen screen, double mouseX, double mouseY,
                                                double scrollX, double scrollY) {
        Claim claim = claimAt(screen, mouseX, mouseY);
        if (claim == null || claim.host() == ownHost(screen) || claim.yieldsToSlot()) return false;
        if (claim.placed() != null) {
            boolean used = claim.host().mouseScrolled(claim.placed(), mouseX, mouseY, scrollX, scrollY);
            if (!used) ContainerRegions.wheel(screen, scrollY);
        }
        return true;
    }

    // Background panels' input (7.4.0): last

    /** The after-scroll phase the last listener runs in: after the default one. */
    private static final Identifier LAST = Identifier.fromNamespaceAndPath("menukit", "after_everyone");

    /**
     * A screen's last wheel listener, in a phase after Fabric's default one, so it hears
     * every other listener's answer whenever that listener registered: a turn neither the
     * screen nor any of them used goes to the background panels' elements, then scrolls
     * the container region ({@link ContainerRegions#wheel}).
     */
    public static void listenLast(Event<ScreenMouseEvents.AfterMouseScroll> after) {
        after.addPhaseOrdering(Event.DEFAULT_PHASE, LAST);
        after.register(LAST, (s, x, y, hAmount, vAmount, consumed) ->
                consumed || dispatchBackgroundScroll(s, x, y, hAmount, vAmount) || ContainerRegions.wheel(s, vAmount));
    }

    /**
     * A click nothing on the screen took (its widgets, every other mod's, every panel
     * above), offered to the background panels, topmost first. On a slot a background
     * panel shows it stays vanilla's (the slot is clicked as any slot is). With a modal
     * up they take nothing.
     *
     * @return whether an element took it
     */
    public static boolean dispatchBackgroundClick(Screen screen, double mouseX, double mouseY, int button) {
        if (!dispatchBackgroundClick(routedOn(screen), mouseX, mouseY, button, modalUpOn(screen))) return false;
        Focus.blurOnOutsideBounds(screen, mouseX, mouseY);
        return true;
    }

    /** {@link #dispatchBackgroundClick(Screen, double, double, int)} over {@code hosts} (bottom to top). */
    public static boolean dispatchBackgroundClick(List<PanelHost> hosts, double mouseX, double mouseY, int button,
                                                  boolean modalUp) {
        if (modalUp) return false;
        List<LayerPlan.Drawn> order = LayerPlan.drawOrder(hosts, LayerPlan.Layer.BACKGROUND);
        for (int i = order.size() - 1; i >= 0; i--) {
            LayerPlan.Drawn d = order.get(i);
            if (d.host().yieldsToSlot(d.placed(), mouseX, mouseY)) return false;
            if (d.host().mouseClicked(d.placed(), mouseX, mouseY, button)) return true;
        }
        return false;
    }

    /** A turn of the wheel nobody used, offered to the background panels, topmost first. */
    public static boolean dispatchBackgroundScroll(Screen screen, double mouseX, double mouseY,
                                                   double scrollX, double scrollY) {
        if (modalUpOn(screen)) return false;
        List<LayerPlan.Drawn> order = LayerPlan.drawOrder(routedOn(screen), LayerPlan.Layer.BACKGROUND);
        for (int i = order.size() - 1; i >= 0; i--) {
            LayerPlan.Drawn d = order.get(i);
            if (d.host().mouseScrolled(d.placed(), mouseX, mouseY, scrollX, scrollY)) return true;
        }
        return false;
    }
}
