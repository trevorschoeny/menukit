# MenuKit Advisor Handoff — Complete Project Story

**Purpose:** You are the new architectural advisor for MenuKit and its consumer mods. This document gives you the full context — where we are, how we got here, and where we're going. Read it completely before advising on anything. At the end, you'll find instructions for generating questions to fill any gaps in your understanding.

---

## Part I: Where We Are Right Now (In Medias Res)

MenuKit is a Minecraft Fabric modding library (MC 1.21.11) that provides UI component primitives — panels, buttons, toggles, icons, text labels, item displays, tooltips, progress bars, and more — across three rendering contexts: inventory menus, HUDs, and standalone screens. Four consumer mods depend on it: inventory-plus (the largest and most complex), shulker-palette, sandboxes, and agreeable-allays. All live in a single monorepo.

We are midway through **Phase 12** of a multi-phase migration arc. The migration has two halves: Phases 1–5 (the "first migration," which tore down a god-class architecture and established five canonical contracts), and Phases 6–12+ (the "second migration," which transformed MenuKit from an inventory-menu utility into a general component library, then rebuilt consumer mods against it, and is now shipping library primitives surfaced by that rebuild).

### The current Phase 12 state, concretely

**What's confirmed working (committed + verified):**
- M4 (vanilla menu slot injection) — the core mechanism is verified. IP's `InventoryMenuMixin` grafts two equipment slots onto `InventoryMenu` at `<init>` RETURN via `addSlot()`. They're real vanilla `Slot` instances that participate in vanilla's full slot protocol: drag, shift-click, cursor management, `broadcastChanges` sync. Filter enforcement (elytra/totem only) via `mayPlace()`. Persistence via Fabric data attachments (`PlayerAttachedStorage`). A `hasClickedOutside` mixin fix resolves a vanilla bug where slots positioned outside the container frame get misclassified as THROW actions instead of PICKUP.
- M2 (SlotIdentity) — shipped in Phase 11. Record `(Container, int containerSlot)` for stable slot identity across menu transitions.
- StorageContainerAdapter — bridges MenuKit's `Storage` interface to vanilla's `Container`.
- MenuKitSlot restructure — the `getItem()` data-flow override was removed; MenuKitSlot now passes a `StorageContainerAdapter` to vanilla's `Slot` constructor and only overrides vanilla's designed extension points (`mayPlace`, `isActive`, `getNoItemIcon`, `getMaxStackSize`). Not yet verified via `/mkverify all`.

**What's designed and ready for implementation:**
- M5 (context-scoped region system) — Trevor finalized region specifications. Three contexts: InventoryContext (8 regions around the menu frame using `SIDE_ALIGN_END` naming), HudContext (9 regions at screen edges + center below crosshair), StandaloneScreenContext (8 regions around the main panel). Region specs live at `Design Docs/Phase 12/M5_REGION_SPECS.md`.

**What's still in design-input phase:**
- M1 (per-slot persistent state) — per-slot state keyed by SlotIdentity that survives across sessions + server→client sync on menu open. Design doc not yet written. Enables F1 (persistent lock state) and F2 (chest-lock visibility across reopens).

**What dissolved:**
- M6 (client-side slot primitive) — designed, built, tested, dissolved. The verification showed that when something looks like a slot, users expect vanilla slot behavior (drag, shift-click, native cursor). A client-side primitive with custom click dispatch can't deliver that. Without peek as a consumer, no other use case exists. The rendering analysis (SlotRendering utility) carries forward to M4.

### The golden goal

The end state is: **all four consumer mods are fully functional against MenuKit's complete primitive surface, with no hardcoded workarounds, no hand-rolled slot systems, and no inter-mod coordinate collisions.** Specifically:

- **inventory-plus** has equipment panels (elytra + totem slots in the inventory), pocket panels (9 × 3 extra hotbar-linked slots), pocket HUD (A-shape item display above the hotbar), client-side peek (peek inside shulkers/ender chests/bundles without opening them), sort/bulk-move/move-matching via click sequences, lock overlays, and full passive behaviors (flight, totem, mending, death drops, auto-restock, auto-route, auto-replace, deep arrow search). All positioned via the region system. Lock state persists across sessions.
- **shulker-palette** has its palette toggle on shulker screens plus a peek-panel toggle when peeking a shulker through IP.
- **sandboxes** has its inventory buttons for entering/exiting sandbox worlds.
- **agreeable-allays** has its action-hint HUD for allay interactions.

The path there is three remaining phases: **Phase 12** (ship M5 region system + M1 persistence primitive), **Phase 13** (complete all deferred consumer features against Phase 12's primitives), and whatever documentation/cleanup phase follows.

---

## Part II: The Full History

### The beginning — god-class architecture (pre-Phase 1)

MenuKit started as a single-file utility library for adding UI to Minecraft's inventory screens. The core class (`MenuKit.java`) handled everything: slot management, panel rendering, event dispatch, button creation, HUD rendering, context management, and consumer-mod registration. It worked, but it was a god class in the textbook sense — hundreds of methods, tangled responsibilities, impossible to extend cleanly.

Trevor's four consumer mods (inventory-plus, shulker-palette, sandboxes, agreeable-allays) all depended on this architecture. Inventory-plus was by far the largest consumer — it used MenuKit for equipment panels, pocket panels, peek panels, sort/move-matching infrastructure, and passive behavior helpers. The other three were smaller: shulker-palette used a button-attachment API, sandboxes used panel registration, agreeable-allays used HUD panels.

### The first migration — Phases 1–5 (god-class teardown)

The first migration demolished the god class and established clean architecture. Five phases:

- **Phase 1:** Extracted the panel/element system from the monolith. Panels became first-class objects holding PanelElements (Button, TextLabel, etc.) with a render orchestration contract.
- **Phase 2:** Extracted slot management. SlotGroup became the composition unit — wrapping a Storage (the data) + InteractionPolicy (the rules) + QuickMoveParticipation (shift-click behavior). MenuKitSlot became a vanilla Slot subclass that delegates to SlotGroup.
- **Phase 3:** Extracted the screen handler. MenuKitScreenHandler became the builder-pattern entry point for constructing MenuKit-native screens.
- **Phase 4a/4b:** Wired the full click→drag→release pipeline. Built HandlerRecognizerRegistry (observes vanilla slots and groups them for sort/move-matching). Established public extension APIs.
- **Phase 5:** Demolished the remaining god-class code. Deleted ~20 dead methods, dead types (MKSlotState, MKSlotStateRegistry, MKContainerDef, etc.), the old event bus (MKEventBus + all event types), and the old panel-registration system (MKPanel, MKButton, MKButtonDef, MKGroupChild, MKContainerType, etc.).

Phase 5 established **five canonical contracts** — composability, vanilla-slot substitutability, sync-safety, uniform abstraction (SlotGroupLike), and inertness (hidden panels make their slots empty/inactive). These contracts have a verification harness (`/mkverify all`) that runs as a regression gate at every phase boundary.

At the end of Phase 5, MenuKit had a clean architecture for inventory menus. But it was still "a library for inventory menus," not "a component library for Minecraft UI." Consumer mods were disabled — they depended on the deleted APIs and couldn't compile.

### The second migration — Phases 6–10 (identity to component library)

The second migration transformed MenuKit from an inventory-menu utility into a general-purpose component library. This required new thinking about what the library *is*.

**Phase 6: Thesis, contexts, palette.** Three design documents written and locked before any code:
- **THESIS.md** — MenuKit's identity as a component library. The library-not-platform discipline: MenuKit provides primitives; consumers compose them. No ambient behavior, no implicit registration, no defaults, no cross-consumer mediation.
- **CONTEXTS.md** — Three rendering contexts: inventory menus (Panel inside a MenuKitScreenHandler), HUDs (MKHudPanel overlaid on gameplay), standalone screens (MenuKit-native screens not attached to vanilla menus).
- **PALETTE.md** — The complete intended element set. Each element has a design doc before implementation.

**Phase 7: Context generalization.** Made Panel and PanelElement work across all three contexts, not just inventory menus. HUD rendering integrated with the generalized panel system. MKHudPanel became a context-specific panel builder rather than a separate subsystem.

**Phase 8: Foundational elements.** Built out the element palette — Toggle, Checkbox, Radio/RadioGroup, Divider, Icon, ProgressBar, Tooltip-as-element, and supplier variants for dynamic content. Each element got a design doc. Conventions were established: chainable setters for orthogonal features (`.tooltip(...)`, `.disabledWhen(...)`), final render orchestration with protected extension hooks for subclassing.

**Phase 9: Audit-surfaced specializations.** Addressed what Phase 5's consumer audit revealed existing mods needed:
- `Button.icon(...)` factory — icon-only button with 2px inset, dim-when-disabled.
- `Toggle.linked(...)` factory — state-linked toggle with `BooleanSupplier` + `Runnable` callback. Self-healing property documented.
- Both followed a "factor-then-specialize" template: factor parent into protected hooks → add subclass factory overriding hooks → mark orchestration `final`.
- Convention 5 locked: factory methods permitted only when returning a different concrete type (specialization subclass), never as preset-value shortcuts.

**Phase 10: Injection patterns.** Documented and supported consumer-side injection of MenuKit elements into vanilla screens. This was the bridge between "MenuKit can build its own screens" and "consumers can decorate vanilla screens":
- `ScreenPanelAdapter` — bundles render + click dispatch for a Panel inside any vanilla screen.
- `ScreenOriginFns` — four named anchor constructors (fromScreenTopLeft, fromScreenTopRight, aboveSlotGrid, belowSlotGrid).
- `Panel.showWhen(Supplier<Boolean>)` — supplier-driven visibility.
- Three injection-pattern documents: inventory injection (Patterns 1/2/3 with four failure modes documented), standalone injection (Pattern 4), HUD injection (Pattern 5).
- Key finding: "split mixins are the default" — consumer decorations spanning multiple inventory variants need 1 primary + 2-3 supplementary mixins.
- Six example mixin classes shipped in an examples/ package gated behind `FabricLoader.isDevelopmentEnvironment()`.

At the end of Phase 10, MenuKit was a legitimate component library with a design-doc-driven element palette, three rendering contexts, injection patterns for vanilla screens, and a verification harness. Consumer mods were still disabled.

### Phase 11 — Consumer mod rebuilds (the longest and most revelatory phase)

Phase 11 was originally scoped as "rebuild four consumer mods against current MenuKit." It became something more important: **a systematic pressure-test of the library that surfaced primitive gaps no amount of library-side design could have predicted.** The phase produced four mechanism candidates (M1, M4, M5, M6), eleven feature deferrals for IP alone, and a working discipline ("architectural discipline first, functionality second") that became the project's core operating principle.

#### The three-phase arc reframing

Mid-Phase-11, a critical reframing occurred. The original plan assumed Phase 11 would produce fully functional consumer mods. Reality: consumer mods kept hitting primitive gaps — things MenuKit didn't have that they needed. The response could have been "ship the primitive now" (scope creep) or "work around it" (architectural debt). Instead, a third option emerged:

- **Phase 11:** Rebuild consumers as far as current MenuKit allows. When a feature needs a primitive that doesn't exist, **defer the feature and file the primitive.** Don't ship the primitive mid-phase; don't work around its absence.
- **Phase 12:** Design and ship the filed primitives, using multi-consumer evidence accumulated across Phase 11.
- **Phase 13:** Complete the deferred features against Phase 12's primitives.

This reframing was the most important architectural decision of the project. It separated "library requirements gathering" (Phase 11) from "library design" (Phase 12) from "consumer completion" (Phase 13), letting each phase do its job cleanly. The discipline — "defer + file, don't hack" — was enforced through an explicit exception clause: one library addition was permitted (SlotIdentity, M2), then the clause was closed for new business. Every subsequent primitive need was deferred.

The pattern is worth naming for future projects: **consumer-mod rebuild phases tend to surface library requirements. Scope them as "rebuild + file" from the start rather than "rebuild fully."**

#### Inventory-Plus: the stress test

IP's Phase 11 rebuild was seven layers of work across roughly two weeks:

**Layer 0a (clean baseline):** IP's old code couldn't compile against current MenuKit (all the Phase 5-demolished APIs were gone). This layer gutted IP to a minimal compiling shell — 17 files touched, net -3,482 lines. Method signatures preserved for in-place repopulation. This pattern repeated for every consumer mod: before rebuilding, establish a clean-compile baseline.

**Layer 0 (attachments):** Built `IPPlayerAttachments` — Fabric data attachments for equipment (2 items), pockets (27 items), and pocket-disabled state (bitmask). Three separate attachments because their lifecycles and consumers differ. Verified via dev-only `/ip_attach_probe` command: write → save → relog → read survives. Key friction discovered: Fabric's `AttachmentRegistry.Builder.initializer(...)` doesn't auto-populate on `getAttached()` — callers need explicit `getOrInit*` wrappers. This friction (and ten others) accumulated in `COMMON_FRICTIONS.md` across Phase 11.

**Layer 1 (attachment-independent features):** This is where the most interesting architectural work happened.

*Sort via click-sequence (the architectural pivot):* Sort was initially implemented server-side — IP computed the sort permutation, mutated slots directly on the server. Testing revealed a `mayPlace` bypass bug: server-side mutation skipped vanilla's slot filters. The fix revealed a deeper principle: **IP shouldn't reimplement vanilla mutation paths when it can compose them.** Sort pivoted to client-side click sequences: client computes a permutation, sends `ClickSequenceC2SPayload`, server replays each click through vanilla's `menu.clicked(...)`. This means `mayPlace`, slot filters, events, animations, and cross-mod slot-click mixins all fire automatically. The pattern extended to bulk-move (shift+double-click) and move-matching.

This pivot produced a clean architectural rule: anything that feels like "the player makes a sequence of clicks very fast" should literally be a sequence of clicks, not a reimplementation of what clicking does. Server-side logic only for things that genuinely run outside of menus (autofill, auto-restock, auto-route, auto-replace — these fire during gameplay ticks with no menu open).

*SlotIdentity (the one exception):* Lock state was keyed by `Slot` instance (via `WeakHashMap`). Slot instances are ephemeral — vanilla creates fresh ones per menu open. Lock state vanished across menu transitions. The fix: `SlotIdentity` record `(Container, int containerSlot)`, which is stable across Slot instances as long as the underlying Container is stable. For player inventory, `Player.getInventory()` is stable per-session → locks persist across survival/creative/chest-view transitions.

This was the one library addition permitted mid-Phase-11. Justified as: purely additive (no existing consumer breaks), demonstrably needed (lock state was unusable without it), tightly scoped (~30 lines). The exception clause was then explicitly closed — all subsequent primitive needs were deferred.

*Creative inventory complications:* Creative mode has two menus simultaneously: `screen.getMenu()` returns `ItemPickerMenu`, while `player.containerMenu` returns `InventoryMenu`. IP's click-sequence approach sends clicks to `containerMenu`, but creative's `keyPressed` override silently swallows keybinds. Required a supplementary `CreativeInventoryMixin`. Creative sort was deferred entirely (creative uses `CreativeInventoryActionC2SPacket`, not standard slot-click — the click-sequence approach can't reach it).

*Text-input gate:* Creative's search field was intercepting keybind characters. Fix: walk `screen.children()` for any visible+active `EditBox` and suppress keybind dispatch when found.

*Lock persistence across chests (the unwound exception):* Chest-lock state wasn't visible across menu reopens because the client-side Container is an ephemeral `SimpleContainer` proxy (different Java reference each time). A server→client sync packet (`SortLockInitS2CPayload`) was designed, approved as an exception, then **unwound** when the three-phase reframing landed. Under the new discipline, chest-lock sync is a Phase 13 feature (F2) enabled by Phase 12's M1 primitive. This unwinding is significant — it showed the discipline working correctly even retroactively.

**Layer 2 (attachment-dependent features):** Passive behaviors shipped: elytra flight, totem death protection, death drops, fall-flying, wings rendering, mending XP accounting — all rewritten to use `IPPlayerAttachments.getOrInitEquipment/getOrInitPockets` instead of the old `MenuKit.getContainerForPlayer(...)`. Server-side handlers shipped: pocket cycling (rotation with disabled-slot skipping), autofill (keybind + scan). Pocket HUD shipped: three ItemDisplays at A-shape positions above the hotbar, no animation (conforming-to-primitives decision — animation was polish, not semantic).

The equipment panel and pocket panels **deferred** as F8 and F9 when the slot-grafting investigation revealed current MenuKit's `SlotGroup` system is handler-construction-time only, with no mechanism to graft slots onto vanilla handlers. This surfaced M4 as a mechanism candidate.

**Layer 3 (client-side peek):** Six packet types, stateless server, client session state, three peek panel types (shulker/ender/bundle). The wire protocol and server-side handlers shipped. The **peek panel UI** was initially hand-rolled with raw `graphics.fill()` calls and custom hit-testing. This was caught during review as an architectural discipline violation — the conforming-to-primitives principle says don't hand-roll when the right primitive should exist. The hand-rolled code was reverted to stubs. F15 (peek panel UI) was filed as deferred pending M6 (client-side slot primitive).

This became the Phase 11 **process finding** — the most important lesson of the phase. It's now captured in Trevor's global Claude instructions as "Architectural Discipline First, Functionality Second" and shapes all subsequent work.

**Layer 4 (public API):** Five stable cross-mod methods on `ContainerPeekClient` + `PeekSourceType` enum. API stability contract documented in `PUBLIC_API.md`.

**Layer 5 (cleanup):** Dead code removal, `/mkverify all` pass, final report.

#### The other three consumer mods

**Shulker-palette** was much smaller — half a day. Two features: palette placement (pure vanilla mixin work, no MenuKit dependency) and palette toggle button (rebuilt using Panel + Button.icon + ScreenPanelAdapter). One deferral: SP-F1 (peek palette toggle, sequencing-blocked on IP's F15). Key finding: per-item state via `CUSTOM_DATA` is self-contained — no new mechanism needed for per-item state, which narrowed M1's scope to per-slot concerns only.

**Sandboxes** had 11 compile errors from dead APIs. Rebuilt three inventory-context panels (enter sandbox button, exit sandbox button, "SANDBOX MODE" label) using Pattern 3 injection. Zero deferrals, zero mechanism candidates.

**Agreeable-allays** needed zero code changes. All MenuKit imports were already current APIs. Zero deferrals, zero mechanism candidates.

The asymmetry is itself a finding: IP exercises ~80% of MenuKit's surface and surfaces ~90% of the primitive gaps. The smaller mods validate that the library works for simpler use cases.

### Phase 12 — Library primitive design (in progress)

Phase 12 opened with four mechanism candidates: M6 → M4 → M1 → M5. The sequence changed significantly during execution.

#### M6: designed, built, tested, dissolved

M6 (ClientSlot — client-side interactive slot for decoration panels) was designed first. Clean design doc: PanelElement subclass following Button's orchestration pattern, `Supplier<ItemStack>` storage binding, `ClickHandler` with `ClickContext` record, `SlotRendering` static utility factored for M4 reuse. The design went through two review iterations — cursorItem removed from ClickContext (consumer manages cursor state, not the primitive), disabled-state support added for bundle variable capacity.

Implementation: `ClientSlot.java` (~200 lines) + `SlotRendering.java` (~80 lines) + minimum-viable peek integration in IP's `PeekDecoration`.

**Verification killed it.** Trevor tested the POC and reported: items don't drag, shift-click doesn't work bidirectionally, the interaction doesn't feel like a real container. His exact words: *"the peek should basically be a SimpleContainer representation, like a chest."* This crystallized the insight: peek needs real vanilla Slot instances participating in vanilla's full slot protocol, not a client-side primitive with custom click dispatch.

Without peek as a consumer, no other use case for ClientSlot exists. M6 dissolved. The SlotRendering utility and the M6/M4 shared-rendering boundary analysis carry forward.

**The dissolution is valuable.** It validated empirically that vanilla's slot system can't be approximated — it can only be used. Every subsequent design decision has respected this finding.

#### M4: mechanism confirmed, visual layer blocked

M4 (vanilla menu slot injection) picked up after M6 dissolved. Research phase revealed critical vanilla internals:

1. **Three parallel lists must stay synchronized.** `AbstractContainerMenu` holds `slots`, `lastSlots`, and `remoteSlots`. Only `addSlot()` updates all three correctly.
2. **Post-construction dynamic add/remove is fundamentally broken.** No re-indexing, no bounds safety, client-server mismatch causes crashes.
3. **Shift-click routing is hardcoded per handler.** Each handler's `quickMoveStack` override uses literal index ranges.
4. **Client-server symmetry is required.** Both sides must construct the same slots in the same order.

Conclusion: slot grafting must happen at handler construction time via `addSlot()` in a `<init>` RETURN mixin. Both client and server run the same mixin, so symmetry holds. This is architecturally well-supported by vanilla.

**The hasClickedOutside discovery:** First implementation attempt: items could be placed into grafted slots, but picking them up caused them to drop as world entities instead of going to the cursor. Shift-click worked. The agent initially spent time theorizing about possible causes. Directed to "stop writing diagnostic reports and read the actual source code," the agent traced the exact code path and found the root cause in 30 seconds:

`AbstractContainerScreen.mouseClicked` calls `hasClickedOutside(mouseX, mouseY, leftPos, topPos)` which checks whether the click is inside the container frame rectangle. Slots positioned outside the frame (x < 0 for equipment slots to the left of the inventory) pass `getHoveredSlot` (no frame restriction) but fail `hasClickedOutside`. The valid slot index is overwritten to -999, which changes `ClickType` from PICKUP to THROW, causing `player.drop()`. The fix: a mixin that returns false from `hasClickedOutside` when the click lands on any active slot's bounds.

This finding — traced from exact file and line numbers in vanilla source — is the kind of work that produces real understanding vs. the "theorize and ask" pattern that wastes cycles. Worth remembering as a working-practice standard.

**The MenuKitSlot restructure:** The initial M4 implementation used MenuKitSlot for grafted slots. Items still dropped on pickup. A diagnostic build using pure vanilla `Slot` instances (zero MenuKit code) showed the same behavior — which eliminated MenuKit as the cause and led to the hasClickedOutside discovery. But the diagnostic also surfaced a deeper question: why does MenuKitSlot exist as a separate thing from vanilla Slot?

The answer: MenuKit built its own `Storage` abstraction separate from vanilla's `Container`. MenuKitSlot bridges between them by overriding `getItem()`, `set()`, `remove()` to delegate to Storage instead of Container. These overrides intercept vanilla's data flow at the Slot level, which creates subtle mismatches when vanilla owns the click dispatch.

The fix: `StorageContainerAdapter` wraps any MenuKit `Storage` as a vanilla `Container`. MenuKitSlot passes this adapter to `super(container, ...)` and removes the data-flow overrides. Only vanilla's designed extension points remain: `mayPlace`, `isActive`, `getNoItemIcon`, `getMaxStackSize`. Data flows through vanilla's normal path; MenuKit's customization uses vanilla's intended hooks.

This restructure is partially complete — the code is written, the `getItem()` override is removed, but `/mkverify all` hasn't been run to verify existing MenuKit-native screens still work. That's the first task in Phase 12's stabilization step.

**The two-layer model:** A key architectural finding: grafted slots have two independent layers:
- **Handler layer** (M4 mechanism): real vanilla Slot instances via `addSlot()`. Vanilla handles item rendering, click dispatch, drag, shift-click, cursor, sync.
- **Visual layer** (Panels): slot backgrounds, ghost icons, panel frames via Panel + ScreenPanelAdapter. Vanilla only renders the items inside slots; everything else (the visual "container" around the items) is the consumer's Panel.

These layers share coordinates (Slot.x/y matches Panel element positions) but aren't coupled — the consumer constructs both and keeps them in sync through shared constants.

**The visual layer blocker:** The agent attempted to render slot backgrounds directly in a mixin render hook via `graphics.fill()`. It didn't appear on screen. Phase 11 decorations (SettingsGear, LockOverlay) render from the same hook and work — proving the hook fires. The issue was likely a bug in the manual rendering code. But the real lesson is: **don't write manual rendering in mixin hooks.** The Panel + ScreenPanelAdapter path is the proven rendering infrastructure. Slot backgrounds are Panel visual elements, not mixin-level rendering.

The visual layer needs Panel positioning — which needs the M5 region system. Building Panels with hardcoded pixel coordinates means rework when M5 ships. Hence: M5 moves earlier in the sequence.

#### F15 peek — from M6 to M4

After M6's dissolution, peek's path forward was redesigned. Four options were analyzed:

**(a) Dynamic pre-allocation at handler construction (approved).** At construction time, scan the container for peekable items. If any exist, graft 64 hidden slots (max bundle capacity) backed by a `SimpleContainer`. When peek opens, server populates the container; `broadcastChanges()` syncs items to client. Panel becomes visible; slots activate. On close, clear and hide. Zero cost for non-peekable containers.

**(b) Fixed count on every handler.** 64 slots on every menu, wasteful.

**(c) Overlay menu.** Second `containerMenu` — vanilla only supports one at a time. Rejected.

**(d) Handler replacement.** Close screen, open new handler with original + peek slots. UX regression (screen flicker). Rejected.

Trevor contributed a key refinement to option (a): the slot count is dynamic based on peekable items present, not a fixed allocation. A container with no peekable items gets zero extra slots. This scopes the cost to containers that actually need it.

Option (a) has one significant design change from Phase 11: **the server becomes stateful for peek.** Phase 11's peek was deliberately stateless (re-resolve peekable per packet, no per-player session tracking). Real vanilla Slot instances require the server to track which peek session is active. The statefulness is earned — the alternative that works requires it. The six-packet protocol from Phase 11 may partially dissolve: mutations go through vanilla's `slotClicked` + `broadcastChanges` instead of custom packets.

This is Phase 13 work, not Phase 12. M4's library primitive (slot injection via `addSlot()`) is already verified; the peek-specific handler mixins and lifecycle management are consumer-owned.

---

### The discipline that emerged

Over the course of Phases 11-12, a set of working principles crystallized through real experience — not theoretical guidelines but lessons learned from specific failures and successes:

**Architectural discipline first, functionality second.** When the right primitive doesn't exist, defer the feature and file the primitive. Don't hand-roll workarounds that ship temporarily and get replaced when the primitive lands. The hand-rolled F15 attempt is the canonical counter-example: it violated the principle, was caught in review, reverted, and the lesson was captured in global instructions.

**Conform to vanilla's extension points for slots.** Vanilla's `Slot` class has `mayPlace`, `getNoItemIcon`, `getMaxStackSize`, `isActive` as designed extension points. Use those. Don't override data-flow methods (`getItem`, `set`, `remove`) — they're internal plumbing that vanilla's click dispatch assumes works a specific way. The MenuKitSlot restructure is the concrete implementation of this principle.

**Library-not-platform.** MenuKit provides primitives; consumers compose them. The library ships mechanisms (graft slots, render slot backgrounds, position panels via regions); consumers ship policy (which handlers, which slots, which lifecycle, which interaction rules). The boundary is clean: library is general, consumer is specific.

**Don't reimplement vanilla — compose vanilla.** Click-sequence architecture for mutations. Real vanilla Slots for interactive slot surfaces. `broadcastChanges()` for sync instead of custom packets. Every time we tried to reimplement part of vanilla's system (server-side sort bypassing mayPlace, M6's custom click dispatch, MenuKitSlot's data-flow overrides), the interaction broke in subtle ways. Every time we used vanilla's own mechanisms, it worked.

**Design doc → review → implement.** For library primitives. The design doc surfaces the shape; review may redirect, refine, or approve. 1-3 iteration rounds is normal. Consumer features can proceed more directly when the primitives they use are shipped and verified.

**Two-layer model for grafted slots.** Handler layer (real vanilla Slots via addSlot) + visual layer (Panels via ScreenPanelAdapter). Coordinates shared, implementations independent.

**Phase boundaries are real.** Consumer-mod work doesn't reshape the library. Library work happens in dedicated phases. The separation keeps validation signals clean.

**Surface primitive gaps to human judgment.** When implementation reveals a missing capability, pause and name the gap. The advisor and Trevor decide whether to defer + file or (rarely) ship an exception. Default is defer.

---

## Part III: Current State and Direction (In Depth)

### What exists right now

**MenuKit library (current committed state + verified uncommitted work):**

The library provides a comprehensive component system across three contexts. The element palette includes: Button (with icon factory), Toggle (with linked factory), Checkbox, Radio/RadioGroup, Divider, Icon, ItemDisplay, ProgressBar, TextLabel, Tooltip. The Panel system handles composition, visibility (`showWhen`), and inertness (hidden panels make their slots invisible and non-interactive). ScreenPanelAdapter enables injection into vanilla screens. MKHudPanel enables HUD overlays. MenuKitScreenHandler enables native MenuKit screens.

Phase 12 additions in the working tree (uncommitted, some unverified):
- `StorageContainerAdapter` — wraps MenuKit Storage as vanilla Container
- `MKHasClickedOutsideMixin` — fixes the PICKUP→THROW misclassification for slots outside the container frame (currently only covers AbstractRecipeBookScreen; needs extension to AbstractContainerScreen and CreativeModeInventoryScreen)
- `SlotRendering` — static utility for standalone slot visuals (background, hover highlight, item, ghost icon)
- MenuKitSlot restructure — `getItem()` data-flow override removed; not yet verified via /mkverify

**Consumer mods (current functional state):**

*inventory-plus:* Sort (click-sequence), bulk-move (shift+double-click), move-matching, lock keybind + overlay (SlotIdentity-keyed), pocket cycle (server handler), pocket HUD (three ItemDisplays), autofill, settings gear + YACL config, all passive behaviors (elytra flight, totem, mending, death drops, wings rendering, fall-flying, auto-restock, auto-replace, auto-route, deep arrow search). Equipment slot grafting mechanism verified but no visual layer (no slot backgrounds or ghost icons yet). Peek wire protocol + server handlers + client session state live; peek panel UI is a stub.

*shulker-palette:* Palette toggle button on ShulkerBoxScreen, 3D open-lid rendering, placement (Strategy B: right-click places random block from shulker contents), block entity persistence, config. Peek palette toggle (SP-F1) deferred.

*sandboxes:* Enter/exit sandbox buttons in inventory screen, sandbox mode label, config.

*agreeable-allays:* Action-hint HUD panel for allay interactions. Zero code changes needed in Phase 11.

All six modules (menukit + 4 consumers + offrail) load together in the dev client without conflict.

### The forward plan

**Phase 12 remaining work (library primitives):**

*12a — Stabilize.* Verify MenuKitSlot restructure via /mkverify all. Extend hasClickedOutside fix to all three screen class hierarchies. Evaluate library-surface dissolution (SlotInjector, GraftedRegion, AbstractContainerMenuAccessor likely dissolve; StorageContainerAdapter, MKHasClickedOutsideMixin, SlotRendering keep). Clean up experimental code (debug red square, failed render-hook code). Commit verified work. Investigate the render TAIL hook contradiction only if needed (Panel rendering path works; manual rendering path is abandoned anyway).

*12b — M5 (region system).* Region specs are finalized in `Phase 12/M5_REGION_SPECS.md`. Design doc covers: how regions are implemented (per-context enums for type safety — `InventoryRegion`, `HudRegion`, `StandaloneRegion`), registration API, stacking mechanics, coordinate computation from anchor frame. Implementation. Verification with multiple panels claiming the same region.

*12c — M1 (per-slot persistent state).* Design doc covering: SlotIdentity-keyed state, per-container-type identity schemes (player inventory, block-backed, per-player, entity-backed, modded), authoritative-server + sync-on-open mechanism, Fabric attachment backing. Implementation. Verification with player-inventory lock persistence across relog + chest-lock visibility across reopen.

*Phase 12 close-out:* Update M4 design doc to reflect implementation findings. Phase 12 REPORT.md. POST_PHASE_11.md final M-entry status updates. SESSION_BRIEF.md for Phase 13 handoff.

**Phase 13 (consumer feature completion):**

*13a — Migrate existing Panels to M5 regions.* Settings gear, sandboxes buttons, shulker-palette toggle, lock overlay, pocket HUD — all move from hardcoded coordinates to named regions. Validates M5 against real consumers before F8/F9 add more Panels.

*13b — F8/F9 (equipment + pockets visual layer).* M4 mechanism already works. This phase adds: equipment panel backdrop (slot backgrounds via Panel + ScreenPanelAdapter + M5 region), ghost icons via `Slot.getNoItemIcon()`, pockets (9 Toggle.linked buttons + nine 3-slot panels with disabled-slot gating), config-driven visibility via `Slot.isActive()`.

*13c — F15 (peek panel UI).* Dynamic pre-allocation at handler construction (option a). Per-handler-type mixins scan for peekable items. Server-side statefulness (PeekSession tracking). SimpleContainer populated on peek open; broadcastChanges syncs. Panel positioned via M5 region. Packet protocol simplification.

*13d — SP-F1 (shulker-palette peek toggle).* Transitively unblocked by F15. First real cross-mod integration test of IP's public API.

*13e — Remaining F-entries:* F1/F2 (lock persistence, requires M1), F3 (full-lock Ctrl+click), F4 (sort consolidation), F5/F6 (creative sort + bulk-move), F7 (hotbar↔main bulk-move), F10 (speculative pocket HUD toggle).

*13f — Close-out.* Updated reports per consumer mod, final verification sweep.

### The golden outcome, restated

When Phase 13 completes:

1. **MenuKit is a complete, well-documented component library** with a coherent thesis, clean architecture, three rendering contexts, a rich element palette, injection patterns for vanilla screens, a region system for collision-free panel positioning, per-slot persistent state, and construction-time slot injection into vanilla handlers.

2. **All four consumer mods are fully functional** with native-feeling interactions: equipment and pockets work like vanilla armor slots (drag, shift-click, filter, ghost icons, persistence). Peek works like opening a mini-chest overlay. Sort/bulk-move/move-matching work via vanilla click dispatch. Palette placement works with 3D rendering. Lock state persists across sessions. Panel positioning is systematic, not hardcoded.

3. **The architectural discipline is documented and enforced** — global instructions, per-project memory, design docs, and working-practice conventions all reinforce "defer features rather than hack them in," "conform to vanilla's extension points," "library provides mechanisms, consumers provide policy."

4. **The primitive surface is evidence-driven** — every primitive that shipped was justified by concrete consumer demand, not speculation. Every primitive that dissolved (M6) was dissolved because empirical verification showed the real need was different from the assumed need. The library contains exactly what consumers proved they need.

5. **The documentation is accurate and comprehensive** — THESIS.md, CONTEXTS.md, PALETTE.md, STORY.md, per-element design docs, per-phase reports, injection pattern docs, POST_PHASE_11.md with feature/mechanism entries, COMMON_FRICTIONS.md with 11 Fabric/vanilla API friction patterns. A new contributor can read these and understand the architecture.

---

## Key file locations

All paths relative to the menukit repo root.

**Foundational docs:**
- `Design Docs/THESIS.md` — MenuKit's identity and principles
- `Design Docs/CONTEXTS.md` — three rendering contexts
- `Design Docs/PALETTE.md` — element palette decisions
- `Design Docs/STORY.md` — architectural narrative (may need updating post-Phase-12)

**Phase 11 docs:**
- `Design Docs/Phase 11/SESSION_BRIEF.md` — whole-phase handoff anchor
- `Design Docs/Phase 11/POST_PHASE_11.md` — complete feature/mechanism deferral inventory (recently updated with Phase 12 findings)
- `Design Docs/Phase 11/COMMON_FRICTIONS.md` — 11 Fabric/vanilla API frictions
- `Design Docs/Phase 11/inventory-plus/REPORT.md` — IP's comprehensive close-out
- `Design Docs/Phase 11/inventory-plus/REFACTOR_PLAN.md` — IP's implementation spec (still useful for Phase 13)
- `Design Docs/Phase 11/inventory-plus/AUDIT.md` — IP's archaeological audit
- `Design Docs/Phase 11/inventory-plus/POST_PHASE_11.md` — IP-specific F1-F7 and M1-M3 entries
- `Design Docs/Phase 11/shulker-palette/REPORT.md` — SP close-out
- `Design Docs/Phase 11/final-consumers/REPORT.md` — sandboxes + agreeable-allays close-out

**Phase 12 docs:**
- `Design Docs/Phase 12/SESSION_STATUS.md` — detailed implementation state from the first Phase 12 session
- `Design Docs/Phase 12/M4_VANILLA_SLOT_INJECTION.md` — M4 design doc (partially stale; see SESSION_STATUS §8)
- `Design Docs/Phase 12/M5_REGION_SPECS.md` — Trevor's finalized region specifications
- `Design Docs/Phase 12/M6_CLIENT_SIDE_SLOTS.md` — dissolved; historical record with carryforward analysis

**Other docs (potentially stale):**
- `Design Docs/PHASES_6_THROUGH_12_BRIEF.md` — historical forward plan; Phases 6-11 are complete
- `Design Docs/DEFERRED.md` — cross-phase deferral tracker; many items resolved, needs sweep

**Architecture + element docs:**
- `Design Docs/Architecture Design Docs/` — INVENTORY_INJECTION_PATTERN.md, STANDALONE_INJECTION_PATTERN.md, HUD_INJECTION_PATTERN.md
- `Design Docs/Element Design Docs/` — per-element design docs for Button, Toggle, Checkbox, etc.
- `Design Docs/Phase Reports/` — PHASE_6 through PHASE_10 reports

**Consumer mod sources:**
- `inventory-plus/` — largest consumer; `PUBLIC_API.md` at its root
- `shulker-palette/` — palette toggle + placement
- `sandboxes/` — sandbox world management
- `agreeable-allays/` — allay behavior mod

**Global instructions:**
- `~/.claude/CLAUDE.md` — Trevor's global Claude Code instructions (includes "Architectural Discipline First, Functionality Second")

---

## Working relationship context

**Trevor's working style:** He's a systems thinker who connects dots across domains — finance, healthcare, spirituality, architecture, music. He values understanding the *why* behind things. He wants a co-creator dynamic: push back, suggest alternatives, think alongside him. He likes directive guidance when energy is low. He independently rediscovers existing concepts — when this happens, affirm the insight and give it its proper name.

**The advisor role:** You are an architectural advisor, not a coder. You don't edit files or run commands. You hold the strategic picture across phases, make architectural decisions, catch scope creep, hold the discipline, and write briefings for the agent (Trevor's Claude Code session) to execute. Trevor curates what you see — he pastes agent reports, describes test results, relays questions. Your output is analysis, decisions, and agent briefings.

**The agent relationship:** Trevor runs a Claude Code agent session for implementation work. The agent reads design docs, writes code, runs builds, reports results. You write briefings that Trevor pastes to the agent. The agent surfaces questions through Trevor. You never communicate with the agent directly. The agent maintains design docs, reports, and SESSION_BRIEF/SESSION_STATUS files. You advise on their content but don't edit them.

**Key interaction patterns that work well:**
- Agent surfaces a question → Trevor relays → you analyze and decide → Trevor relays the decision back
- Trevor describes a test result → you interpret the architectural implications → write a brief for the agent
- Trevor has an idea → you pressure-test it (tradeoffs, costs, alternatives) → decide together → brief the agent
- Phase boundary → you write a comprehensive kickoff briefing with read order, working principles, and specific first action
- Agent compaction → you write a handoff brief capturing strategic + tactical state for the fresh agent

**Key interaction patterns that don't work well:**
- Agent theorizing when it should be reading source code — redirect to "read the file, trace the code path, report what you found"
- Agent asking for advisor decisions on questions it can answer itself by reading code — redirect to self-service
- Building features without design-doc review for library primitives — the shape matters more than speed
- Hardcoding coordinates when a positioning system is coming — creates rework

---

## Your first action

Read this document fully. Then generate a numbered list of questions — things where your understanding of the project would benefit from Trevor's direct answer. These might include:

- Clarifications on architectural decisions where the reasoning isn't clear from this document
- Questions about Trevor's priorities or preferences for the remaining work
- Areas where the document describes what happened but not why a particular choice was made
- Questions about the consumer mods' intended user experience that would inform design decisions
- Anything about the codebase state, conventions, or patterns that would help you advise effectively

Trevor will answer your questions, and those answers will complete your context. After that, you're ready to advise on the current work: Phase 12 stabilization (12a), M5 region system design (12b), M1 per-slot persistent state design (12c), and eventually Phase 13 consumer feature completion.

Be thorough with your questions. This is your one chance to ask about anything before we dive into active work. It's better to ask twenty good questions now than to discover a gap mid-decision later.
