# Phase 11 — Post-Phase-11 Entries

Accumulated feature deferrals (F*) and mechanism candidates (M*) across all consumer-mod refactors. Phase 12 reads the M* entries as design input for MenuKit primitives. Phase 15 completes the F* entries against Phase 12's primitives.

Organized by origin mod. Each entry names what Phase 15 delivers, the mechanism (if any) that enables it, and a rough implementation sketch.

---

## Inventory Plus

### Features

**F1 — Persistent player-slot lock state across sessions.**
Lock state currently lives in `ServerLockStateHolder` / `ClientLockStateHolder` (per-session, keyed by `SlotIdentity`). Survives menu transitions within a session but resets on disconnect. Persistence requires a per-slot state primitive that survives across sessions.
**Blocked on:** M1 (unified per-slot state primitive).
**Phase 15 sketch:** When M1 ships, back lock state by the primitive. Migration: read existing per-session state into the persistent store on first use; clear per-session holder.

**F2 — Chest-slot lock state visible across menu reopens.**
When a chest is reopened, the lock overlay should show which slots the player previously locked. Currently reset because lock state is per-session.
**Blocked on:** M1.
**Phase 15 sketch:** Same backing store as F1. Per-container-slot state keyed by `SlotIdentity`.

**F3 — Full-lock (Ctrl+click) feature.**
A broader lock that blocks direct PICKUP clicks as well as auto-routing. Different semantic from sort-lock (which only blocks `moveItemStackTo` + `QUICK_MOVE`). New feature, not a fix.
**Category:** feature-addition.
**Phase 15 sketch:** New `FullLock` state alongside `SortLock`. `IPSlotMayPlaceMixin` checks both; `IPSlotGetItemMixin` checks both during routing + blocks during PICKUP for full-lock.

**F4 — Sort consolidation.**
After sorting, merge partial stacks of the same item into full stacks before arranging. Currently sort only reorders without consolidating.
**Category:** feature-complexity.
**Phase 15 sketch:** Pre-pass in `SortAlgorithm`: group by item, consolidate to max stack size, then run existing sort. Click sequence computes PICKUP + transfer + DROP clicks for the merge phase.

**F5 — Creative-mode sort.**
Sort in creative inventory. Requires routing through the creative tab's separate packet path.
**Category:** feature-scope (creative packet path).
**Phase 15 sketch:** Investigate whether `ClickSequenceC2SPayload` can route through creative's container, or whether a separate creative-specific payload is needed.

**F6 — Creative-mode bulk-move.**
Same creative-packet-path blocker as F5.
**Category:** feature-scope.

**F7 — Bulk-move within a single player-inventory region (no container open).**
Shift+double-click in the hotbar or main inventory with no container open. Vanilla's `doubleclick` / `lastQuickMoved` interaction produces a one-stack-back-to-anchor bug.
**Category:** feature-complexity (vanilla click-protocol edge case).
**Phase 15 sketch:** May resolve for free if F3 investigation digs into vanilla's double-click state fields. Otherwise defer indefinitely.

**F8 — Equipment panel.**
Two-slot panel (elytra + totem) visible in `InventoryScreen` + `CreativeModeInventoryScreen`, with per-slot filters, ghost icons, and config-driven disable. Passive behaviors (flight, totem, mending, death drops, wings rendering) are already live in Layer 2 — only the in-inventory UI is deferred.
**Category:** primitive-blocked (visual layer pending M5 region system).
**Blocked on:** M5 (region system for panel positioning). M4 mechanism (slot grafting via `addSlot()`) is **verified working** in Phase 12 — equipment slots graft onto InventoryMenu at construction, accept elytra/totem via `mayPlace`, enforce max stack 1, shift-click routes correctly, persistence works via Fabric attachments. What remains is the visual layer (slot backgrounds, ghost icons, panel frame) which should go through Panels + ScreenPanelAdapter positioned via M5 regions.
**Phase 15 sketch:** Equipment slot grafting is already implemented (IP's `InventoryMenuMixin`). Phase 15 adds the visual backdrop Panel positioned via M5 region (likely `InventoryRegion.LEFT_ALIGN_TOP`). Ghost icons via vanilla's `Slot.getNoItemIcon()` extension point. Config-driven visibility via `Slot.isActive()` override.

**F9 — Pockets panels.**
Nine `Toggle.linked` buttons (one per hotbar slot, RAISED/INSET) + nine 3-slot pocket panels backed by the `POCKETS` attachment, with `POCKET_DISABLED` integration for per-slot disable-toggling via empty-click.
**Category:** primitive-blocked (visual layer pending M5 region system).
**Blocked on:** M5 (region system for panel positioning). M4 mechanism (slot grafting) applies here the same way as F8 — pockets slots graft onto InventoryMenu at construction time, backed by `PlayerAttachedStorage.forPocketSlice(hotbarSlot)`. The grafting mechanism is verified; the visual layer is what remains.
**Phase 15 sketch:** Graft 27 pocket slots (9 × 3) onto InventoryMenu via the same `addSlot()` pattern as F8. Per-slot `mayPlace` for disabled-slot gating. Panel visibility via `Slot.isActive()` returning false when pocket is closed (`openPocketIndex != hotbarSlot`). Nine `Toggle.linked` buttons at hotbar positions. Visual backdrop Panel positioned via M5 region (likely `InventoryRegion.BOTTOM_ALIGN_LEFT` or similar).

**F10 — In-inventory pocket HUD toggle button.**
If eventually desired: a small button in the inventory screen that toggles the pocket HUD's visibility. Not currently in the refactor plan — speculative placeholder if users request it. HUD itself (Layer 2 shipped) exists independently of this.
**Category:** feature-addition.
**Phase 15 sketch:** Small `Toggle.linked` button in the settings region, reading/writing `config.showPocketHud`.

**F15 — Peek panel UI (umbrella — subsumes F11–F14).**
The visible peek panel and all user-facing peek behavior: rendering, click dispatch, sort-within-peek, move-matching-into-peek, drop, double-click-collect. These defer together because they all depend on the same slot injection mechanism.
**Category:** primitive-blocked.
**Blocked on:** M4 (vanilla menu slot injection — option (a) approved: dynamic pre-allocation of peek slots at handler construction based on peekable item count). M6 dissolved in Phase 12; peek needs real vanilla Slot instances, not client-side decoration slots.
**What ships in Phase 11 Layer 3 (kept live):**
- Six peek packet types (wire protocol)
- `ContainerPeek` server-side: stateless open/move/close handlers with per-source-type storage access (shulker `CUSTOM_DATA`, bundle `BUNDLE_CONTENTS`, ender inventory)
- `ContainerPeekClient` client session state + S2C receivers
- Cross-mod public API (five stable methods + `PeekSourceType` enum) — see `inventory-plus/PUBLIC_API.md`
- Peek keybind registered; consumes key silently (doesn't conflict with vanilla Alt-handling)

**What's deferred:**
- Visual peek panel rendering
- Click dispatch into peek slots
- Sort / move-matching / drop / double-click-collect actions
- The `handlePeek` keybind behavior that opens a session

**Phase 15 sketch:** Uses M4's slot-injection mechanism (option a: dynamic pre-allocation). Per-handler-type mixins scan container contents at construction for peekable items; if any found, graft 64 hidden peek slots (max bundle capacity) backed by a `SimpleContainer`. Server-side statefulness: `PeekSession` tracks which slot is peeked, populates the SimpleContainer on peek-open, clears on peek-close. `broadcastChanges()` handles sync automatically. Panel visibility via `Slot.isActive()` + Panel.showWhen. Peek panel visual backdrop via M5 region. Phase 11's six-packet protocol may partially simplify — `PeekMoveC2SPayload` dissolves (vanilla's `slotClicked` handles mutations); `PeekSyncS2CPayload` dissolves (`broadcastChanges` handles sync). Remaining: `PeekOpenC2S`, `PeekCloseC2S`, `PeekErrorS2C`.

**Historical note:** A prior Layer 3 pass hand-rolled peek panel rendering via raw `graphics.fill()` calls and custom hit-testing, violating the conforming-to-primitives principle. Reverted when the architectural mismatch was called out. F15 now properly subsumes what were F11–F14 separate-feature entries; those finer-grained sub-features are implementation choices within F15's scope rather than independently-deferrable items.

### Mechanisms

**M1 — Unified per-slot state primitive.**
MenuKit primitive for persistent per-slot metadata that survives menu transitions and sessions. Keyed by `SlotIdentity` at the runtime/read layer; persistence keys through `PersistentContainerKey` variants (PlayerInventory, EnderChest, BlockEntityKey, EntityKey, Modded) attached to the natural owner via Fabric attachments.
**Status:** shipped in Phase 12, commit `9cdc553`. Typed channels with dual `Codec<T>` (NBT) + `StreamCodec<T>` (wire) codecs; Tag-native storage inspectable via `/data get`; menu-open + player-join/respawn snapshot paths; per-player private on shared-owner containers with V2 shared-state migration hook. `/mkverify all` contract 7 covers the server-side persistence path with Tag-level inspection.
**v1 coverage:** PlayerInventory + BlockEntityKey full; EnderChest needs player context at resolver (server-explicit API); EntityKey + Modded stubs. Full-coverage lands in Phase 15 consumer work as needed.
**THESIS addition:** principle 6 "Match vanilla's persistence patterns" — codifies the NBT-native discipline across the library.
**Design doc:** `Design Docs/Mechanisms/M1_PER_SLOT_STATE.md` (status: Resolved, §10 decisions locked).
**Phase 15 consumer:** 13e-1 (IP sort-lock migration) replaces `ClientLockStateHolder` + `ServerLockStateHolder` + `SortLockC2SPayload` + `SlotLockState` with a single `IPSlotState.SORT_LOCK` channel declaration. F1 (player-slot lock persistence) + F2 (chest-lock visibility across reopens) fall out automatically.

**M2 — SlotIdentity.**
`SlotIdentity` record `(Container, int containerSlot)` + static factory `SlotIdentity.of(Slot)`. Zero-dependency primitive for cross-menu stable slot identity.
**Status:** shipped in Phase 11 IP. M1 builds on it.

**M3 — MKFamily — DELETED (executed Phase 14a).**

**Status (current, Phase 14a):** MKFamily and the mod-family concept are out of MenuKit. `MenuKit.family()`, `MKFamily.java` (the full class), and the orphaned `com.trevorschoeny.menukit.config` package are deleted. The four consumer mods migrated:
- **inventory-plus** — direct `KeyMapping.Category.register(Identifier.fromNamespaceAndPath("inventory-plus", "inventory-plus"))`. 1:1 swap.
- **sandboxes** — same pattern with the sandboxes mod-id. 1:1 swap.
- **shulker-palette** — pure deletion. SP had no keybinds; the family call was a post-scope-down no-op.
- **agreeable-allays** — pure deletion. AA retrieved `category` but never used it (no keybinds in client init).

Validator V8 scenario (which exercised MKFamily Layer A) deleted — compiler enforces absence; an absence-probe has no architectural value. M3 slot in the `Mechanisms/` folder remains unfilled (M3-as-vanilla-slot-injection from Phase 13 renumbering is unrelated to the historical M3 MKFamily slot).

User-facing effect: vanilla Controls screen now shows `Inventory Plus` and `Sandboxes` as separate sections instead of a single shared "Trev's Mods" section. Intended end-state per Trevor's Phase 13 decision; SP/AA contribute no section since they have no keybinds.

*(Below content preserved as historical record of the Phase 12.5 scope-down state, which preceded the Phase 13 deletion decision and Phase 14a execution.)*

*(Phase 12.5 scope-down record preserved below.)*

**Status (Phase 12.5, historical):** resolved in Phase 12.5, disposition 2 (scope down to grouping only). See `menukit/Design Docs/Phases/12.5/DESIGN.md` §11 for the scope-down rationale; the advisor rulings + implementation record are folded into this entry rather than a separate sub-phase REPORT (M3 is library cleanup, not a validation scenario).

**Shipped (post-scope-down `MKFamily` surface).** Layer A only — identity grouping + keybind-category sharing. `MenuKit.family("id")` returns a canonical instance; mods contribute `displayName` / `description` / `modId`; `getKeybindCategory()` provides the shared section in vanilla's Controls. Zero YACL, zero ModMenu, zero persistent storage owned by MenuKit.

**Deleted in full.** `MKFamilyConfig` (~107 lines), `GeneralOption` (~63 lines), `ModMenuIntegration` (~46 lines), `MKKeybindController` (~400 lines — dead code found in-phase; was a YACL widget bridge that no consumer wired up), `KeyEntryAccessor` + its auto-scroll mixin injection. MenuKit's own `SHOW_ITEM_TIPS` toggle deleted; tooltip enrichment now always-on. `MKFamily` trimmed from 369 → ~115 lines.

**Decisions landed (mid-phase advisor rulings, Reading B/C on storage).**
- *Storage layer leaves entirely.* Reading A (keep storage primitive as Layer A, drop only rendering) rejected: shared cross-mod persistent storage is still Layer B aggregation at 40% scale — a second mod author still inherits coordination responsibility, and keeping the storage primitive keeps the seed for the UI-aggregation layer scope-down just removed.
- *Four IP mixin `getGeneral` reads deleted (Reading C).* Reads were orphan — no UI registered `auto_restock` / `auto_replace_tools` / `autofill_enabled` / `deep_arrow_search` as togglable, so effective behavior had always been always-on. Not a feature regression; recognition that the toggle affordance was never finished. If Phase 15+ wants real user-facing toggles, IP adds them to `InventoryPlusConfig` at that point.
- *`SHOW_ITEM_TIPS` hardcoded to true.* MenuKit-internal polish feature, no user-facing UI ever shipped, always-on is the reasonable default. "MenuKit own-config primitive" filed as deferred mechanism candidate below.
- *In-UI config entry points: disposition (a), symmetric across IP and sandboxes.* IP's `SettingsGearDecoration` gear opens IP's own config screen; sandboxes' `SandboxScreen.settingsButton` opens sandboxes' own config screen. No cross-mod launcher, no aggregated tabs. Each mod owns its own in-UI entry; each mod gets its own ModMenu entry.

**Consumer migration cost.** Three of four mods (IP, shulker-palette, sandboxes) added their own `<Mod>ModMenuIntegration implements ModMenuApi` + their own standalone `<Mod>ConfigScreen.build(parent)` + `modImplementation "com.terraformersmc:modmenu"` in their `build.gradle` + a `modmenu` entrypoint in their `fabric.mod.json`. Agreeable-allays unchanged (pure Layer A consumer: only `family.getKeybindCategory()` + `modId(...)`).

**UX shift.** Previously: clicking config on any `trevmods` mod opened one unified YACL screen with a tab per mod + auto-focus on the clicking mod. Post-M3: each mod has its own separate config screen; the unified-tabs experience is retired (that was the Layer B the scope-down explicitly removes). For Trevor's personal use this is fine; for eventual public release, a mild UX regression that each mod handles on its own — correct distribution of responsibility.

**Migration artifact — stale config files.** Existing `config/menukit-family-*.json` files on users' disks become orphan after M3 — no runtime effect, just unused files until manually deleted. Not solving in this session; Phase 14+ cleanup pass if public release ever cares.

**Known issue carried forward — sandboxes in-UI Settings button click.** `SandboxScreen.settingsButton` (top-right of the sandbox management screen) doesn't open the config screen when clicked. Log confirms `YetAnotherConfigLib` does generate the screen and vanilla does pause-transition (so `setScreen` is taking effect), but the screen never becomes visible — best-fit hypothesis is press-release propagation across the screen transition: vanilla `Button.onPress` fires on mouse-press, synchronously calls `setScreen(YACL)`, and the subsequent mouse-release propagates to the newly-active YACL screen at the same top-right coordinate, where some clickable YACL element may consume it and immediately close. Failure shape likely pre-existed M3 (pre-M3 click path was `family.buildConfigScreen → setScreen`, structurally identical to post-M3 `SandboxConfigScreen.build → setScreen`). ModMenu's sandboxes entry works — users can still configure sandboxes, just not via the in-UI Settings button. Deferred to the Phase 15 sandboxes refactor for proper diagnosis + fix (either reposition the button away from the top-right, switch to click-on-release dispatch, or route via a MenuKit `Button`-in-`Panel` adapter like IP's gear — IP's click path works because `ScreenPanelAdapter` + its mixin intercept both press and release cleanly before vanilla).

**Deferred mechanism candidate — MenuKit own-config primitive.** If MenuKit later needs its own user-facing toggles (beyond the tooltip-enrichment hardcode), that's when it ships a library-internal config file — no YACL, no ModMenu, just GSON + a single JSON under its mod-id. Not in scope until a real consumer need surfaces; speculation otherwise.

**Deferred question — keybind-category sharing review (Phase 15 public-release prep candidate).** §11's scope-down preserved keybind-category sharing as Layer A: `getKeybindCategory()` returns a single shared category per family, so all mods in the `trevmods` family show up under one "Trev's Mods" section in vanilla's Controls screen. Rationale at the time: grouping is a real user-facing coordination primitive — better UX than four separate sections scattered alphabetically among every other mod's categories. Question raised during M3 smoke-test that's worth examining before public release: when a non-Trevor author adopts MenuKit, their mod isn't necessarily part of a "family" at all, and MenuKit shipping a grouping primitive may be the same shape of ecosystem-shaping assumption that Layer B was. Not resolving in M3 (would reverse §11 mid-implementation); file as a Phase 15 design pass when public-release prep begins. If the answer is "keybind-sharing leaves too," MKFamily's Layer A shrinks further to identity + mod-id roster only, and `getKeybindCategory()` extracts to a consumer concern.

**M6 — Client-side slot primitive for decoration panels.**
**Status:** dissolved in Phase 12. Verification showed peek requires vanilla-native slot instances via M4 (full drag / shift-click / cursor protocol), not client-side decoration slots. No other consumer evidence exists for a client-side slot primitive without peek. Rendering analysis (SlotRendering utility) carries forward to M4.
**Dissolution record:** `Design Docs/Archived/M6_CLIENT_SIDE_SLOTS.md` — preserved as historical record with the verification finding and carryforward analysis.

**M5 — Context-scoped region system for panel positioning.**
MenuKit primitive for declaring panel positions by named region rather than pixel coordinates, scoped per-context. Collision arbitration via stacking along region-defined flow axes.
**Status:** shipped in Phase 12, commit `21c935d`. Three per-context enums (InventoryRegion 8, HudRegion 9, StandaloneRegion 8), `RegionMath` pure resolver, `RegionRegistry` internal state, `ScreenPanelAdapter` + `MKHudPanel.Builder` overloads, `PanelPosition.IN_REGION` reserved API, `Panel.getWidth/getHeight/size` stacking support. `/mkverify all` contract 6 covers the coordinate math for all 25 regions + overflow.
**Key findings (M5 round-2):**
- **By-value vs by-reference composition distinction (§4A)** — regions model by-value (stackable) decorations. By-reference panels (grafted-slot backdrops, vanilla-anchored overlays) stay on the lambda path with shared constants. Named as a library-wide pattern.
- **Shared-constants pattern for grafted-slot backdrops (§5.6)** — same coordinates drive the handler-layer `addSlot` and the visual-layer Panel origin. One source of truth; no drift between layers.
- **Standalone regions: enum shipped, solver deferred** — `StandaloneRegion` + `PanelPosition.IN_REGION` are reserved API; the layout solver lands when a concrete standalone consumer surfaces.
**Design doc:** `Design Docs/Mechanisms/M4_REGION_SYSTEM.md` (status: Resolved, §10 decisions locked). Region catalog in `M5_REGION_SPECS.md`.
**Phase 15 consumer:** 13a migrates IP settings gear + sandboxes buttons to `InventoryRegion.TOP_ALIGN_RIGHT`. Gear position shifts from inside-the-frame to outside-above; pending Trevor screenshot sign-off per M5 §5.1. Sandboxes adds `fabric.mod.json depends` on inventory-plus for stable registration ordering.

**M4 — Vanilla menu slot injection primitive.**
MenuKit primitive for injecting real interactive slots into a vanilla menu at construction time via `addSlot()`. Consumers write their own handler-specific mixins; the library provides the supporting pieces.
**Status:** mechanism shipped in Phase 12 checkpoint `4ed9793` (IP's `InventoryMenuMixin` — equipment slots). 12a stabilization cleaned up library surface and fixed the cross-cutting `hasClickedOutside` issue (commit `d22bdf8`). Visual-layer pattern established via M5 §5.6 (by-reference-to-slot-coords with shared constants + `ScreenPanelAdapter`).
**Key Phase 12 findings (full discussion in `Mechanisms/M3_VANILLA_SLOT_INJECTION.md` "Implementation findings" section):**
- Vanilla's Slot extension points (`mayPlace`, `getNoItemIcon`, `getMaxStackSize`, `isActive`) are sufficient. No MenuKitSlot data-flow overrides needed for grafted slots — plain Slot subclasses with 2-3 overrides work.
- `hasClickedOutside` misclassifies clicks on slots outside the container frame (overwrites valid slot index to -999, changing PICKUP to THROW). Fix: three-screen mixin coverage (AbstractContainerScreen + AbstractRecipeBookScreen + CreativeModeInventoryScreen) with shared `MKClickOutsideHelper`.
- Two-layer model: handler layer (real vanilla Slots via `addSlot`) + visual layer (Panels via `ScreenPanelAdapter`). They share coordinates but aren't coupled — coordinates live in a shared-constants file that both layers read.
- `MenuKitSlot.getItem()` override is load-bearing for inertness. 12a removal caused an `/mkverify` regression; restored in `03b2a1a` with a "don't remove this again" javadoc.
- SlotInjector / GraftedRegion / AbstractContainerMenuAccessor dissolved — IP's mixin calls `addSlot` directly via its `@Mixin`-generated superclass. Library surface: `StorageContainerAdapter` + `MKHasClickedOutside*Mixin` family + `SlotRendering`.
- F15 (peek) uses the same mechanism via option (a): dynamic pre-allocation at construction based on peekable item count. 64 hidden slots (max bundle capacity), zero-cost for non-peekable containers.
**Phase 15 consumers:**
- 13b (F8 equipment panel backdrop + F9 pockets — F9 pending UI-structure clarification)
- 13c (F15 peek panel UI)
- SP-F1 (shulker-palette peek toggle — transitively enabled by F15)

---

## Shulker Palette

### Features

**SP-F1 — Peek palette toggle.**
When IP's peek panel shows a shulker box, a palette toggle button should appear on the peek panel. Reads palette state from the peeked item's CUSTOM_DATA. Sends toggle packet with the peeked slot index.
**Category:** sequencing-blocked (depends on IP Layer 3, not on a missing MenuKit primitive).
**Blocked on:** IP Layer 3 (client-side peek: shulker / ender / bundle panels, peek keybind wiring). IP's peek is currently stubs — `ContainerPeekClient.isPeeking()` returns false, `getPeekedSlot()` returns -1.
**Phase 15 sketch:** When IP's Layer 3 ships and peek is functional, rebuild the peek toggle as a Pattern 2 injection on the peek panel. Same approach as the ShulkerBoxScreen toggle (Panel + Button.icon + ScreenPanelAdapter). `ShulkerPalettePeekCompat` was deleted during Phase 11 cleanup — rewrite from scratch against current APIs.
**Note:** This is NOT primitive-blocked. No Phase 12 mechanism needed. It's a sequencing dependency on IP work.

### Per-Item State Finding

The kickoff predicted per-shulker palette state would surface a new mechanism candidate (sibling or extension of M1). **Negative finding: no new mechanism needed.**

Shulker-palette's per-item state (palette flag) is self-contained via CUSTOM_DATA on the ItemStack:
- Placed block: `trevorMod$isPalette` field on `ShulkerBoxBlockEntity`, synced to client via DataSlot on `ShulkerBoxMenu`.
- Item: `trevormod_palette` boolean in CUSTOM_DATA component, readable anywhere the ItemStack is accessible.
- Peeked item: when IP's peek shows a shulker, the ItemStack is in a real slot in `containerMenu` — CUSTOM_DATA is accessible.

M1 (unified per-slot state) stays scoped to per-slot concerns. Per-item state stored on the item itself doesn't need a library abstraction.
