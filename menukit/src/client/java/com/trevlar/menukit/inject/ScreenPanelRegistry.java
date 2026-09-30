package com.trevlar.menukit.inject;

import com.trevlar.menukit.core.MKFocus;
import com.trevlar.menukit.core.Panel;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.mixin.ScreenAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
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
 * The one registry of panel placement (§0065): which panels are declared, which
 * {@link PanelHost}s exist on each open screen, and every global question about
 * them.
 *
 * <h2>Declarations</h2>
 * The three adapters ({@link ScreenPanelAdapter}, {@link VanillaScreenPanelAdapter},
 * {@link SlotGroupPanelAdapter}) declare here. Declaring, targeting and unregistering
 * all freeze with the rest of MenuKit's declarations ({@code Declarations.requireOpen}):
 * after client start they throw. Order is never registration order: each host sorts
 * its entries by {@code (priority, modId, sequence)}.
 *
 * <h2>Hosts per screen</h2>
 * When a screen opens, it gets up to three kinds of host, bottom to top:
 * <ol>
 *   <li><b>own</b>: a standalone screen's own panels ({@code MKScreen},
 *       {@code MKCHandledScreen}), attached by the screen itself
 *       ({@link #attachOwnHost});</li>
 *   <li><b>context</b>: the adapters targeting this screen (container adapters on a
 *       container screen, vanilla-screen adapters on any other);</li>
 *   <li><b>slot groups</b> (container screens): one host per targeted slot group.</li>
 * </ol>
 *
 * <h2>Global questions, one answer each</h2>
 * {@link #claimAt} is the one "who takes this point" answer every suppressor and
 * dispatcher asks: a shown modal claims everything; otherwise the topmost claiming
 * panel, OVERLAY layer before FLOW, upper host before lower, by
 * {@link PanelHost#claimsPoint}. Slot hover, widget hover, list and tab hover,
 * tooltips and the MouseHandler-level input eat all reduce to it, so a new host (the
 * slot-group host and the standalone hosts are new to it in 6.0.0) cannot fall
 * through a per-site gap.
 *
 * <p>Internal: consumers use the adapters; the mixins call in here.
 */
@ApiStatus.Internal
public final class ScreenPanelRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private ScreenPanelRegistry() {}

    // ── Declarations ───────────────────────────────────────────────────

    private static final List<ScreenPanelAdapter> CONTAINER = new CopyOnWriteArrayList<>();
    private static final List<VanillaScreenPanelAdapter> VANILLA = new CopyOnWriteArrayList<>();
    private static final List<SlotGroupPanelAdapter> SLOT_GROUP = new CopyOnWriteArrayList<>();

    static void declare(ScreenPanelAdapter adapter) { CONTAINER.add(adapter); }
    static void declare(VanillaScreenPanelAdapter adapter) { VANILLA.add(adapter); }
    static void declare(SlotGroupPanelAdapter adapter) { SLOT_GROUP.add(adapter); }

    static void withdraw(ScreenPanelAdapter adapter) { CONTAINER.remove(adapter); }
    static void withdraw(VanillaScreenPanelAdapter adapter) { VANILLA.remove(adapter); }
    static void withdraw(SlotGroupPanelAdapter adapter) { SLOT_GROUP.remove(adapter); }

    // ── Hosts per screen ───────────────────────────────────────────────

    /** The hosts on one open screen. */
    private static final class ScreenHosts {
        @Nullable PanelHost own;
        @Nullable PanelHost context;
        final Map<SlotGroupId, PanelHost> slotGroups = new LinkedHashMap<>();

        /** Every host, bottom to top. */
        List<PanelHost> all() {
            List<PanelHost> out = new ArrayList<>(2 + slotGroups.size());
            if (own != null) out.add(own);
            if (context != null) out.add(context);
            out.addAll(slotGroups.values());
            return out;
        }

        /** The hosts the registry routes input to (not own: the screen routes its own). */
        List<PanelHost> routed() {
            List<PanelHost> out = new ArrayList<>(1 + slotGroups.size());
            if (context != null) out.add(context);
            out.addAll(slotGroups.values());
            return out;
        }
    }

    /** Weak on the screen: a closed screen's hosts go with it. */
    private static final Map<Screen, ScreenHosts> HOSTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Attaches a standalone screen's own host, so global questions (claims, modals)
     * see its panels. Called by {@code MKScreen} and {@code MKCHandledScreen} from
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

    // ── Screen open ────────────────────────────────────────────────────

    private static volatile boolean checkpointRun = false;

    /** Registers the one {@code AFTER_INIT} listener. Called once from {@code MKClient}. */
    public static void init() {
        ScreenEvents.AFTER_INIT.register(ScreenPanelRegistry::onScreenInit);
    }

    /**
     * Builds the screen's context and slot-group hosts from the declared adapters,
     * attaches their elements, and wires render (non-container screens; container
     * screens render through {@link ContainerScreenLayers}) and input.
     */
    private static void onScreenInit(Minecraft client, Screen screen, int w, int h) {
        if (!checkpointRun) {
            checkpointRun = true;
            requireTargeting();
        }

        ScreenHosts hosts = HOSTS.computeIfAbsent(screen, s -> new ScreenHosts());
        hosts.slotGroups.clear();
        if (screen instanceof AbstractContainerScreen<?> acs) {
            PanelHost context = PanelHost.container(() -> containerFrame(acs));
            for (ScreenPanelAdapter a : CONTAINER) {
                if (a.matches(acs)) context.add(a.getPanel(), a.getPanel().getPosition(), a.getPadding(), a.modId(), a.seq());
            }
            hosts.context = context.isEmpty() ? null : context;
            for (SlotGroupPanelAdapter a : SLOT_GROUP) {
                if (a.getTargets() == null) continue;
                for (SlotGroupId id : a.getTargets()) {
                    PanelHost group = hosts.slotGroups.computeIfAbsent(id,
                            gid -> PanelHost.slotGroup(() -> slotGroupFrame(acs, gid)));
                    group.add(a.getPanel(), a.getPanel().getPosition(), a.getPadding(), a.modId(), a.seq());
                }
            }
        } else {
            PanelHost context = PanelHost.vanillaScreen(() -> new PanelHost.Frame(screen.width, screen.height, null));
            for (VanillaScreenPanelAdapter a : VANILLA) {
                if (a.matches(screen)) context.add(a.getPanel(), a.getPanel().getPosition(), a.getPadding(), a.modId(), a.seq());
            }
            hosts.context = context.isEmpty() ? null : context;
        }

        List<PanelHost> routed = hosts.routed();
        if (routed.isEmpty() && hosts.own == null) return;

        // Element lifecycle for the hosts the registry owns (the own host attaches in
        // its screen's init). Widget-wrapping elements register their vanilla widget.
        for (PanelHost host : routed) host.attach(screen);
        ScreenEvents.remove(screen).register(removed -> {
            for (PanelHost host : routed) host.detach(removed);
        });

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
        // no panel claims (a transparent panel's click-through elements), plus the
        // popover janitor. A consumed click is eaten from vanilla.
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
            for (PanelHost host : routed) host.notifyOutsideClick(event.x(), event.y());
            boolean modal = modalUpOn(s);
            for (int i = routed.size() - 1; i >= 0; i--) {
                if (routed.get(i).mouseClicked(event.x(), event.y(), event.button(), modal)) {
                    MKFocus.blurOnOutsideBounds(s, event.x(), event.y());
                    return false;
                }
            }
            return true;
        });
        ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, hAmount, vAmount) -> {
            boolean modal = modalUpOn(s);
            for (int i = routed.size() - 1; i >= 0; i--) {
                if (routed.get(i).mouseScrolled(mouseX, mouseY, hAmount, vAmount, modal)) return false;
            }
            return true;
        });
        ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
            for (PanelHost host : routed) host.mouseReleased(event.x(), event.y(), event.button());
            return true;
        });
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyEvent) -> {
            boolean modal = modalUpOn(s);
            for (int i = routed.size() - 1; i >= 0; i--) {
                if (routed.get(i).keyPressed(keyEvent.key(), keyEvent.scancode(), keyEvent.modifiers(), modal)) {
                    return false;
                }
            }
            // Escape with an injected modal up dismisses the topmost modal (its
            // onEscape; ConfirmDialog/AlertDialog wire theirs) instead of closing the
            // screen out from under it. Eaten either way while the modal is up.
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
                Panel topModal = topmostModal(routed);
                if (topModal != null) {
                    Runnable escape = topModal.getEscapeAction();
                    if (escape != null) escape.run();
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Vanilla-screen and slot-group adapters must name their targets (an everywhere
     * default makes no sense on the title screen, and "any slot group" is not a
     * target). Checked once, at the first screen open, and loud.
     */
    private static void requireTargeting() {
        List<String> missing = new ArrayList<>();
        for (VanillaScreenPanelAdapter a : VANILLA) {
            if (!a.isTargetingDeclared()) missing.add("VanillaScreenPanelAdapter " + a.getPanel().getId());
        }
        for (SlotGroupPanelAdapter a : SLOT_GROUP) {
            if (!a.isTargetingDeclared()) missing.add("SlotGroupPanelAdapter " + a.getPanel().getId());
        }
        if (missing.isEmpty()) return;
        String message = "MenuKit: adapters constructed but never targeted (.on(...)): "
                + String.join(", ", missing) + ". Add the missing .on(...) call(s).";
        LOGGER.error("[ScreenPanelRegistry] {}", message);
        throw new IllegalStateException(message);
    }

    // ── Frames ─────────────────────────────────────────────────────────

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
        return new PanelHost.Frame(screen.width, screen.height, frame);
    }

    /**
     * One slot group's frame: the box around its slots this frame (re-resolved every
     * time, since creative tab switches and dynamic menus change {@code menu.slots}),
     * or {@code null} when the group is not on this menu right now. Each group gets
     * its own box: a created group declaring a vanilla category never stretches that
     * category's box.
     */
    private static PanelHost.@Nullable Frame slotGroupFrame(AbstractContainerScreen<?> screen, SlotGroupId id) {
        for (ResolvedSlotGroup group : SlotGroupCategories.groups(screen.getMenu())) {
            if (!group.id().equals(id) || group.slots().isEmpty()) continue;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (Slot slot : group.slots()) {
                minX = Math.min(minX, slot.x);
                minY = Math.min(minY, slot.y);
                maxX = Math.max(maxX, slot.x + 16);
                maxY = Math.max(maxY, slot.y + 16);
            }
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
            Reference box = new Reference(acc.mk$getLeftPos() + minX, acc.mk$getTopPos() + minY,
                    maxX - minX, maxY - minY);
            return new PanelHost.Frame(screen.width, screen.height, box);
        }
        return null;
    }

    // ── Rendering ──────────────────────────────────────────────────────

    /** A non-container screen's whole LayerPlan: FLOW, dim, OVERLAY for every host. */
    private static void renderAll(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        LayerPlan.compose(hostsOn(screen), graphics, mouseX, mouseY, screen.width, screen.height,
                pointerPolicy(screen, mouseX, mouseY));
    }

    /**
     * Layer 2 of {@link ContainerScreenLayers}: the FLOW layer of the registry's
     * hosts (context, then slot groups). The screen's own FLOW (an
     * {@code MKCHandledScreen}'s panels) drew earlier, under vanilla's slots.
     */
    public static void renderFlow(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics,
                                  int mouseX, int mouseY) {
        PanelHost.PointerPolicy pointer = pointerPolicy(screen, mouseX, mouseY);
        for (PanelHost host : routedOn(screen)) {
            host.render(graphics, mouseX, mouseY, LayerPlan.Layer.FLOW, pointer);
        }
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

    // ── The one claim answer ───────────────────────────────────────────

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

    /**
     * The one claim answer for a point on {@code screen}. A shown modal (the topmost
     * {@code tracksAsModal} panel) claims every point: its own where it is placed and
     * claims, otherwise an inert claim. Without a modal: the topmost claiming panel,
     * the OVERLAY layer above FLOW, an upper host above a lower one, a later entry
     * above an earlier one.
     */
    public static @Nullable Claim claimAt(@Nullable Screen screen, double mouseX, double mouseY) {
        List<PanelHost> hosts = hostsOn(screen);
        if (hosts.isEmpty()) return null;

        for (int i = hosts.size() - 1; i >= 0; i--) {
            PanelHost host = hosts.get(i);
            Panel modal = host.topmostModal();
            if (modal == null) continue;
            PanelHost.Placed placed = host.placedOf(modal);
            if (placed != null && PanelHost.claimsPoint(placed, mouseX, mouseY)) {
                return new Claim(host, placed, host.yieldsToSlot(placed, mouseX, mouseY));
            }
            return new Claim(host, null, false);
        }

        for (LayerPlan.Layer layer : new LayerPlan.Layer[]{LayerPlan.Layer.OVERLAY, LayerPlan.Layer.FLOW}) {
            for (int i = hosts.size() - 1; i >= 0; i--) {
                PanelHost host = hosts.get(i);
                PanelHost.Placed placed = host.claimAt(mouseX, mouseY, layer);
                if (placed != null) {
                    return new Claim(host, placed, host.yieldsToSlot(placed, mouseX, mouseY));
                }
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

    // ── Modal and dim ──────────────────────────────────────────────────

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
     * keyboard gate and the cursor lock). A standalone screen's own modal is left to
     * that screen, which routes keys to the modal itself (a dialog's text field must
     * still type).
     */
    public static boolean modalGatesInput() {
        return topmostModal(routedOn(currentScreen())) != null;
    }

    // ── MouseHandler-level dispatch (claimed input) ────────────────────

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
            // run: blur a focused MK widget the click landed outside of. (Outside a
            // modal, placed is null and the modal's own widgets keep focus.)
            MKFocus.blurOnOutsideBounds(screen, mouseX, mouseY);
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

    /** Scroll: routed to the claiming panel and eaten, under the same rule as a press. */
    public static boolean dispatchCoveredScroll(Screen screen, double mouseX, double mouseY,
                                                double scrollX, double scrollY) {
        Claim claim = claimAt(screen, mouseX, mouseY);
        if (claim == null || claim.host() == ownHost(screen) || claim.yieldsToSlot()) return false;
        if (claim.placed() != null) {
            claim.host().mouseScrolled(claim.placed(), mouseX, mouseY, scrollX, scrollY);
        }
        return true;
    }
}
