# Phase 11 — Inventory-Plus Audit

**Phase 11 deliverable (audit only).** This document catalogs inventory-plus's user-visible behavior as it currently lives in the consumer mod's source tree. The tree still compiles against pre-Phase-5 MenuKit APIs — it is disabled in `dev/build.gradle` and has not been touched since Phase 5's library demolition. Read it as archaeology: the *what* and *why* transfer; the *how* does not.

The purpose is to produce the list of behavioral requirements Phase 11's refactor must deliver. It intentionally does not plan the refactor. Mapping to current primitives and friction notes appear inline where the mapping is useful for later planning, flagged as **Maps to** / **Friction** — but the audit does not commit to file structure, mixin counts, or public API shapes. Those belong to the refactor plan, after this audit is reviewed.

The audit also does not attempt to explain IP to a first-time reader. It assumes a reader who has read THESIS/CONTEXTS/PALETTE/DEFERRED and the three injection pattern docs. The audit's job is to establish the **requirements** IP imposes on those primitives, not to re-teach what the primitives are.

---

## 0. Method and scope

**Read scope.** All source files under `inventory-plus/src/main/java/com/trevorschoeny/inventoryplus/`, the full `InventoryPlusConfig`, the network payloads, the nine passive-behavior mixins, and the three UI-behavior mixins. Not read: `build.gradle` / `fabric.mod.json` (mechanical; not part of behavior). Assets (textures under `src/main/resources`) are named in the audit where relevant but not inventoried pixel-by-pixel.

**Out of scope for the audit.**
- Optimizing IP's internal organization. If IP's package layout is awkward, that's not the audit's problem — the refactor may restructure as needed, but the audit reports behavior, not preferred topology.
- Feature additions or behavioral improvements. Anything not currently working in the old tree is not something Phase 11 adds; reconsideration triggers get filed in DEFERRED post-Phase-11.
- Re-auditing MenuKit's surface. The three pattern docs are the spec; the audit trusts them.

**Audit style.** Each UI surface and behavior gets:
- **What it does** (user-visible outcome).
- **Where it appears** (screens, anchors, contexts).
- **How it's triggered** (input, state, predicate).
- **Maps to** (the Phase 6–10 primitive or pattern that covers it, cleanly or not).
- **Friction** (where the mapping is imperfect). Absent friction → the mapping is clean.

Friction is an observation, not a proposal. The friction list feeds the planning stage; the audit does not decide how to resolve it.

---

## 1. UI surfaces

IP renders seven categorically distinct UI surfaces across three contexts. Enumerated here in order of architectural weight (most demanding → least).

### 1.1 Peek panels (3 variants: shulker / ender / bundle)

**What it does.** Opens a popup grid showing the contents of a peekable item (shulker box, bundle, or ender chest) without leaving the current screen. Contents are live: modifications in the peek panel write through to the item's backing storage via the server's `MKContainerSource`.

**Where it appears.** Inside any container screen — player inventory, vanilla chests, shulker box screens, hoppers, dispensers, third-party modded containers — wherever the player can hover a peekable item. Panel positioning: `posLeft` (left-anchored, appended to the container screen's body via MenuKit's constraint-based layout). Three panels registered separately, all starting hidden, mutually exclusive visibility (at most one shown at a time).

**Content structure.**
- Shulker peek: fixed 3×9 grid (27 slots) + vertical title label on the left.
- Ender peek: fixed 3×9 grid (27 slots) + vertical title label.
- Bundle peek: variable grid, up to 64 slots, 9 rows, fills right. Effective slot count computed client-side from bundle weight + occupancy (`getEffectiveBundleSlots()`), slots beyond the effective count are hidden via `disabledWhen`. Title label same as the others.

All three panels use `PanelStyle.RAISED`, `shiftClickIn=true`, `shiftClickOut=true`.

**How it's triggered.**
- **Client-side input.** Peek keybind (default `Left Alt`) pressed while hovering a peekable slot in any container screen. Current code uses `MKEvent.Type.KEY_PRESS` slot-handler; new shape will be Pattern 1 (keybind interception via consumer mixin).
- **Protocol.** `PeekC2SPayload(menuSlotIndex)` → server resolves type (shulker / ender / bundle) from the hovered item, selects the correct container, binds the appropriate `MKContainerSource`, registers a dynamic region on `player.containerMenu`, returns `PeekS2CPayload(slotIndex, sourceType, activeSlots, title)`.
- **Toggle semantics.** If the hovered slot is already being peeked, C2S sends `-1` → close. If a different peekable is hovered, server unbinds the previous source before binding the new one (switch-on-move is handled server-side).
- **Close paths.** Menu close (handled by server `MENU_CLOSE` after-handler), disconnect (handled by Fabric disconnect event), explicit C2S close.

**Recipe book interaction.** When a peek opens, IP closes the recipe book and records its prior state. When the peek closes, IP restores the recipe book if it was open. This is because the recipe book widget would visually overlap the peek panel.

**Cross-mod surface.** Other mods observe whether a peek is open, which slot, and which type, to compose decorations (shulker-palette's mode toggle on peek). Current accessors: `ContainerPeekClient.getPeekedSlot()`, `getSourceType()`, `getPeekTitle()`, `isPeeking()`.

**Maps to.**
- Panel construction (three grid panels with vertical text + slot group + supplier-driven title): new `Panel` + PanelBuilder + SlotGroup (inventory-menu context).
- Peek client-state-driven visibility: `Panel.showWhen(Supplier<Boolean>)` on each of the three panels.
- Injection into container screens: Pattern 1 (keybind interception via `ScreenPanelAdapter` + mixin into vanilla container screens). The adapter renders the peek panel; the mixin intercepts the keybind and flips state.
- Cross-mod API: consumer-mod-owned public Java (IP exposes a stable accessor; shulker-palette imports it). Inventory-Menu doc's "Cross-mod composition" section applies.
- Sort + move-matching buttons on peek panels: covered under 1.6 below, shared with other SIMPLE containers.

**Friction — significant.**
- **Peek panels live in an inventory-menu panel tree, but they're conceptually decorations of the container screen.** The current code registers peek panels as top-level MenuKit panels (via `MKPanel.builder`) showing `.showIn(MKContext.ALL)` — which in the old architecture meant "ambient injection into every inventory screen." The new architecture removes `MKContext`/`showIn`. Question: are peek panels best modeled as (a) consumer-owned panels inside IP's own MenuKit-handled screen (doesn't exist — IP doesn't own the container screen), (b) injected via Pattern 1 + `ScreenPanelAdapter` into each vanilla container screen individually (would require a separate mixin per screen class, matching Pattern 1/2/3 semantics), or (c) registered once against `AbstractContainerScreen` as a broad-target mixin?
  - **Probable answer:** (c) — broad-target mixin into `AbstractContainerScreen` with `ScreenPanelAdapter` + `Panel.showWhen(() -> peekVisible)`, plus the failure-mode supplementaries for render/keyPressed on survival (`AbstractRecipeBookScreen`) and creative (`CreativeModeInventoryScreen` override of `render` + `mouseClicked`). This matches Pattern 1's shape, and the peek panel itself is the decoration; the keybind interception and visibility state are consumer-owned.
  - **Supplementary count estimate:** 1 primary (render TAIL + keyPressed HEAD on `AbstractContainerScreen`) + 2 supplementaries (render TAIL on `AbstractRecipeBookScreen`, keyPressed HEAD on `AbstractRecipeBookScreen`) + possibly 1 for creative's render TAIL override. 3–4 mixin classes + 1 shared state holder.
- **Dynamic slot-count for bundles.** Bundle's effective slot count is computed from bundle weight + occupancy each frame. Current code uses `disabledWhen(() -> slotIndex >= getEffectiveBundleSlots())` on each of the 64 slot declarations. The new architecture supports `disabledWhen` on slots (per DEFERRED Phase 9 / palette), so this should port cleanly. Verify: Phase 9's Slot/SlotGroup state mutability exceptions permit this use.
- **Dynamic region registration for sort/move-matching.** Server- and client-side `registerDynamicRegion` on `player.containerMenu` is required so sort and move-matching keybinds can resolve peek slots to a region. The new architecture replaces `MKRegion` with `SlotGroupLike` + `HandlerRecognizerRegistry`; it's unclear whether the same "dynamic overlay" concept exists. Phase 11's IP refactor will have to decide whether peek slots are observed as a VirtualSlotGroup via the recognizer registry, or whether peek panels' slot groups become native `SlotGroupLike` instances visible to sort/move-matching resolvers by being the same type.
  - **Preservation-required bug fixes (from DEFERRED):**
    - *Peek region arg:* `registerDynamicRegion` takes the MKContainer directly, not `container.getDelegate()`. The new-arch equivalent must preserve this fix.
    - *Creative ItemPickerMenu symmetric registration:* creative mode has two menus (`screen.getMenu() == ItemPickerMenu`, `player.containerMenu == InventoryMenu`) that share slots. Both need regions registered so hover-based addressing works in both.
- **Recipe book open-state preservation.** Current code reads/writes recipe-book open state via `MenuKitClient.setRecipeBookOpen()` / `isRecipeBookOpen()`. Are these survival-era accessors in the new architecture? If not, the refactor either (a) wraps vanilla's recipe-book widget directly via a mixin/reflection, (b) drops the recipe-book-close behavior (regresses UX — the widget would overlap the peek), or (c) builds its own supplementary mixin into `AbstractRecipeBookScreen` to force the widget closed when peek is active. **Flag as open question.**
- **Title as supplier-driven `TextLabel` with `.vertical()` option.** Current code uses `.text().content(ContainerPeekClient::getPeekTitle).vertical()`. Verify: does the new TextLabel support vertical rendering? If not, this is either a reconsideration trigger for TextLabel, or a consumer-side custom `PanelElement` implementation for vertical text. **Flag as open question.** PALETTE mentions `TextLabel(x, y, Supplier<Component>)` with no vertical option named.

### 1.2 Pocket buttons (9 × 16×16)

**What it does.** Nine small toggle-like buttons, one directly below each hotbar slot in the player inventory area. Clicking a button reveals that hotbar slot's pocket panel (1.3). Only one pocket panel is visible at a time — clicking another button hides whichever is currently open and shows the new one. The button shows a "pressed" visual state when its corresponding pocket panel is visible, "unpressed" otherwise.

**Where it appears.** Contexts: `ALL_WITH_PLAYER_INVENTORY` (old-arch enum) — meaning every screen that shows the player's inventory (survival, creative inventory tab). Positioned via `posRelativeToHotbarSlot(hotbarIndex, 0, BUTTON_Y_OFFSET)` — directly below the hotbar slot, per-context resolution handled by the old arch.

**How it's triggered.** Click → `PocketsPanel.togglePocket(hotbarIndex)` → hide all pockets, show the clicked one if it was hidden.

**Styling.** Two icon sprites (`pocket`, `pocket_toggled`), 13×13 inside a 16×16 button. Per-button visual state driven by `.pressedWhen(() -> !MenuKit.isPanelInactive("pocket_" + index))` — a supplier against the target pocket panel's visibility.

**Maps to.**
- Button: `Button.icon` (fixed sprite). But: the two-sprite pressed/unpressed visual is specifically a `Toggle.linked` / Icon-with-supplier-sprite pattern — the button's visual state is bound to the pocket panel's visibility, which is consumer-owned.
  - **Candidate shape.** Each pocket button is a `Toggle.linked(() -> pocketVisible(i), () -> togglePocket(i))`. The supplier queries IP's own "which pocket is open" state; the callback flips it. Toggle's render shows the pressed state visually. Icon swap by state (two sprites) is a `Toggle.linked` with a sprite supplier, or — if Toggle.linked doesn't take a sprite supplier — a custom PanelElement subclass that renders an Icon whose sprite is supplier-driven inside Toggle's render hooks.
- Panel positioning: screen-anchored, per-screen mixin. Pattern 3 (`fromScreenTopLeft` relative to the inventory panel's region, or the hotbar's known position — see 1.7 for the per-screen hotbar position issue).
- Panel visibility control (mutually exclusive): consumer state holds which index (if any) is currently open; each pocket panel's `.showWhen(() -> openIndex == i)`; each button's toggle flips `openIndex`.

**Friction — moderate.**
- **Per-hotbar-slot positioning relative to the hotbar.** The button's X position depends on which hotbar slot it's below; the hotbar's position within the inventory screen varies by screen (survival vs. creative). Current code uses `posRelativeToHotbarSlot` — an old-arch helper. New architecture expects the consumer's mixin to know screen-specific offsets. IP's refactor will need to compute button X/Y for each (screen variant, hotbar index) pair or encode a shared anchor calculation. Not architecturally deep, but tedious.
- **Per-screen mixin with 9 buttons + 1 pocket panel per click visible.** Treating each of the 9 buttons as its own Panel + adapter would be 9 adapters per screen mixin. Treating all 9 buttons as one Panel (a single 9-button column of buttons all at once) collapses to 1 adapter per screen mixin — cleaner. The new PanelBuilder supports layouts with multiple buttons in one panel. **Strong preference for the single-panel approach.**
- **`ALL_WITH_PLAYER_INVENTORY` context.** Old-arch shorthand for "every screen that shows the player inventory." The new architecture does not have this enum — see 4.5. Each screen gets its own mixin; pocket UI appears wherever IP's mixin targets a screen showing the player inventory. Practically: `InventoryScreen` (survival) + `CreativeModeInventoryScreen` (creative). This is the same targeting pair as the Pattern 3 corner-button example — IP would follow its exact mixin shape plus the `AbstractRecipeBookScreen` supplementary for survival's render z-order.
- **`pressedWhen` vs. `Toggle.linked`.** Current code drives the pressed visual via `.pressedWhen(supplier)`. New Toggle.linked uses `BooleanSupplier`-driven state. They map cleanly: `pressedWhen = state supplier`. Semantic check: does Toggle.linked render correctly with both states having different sprites? Reading Toggle design doc suggests it uses RAISED/INSET panel styles, not sprite swaps. **This is a Phase 9 reconsideration trigger candidate:** if IP's pocket buttons visually don't work well as a RAISED/INSET toggle (they want sprite swap), IP either (a) subclasses Toggle via the Phase 9 hooks to paint icons instead, (b) uses Button.icon with a Supplier<Identifier> sprite + pressed-state detection in the consumer's own sprite supplier (no toggle element at all), or (c) proposes a `Toggle.linked` sprite-supplier overload. See 4.4.

### 1.3 Pocket panels (9 × 3 slots)

**What it does.** Nine panels, one per hotbar slot, each containing up to 3 pocket slots in a row. At most one panel visible at a time (driven by 1.2's button clicks). Each pocket slot is a storage slot backed by a per-player persistent container (`pocket_0` through `pocket_8`). Empty-click on an empty pocket slot toggles the slot's "disabled" state — disabled slots reject items, skip cycling (1.4), and show a barrier icon + "Enable slot" empty tooltip. Enabled empty slots show "Disable slot" empty tooltip.

**Where it appears.** Same contexts as 1.2. Each panel positioned via `posRelativeToHotbar(panelXOffset(i), PANEL_Y_OFFSET)` — below the pocket-button row, centered under the hotbar slot.

**Slot filtering.** Slots reject items when: (a) disabled by user click, or (b) pocket index `>= pocketSlotCount` config (1-3). Visual difference: disabled slots show barrier ghost icon; over-configured-count slots are entirely hidden (via `disabledWhen` on the slot itself). Filter + ghost icon + empty-click + empty-tooltip all use supplier-driven state.

**Shift-click.** `shiftClickOut=true` (pockets push items into the main inventory on shift-click), `shiftClickIn` defaults (likely true — current code doesn't state explicitly). `allowOverlap=true` (pocket panels stack on top of the main inventory body, which is normal).

**Maps to.**
- Panel: new `Panel` with `SlotGroup` of size 3, `showWhen(() -> openIndex == i && configEnabled)`. Each slot configured via SlotGroup's `.filter`, `.disabledWhen`, `.ghostIcon`, `.onEmptyClick`, `.emptyTooltip`. Those configuration surfaces are slot-group-native (per PALETTE).
- Injection: same per-screen mixin as 1.2's buttons — the pocket panels and pocket buttons appear on the same screens via the same mixin. One mixin's `@Unique` adapter holds both the button panel and (conceptually) the 9 pocket panels. Actually: 1 adapter per panel, so 10 total (1 button panel + 9 pocket panels), or 1 adapter per visible-at-a-time panel if we declare a single "pockets" composite panel (less clean because the button panel is structurally different from the pocket panels). Flag for the plan.
- Persistence: `MenuKit.registerPersistence("panel_pocket_disabled", save, load)` — current code uses this for the disabled-slot sets. If the new architecture has a `registerPersistence` equivalent, same shape works. If not, IP stores disabled-slot data inside the pocket container (e.g., slot 0 is a metadata slot), or ships its own NBT-persistence adapter. **Flag as open question.**

**Friction — moderate.**
- **9 separate slot groups.** Current code registers `pocket_0` through `pocket_8` as 9 independent slot groups (via `slotGroup(name).slots(POCKET_SIZE).playerBound()`). This matches the new architecture's `SlotGroup` concept but asks: does the new SlotGroup API support 9 groups of size 3 under the same container-level namespace, or is a single group-of-groups more idiomatic? Current code's 9 groups is fine — SlotGroup groups are already container-independent.
- **Mutually exclusive visibility with panel visibility driving button state.** State is circular at the supplier level: buttons' `pressedWhen` queries pocket panel visibility; pocket panel `showWhen` queries IP's own openIndex. The chain is consumer → panel → button, not button → panel, so no library-level circular dependency. Cleanly expressible.
- **Persistence granularity.** Current `registerPersistence("panel_pocket_disabled")` uses a custom key and writes a per-hotbar comma-separated string. The new architecture's persistence surface is undocumented here — if it requires an explicit adapter or a specific callback shape, IP will adapt. If persistence surface doesn't survive Phase 5, IP rolls its own player-NBT read/write through a Fabric attachment or similar. **Flag.**

### 1.4 Pocket HUD (render-only, HUD context)

**What it does.** A HUD overlay above the hotbar. The selected hotbar slot's pocket items (up to 3) render as scaled-down item previews in an A-shape: bottom-left (pocket 0), top-center (pocket 1), bottom-right (pocket 2). During cycling, items animate between positions with ease-out-cubic interpolation over 300ms — including the hotbar item itself, which is covered by a sprite overlay during animation and re-rendered at interpolated scale. Outside of animation, pocket items render statically at 0.6× scale above the hotbar; the hotbar item renders normally at its vanilla position.

**Where it appears.** HUD context. Anchor: `BOTTOM_CENTER` with zero offset. Auto-sized. Style `NONE` (transparent background).

**How it's triggered.**
- **Visibility predicate:** `PocketHud::hasAnyPocketItems` — HUD panel's `showWhen` supplier. Returns true when: pockets-enabled config is true, HUD config is true, player exists, current selected slot has at least one enabled pocket position (regardless of whether it contains items).
- **Animation trigger:** `PocketCycler.triggerAnimation(boolean forward)` called from the cycle keybind handler on the client, before the C2S cycle packet. Snapshots current items and enabled positions, starts the animation timer. Animation runs alongside the server's rotation; by the time the animation completes, the server has committed the rotation and the client has been synced.

**Custom render.** Uses MKHudPanel's `.custom(x, y, w, h, renderFn)` API to paint the A-shape + animations. The render function is non-trivial: position/scale interpolation, hotbar cover sprite, z-order management for the hotbar-bound item (rendered last so pocket items don't occlude it), item + decoration rendering via vanilla `GuiGraphics`.

**Maps to.**
- HUD panel: `MKHudPanel.builder().anchor(BOTTOM_CENTER, 0, 0).showWhen(predicate).custom(...).build()`. Directly supported per Pattern 5 (HUD injection pattern doc).
- Custom render: MKHudPanel's `.custom()` signature changed in Phase 7 from `(graphics, x, y, w, h, deltaTracker)` to `Consumer<RenderContext>` per DEFERRED. Current code uses the old shape — will migrate.

**Friction — small.**
- **Custom render needs `DeltaTracker` for animation timing.** Current code uses `System.currentTimeMillis()` for animation, not `DeltaTracker`, so the signature change is transparent. No friction.
- **Anchor `BOTTOM_CENTER`.** Current code uses `MKHudAnchor.BOTTOM_CENTER`; new architecture uses `HudAnchor.BOTTOM_CENTER`. Rename only.
- **Hotbar item coverage during animation.** The animation paints the selection sprite + black fill over vanilla's hotbar item, then re-renders the item at scaled interpolation. This works because the HUD overlay renders after vanilla's hotbar sprite. Order depends on HUD callback chain; preserve via MKHudPanel's normal registration.
- **`hasAnyPocketItems` predicate is cheap.** The predicate queries config + player + one MKContainer per frame. This is within the "keep the predicate cheap" guidance from HUD doc; `MenuKit.getContainerForPlayer` is a map lookup. No HUD-tick concerns.

### 1.5 Equipment panel (2 slots)

**What it does.** Two extra equipment slots (passive elytra + passive totem) that blend visually with vanilla's armor/offhand column. Items placed in these slots grant their effect (flight from elytra, death-save from totem) without occupying the chest-armor or held slot. The slots look native — they use vanilla slot sprite backgrounds and sit near the offhand in both survival and creative inventory layouts.

**Where it appears.** Survival inventory (`SURVIVAL_INVENTORY` context, `pos(77, 25)` — above offhand), creative inventory (`CREATIVE_INVENTORY` context, `posFor(CREATIVE_INVENTORY, 16, 10)` — left of character, stacked vertically). Style `NONE`, no padding.

**Slot filters.**
- Slot 0 (elytra): filter `EquipmentPanel::isElytra`, maxStack 1, ghost icon `elytra`, disabledWhen `!enableElytraSlot`.
- Slot 1 (totem): filter `stack.is(Items.TOTEM_OF_UNDYING)`, maxStack 1, ghost icon `totem`, disabledWhen `!enableTotemSlot`.

**Shift-click priority routes.**
- `MenuKit.shiftClickPriority(isElytra, "equipment", 0)` — any elytra anywhere shift-clicked routes to equipment slot 0 before other destinations.
- Same for totem → slot 1.

**Storage.** Slot group `equipment` registered with `.playerBound().excludeFromAutoPickup()` — auto-pickup doesn't target the equipment slots (items picked up from the world stay in the main inventory).

**Passive behaviors.** The slots' *contents* are read by server-side mixins (1.8 passive behaviors) to provide elytra flight and totem protection without requiring the items to be in vanilla's armor slot or held.

**Maps to.**
- Panel: new inventory-menu Panel with a 2-slot SlotGroup. `PanelStyle.NONE` (no background).
- Per-context positioning: `PanelPosition` with per-context coordinates. **Flag**: current code uses `.pos(x, y).posFor(CTX, x, y)` — the per-context override. New architecture's PanelPosition is constraint-based layout; does it support per-context positioning, or is each context's panel a separate declaration/mixin? See 4.5.
- Slot filter / maxStack / ghost icon / disabledWhen: native SlotGroup configuration, all surveyed in PALETTE.
- Passive behaviors: server-side mixins reading from MenuKit's player-bound container. If the new architecture preserves `MenuKit.getContainerForPlayer` (likely — storage abstractions are mentioned in CONTEXTS), the mixins port with minor renames.

**Friction — significant.**
- **Shift-click priority system.** `MenuKit.shiftClickPriority(predicate, targetGroup, targetIndex)` is a consumer-registered cross-slot routing rule. Any screen that has an elytra somewhere and shift-clicks it should route to the equipment slot. This requires the handler's quick-move routing to consult the priority rule *before* its default shift-click routing. The new architecture's three-layer shift-click routing (directional pairings → source-aware baseline → declared priority, per CONTEXTS § Inventory menus) includes a **declared priority** layer. Verify: does the declared-priority API expose a predicate-driven route like current IP uses, or is it index-range-based? **Flag as open question.**
- **Auto-pickup exclusion flag.** `excludeFromAutoPickup()` is a SlotGroup flag in the old architecture. Per DEFERRED, Cairn decision 010 covers it. New SlotGroup API needs the same flag. Likely preserved; verify.
- **Per-context positioning.** See 4.5 — the old architecture's `.showIn(CTX1, CTX2).pos(x, y).posFor(CTX2, x2, y2)` lets one panel declaration render at different positions per context. The new architecture, per DEFERRED, removes `showIn` (ambient injection). Consumer mods write a per-screen mixin; each mixin declares its own panel coordinates for that screen. So for the equipment panel, IP writes two mixins: one for survival `InventoryScreen` with `(77, 25)`, one for creative `CreativeModeInventoryScreen` with `(16, 10)`. Or one mixin targeting both with an `instanceof`-gated coordinate resolver.
  - **Probable shape.** Equipment panel declared once (with `Panel.showWhen(() -> true)`), mixed in via Pattern 3 (`fromScreenTopLeft(xForThisScreen, yForThisScreen)` computed at render time with an `instanceof` check). Single mixin, per-variant coord logic inside the origin lambda.
  - **Alternative shape.** Two entirely separate panel declarations, one per screen, each injected with a dedicated mixin. Larger but cleaner for per-variant ghost-icon or filter differences if any arose. None are currently present, so the single-panel-with-two-coords is probably right.

### 1.6 Sort + Move-matching buttons (via old button-attachment system)

**What it does.** A pair of 9×9 icon buttons above every SIMPLE container region (chest, shulker, hopper, dispenser, peek panel). Left button = "Move Matching Items Here" (moves all matching items from other containers into this region); right button = "Sort Items" (sorts this region in-place).

**Where it appears.** Above the slot grid of *every* SIMPLE region in every screen IP cares about, with exclusions:
- Hidden when `enableSorting=false` or `showSortButton=false` or creative tabs view (`mc.screen instanceof CreativeModeInventoryScreen cs && !cs.isInventoryOpen()`).
- Move-matching hidden when fewer than 2 SIMPLE containers are currently open (nothing to move between).
- Excluded regions: `pocket_*` (per IP's config).

**How it's triggered.**
- Current code uses `MenuKit.buttonAttachment("ip_sort").forContainerType(MKContainerType.SIMPLE).above()...buttons(regionName -> ...)` — an old-architecture "ambient injection" facility where MenuKit itself attaches the buttons to every SIMPLE region across all consumer containers.
- Per DEFERRED ("No region-based button attachments"), this facility **is gone**. The new architecture does not ship ambient button attachment.

**Button behaviors.**
- **Sort button click:** sends `SortC2SPayload(regionName)`. Server looks up the region, calls `MKContainerSort.sortRegion(region, menu)`. Sort method (`MOST_ITEMS` | `BY_ID`) comes from config.
- **Move-matching button click:** `onMoveMatchingClick(regionName)` — resolves source/dest region groups client-side, sends `MoveMatchingC2SPayload(sourceGroupName, destGroupName, destRegionName, includeHotbar)`. Server resolves groups, performs matching.

**Maps to.**
- **No direct primitive.** The old architecture's button-attachment system was a MenuKit-internal platform feature, not a consumer primitive. Its replacement is: per-vanilla-screen consumer mixin using Pattern 2 (button at slot-grid region). Each container screen class IP wants to decorate → a mixin that renders a sort + move-matching button panel above the slot grid.
- **Consumer screens (IP-owned MenuKit-handled screens), if any.** IP currently doesn't define its own container screens (peek panels live inside vanilla screens). Sort + move buttons on peek panels come from this same ambient attachment — which in the new architecture means: the peek panel itself contains sort + move buttons as regular Panel elements (not via ambient attachment), because the peek panel's slot group is the region they address.

**Friction — architectural (largest in the audit).**
- **Ambient button attachment is gone.** The refactor has three options:
  1. **Per-screen-class mixins, one per decorated screen.** Each of `InventoryScreen`, `CreativeModeInventoryScreen`, `ChestScreen`, `ShulkerBoxScreen`, `HopperScreen`, `DispenserScreen` (and any others IP decorates) gets its own mixin with Pattern 2 adapter + sort + move buttons. High file count (6+ mixins), but each is small. Covers exactly the screens IP targets.
  2. **Broad-target mixin into `AbstractContainerScreen` with runtime region enumeration.** One mixin, walks the hovered or current menu's regions (via Phase 4b's `HandlerRecognizerRegistry`), for each SIMPLE region paints a sort + move button above it. Requires at render time knowing where each region's slot grid is on screen — the recognizer registry returns `SlotGroupLike` instances, but their screen-space positions depend on the vanilla screen's layout, which varies per screen class. Possible via `slot.x`/`slot.y` (position of the slot in screen space), but more complex than option 1.
  3. **Consumer-specific logic for each container type, no universal decoration.** IP decorates only the two screens it most cares about (player inventory main + hotbar regions; peek panels). Other SIMPLE containers (chests, hoppers, dispensers) just don't get sort/move buttons. Behavioral regression from current behavior — the audit should flag this.
- **Recommendation for the plan stage:** option 2 is technically elegant and matches how the old architecture conceived the feature ("SIMPLE regions get sort buttons"), but risks cross-mod coupling where IP's mixin reads regions contributed by third-party mods whose screens render buttons in unexpected places. Option 1 is mechanical and per-screen predictable. **Flag for refactor plan decision.**
- **Move-matching source/dest group resolution.** Current code uses `MKRegionGroup` + `MKRegionRegistry.getGroupsForRegion(menu, regionName)` to resolve "which group contains this region" and "which group is the opposite for moving." New architecture uses `SlotGroupLike` + `HandlerRecognizerRegistry`. Region *groups* (the `container_storage` and `player_extended` concepts) may or may not have a direct new-architecture analogue. **Flag as open question.**
- **Creative-tab-view suppression.** `isCreativeTabsView()` checks `cs.isInventoryOpen()`. In the new architecture, suppression happens via the button panel's `showWhen` or the mixin's render gate. Cleanly expressible; no architectural concern.
- **"Bundle weight" bundle size computation.** Not a sort/move concern but lives in related code (bundle peek).
- **Preserved from DEFERRED:** creative-mode `ItemPickerMenu` symmetric region registration must be preserved for move-matching to work on creative's hotbar/inventory slot views.

### 1.7 Settings gear button (family-shared panel)

**What it does.** An 11×11 gear-icon button above the personal inventory, right-aligned. Click opens the trevmods family's unified YACL config screen, pre-focused on the Inventory Plus tab. Hidden when the family-level "show settings button" general option is off.

**Where it appears.** PERSONAL context (player inventory). Family-shared — registered via `family.sharedPanel("trevmods_settings", ...)`. Other family mods (agreeable-allays, shulker-palette) see the same panel; whichever family mod loads first registers it.

**How it's triggered.** Button click → `family.buildConfigScreen(currentScreen, InventoryPlus.MOD_ID)` → set as current screen.

**Maps to.**
- Button: `Button.icon(spriteId, onClick)` with `.tooltip("TrevMod Settings")`.
- Panel: Pattern 3 corner-anchor mixin into survival + creative inventory (`fromScreenTopRight(width, -4, -4)` or similar).
- Visibility: `Panel.showWhen(() -> family.getGeneral(SHOW_SETTINGS_BUTTON))`.
- Family-shared registration: **flag as open question.** If `family.sharedPanel` is a new-architecture facility, use as-is. If not, then either (a) each family mod registers its own mixin and they race — first one to init wins, (b) a coordination mechanism via shared flag check ("has this panel been registered?"), or (c) the family concept gets a per-screen-panel-registry facility back.

**Friction — small to moderate.**
- **Family sharedPanel facility.** May or may not survive. If YACL family integration is still a separate subsystem adjacent to MenuKit (per THESIS's "fellow traveler" pattern for keybind infrastructure), the config-category and sharedPanel APIs likely still exist. If not, IP handles the "only show one of these per session" contract via consumer-level coordination. **Open question.**
- **YACL integration surface.** The config screen uses `family.buildConfigScreen(currentScreen, InventoryPlus.MOD_ID)`. YACL3 is a separate mod dependency, not part of MenuKit. The config-panel registration mechanism is therefore consumer-scoped; MenuKit's role is just exposing an API for family modes to register config categories. This surface is likely preserved since it's orthogonal to the component-library migration.

### 1.8 Passive-behavior surface (zero UI, but tied to equipment panel contents)

Not a UI surface, but the *reason* the equipment panel exists is the set of nine passive behaviors driven by the equipment slots' contents. Listed here so the audit captures the full scope of what depends on IP's UI surfaces being correctly refactored.

| Mixin | Target | Purpose |
|---|---|---|
| `IPCanGlideMixin` | `LivingEntity.canGlide` | Player can glide if elytra is in equipment slot (not worn). |
| `IPFallFlyingMixin` | `LocalPlayer.serverAiStep` (or similar) | Trigger flight animation when elytra is in equipment slot. |
| `IPWingsLayerMixin` | `PlayerRenderer` | Render elytra wings when equipment-slot elytra is present, regardless of chest armor. |
| `IPTotemMixin` | `LivingEntity.checkTotemDeathProtection` | Check equipment-slot totem before vanilla death logic. |
| `IPDeathDropsMixin` | `Player.dropAllDeathLoot` | Preserve equipment-slot items across death (same as armor). |
| `IPMendingMixin` | `EnchantmentHelper.processMobExperience` | Apply mending XP to equipment-slot items on mob death. |
| `IPOrbMendingMixin` | `Player.takeXpDelay` or XP orb pickup | Apply mending XP to equipment-slot items on XP orb pickup. |
| `IPAutoRestockMixin` | `Player.tick` (server-side) | Auto-refill hotbar slots from main inventory + shulker boxes + pockets. |
| `IPAutoReplaceMixin` | `Item.onDestroyed` or similar | Replace broken tool with another of the same type from inventory. |
| `IPAutoRouteMixin` | `Inventory.add` or `Player.addItem` | Route picked-up items to matching hotbar/inventory/shulker slots. |
| `IPDeepArrowMixin` | `Player.getProjectile` | Search bundles, shulkers, ender chest for arrows when none are loose. |
| `IPBowArrowMixin`, `IPCrossbowArrowMixin` | Supporting mixins for deep arrow search |

**Maps to.** None. These are vanilla mixins, not MenuKit-touching code. The only MenuKit dependency: reading from player-bound containers via `MenuKit.getContainerForPlayer("equipment" | "pocket_<n>", player.getUUID(), true)`. If the new architecture preserves `getContainerForPlayer`, these mixins port unchanged (just imports + names). If not, each mixin uses a Fabric attachment or similar player-bound storage, and the IP config holds a compatibility shim.

**Friction — small.** Assuming `MenuKit.getContainerForPlayer` survives. If not, IP's refactor has a parallel "server-side player-bound storage adapter" to build. **Flag as open question: does the new architecture preserve MKContainer + player-bound storage accessors?**

---

## 2. Interactions

### 2.1 Keybinds (configurable; vanilla KeyMapping + MKKeybindExt duck for multi-key combos)

| Keybind | Default | Context | Behavior |
|---|---|---|---|
| Sort Region | unbound | hovering a slot in any container screen | Server sorts the hovered slot's region |
| Move Matching | unbound | hovering a slot in any container screen | Move all matching items from other regions into this region (same effect as move-matching button click) |
| Lock Slot | unbound | hovering a slot | Toggle the hovered slot's sort-lock state (excluded from sort + shift-click-in); audio feedback (UI_BUTTON_CLICK); syncs to server |
| Peek Container | Left Alt | hovering a peekable item (shulker / bundle / ender chest) in any container screen | Toggle peek panel for that item; see 1.1 for semantics |
| Pocket Cycle Right | Right Arrow | in-game (no screen open) | Rotate selected hotbar slot's pocket items forward |
| Pocket Cycle Left | Left Arrow | in-game (no screen open) | Rotate selected hotbar slot's pocket items backward |
| Autofill | (need to verify keybind registration location) | in-game | Send `AutoFillC2SPayload`; server scans for partial stacks and fills from shulkers |

**Maps to.**
- **In-screen keybinds** (Sort, Move Matching, Lock Slot, Peek): Pattern 1 shape — consumer mixin on `AbstractContainerScreen.keyPressed` with `instanceof` gating + supplementary mixins on `AbstractRecipeBookScreen.keyPressed` for survival and on creative for its override. Each keybind gets a branch in the mixin's keyPressed handler. The handler resolves hovered slot (via `this.hoveredSlot`), determines its region membership, and issues the appropriate C2S packet. One primary mixin + one supplementary mixin can cover all four in-screen keybinds together (shared state holder is just IP's keybind registration + slot-resolution helper). Creative's `mouseClicked` override and its render need consideration for peek's panel rendering but not necessarily for keybind interception (`keyPressed` on creative should super-call unless vanilla's behavior surprises).
- **Out-of-screen keybinds** (pocket cycle, autofill): Fabric's `ClientTickEvents.END_CLIENT_TICK` polling loop with `keyMapping.consumeClick()`. These don't involve MenuKit at all — they're client-tick event consumers. The cycle trigger fires `PocketHud.triggerAnimation()` then sends `PocketCycleC2SPayload`; autofill fires `AutoFillC2SPayload`.
- **YACL keybind capture + Controls→config sync:** `MKKeybindController` + `MKKeybindSync` — the MenuKit keybind infrastructure per THESIS ("fellow traveler" to the component library). Assumed preserved.

**Friction — moderate.**
- **Slot-region resolution in the mixin.** Current code uses `event.getRegion()` from `MKSlotEvent` — the old event bus emitted slot-event objects with pre-resolved region info. New architecture has no MKEvent. Consumer mixin needs to do this resolution itself: given `hoveredSlot`, find its `SlotGroupLike` via recognizer registry + map to a region name. **Flag.** The resolution is mechanical but requires a helper — possibly worth a small utility in IP.
- **`getMenuSlotIndex()` for creative-tabs safety.** Current code's `event.getMenuSlotIndex()` returns -1 for client-only fake slots (creative tabs' fake inventory). The new architecture's mixin-level slot access uses `slot.index` directly — IP's consumer code needs an equivalent "is this a real slot on the server's menu?" check. **Flag.**
- **`MKKeybindExt.matchesEvent(keyMapping, keyCode, modifiers)` for matching combos while a screen is open.** Current code explicitly uses this because vanilla doesn't update `KeyMapping.isDown()` while a screen is open (key events route through `Screen.keyPressed`, not the GLFW callback). MKKeybindExt is part of the keybind infrastructure subsystem — assumed preserved. If it's not, IP's mixin queries modifier state via GLFW polling directly.
- **Audio feedback sound.** `SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)` — pure vanilla API call, not MenuKit concern. Clean.

### 2.2 Mouse interactions inside container screens

**Shift+double-click bulk move.** Current code handles via `MKEvent.Type.DOUBLE_CLICK` with a shift-modifier gate. Handler: resolves hovered slot + region, resolves item id, sends `BulkMoveC2SPayload(regionName, itemId)`. Server iterates the region and `quickMoveStack`s each matching slot.

- **Maps to.** Consumer mixin on `AbstractContainerScreen.mouseClicked` (or `slotClicked`) + double-click detection logic (consumer-side — track last click time per slot), shift-modifier check, issue packet. The double-click detection is a mild friction — vanilla's double-click detection is internal to `AbstractContainerScreen`, and consumers don't easily hook the "this was a double-click" signal. **Flag.** Options: (a) the consumer mixin hooks `AbstractContainerScreen.checkHotbarMouseClicked` or the internal double-click dispatch method, (b) consumer tracks clicks manually (time-since-last-click-on-same-slot < 250ms).
- **Alternative.** Current MKEvent DOUBLE_CLICK was a MenuKit event emitted from inside the inventory-menu machinery. The new per-screen-scoped event system (`MenuKitHandledScreen.ScreenEventListener`) is on MenuKit-owned screens only, not vanilla screens. Bulk move inside a vanilla chest needs consumer-side double-click detection.

**Right-click on peekable (old trigger, replaced by keybind in new design).** Current code still has `IPPeekClickMixin` and `IPCreativePeekMixin` that intercept right-clicks on peekable items. These are **legacy** — InventoryPlusClient comments state the new design uses the peek keybind, and right-clicks now fall through to vanilla "so bundle extraction and Easy Shulker Boxes work as intended." **Refactor deletes these mixins.** Flag for removal.

**Empty-click on empty pocket slot → toggle disabled state.** Handled at slot level via `.onEmptyClick(slot -> toggleDisabled(hIdx, pIdx))`. Native SlotGroup configuration per PALETTE. No friction.

### 2.3 Shift-click routing policies

- Equipment panel: `shiftClickIn=false`, `shiftClickOut=true`. Items reaching the equipment slots come only via declared priority routes (elytra, totem).
- Pockets panels: `shiftClickOut=true` (items can leave pockets via shift-click).
- Peek panels: `shiftClickIn=true`, `shiftClickOut=true`.
- Declared priority routes via `MenuKit.shiftClickPriority(predicate, targetGroup, targetIndex)`: elytra → equipment slot 0, totem → equipment slot 1.

**Maps to.** Per CONTEXTS, the three-layer routing (directional pairings, source-aware baseline, declared priority) is part of the new architecture. Shift-click in/out flags on SlotGroup are preserved per PALETTE. Declared priority routing needs verification: is the API predicate-driven or index-range-based? **Flag.**

### 2.4 Button clicks and touch surfaces

- Pocket button: `onClick(btn -> togglePocket(index))`.
- Sort button: `onClick(btn -> ClientPlayNetworking.send(new SortC2SPayload(regionName)))`.
- Move-matching button: `onClick(btn -> onMoveMatchingClick(regionName))`.
- Settings gear: `onClick(btn -> openConfigScreen())`.

All direct Button onClick callbacks. No friction.

---

## 3. State

### 3.1 Per-player persistent (server + client — syncs via MenuKit storage)

- **Equipment container (2 slots):** elytra + totem. Persists across sessions via MenuKit's player-NBT attachment.
- **Pocket containers (9 × 3 slots):** all pocket items. Persists via MenuKit's player-NBT attachment.
- **Pocket disabled-slot sets:** per-hotbar-slot sets of disabled pocket indices. Persists via `MenuKit.registerPersistence("panel_pocket_disabled", save, load)` — a per-mod key in the same player-NBT system.

### 3.2 Per-menu session (server-side, lives for the duration of `containerMenu`)

- **Peek bindings:** `MKContainer` instances for `peek_shulker` / `peek_ender` / `peek_bundle`, bound to a live `MKContainerSource` (item-container / vanilla ender-chest / bundle contents). Unbind on menu close, disconnect, or explicit close.
- **Peek dynamic regions:** registered on `player.containerMenu` via `MKRegionRegistry.registerDynamicRegion`. Removed on close paths.

### 3.3 Per-slot state (`MKSlotStateRegistry`)

- **Sort-lock flag:** per `Slot` instance, `isSortLocked()`. Set via lock-slot keybind (client toggles, syncs to server via `SortLockC2SPayload`). Read by sort + shift-click-routing on both client (visual overlay) and server (behavior).
- **Locked flag:** per `Slot` instance, `isLocked()`. Set via old `MKSlotClickBusMixin` Ctrl+click handler (not read in code I audited; possibly legacy or in a code path I didn't cover). Read alongside sort-lock by bulk-move to skip locked slots.

**Friction — architectural.** `MKSlotStateRegistry` was part of the old architecture. Per DEFERRED ("Delete superseded types"), it's slated for removal. New architecture replaces it with… **unclear.** Candidate replacements:
- Per-slot state attached to `MenuKitSlot` as a `SlotState` field. Would require mixin or widening the slot subclass to carry flags.
- Per-slot state lives on the slot's backing `SlotGroupLike` or region. Current IP model (flag per slot) doesn't map cleanly.
- Consumer-owned state holder: IP keeps a `WeakHashMap<Slot, SlotState>` or per-menu map. Leaks complexity into every consumer that needs per-slot UI state.

**Flag as major open question.** If the new architecture doesn't ship a replacement, IP rolls its own consumer-scoped per-slot state, likely per-menu-session. Sort-lock and move-matching source/exclusion predicates need the same underlying mechanism.

### 3.4 Config (client-local file `config/inventory-plus.json`)

| Key | Type | Default | Purpose |
|---|---|---|---|
| `enableElytraSlot` | boolean | true | equipment slot 0 visible |
| `enableTotemSlot` | boolean | true | equipment slot 1 visible |
| `mendingInventoryWide` | boolean | false | mending applies to all inventory items, not just equipment/hand/armor |
| `enableSorting` | boolean | true | master toggle for sort keybind + button |
| `sortMethod` | SortMethod enum | MOST_ITEMS | sort order |
| `showSortButton` | boolean | true | sort button visible in inventory screens |
| `includeHotbarInMoveMatching` | boolean | true | move-matching may source from hotbar |
| `enablePockets` | boolean | true | master toggle for pocket UI + keybinds + HUD |
| `pocketSlotCount` | int 1–3 | 3 | how many pocket slots per hotbar position |
| `showPocketHud` | boolean | true | pocket HUD overlay visible |
| `enablePeekShulker` / `enablePeekBundle` / `enablePeekEnderChest` | boolean | true | peek-per-type enable |
| `sortKeybind` / `moveMatchingKeybind` / `lockSlotKeybind` / `peekKeybind` / `pocketCycleLeftKeybind` / `pocketCycleRightKeybind` | MKKeybind | various | keybind records |

### 3.5 Family-scoped general options (stored in `config/menukit-family-trevmods.json`)

| Key | Default | Purpose |
|---|---|---|
| `show_settings_button` | true | gear button visible (1.7) |
| `autofill_enabled` | true | autofill feature gate (server-side check) |
| `auto_restock` | true | auto-restock feature gate |
| `auto_replace_tools` | true | auto-replace feature gate |
| `deep_arrow_search` | true | deep arrow search gate |
| `show_item_tips` | true | MKItemTips integration |

**Maps to.** MKFamily config subsystem. Assumed preserved as a fellow-traveler system like keybinds. **Flag as open question** if the family system doesn't survive Phase 5's demolition.

### 3.6 Runtime-only client state

- **Peek state** (ContainerPeekClient): peekedSlot, sourceType, bundleActiveSlots, peekTitle, wasRecipeBookOpen. Static fields on a client-only class. Will port cleanly to a plain helper in the new architecture (Phase 10's `examples/shared/` pattern).
- **Pocket animation state** (PocketHud): animProgress, animStartTime, animForward, animHotbarSlot, animOldItems, animEnabledPositions. Same shape.
- **"Which pocket is open"** (new state needed): currently implicit via panel visibility (`MenuKit.isPanelInactive` queries). New architecture: consumer holds an `Integer openPocketIndex = null` field, pocket panel `showWhen(() -> openPocketIndex == i)`, button `Toggle.linked(() -> openPocketIndex == i, () -> toggle(i))`.

---

## 4. Architectural mapping and cross-cutting concerns

Collected here for the refactor plan. The audit does not propose choices; it identifies where choices are due.

### 4.1 Global event bus replacement

Old: `MenuKit.on(MKEvent.Type.KEY_PRESS|DOUBLE_CLICK|MENU_CLOSE|...).slotHandler(event -> ...)`.

Used by IP for: all four in-screen keybinds (sort, move-matching, lock, peek), shift+double-click bulk-move, menu-close cleanup for peek.

Per DEFERRED: **gone.** Replacements:
- For MenuKit-owned screens: `MenuKitHandledScreen.ScreenEventListener` (per-screen, declarative).
- For vanilla screens: consumer mixin into vanilla's keyPressed / mouseClicked / other event sources, with the consumer-provided handler dispatching to IP logic.

**IP implication.** Every IP MKEvent handler becomes mixin code. Approximately 6–8 handlers in current IP → mostly consolidated into 1–3 mixins (primary + survival supplementaries) because many share targeting.

### 4.2 Region system replacement

Old: `MKRegion`, `MKRegionGroup`, `MKRegionRegistry`, `registerDynamicRegion`, `getGroupsForRegion`, `getRegions`, `countRegionsByType`, `getActiveMenu`.

Used by IP for: sort region resolution, move-matching source/dest groups, peek dynamic region registration, container-type classification (SIMPLE vs. other).

Replacement per CONTEXTS/PALETTE: `SlotGroupLike` + `HandlerRecognizerRegistry`. Consumer queries the registry for the current menu's groups and uses them to identify source/dest of moves and addressable regions for sort.

**IP implication.** Non-trivial rewrite of `onMoveMatchingClick`, the sort keybind handler, and the bulk-move handler. The region-group abstraction (grouping multiple regions under a name like `container_storage`) may need a consumer-side helper — "here are the SIMPLE regions in this menu matching these names" — built on top of `HandlerRecognizerRegistry`. **Flag.**

### 4.3 Ambient button attachment

Old: `MenuKit.buttonAttachment("ip_sort").forContainerType(SIMPLE).above()...buttons(regionName -> ...)`.

Used by IP for: sort + move-matching buttons on every SIMPLE region.

Per DEFERRED: **gone.** Covered in 1.6 above with options (per-screen mixin / broad-target mixin with runtime enumeration / narrow scope). Decision pending the refactor plan.

### 4.4 Toggle.linked with sprite swap

Toggle.linked ships with RAISED/INSET visual. IP's pocket buttons want sprite swap (two textures — pocket / pocket_toggled). Per PALETTE + DEFERRED, Icon has a `Supplier<Identifier>` variant; Button.icon has a `Supplier<Identifier>` overload.

**Probable shape:** each pocket button is a `Button.icon(Supplier<Identifier>)` where the sprite supplier reads IP's "is this pocket open" state. The `Toggle.linked` abstraction isn't needed — a Button with a state-driven sprite gets the same visual outcome.

Alternative: `Toggle.linked` subclass (via Phase 9 protected hooks: `renderBackground`, `renderContent`) that paints an Icon instead of panel styles.

**Flag for the refactor plan** — no architectural decision needed, just a consumer-side shape pick.

### 4.5 MKContext + showIn replacement

Old: `MKContext.SURVIVAL_INVENTORY | CREATIVE_INVENTORY | PERSONAL | ALL_WITH_PLAYER_INVENTORY | ALL` with `.showIn(...)` and `.posFor(CTX, x, y)`.

Used by IP for: per-context positioning (equipment panel), per-context visibility (settings gear visible only in PERSONAL), and ambient panel injection (peek panels shown in ALL).

Per DEFERRED/INVENTORY_INJECTION_PATTERN: **gone.** Replacement: per-vanilla-screen mixin with per-screen coordinates.

**IP implication.** For each panel currently using `.showIn` + `.posFor`:
- Equipment panel → mixins into survival + creative with per-variant coordinates.
- Peek panels → broad-target mixin into `AbstractContainerScreen` (covers every container screen at once).
- Settings gear → mixin into survival + creative player-inventory screens.
- Pocket buttons + panels → mixins into survival + creative player-inventory screens.

Total estimated mixin count: 5–8 primary mixins + 3–5 supplementary mixins (for survival's recipe-book silent-inert dispatch on render + keyPressed). Not a small refactor, but mechanical once the targeting map is drawn.

### 4.6 Storage abstractions

Old: `MenuKit.slotGroup(name).slots(n).playerBound().excludeFromAutoPickup().register()`; `MenuKit.getContainerForPlayer(name, uuid, isServer)`; `MenuKit.getContainerDef(name)`; `MenuKit.registerPersistence(key, save, load)`.

Used by IP for: equipment container, 9 pocket containers, 3 peek containers, pocket-disabled-slots persistence.

Replacement per CONTEXTS: storage abstractions live under the new architecture. CONTEXTS explicitly names "block-entity-backed, player-backed, ephemeral, virtual, read-only, item-stack-backed" storage types. `.playerBound()` → new name likely `.playerStorage()` or similar. `.ephemeral()` → `.ephemeralStorage()`. `.excludeFromAutoPickup()` — Cairn decision 010, assumed preserved. `getContainerForPlayer` — flag for new-API name.

`registerPersistence` — **flag as open question.** If the new architecture doesn't ship a generic mod-namespaced persistence hook, IP rolls its own via Fabric attachments.

### 4.7 Per-slot state replacement

Discussed in 3.3. Major open question. Possible paths: consumer-owned map, SlotState shipped by MenuKit, state on the SlotGroupLike interface, attachment per slot. No PALETTE entry covers this — it's a subsystem question, not an element question. **Flag as primary open question for review.**

### 4.8 Cross-mod public API surface (IP → shulker-palette)

Current: `ContainerPeekClient.getPeekedSlot()`, `getSourceType()`, `getPeekTitle()`, `isPeeking()`. Public static methods on a client-only class.

Refactor: designate these (or their equivalents) as IP's public API for consumer decorations. Document in IP's own README/javadoc as a stable contract. Shulker-palette imports directly per INVENTORY_INJECTION_PATTERN § Cross-mod composition.

**Consider exposing.** Whether a peekable item is being peeked *specifically of a given type* (shulker? bundle? ender?). Shulker-palette only cares about shulker peeks. Either `getSourceType()` returns an enum, or IP exposes a `peekedItemMatches(Predicate<ItemStack>)` helper. Minor shape choice.

---

## 5. Open questions (consolidated — for refactor plan resolution)

In rough order of architectural weight:

1. **Per-slot state replacement.** What survives of `MKSlotStateRegistry`? Sort-lock and locked flags need a home. See 3.3 + 4.7.
2. **Region system to SlotGroupLike + HandlerRecognizerRegistry mapping.** How do "region groups" (container_storage, player_extended) translate? See 4.2.
3. **Ambient button attachment replacement.** Per-screen mixin (option 1), broad-target enumeration (option 2), or narrower scope (option 3)? See 1.6 + 4.3.
4. **Peek panel injection shape.** Broad-target `AbstractContainerScreen` mixin + supplementaries for survival + creative render path + keyPressed? See 1.1.
5. **`MenuKit.registerPersistence` equivalent.** Does the new architecture ship a generic mod-namespaced player-NBT persistence API? See 3.1 + 4.6.
6. **`Panel.showWhen` on inventory-menu panels with slot groups.** DEFERRED flags this — `showWhen` doesn't trigger `broadcastChanges` sync. Do peek panels need this? (Likely yes — peek panels have slots.) If so, does IP use `setVisible` + manual sync call? See DEFERRED Phase 10 reconsideration triggers.
7. **Vertical `TextLabel` support.** Peek panel titles render vertically. Does new TextLabel support `.vertical()`? See 1.1.
8. **Recipe-book state access.** `MenuKitClient.setRecipeBookOpen` / `isRecipeBookOpen` — preserved? See 1.1.
9. **Family sharedPanel + config-category API.** Preserved as fellow-traveler subsystem? See 1.7.
10. **`MKKeybindExt.matchesEvent` / `MKKeybindController` / `MKKeybindSync`.** Preserved as fellow-traveler keybind subsystem? See 2.1.
11. **Shift-click declared priority API shape.** Predicate-driven or index-range? See 2.3 + 4.6.
12. **Double-click detection in vanilla screens.** No built-in hook; consumer-side manual detection? See 2.2.
13. **`MenuKit.getContainerForPlayer` equivalent.** Preserved? See 4.6 + 1.8.
14. **`MKInventory.toUnifiedPlayerPos`** — small utility, needed for creative inventory slot translation. Preserved? See IPPeekClickMixin (likely dead code, but mentioned).

Each question that resolves to "not preserved" → a consumer-side workaround in IP. Each that resolves to "preserved" → minor import rename in IP's refactor.

---

## 6. Preservation-required items (from DEFERRED, carried forward)

Both are user-experience bug fixes captured during the Phase 5 demolition. They were lost when IP's old-arch code was disabled; they must land in the refactored IP so the refactor doesn't regress behavior:

- **Peek region arg:** `registerDynamicRegion` (or new-arch equivalent) takes the `MKContainer` directly, not `container.getDelegate()`.
- **Creative-mode ItemPickerMenu symmetric registration:** when `screen.getMenu() != player.containerMenu` (creative tabs), register the peek region on **both** menus. Same on close.

---

## 7. Surprises and notes

- **IP's internal code is well-structured, with clear per-feature separation.** Each feature (pockets, equipment, peek, sorting, passive behaviors) is in its own class or package. The refactor's surface-area concern is therefore largely the MenuKit API migration, not internal redesign.

- **Legacy code that will be deleted.** `IPPeekClickMixin` and `IPCreativePeekMixin` (right-click peek triggers) were superseded by the peek keybind in the current design. Comments confirm the keybind was intended to replace them. Refactor deletes both.

- **`getMenuSlotIndex()` for creative safety** is load-bearing across multiple keybind handlers (sort, lock, peek). The new architecture likely requires a consumer-side replacement — a small utility mapping a client-side `Slot` to a server-addressable menu-slot index, returning -1 for client-only fake slots. Consolidate as a utility during refactor.

- **Family config system is a substantial dependency.** `MKFamily` handles: keybind category grouping, general-option storage, shared-panel registration, config-screen composition with YACL. Five of IP's six "family" calls touch config-only machinery. The refactor assumes this subsystem survives; if not, MKFamily is effectively its own refactor target. **Verify early.**

- **Autofill keybind registration** not found in the range of `InventoryPlusClient` I audited. Likely in the unread middle (line 470–800 territory) or after line 970. Flag for the refactor to confirm its location and keybind-default.

- **"Sort by Most Items" vs "Sort by ID"** — purely server-side enum, no UI concern.

- **Cross-mod API shape is latent, not concrete.** Current code exposes four static accessors on `ContainerPeekClient`. No documented contract, no javadoc-as-API. The refactor's IP should promote these to documented public API — partly because Phase 11 explicitly validates cross-mod composition, partly because undocumented cross-mod APIs break silently when the provider refactors.

---

## 8. Summary

IP exercises a substantial portion of the new MenuKit surface: three contexts (inventory menus, HUDs, standalone — via the settings gear's redirect to YACL screens), five-plus injection pattern instantiations, all core elements (Button, Button.icon, Toggle.linked, TextLabel with supplier, Icon, custom HUD render), Panel.showWhen with supplier-driven visibility in both HUD and inventory-menu contexts, plus adjacent subsystems (storage, keybinds, family config, shift-click routing policies).

The refactor's complexity is front-loaded into architectural questions (4.7 per-slot state; 4.2 region → SlotGroupLike; 1.6 button attachment replacement) and moderate for mechanical work (1.1 peek panel mixins + supplementaries; 4.5 MKContext removal → per-screen mixins).

Phase 11's working practice — read intent, map to primitives, plan file structure, rewrite from scratch — applies cleanly. The intent catalog lives here. The mapping is mostly drawn. The plan and rewrite wait for the audit review.

**Pending review.** Once this audit is read and questions 1–14 are triaged (answer: preserved / not preserved / needs decision), the refactor plan can commit to file structure, mixin targeting, and public API surface. Implementation proceeds from there.
