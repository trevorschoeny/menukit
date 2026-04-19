package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.SlotGroupCategory;
import com.trevorschoeny.menukit.mixin.AbstractContainerScreenAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Library-owned registry of MenuContext {@link ScreenPanelAdapter}s that
 * declare targeting via {@code .on(...)} or {@code .onAny()}. Listens once
 * on {@link ScreenEvents#AFTER_INIT} and dispatches render/input to the
 * adapters whose targeting matches each opened {@link AbstractContainerScreen}.
 * Consumers stop writing per-screen {@code ScreenEvents.AFTER_INIT}
 * boilerplate — the library owns the hook.
 *
 * <p>See {@code menukit/Design Docs/Phase 12.5/M8_FOUR_CONTEXT_MODEL.md} §8
 * for the design and §7.3 for orphan-adapter enforcement.
 *
 * <h3>Adapter lifecycle</h3>
 *
 * <ol>
 *   <li><b>Construction.</b> {@code new ScreenPanelAdapter(panel, region)}
 *       calls {@link #trackPending} — the adapter joins {@link #PENDING}
 *       until it declares targeting.</li>
 *   <li><b>Targeting declaration.</b> {@code .on(Class...)} or
 *       {@code .onAny()} calls {@link #markTargetingDeclared} — the adapter
 *       moves from {@link #PENDING} to {@link #REGISTERED}.</li>
 *   <li><b>Orphan checkpoint.</b> On the first screen-open event after init,
 *       {@link #validateTargetingDeclared} runs. If {@link #PENDING} is
 *       non-empty, those adapters are orphans — {@link IllegalStateException}
 *       fails the client boot visibly, naming each orphan's panel ID.</li>
 *   <li><b>Dispatch.</b> For each opened {@link AbstractContainerScreen},
 *       walk {@link #REGISTERED} and for adapters whose targeting matches
 *       the screen class, register per-screen {@code afterRender} +
 *       {@code allowMouseClick} hooks. Fabric handles per-screen lifetime
 *       cleanup when the screen closes.</li>
 * </ol>
 *
 * <h3>Lambda-based adapters are exempt</h3>
 *
 * Adapters constructed with a {@link ScreenOriginFn} (rather than a
 * {@link com.trevorschoeny.menukit.core.MenuRegion}) don't participate in
 * this registry — they're the escape hatch scoped by the consumer's own
 * mixin. {@link ScreenPanelAdapter#on} / {@link ScreenPanelAdapter#onAny}
 * throw if called on them.
 */
public final class ScreenPanelRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private ScreenPanelRegistry() {}

    // ── Adapter tracking ────────────────────────────────────────────────
    //
    // Region-based adapters move from PENDING → REGISTERED when they
    // declare targeting. Strong references (not WeakHashMap): consumers
    // typically hold adapters as static final fields, so they're
    // process-lifetime anyway.

    private static final Set<ScreenPanelAdapter> PENDING =
            Collections.synchronizedSet(new HashSet<>());

    private static final List<ScreenPanelAdapter> REGISTERED =
            Collections.synchronizedList(new ArrayList<>());

    // Parallel tracking for SlotGroupContext adapters. Distinct sets rather
    // than a shared abstract base because SlotGroupPanelAdapter has a
    // different render signature (takes SlotGroupBounds + category) than
    // ScreenPanelAdapter — unifying would dilute the call sites.
    private static final Set<SlotGroupPanelAdapter> PENDING_SLOT_GROUP =
            Collections.synchronizedSet(new HashSet<>());

    private static final List<SlotGroupPanelAdapter> REGISTERED_SLOT_GROUP =
            Collections.synchronizedList(new ArrayList<>());

    private static volatile boolean checkpointRun = false;

    // ── API called by ScreenPanelAdapter ────────────────────────────────

    /**
     * Called from {@link ScreenPanelAdapter}'s region-based constructor —
     * marks the adapter as pending (awaiting targeting declaration).
     */
    static void trackPending(ScreenPanelAdapter adapter) {
        PENDING.add(adapter);
    }

    /**
     * Called from {@link ScreenPanelAdapter#on} / {@link ScreenPanelAdapter#onAny}.
     * Moves the adapter from pending to registered.
     */
    static void markTargetingDeclared(ScreenPanelAdapter adapter) {
        PENDING.remove(adapter);
        REGISTERED.add(adapter);
    }

    /**
     * Called from {@link SlotGroupPanelAdapter}'s constructor — marks the
     * slot-group adapter as pending (awaiting {@code .on(...)}).
     */
    static void trackPendingSlotGroup(SlotGroupPanelAdapter adapter) {
        PENDING_SLOT_GROUP.add(adapter);
    }

    /**
     * Called from {@link SlotGroupPanelAdapter#on}. Moves the adapter from
     * pending to registered.
     */
    static void markSlotGroupTargetingDeclared(SlotGroupPanelAdapter adapter) {
        PENDING_SLOT_GROUP.remove(adapter);
        REGISTERED_SLOT_GROUP.add(adapter);
    }

    // ── Observable state ────────────────────────────────────────────────

    /** Returns an unmodifiable snapshot of orphan (untargeted) adapters. */
    public static Set<ScreenPanelAdapter> pendingSnapshot() {
        synchronized (PENDING) {
            return Set.copyOf(PENDING);
        }
    }

    /** Returns an unmodifiable snapshot of registered adapters. */
    public static List<ScreenPanelAdapter> registeredSnapshot() {
        synchronized (REGISTERED) {
            return List.copyOf(REGISTERED);
        }
    }

    /** Returns an unmodifiable snapshot of orphan SlotGroupContext adapters. */
    public static Set<SlotGroupPanelAdapter> pendingSlotGroupSnapshot() {
        synchronized (PENDING_SLOT_GROUP) {
            return Set.copyOf(PENDING_SLOT_GROUP);
        }
    }

    /** Returns an unmodifiable snapshot of registered SlotGroupContext adapters. */
    public static List<SlotGroupPanelAdapter> registeredSlotGroupSnapshot() {
        synchronized (REGISTERED_SLOT_GROUP) {
            return List.copyOf(REGISTERED_SLOT_GROUP);
        }
    }

    // ── Initialization ──────────────────────────────────────────────────

    /**
     * Registers the library-owned {@link ScreenEvents#AFTER_INIT} listener.
     * Called once from {@code MenuKitClient.onInitializeClient}. After this,
     * any region-based adapter that declared targeting will render on
     * matching screens without the consumer writing per-screen boilerplate.
     */
    public static void init() {
        ScreenEvents.AFTER_INIT.register(ScreenPanelRegistry::onScreenInit);
    }

    // ── Screen-open dispatch ────────────────────────────────────────────

    /**
     * Called on every screen-open after {@link #init}. Runs the orphan
     * checkpoint once, then wires per-screen render + input hooks for the
     * adapters whose targeting matches this screen.
     */
    private static void onScreenInit(Minecraft client, Screen screen,
                                      int scaledWidth, int scaledHeight) {
        // Orphan checkpoint — runs once per client session. All region-based
        // adapters that were constructed during init have completed their
        // targeting declaration by now (static initializers + onInitializeClient
        // ran before the first screen opened). Any still in PENDING are
        // orphans.
        if (!checkpointRun) {
            checkpointRun = true;
            validateTargetingDeclared();
        }

        // MenuContext + SlotGroupContext dispatch only for AbstractContainerScreen —
        // other screen types (title, pause menu, etc.) are outside scope.
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return;

        // ── MenuContext matching ────────────────────────────────────────
        Class<? extends AbstractContainerScreen<?>> screenClass =
                asConcreteScreenClass(acs.getClass());
        List<ScreenPanelAdapter> menuMatches = new ArrayList<>();
        for (ScreenPanelAdapter adapter : registeredSnapshot()) {
            if (adapter.matches(screenClass)) {
                menuMatches.add(adapter);
            }
        }

        // ── SlotGroupContext matching ───────────────────────────────────
        // Resolve the opened menu's slot groups once per screen-open; the
        // result is re-used per-frame to compute slot-group bounds. Match
        // any registered adapter whose declared targets overlap this menu's
        // resolved categories.
        Map<SlotGroupCategory, List<Slot>> resolved = SlotGroupCategories.of(acs.getMenu());
        List<SlotGroupMatch> slotGroupMatches = new ArrayList<>();
        for (SlotGroupPanelAdapter adapter : registeredSlotGroupSnapshot()) {
            List<SlotGroupCategory> targets = adapter.getTargets();
            if (targets == null) continue;
            for (SlotGroupCategory category : targets) {
                List<Slot> slots = resolved.get(category);
                if (slots == null || slots.isEmpty()) continue;
                slotGroupMatches.add(new SlotGroupMatch(adapter, category, slots));
            }
        }

        if (menuMatches.isEmpty() && slotGroupMatches.isEmpty()) return;

        // Per-screen render + input hooks. Fabric auto-unregisters these
        // when the screen closes, so no manual cleanup is needed.
        ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> {
            ScreenBounds frame = frameBounds(acs);
            for (ScreenPanelAdapter adapter : menuMatches) {
                adapter.render(graphics, frame, mouseX, mouseY, acs);
            }
            for (SlotGroupMatch match : slotGroupMatches) {
                SlotGroupBounds sgBounds = computeSlotGroupBounds(match.slots, acs);
                match.adapter.render(graphics, sgBounds, match.category, mouseX, mouseY, acs);
            }
        });
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
            ScreenBounds frame = frameBounds(acs);
            for (ScreenPanelAdapter adapter : menuMatches) {
                adapter.mouseClicked(frame, event.x(), event.y(), event.button(), acs);
            }
            for (SlotGroupMatch match : slotGroupMatches) {
                SlotGroupBounds sgBounds = computeSlotGroupBounds(match.slots, acs);
                match.adapter.mouseClicked(sgBounds, match.category,
                        event.x(), event.y(), event.button(), acs);
            }
            // Return true — vanilla continues processing clicks that missed
            // any adapter's interactive element. Per M8 §8.3, future
            // cancellation-aware behavior will be added via
            // ScreenPanelAdapter.cancelsUnhandledClicks(boolean).
            return true;
        });
    }

    /**
     * Captures a matched (adapter, category, slots) tuple for per-frame
     * slot-group dispatch. Resolved once at screen-open so the per-frame
     * render loop doesn't re-walk resolvers.
     */
    private record SlotGroupMatch(SlotGroupPanelAdapter adapter,
                                   SlotGroupCategory category,
                                   List<Slot> slots) {}

    /**
     * Computes the bounding rectangle enclosing the given slots in screen
     * space. Slots store {@code x}/{@code y} relative to the screen frame;
     * the returned bounds are absolute (includes {@code leftPos}/{@code topPos}).
     * Standard slot visual is 16×16.
     */
    private static SlotGroupBounds computeSlotGroupBounds(List<Slot> slots,
                                                           AbstractContainerScreen<?> screen) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Slot slot : slots) {
            int sx = slot.x;
            int sy = slot.y;
            if (sx < minX) minX = sx;
            if (sy < minY) minY = sy;
            if (sx + 16 > maxX) maxX = sx + 16;
            if (sy + 16 > maxY) maxY = sy + 16;
        }
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int frameLeft = acc.trevorMod$getLeftPos();
        int frameTop = acc.trevorMod$getTopPos();
        return new SlotGroupBounds(
                frameLeft + minX,
                frameTop + minY,
                maxX - minX,
                maxY - minY);
    }

    /**
     * Reads the screen's frame bounds via
     * {@link AbstractContainerScreenAccessor}. Computed per-frame because
     * {@code leftPos}/{@code topPos} shift on resize and recipe-book toggle.
     */
    private static ScreenBounds frameBounds(AbstractContainerScreen<?> screen) {
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        return new ScreenBounds(
                acc.trevorMod$getLeftPos(),
                acc.trevorMod$getTopPos(),
                acc.trevorMod$getImageWidth(),
                acc.trevorMod$getImageHeight());
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends AbstractContainerScreen<?>> asConcreteScreenClass(
            Class<?> raw) {
        return (Class<? extends AbstractContainerScreen<?>>) raw;
    }

    // ── Orphan enforcement ──────────────────────────────────────────────

    /**
     * Validates that every region-based adapter constructed during init
     * declared its targeting. Called once, on the first screen-open event.
     * Per M8 §7.3: throws {@link IllegalStateException} naming the orphan
     * panel IDs, failing the client boot visibly rather than silently
     * skipping the broken decoration.
     *
     * <p>Orphans are a build/wiring defect, not a runtime recoverable
     * condition — a consumer constructed a region-based adapter and never
     * called {@code .on()} / {@code .onAny()}. The fix is to add the
     * missing targeting declaration.
     */
    public static void validateTargetingDeclared() {
        Set<ScreenPanelAdapter> pendingMenu = pendingSnapshot();
        Set<SlotGroupPanelAdapter> pendingSlotGroup = pendingSlotGroupSnapshot();
        if (pendingMenu.isEmpty() && pendingSlotGroup.isEmpty()) return;

        StringBuilder msg = new StringBuilder("MenuKit: ");
        if (!pendingMenu.isEmpty()) {
            msg.append(pendingMenu.size())
               .append(" region-based ScreenPanelAdapter(s) constructed but never " +
                       "declared targeting (.on / .onAny). Panel IDs: ");
            boolean first = true;
            for (ScreenPanelAdapter adapter : pendingMenu) {
                if (!first) msg.append(", ");
                msg.append(adapter.getPanel().getId());
                first = false;
            }
        }
        if (!pendingSlotGroup.isEmpty()) {
            if (!pendingMenu.isEmpty()) msg.append("; ");
            msg.append(pendingSlotGroup.size())
               .append(" SlotGroupPanelAdapter(s) constructed but never declared " +
                       "targeting (.on(SlotGroupCategory...)). Panel IDs: ");
            boolean first = true;
            for (SlotGroupPanelAdapter adapter : pendingSlotGroup) {
                if (!first) msg.append(", ");
                msg.append(adapter.getPanel().getId());
                first = false;
            }
        }
        msg.append(". Fix by adding the missing .on(...) call(s).");
        String message = msg.toString();
        LOGGER.error("[ScreenPanelRegistry] {}", message);
        throw new IllegalStateException(message);
    }
}
