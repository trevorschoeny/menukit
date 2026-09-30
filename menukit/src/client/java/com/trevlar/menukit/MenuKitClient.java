package com.trevlar.menukit;

import com.trevlar.menukit.api.MK;
import com.trevlar.menukit.api.panel.Focus;
import com.trevlar.menukit.inject.MenuChrome;
import com.trevlar.menukit.inject.ScreenPanelRegistry;
import com.trevlar.menukit.inject.VanillaSlotGroupResolvers;
import com.trevlar.menukit.input.CursorContinuity;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.mixin.MKRecipeBookAccessor;

import com.trevlar.menukit.api.window.WindowSignals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import org.jetbrains.annotations.ApiStatus;

/**
 * Client-side entry point for MenuKit.
 *
 * <p>Also holds the recipe-book utilities behind {@code MK.isRecipeBookOpen} and
 * {@code MK.setRecipeBookOpen}. Item Tips (durability and food lines on every
 * tooltip) left MenuKit in 6.0.0 for Inventory Plus (§0068): it is a feature, and
 * §0019 leaves features to consumers.
 */
@ApiStatus.Internal
public class MenuKitClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Declarations freeze once every client entrypoint has run (§0063), in a phase
        // after the default one, so anything a library wires at client start (Containers'
        // container panels) declares before the freeze, whichever mod's entrypoint ran first.
        net.minecraft.resources.Identifier freeze =
                net.minecraft.resources.Identifier.fromNamespaceAndPath("menukit", "freeze");
        var started = net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED;
        started.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, freeze);
        started.register(freeze, client -> com.trevlar.menukit.api.window.Declarations.freeze("client started"));

        MK.initClient();

        // M7 — vanilla menu-chrome providers. Registered at client init so
        // any region-aware ScreenPanelAdapter constructed later sees
        // chrome-aware origin resolution. See M7 design doc §3.3 for scope.
        registerVanillaMenuChrome();

        // M8 — vanilla slot-group resolvers for SlotGroupContext dispatch.
        // Per §0043 (Complete-on-Side Feature Ownership): observation idioms
        // including slot-group recognition are MK-side, complete in MK
        // without MKC. Resolvers register the 22 vanilla menu classes (M8 §6)
        // so consumer mods anchoring panels to recognized slot groups work
        // even when MKC is not loaded.
        // Seeing through creative's slot wrapper: a client class, so installed here.
        com.trevlar.menukit.inject.Slots.installUnwrap(slot -> {
            if (slot instanceof com.trevlar.menukit.mixin.SlotWrapperAccessor wrapper) {
                net.minecraft.world.inventory.Slot t = wrapper.mk$getTarget();
                if (t != null) return t;
            }
            return slot;
        });
        com.trevlar.menukit.inject.CreativeSlotGroupResolver.registerClient();   // the rest registered in MK.init, both sides

        // M8 — library-owned ScreenEvents.AFTER_INIT dispatch for MenuContext
        // adapters. Replaces per-consumer listener boilerplate. See
        // M8_FOUR_CONTEXT_MODEL.md §8 for design.
        ScreenPanelRegistry.init();

        // Phase 18r-5 follow-up — MK-managed focus janitor. Wires
        // ScreenMouseEvents.afterMouseClick on every opened screen so a
        // focused MK-managed widget (e.g. TextField, Keybindery's SearchBox)
        // releases focus when the user clicks anywhere outside its bounds.
        // Compensates for vanilla's setFocused-on-claim-only semantics
        // combined with MK's panel-click-eat suppressing the natural flow.
        // See Focus class javadoc for the full design.
        Focus.init();

        // Phase 16h — cursor preservation. Universal AFTER_INIT listener
        // that fires for any screen open (vanilla, MK, MKC, third-party)
        // and wires a per-screen remove listener to stash the cursor.
        // Restore reads the stash on the next screen's init. No consumer
        // opt-in — the mechanism counters vanilla / OS cursor centering
        // on screen transitions. See CursorContinuity javadoc.
        CursorContinuity.registerRestoreHook();

        // Phase 14d-2.7 — visual smoke wireups (dialog, scroll, opacity)
        // migrated to validator/.../scenarios/smoke per the testing
        // convention's library/validator split.

        // Phase 14d-1 / M9 modal-tracking cursor suppression — architectural
        // mechanism. Window has a global allowCursorChanges flag;
        // setAllowCursorChanges(false) coerces all selectCursor calls to
        // CursorType.DEFAULT (verified in vanilla bytecode — selectCursor
        // early-returns DEFAULT when allowCursorChanges is false). Single
        // vanilla-flag toggle replaces per-widget hover-suppression
        // patches.
        //
        // Synced per-tick: flag = !hasAnyVisibleModalTracking(). When a
        // tracksAsModal panel becomes visible, cursor stays as DEFAULT
        // regardless of what hover code requests; when modal-tracking
        // hides, cursor resumes vanilla behavior. Per-tick (20Hz) is
        // sufficient because cursor state only matters at user-visible
        // granularity; lower-frequency updates avoid the need for state
        // tracking on visibility transitions.
        //
        // M9 §4.7 asymmetry: cursor lock stays gated on tracksAsModal
        // (window-state suppression scoped to modal-tracking) — non-modal
        // opaque panels do NOT lock cursor (cursor is a window-edge state,
        // not pointer-driven; localizing would flicker on edge crossings).
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getWindow() != null) {
                client.getWindow().setAllowCursorChanges(
                        !com.trevlar.menukit.inject.ScreenPanelRegistry.modalGatesInput());
            }
            // Client-observed reactive verbs (ON_*_OBSERVED) — diff the open
            // container menu's synced contents and fire UI-feedback reactions.
            AbstractContainerScreen<?> acs =
                    client.gui.screen() instanceof AbstractContainerScreen<?> s ? s : null;
            com.trevlar.menukit.window.ObservedReactions.tick(acs != null ? acs.getMenu() : null);
            // Slot interaction signals — which slot the cursor is over (read from the
            // screen's hoveredSlot via the accessor); the click half is wired below.
            WindowSignals.tickHover(
                    acs != null ? acs.getMenu() : null,
                    acs != null ? ((AbstractContainerScreenAccessor) (Object) acs).mk$getHoveredSlot() : null);
        });

        // Slot interaction signals — record the last-clicked slot on any container
        // screen (the slot under the cursor at click time) and clear it on close.
        // Pure observation; the click still does its normal vanilla thing.
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?> acs) {
                ScreenMouseEvents.afterMouseClick(screen).register((scr, event, consumed) -> {
                    WindowSignals.recordClick(acs.getMenu(),
                            ((AbstractContainerScreenAccessor) (Object) acs).mk$getHoveredSlot());
                    return false; // observation only — never consume
                });
                ScreenEvents.remove(screen).register(scr -> WindowSignals.clearSelection());
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    // M7 — Vanilla menu chrome
    // ══════════════════════════════════════════════════════════════════════
    //
    // v1 scope is evidence-driven: screens that current library work (V2
    // probes, V4.2's inventory decoration) actually exercises. Other
    // recipe-book screens (CraftingScreen, FurnaceScreen, SmokerScreen,
    // BlastFurnaceScreen) are candidate additions pending consumer evidence.

    /**
     * Shared recipe-book chrome formula used by both {@link InventoryScreen}
     * and {@link CraftingScreen}. Derived from vanilla's
     * {@code RecipeBookComponent.updateTabs}: when the book is visible and
     * the screen is wide enough, the book body is 147px to the left of the
     * vanilla frame and the filter-tab column inserts an additional ~31px
     * beyond that.
     *
     * <p>Assumes {@code xOffset = 86} when visible — the constant in
     * vanilla's {@code AbstractRecipeBookScreen.getXOffset(boolean narrow)}
     * for both player-inventory and crafting-table contexts. If a subclass
     * overrides {@code getXOffset} with a different value, its provider
     * must compute the offset dynamically rather than using this shared
     * formula.
     */
    private static MenuChrome.ChromeExtents recipeBookChromeFor(
            AbstractContainerScreenAccessor screenAcc, int screenWidth) {
        int currentLeftPos = screenAcc.mk$getLeftPos();
        int tabLeft = (screenWidth - 147) / 2 - 116;
        int chromeLeft = currentLeftPos - tabLeft;
        if (chromeLeft <= 0) return MenuChrome.ChromeExtents.NONE;
        return new MenuChrome.ChromeExtents(0, chromeLeft, 0, 0);
    }

    private static void registerVanillaMenuChrome() {
        // CreativeModeInventoryScreen: two tab rows above + below the
        // declared 195×136 frame. The TAB_HEIGHT=32 constant includes 3-4px
        // of transparent sprite padding around each tab's visible shape;
        // anchoring to 32 leaves probes floating too far from the visible
        // tab edge. Values below come from vanilla's own hit-test geometry
        // in checkTabHovering (21×27 at sprite offset +3, +3):
        //   Top visible tab: topPos - 25 to topPos + 2  → 25px above frame
        //   Bottom visible tab: topPos + iH - 1 to topPos + iH + 26  → 26px below
        // Asymmetry (25 vs 26) matches vanilla's 1px deeper bottom-tab bias.
        // Probes land STACK_GAP=2 past the visible edge — clean 2px gap.
        MenuChrome.register(CreativeModeInventoryScreen.class,
                screen -> new MenuChrome.ChromeExtents(25, 0, 0, 26));

        // InventoryScreen: recipe book widget (when visible) extends left of
        // the inventory frame by the book-body width (147px) PLUS the filter
        // tab column (~31px with +35-wide tab buttons). Formula lives in
        // recipeBookChromeFor — shared with CraftingScreen since both have
        // xOffset=86 in vanilla AbstractRecipeBookScreen.
        //
        // Per-frame recompute because screen.width changes on resize. Skip
        // chrome when the book is in "widthTooNarrow" mode (screen width
        // < 379) — vanilla overlays the book on top of the inventory
        // instead of shifting the frame, so there's no clean "left of the
        // book" space to anchor LEFT_ALIGN regions to.
        MenuChrome.register(InventoryScreen.class,
                screen -> recipeBookChromeIfOpen((AbstractRecipeBookScreen<?>) screen));

        // CraftingScreen: same recipe-book treatment as the player inventory.
        // V2 completeness pass adds this as evidence that M7's pattern
        // generalizes to other AbstractRecipeBookScreen subclasses. If the
        // vanilla xOffset turns out to differ for CraftingScreen (the
        // provider formula assumes 86), probes will visibly misalign and
        // we'll learn the formula is per-screen rather than universal.
        // Outcome informs M7 v2 scope — either "add Furnace/Smoker/
        // BlastFurnace with the same formula" or "extract per-screen
        // getXOffset access and compute dynamically."
        MenuChrome.register(CraftingScreen.class,
                screen -> recipeBookChromeIfOpen((AbstractRecipeBookScreen<?>) screen));
    }

    /**
     * Returns chrome extents for a recipe-book screen iff the book is
     * visible and the screen is wide enough for the book to shift the
     * frame (not overlay it). Otherwise {@link MenuChrome.ChromeExtents#NONE}.
     */
    private static MenuChrome.ChromeExtents recipeBookChromeIfOpen(
            AbstractRecipeBookScreen<?> screen) {
        var rbAccessor = (MKRecipeBookAccessor) screen;
        if (!rbAccessor.mk$getRecipeBookComponent().isVisible()) {
            return MenuChrome.ChromeExtents.NONE;
        }
        if (screen.width < 379) {
            return MenuChrome.ChromeExtents.NONE;
        }
        var bndsAccessor = (AbstractContainerScreenAccessor) screen;
        return recipeBookChromeFor(bndsAccessor, screen.width);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Recipe Book Utilities
    // ══════════════════════════════════════════════════════════════════════
    //
    // The recipe book is a client-side overlay rendered by AbstractRecipeBookScreen
    // (InventoryScreen, CraftingScreen, etc.). These helpers let consumers check
    // or toggle its visibility without importing vanilla screen classes or
    // writing their own accessor mixin.

    /**
     * Returns whether the recipe book is currently visible on the active screen.
     *
     * <p>Safe to call at any time. Returns {@code false} if:
     * <ul>
     *   <li>There is no active screen</li>
     *   <li>The active screen does not extend {@link AbstractRecipeBookScreen}</li>
     * </ul>
     */
    public static boolean isRecipeBookOpen() {
        var mc = Minecraft.getInstance();

        if (!(mc.gui.screen() instanceof AbstractRecipeBookScreen<?>)) {
            return false;
        }

        var accessor = (MKRecipeBookAccessor) mc.gui.screen();
        return accessor.mk$getRecipeBookComponent().isVisible();
    }

    /**
     * Sets the recipe book's visibility on the active screen.
     *
     * <p>No-op if the active screen doesn't have a recipe book or if the
     * requested state already matches the current state.
     *
     * @param open {@code true} to show the recipe book, {@code false} to hide it
     */
    public static void setRecipeBookOpen(boolean open) {
        var mc = Minecraft.getInstance();

        if (!(mc.gui.screen() instanceof AbstractRecipeBookScreen<?>)) {
            return;
        }

        var accessor = (MKRecipeBookAccessor) mc.gui.screen();
        var recipeBook = accessor.mk$getRecipeBookComponent();

        // Only toggle if the current state differs from the requested state.
        // toggleVisibility() is a flip, so calling it when already in the
        // desired state would incorrectly reverse it.
        if (recipeBook.isVisible() != open) {
            recipeBook.toggleVisibility();
        }
    }
}
