# Phase 11 — Inventory-Plus Refactor Plan (v2)

**Phase 11 deliverable (plan, pre-implementation, v2).** Commits to the specifics that make IP's refactor mechanical: file layout, mixin targeting, packet protocols, the attachment-management seam, per-feature implementation order.

Predecessor: `AUDIT.md` (sibling). Bucket C resolutions from advisor triage are taken as binding. Cross-references to `THESIS.md`, `CONTEXTS.md`, `PALETTE.md`, and the three injection-pattern docs are not repeated.

**v2 revisions (2026-04-15).** Advisor sweep surfaced a recurring pattern: consumer-side complexity added to preserve old visuals or anticipate future needs. v2 applies the conforming-to-primitives lens (see § 0 below). Significant deltas from v1:

- Pocket buttons → `Toggle.linked` (was `Button.icon` with sprite supplier). Lose sprite swap; accept RAISED/INSET visual.
- Peek panel titles → horizontal `TextLabel`. No custom `VerticalTextLabel`. Panel layout reshapes; accept consequence.
- Peek protocol → one error mode (close peek + toast), no optimistic updates. Was five named error modes with revert logic.
- Mixin consolidation: three mixin classes (AbstractContainerScreen + AbstractRecipeBookScreen + CreativeModeInventoryScreen) cover the entire container-screen decoration surface, each carrying multiple @Inject methods across hooks. Was ~12 feature-scoped mixin classes.
- `PeekItemSource` abstraction dropped. Server-side peek handlers branch on sourceType directly against vanilla storage.
- Per-frame adapter cache in sort/move-button mixin dropped. Construct per render; profile if needed.
- **Pocket HUD: open question surfaced (see § 4.3).** Conforming shape uses three `ItemDisplay`s at fixed positions with no animation; preserving shape uses current custom render. Genuinely a judgment call; defers to advisor decision before implementation.

---

## 0. Conforming-to-primitives principle

The audit was archaeology — catalog what consumers wanted. The plan is design — express that intent using MenuKit's conforming primitives. Where the new primitive produces a slightly different visual or behavior, the migration pays that cost. Consumer-side workarounds to preserve old visuals defeat the purpose of a conforming primitive set.

Three implications for this plan:

1. **Visual differences are acceptable; custom PanelElements are not the default answer.** If a feature can be expressed with palette primitives, even imperfectly, it wins over a hand-rolled element.
2. **Mixin consolidation where targeting matches.** Phase 10's "one mixin per logical feature" applies when logical features have distinct targeting. When multiple features hook the same method on the same class chain, they consolidate into one mixin with branching inside.
3. **Don't ship complexity for anticipated problems.** Five error modes, optimistic updates, per-frame caches — all future-proofing for conditions that aren't observed yet. Ship the minimum viable shape; add complexity when measured need surfaces.

Cases where conforming-to-primitive genuinely loses something significant surface as open questions to the advisor, not as consumer-side customizations the plan slips in.

---

## 1. Architectural foundations

Three novel pieces land first (Layer 0 of § 6). All three are architectural foundations multiple features depend on.

### 1.1 IPPlayerAttachments layer

**Purpose.** The seam between IP-owned persistent state and MenuKit's Storage abstraction. Holds equipment + pocket items + pocket-disabled-slot metadata per-player, persistent across sessions, synced client↔server by Fabric's attachment lifecycle. Accessible from server-side passive-behavior mixins, packet handlers, client-side HUD render, and MenuKit-declared `SlotGroup`s via a `Storage` proxy.

**Technology.** Fabric API's data attachment system (`net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry`). Three `AttachmentType<T>` registrations — separate because their lifecycles and consumers differ (equipment mutates rarely; pockets mutate frequently on cycle; pocket-disabled-slots mutate rarely). Collapsing into one attachment would re-sync unchanged data on every mutation.

**Attachment types.**

| Attachment | Value shape | Mutators | Readers |
|---|---|---|---|
| `EQUIPMENT` | `EquipmentData(NonNullList<ItemStack> size=2)` | Equipment panel slot-click (via Storage proxy) | Equipment panel render; passive mixins (elytra, totem, mending, etc.) |
| `POCKETS` | `PocketsData(NonNullList<ItemStack> size=27)` — 9 hotbar slots × 3 pocket indices, flat | Pocket panel slot-click (Storage proxy); pocket-cycle server handler | Pocket panel render; pocket HUD; auto-restock / auto-route mixins |
| `POCKET_DISABLED` | `PocketDisabledData(byte[9])` — 1 byte per hotbar slot, 3 bits per pocket index | Pocket panel empty-click (toggleDisabled) | Pocket panel render (ghost icon); HUD (skip disabled); auto-route (skip disabled); pocket-cycle (skip disabled) |

**Codecs.** Each data class ships its own `Codec<Self>` (for NBT persistence) and `StreamCodec<RegistryFriendlyByteBuf, Self>` (for sync). Fabric's `AttachmentRegistry.Builder.persistent(Codec).syncWith(StreamCodec, ...).buildAndRegister()` wires both.

**Accessor facade.**

```java
public final class IPPlayerAttachments {
    public static void register();  // called at mod init (common + client)

    public static EquipmentData getEquipment(Player p);
    public static PocketsData getPockets(Player p);
    public static PocketDisabledData getPocketDisabled(Player p);

    // Initialize-with-default on first access; never returns null.
    // Used by passive-behavior mixins that may fire before any inventory interaction.
    public static EquipmentData getOrInitEquipment(Player p);
    public static PocketsData getOrInitPockets(Player p);
    public static PocketDisabledData getOrInitPocketDisabled(Player p);
}
```

**Sync protocol.** After any in-place mutation of the attachment's backing list, the data class calls `player.setAttached(TYPE, this)` via its own `markDirty(Player)` method. That setAttached call is what Fabric uses to detect changes and trigger sync. Without it, in-place list mutation is invisible to Fabric.

**Storage proxy** (`PlayerAttachedStorage implements Storage`). Thin adapter that delegates `getStack`/`setStack`/`size`/`markDirty` to the attachment's backing list. Constructed per-SlotGroup-per-handler-open with a `Supplier<Player>` (resolves to the opening player). Mutations go through one path: Storage → attachment → auto-sync.

```java
public final class PlayerAttachedStorage implements Storage {
    // Holds Supplier<Player>, Function<Player, NonNullList<ItemStack>> extractor,
    // Consumer<Player> markDirtyFn, int size.
    //
    // getStack: extractor.apply(player).get(i)
    // setStack: extractor.apply(player).set(i, s); markDirtyFn.accept(player);
    // markDirty: markDirtyFn.accept(player)
}
```

**Open question deferred to Layer 0 implementation.** Whether MenuKit's `MenuKitScreenHandler` requires `Storage instanceof PersistentStorage` in its handler-close lifecycle. If so, `PlayerAttachedStorage` adds no-op `save(ValueOutput)` / `load(ValueInput)` methods (since Fabric handles persistence independently). Resolved by reading the handler's close path in Layer 0.

### 1.2 Client-side peek protocol (simplified)

**Purpose.** Peek panels render locally from a client-side snapshot; each user interaction round-trips to the server for authoritative mutation. On close, no commit is needed because each prior mutation was already authoritative.

**Why client-side.** Per advisor triage (AUDIT § 5 resolutions): peek slots are not in the vanilla menu's slot list. This dissolves the old `registerDynamicRegion` requirement — there is no dynamic region to graft onto. The peek panel's slots live entirely in IP's client-side state.

**State machine (simplified).**

```
CLIENT:  idle ──open keybind──▶ loading ──PeekOpenS2C──▶ open ──close keybind / menu close──▶ idle
                                           │               │
                                           PeekErrorS2C    per click/shift-click/double-click:
                                           ▼                   send PeekMoveC2S
                                           idle                await PeekSyncS2C or PeekErrorS2C
                                           (show toast)        on PeekSyncS2C: overwrite peek storage
                                                               on PeekErrorS2C: close peek, show toast

SERVER: stateless — each packet carries peekedMenuSlotIndex, server re-resolves the peekable per packet.
```

**Packets (six total).**

| Direction | Name | Fields | Purpose |
|---|---|---|---|
| C2S | `PeekOpenC2SPayload` | `int menuSlotIndex` | "Open a peek on menu slot N." |
| S2C | `PeekOpenS2CPayload` | `int menuSlotIndex, int sourceType, int activeSlots, Component title, List<ItemStack> items` | Response to open; carries the snapshot. |
| C2S | `PeekMoveC2SPayload` | `int menuSlotIndex, int action, int fromIndex, int toIndex, int count` | Single mutation. `action` enum: `PICKUP`, `DROP`, `SHIFT_CLICK`, `DOUBLE_CLICK_COLLECT`. |
| S2C | `PeekSyncS2CPayload` | `int menuSlotIndex, List<ItemStack> items` | Server-confirmed peek contents after a successful mutation. |
| C2S | `PeekCloseC2SPayload` | `int menuSlotIndex` | "Close the peek." Client-initiated close; server no-op. |
| S2C | `PeekErrorS2CPayload` | `int menuSlotIndex, Component message` | Any server rejection. Single handling path on client: close peek + show toast. |

**Server-side peek state.** None beyond what each packet carries. Server re-resolves the peekable on every packet. No session map, no lifetime tracking, no cleanup beyond the packet protocol. Disconnect is handled by Fabric's disconnect event — no per-player peek state to clean.

**Error handling (simplified).** One error mode, one response. Any server rejection (peekable disappeared, peekable type changed, invalid slot index, storage rejection) results in `PeekErrorS2C` → client closes peek + shows a toast with the server-supplied message. No separate revert logic, no optimistic update, no differentiated error codes.

The old IP didn't differentiate these cases either. If finer-grained error handling becomes needed during testing, it's added then.

**Move-matching and sort integration.** Peek slots participate in sort + move-matching by carrying `peekMenuSlotIndex` on the existing C2S packets. `MoveMatchingC2SPayload` and `SortC2SPayload` each gain an `int peekMenuSlotIndex` field (−1 sentinel when peek not involved). Server-side resolves the peekable from that field and treats it as a transient source/dest group.

**Layer 3 checkpoint.** Before implementing peek sort / peek move-matching, verify the feature is well-defined given peek is client-side — specifically, that sort results propagate back cleanly via `PeekSyncS2C` and move-matching routing doesn't dead-end. If issues surface, peek-specific sort/move becomes a candidate for deferral.

### 1.3 Per-menu lock state holders

**Purpose.** Preserve current IP behavior: sort-lock survives only as long as the current `AbstractContainerMenu` instance exists. No persistence, no cross-session identity.

```java
public final class ClientLockStateHolder {
    private static final Map<Slot, SlotLockState> states =
        Collections.synchronizedMap(new WeakHashMap<>());
    // getOrCreate, get, isSortLocked, toggleSortLocked, clear
}

public final class ServerLockStateHolder {
    private static final Map<UUID, Map<Slot, SlotLockState>> byPlayer = ...;
    // getOrCreate(ServerPlayer, Slot), isSortLocked, setSortLocked, clearFor(ServerPlayer)
}

public final class SlotLockState {
    private boolean sortLocked;
    // isLocked() intentionally absent — dead code per audit § 7
    public boolean isSortLocked();
    public void setSortLocked(boolean);
    public void toggleSortLocked();
}
```

**Lifecycle.**
- Client: `ClientLockStateHolder.clear()` on screen close via Fabric event. WeakHashMap gives belt-and-suspenders GC.
- Server: `ServerLockStateHolder.clearFor(player)` on menu close event + on disconnect.

**Visual overlay.** `LockOverlayMixin` (at `inject/InventoryContainerMixin`'s render hook) reads from `ClientLockStateHolder.isSortLocked(slot)` and paints a lock icon at each locked slot's position. No state duplication.

---

## 2. File structure

Respects "don't restructure consumer mod code beyond what the architectural shift requires." Current files mostly keep their locations; new architectural layers get dedicated subpackages; Phase 10's mixin-package-and-sibling convention is followed.

```
inventory-plus/src/main/java/com/trevorschoeny/inventoryplus/
  InventoryPlus.java                   ← server init (rewritten)
  InventoryPlusClient.java             ← client init (rewritten)
  InventoryPlusConfig.java             ← unchanged
  SortMethod.java                      ← unchanged

  attachments/                         ← NEW: persistence layer (§ 1.1)
    IPPlayerAttachments.java
    EquipmentData.java
    PocketsData.java
    PocketDisabledData.java
    PlayerAttachedStorage.java

  network/                             ← existing; peek payloads replaced
    SortC2SPayload.java                ← extended: adds `int peekMenuSlotIndex` (-1 sentinel)
    SortLockC2SPayload.java            ← unchanged
    BulkMoveC2SPayload.java            ← unchanged
    MoveMatchingC2SPayload.java        ← extended: adds `peekMenuSlotIndex`
    AutoFillC2SPayload.java            ← unchanged
    PocketCycleC2SPayload.java         ← unchanged
    PeekOpenC2SPayload.java            ← replaces PeekC2SPayload
    PeekMoveC2SPayload.java            ← NEW
    PeekCloseC2SPayload.java           ← NEW
    PeekOpenS2CPayload.java            ← replaces PeekS2CPayload; carries items[]
    PeekSyncS2CPayload.java            ← NEW
    PeekErrorS2CPayload.java           ← NEW (single error shape)

  EquipmentPanel.java                  ← Panel definition (rewritten)
  PocketsPanel.java                    ← Pocket panel + button definitions (rewritten)
  PocketHud.java                       ← HUD panel (shape pending § 4.3 advisor decision)
  PocketCycler.java                    ← keybind + tick handler
  ContainerPeek.java                   ← server-side peek handlers (rewritten; stateless)
  ContainerPeekClient.java             ← client-side peek state + panels (rewritten)
  SettingsGearButton.java              ← NEW: Panel definition

  inject/                              ← NEW: Fabric-mixin package (per Phase 10 failure mode #3)
    InventoryContainerMixin.java       ← @Mixin(AbstractContainerScreen.class)
                                         render TAIL + mouseClicked HEAD + keyPressed HEAD
                                         + slotClicked HEAD (bulk-move). Dispatches to
                                         the appropriate feature per the hovered slot's menu
                                         type, peek state, hovered region, and keybind match.
    RecipeBookMixin.java               ← @Mixin(AbstractRecipeBookScreen.class)
                                         extends AbstractContainerScreen<AbstractContainerMenu>.
                                         Supplementary render TAIL + keyPressed HEAD
                                         gated by `instanceof InventoryScreen`.
                                         Fixes Phase 10 failure modes #1 + #2 for survival.
    CreativeInventoryMixin.java        ← @Mixin(CreativeModeInventoryScreen.class)
                                         Supplementary render TAIL + mouseClicked HEAD
                                         for creative's override behavior.

  locks/                               ← NEW: per-menu lock state (§ 1.3)
    ClientLockStateHolder.java
    ServerLockStateHolder.java
    SlotLockState.java

  regions/                             ← NEW: Q2 group composition
    IPRegionGroups.java                ← forMenu(menu, peekState) → partitioned SlotGroupLike views

  decoration/                          ← NEW: sibling (non-mixin) for inject/
                                         holds feature-specific dispatch logic referenced by the mixins
    EquipmentDecoration.java           ← Panel + origin resolvers + per-variant gating
    PocketsDecoration.java             ← pocket buttons + pocket panels + "which pocket is open"
    SettingsGearDecoration.java
    SortMoveButtonsDecoration.java     ← allowlist + per-region adapter construction
    PeekDecoration.java                ← peek panel rendering; packet send helpers
    LockOverlayDecoration.java         ← lock icon render helper
    KeybindDispatch.java               ← sort/lock/peek/move-matching keybind matching + dispatch

  features/                            ← existing, unchanged
    autofill/AutoFill.java             ← rewrite: reads IPPlayerAttachments.getPockets
    autoreplace/AutoReplace.java       ← rewrite
    autorestock/AutoRestock.java       ← rewrite
    autoroute/AutoRoute.java           ← rewrite
    mending/MendingHelper.java         ← rewrite: reads IPPlayerAttachments.getEquipment
    arrows/DeepArrowSearch.java        ← rewrite

  mixin/                               ← existing, passive-behavior mixins
    IPCanGlideMixin.java               ← rewrite: reads attachment
    IPFallFlyingMixin.java
    IPWingsLayerMixin.java
    IPTotemMixin.java
    IPDeathDropsMixin.java
    IPMendingMixin.java                ← minor import updates
    IPOrbMendingMixin.java
    IPAutoRestockMixin.java
    IPAutoReplaceMixin.java
    IPAutoRouteMixin.java
    IPDeepArrowMixin.java
    IPBowArrowMixin.java
    IPCrossbowArrowMixin.java
    IPRecipeBookAccessor.java          ← unchanged

  resources/
    inventory-plus.mixins.json         ← rewrite: package="...inject", client-side mixins listed
    fabric.mod.json                    ← minor: data-attachment API dependency
    assets/                            ← unchanged
```

### 2.1 Deletions

- `network/PeekS2CPayload.java` (replaced)
- `network/PeekC2SPayload.java` (replaced)
- `mixin/IPPeekClickMixin.java` (right-click peek — superseded by keybind, dead)
- `mixin/IPCreativePeekMixin.java` (same)
- Dead `isLocked()` branch in bulk-move handler (audit § 7)
- All imports of `MKSlotState*`, `MKRegion*`, `MKContainer*`, `MKEvent*`, `MKContext`, `MKItemTips`, `MKInventory`, `MKPanel`, `MKButton*`, `MKMoveMatching`, `MKContainerSort`, `MKGroupChild`

### 2.2 Why three mixin classes, not per-feature

Phase 10's "one mixin per logical feature" guideline applies when features target different classes or methods. IP's container-screen decoration surface targets the same three vanilla classes across all features — one mixin per target class, multi-@Inject, with feature dispatch inside the mixin methods delegating to non-mixin helpers in `decoration/`.

Splitting further (one mixin per feature) would create a fleet of classes each hooking the same method on the same class — fragmentation without isolation benefit. Each mixin's method becomes a thin dispatch layer; feature logic lives in `decoration/` helpers that keep the mixin short and readable.

---

## 3. Mixin targeting (consolidated)

### 3.1 `InventoryContainerMixin` (primary, all container screens)

`@Mixin(AbstractContainerScreen.class)`. Abstract class with one instance field per adapter (`@Unique`) — adapter construction happens lazily on first render per menu.

Four @Inject methods:

**Render TAIL** (`@Inject(method = "render", at = @At("TAIL"))`):
- If `menu instanceof InventoryMenu || menu instanceof CreativeModeInventoryScreen`: render equipment panel, 9 pocket panels, 9 pocket buttons, settings gear (via `EquipmentDecoration.render`, `PocketsDecoration.render`, `SettingsGearDecoration.render`).
- If `SortMoveButtonsDecoration.isAllowlisted(menu)`: render sort/move buttons for each SIMPLE region (`SortMoveButtonsDecoration.render`).
- If `ContainerPeekClient.isPeeking()`: render active peek panel (`PeekDecoration.render`).
- Always: render lock overlay on sort-locked slots (`LockOverlayDecoration.render`).

**mouseClicked HEAD** (`@Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true)`):
- Dispatch click through each feature's adapter in z-order (peek on top, then inventory decorations, then sort/move buttons).
- First adapter to consume cancels vanilla.

**keyPressed HEAD** (`@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)`):
- `KeybindDispatch.dispatch(...)` matches the event against sort, move-matching, lock, and peek keybinds via `MKKeybindExt.matchesEvent`. Returns `KeybindDispatch.CONSUMED` or `PASS`.
- If CONSUMED, sets `cir.setReturnValue(true)`.

**slotClicked HEAD** (`@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)`):
- `BulkMove.handleSlotClicked(slot, button, clickType, this)`: detects double-click + shift modifier; sends `BulkMoveC2SPayload`; cancels vanilla on match.

### 3.2 `RecipeBookMixin` (supplementary, survival inventory)

`@Mixin(AbstractRecipeBookScreen.class) extends AbstractContainerScreen<AbstractContainerMenu>` — extends-target pattern for inherited field access (Phase 10 failure mode #4).

Two @Inject methods, both gated by `instanceof InventoryScreen`:

**Render TAIL:** mirrors the primary's render dispatch logic for the survival-specific z-order fix (Phase 10 failure mode #2).

**keyPressed HEAD:** mirrors the primary's keyPressed dispatch for silent-inert fix (Phase 10 failure mode #1). Cancels vanilla on match to prevent double-dispatch if `AbstractRecipeBookScreen` starts super-calling in future versions.

### 3.3 `CreativeInventoryMixin` (supplementary, creative inventory)

`@Mixin(CreativeModeInventoryScreen.class)`.

Two @Inject methods:

**Render TAIL:** mirrors primary's render for creative's tab-icon z-order.

**mouseClicked HEAD:** mirrors primary's mouseClicked for creative's override (which bypasses parent's mouseClicked).

### 3.4 Why only three mixin classes

Four targets matter: `AbstractContainerScreen` (all container screens), `AbstractRecipeBookScreen` (survival inventory via inheritance), `CreativeModeInventoryScreen` (creative inventory), and `slotClicked` is a method on `AbstractContainerScreen`. Three classes × however-many-hooks-they-need covers the full decoration surface.

---

## 4. Feature-by-feature commitments

### 4.1 Equipment panel

- **Panel:** single `Panel` with a 2-slot `SlotGroup` backed by `PlayerAttachedStorage` over `IPPlayerAttachments.EQUIPMENT`.
- **Per-slot config:** `.filter(isElytra | isTotem)`, `.maxStack(1)`, `.ghostIcon(sprite)`, `.disabledWhen(configToggle)`.
- **Shift-click item routing:** SlotGroup's `shiftClickIn=true`, high `shiftClickPriority`. Per-slot `InteractionPolicy` rejects non-matching items. Elytra tries equipment first, slot 0 accepts, done. Totem: symmetric. Non-matching items fail the filter and continue to the next group. Behaviorally equivalent to the old predicate routing.
- **Per-screen coords:** `EquipmentDecoration.originForScreen(screen)` returns `(leftPos + 77, topPos + 25)` for `InventoryScreen` and `(leftPos + 16, topPos + 10)` for `CreativeModeInventoryScreen`.
- **Ghost icon preservation.** Uses SlotGroup's `.ghostIcon(Identifier)` builder option directly; per PALETTE, supported natively.
- **Style:** `PanelStyle.NONE` — no panel background; blends with vanilla armor column.
- **Passive behaviors** (in `mixin/`) now read `IPPlayerAttachments.getEquipment(player).items()` instead of `MenuKit.getContainerForPlayer(...)`.

### 4.2 Pocket buttons and pocket panels (conforming choice)

**Pocket buttons.** One `Panel` containing nine `Toggle.linked` children, one per hotbar slot.

```java
for (int i = 0; i < 9; i++) {
    final int idx = i;
    panelBuilder.element(Toggle.linked(
        x(i), y,      // position under hotbar slot i
        16, 16,       // size
        () -> openPocketIndex == idx,           // BooleanSupplier
        () -> openPocketIndex = openPocketIndex == idx ? -1 : idx  // Runnable
    ));
}
```

Visual: RAISED when closed, INSET when open. No sprite; positional association under the hotbar slot carries the "which pocket" signal. **Loss accepted:** the old pocket/pocket_toggled sprite swap is gone. If Phase 11 testing reveals users can't tell which hotbar slot a button is for, that's a reconsideration trigger for Phase 12 (either `Toggle.linked` with sprite-supplier overload in the library, or a tiny numbered label).

**Pocket panels.** Nine `Panel`s with 3-slot `SlotGroup`s backed by `PlayerAttachedStorage` over `IPPlayerAttachments.POCKETS`. Each uses `.showWhen(() -> openPocketIndex == i && config.enablePockets)`. Per-slot:

- `.filter(slot -> pocketIndex < config.pocketSlotCount && !isDisabled(hotbarIdx, pocketIndex))`
- `.ghostIcon(supplier returning BARRIER when disabled else null)`
- `.onEmptyClick(slot -> toggleDisabled(hotbarIdx, pocketIndex))` (empty-click toggles disabled state)
- `.emptyTooltip(supplier returning "Enable slot" | "Disable slot")`
- `.disabledWhen(pocketIndex >= config.pocketSlotCount)` (fully hides slots beyond configured count)

**`openPocketIndex`** is a client-only int field in `PocketsDecoration`. `-1` when no pocket open. Reset on menu close via Fabric screen-close event.

### 4.3 Pocket HUD — conforming shape (advisor-resolved)

Three `ItemDisplay`s at fixed A-shape positions, each with explicit size for ~0.6× scaling and a supplier reading `IPPlayerAttachments.getPockets(mc.player).items(selected*3 + i)`. No animation. Cycle mutations show via next-frame re-render.

```java
MKHudPanel.builder("pocket_hud")
    .anchor(HudAnchor.BOTTOM_CENTER, 0, 0)
    .autoSize()
    .showWhen(PocketHud::hasAnyPocketItems)
    .element(new ItemDisplay(posX(1), bottomRowY, () -> pocketStack(0), /* size */ 10))
    .element(new ItemDisplay(posX(2), topRowY,    () -> pocketStack(1), /* size */ 10))
    .element(new ItemDisplay(posX(3), bottomRowY, () -> pocketStack(2), /* size */ 10))
    .build();
```

Implementation size: ~30 lines. Cycle animation is dropped per conforming-to-primitives principle; Phase 12 reconsiders (likely as a library-level animation primitive benefiting other HUD consumers).

**Layer 2 implementation check.** § 4.3's shape assumes `ItemDisplay` supports a `size` parameter that scales the rendered item (not just bounding box). Verify during Layer 2. If size parameter doesn't scale the sprite, fallback: vanilla 16px items with the A-shape spread wider rather than scaled smaller — same conforming principle, different layout. If neither works, surface as follow-up rather than reaching for custom render.

### 4.4 Peek panels

- **Client-side storage:** `PeekSession.getStorage()` — plain `Storage` implementation backed by `NonNullList<ItemStack>` populated from `PeekOpenS2CPayload.items` and overwritten on each `PeekSyncS2CPayload`.
- **Three panels** (shulker / ender / bundle), each with `.posLeft()`, `.hidden()`, `.exclusive()`, `PanelStyle.RAISED`, `shiftClickIn=true`, `shiftClickOut=true`.
- **Title:** **horizontal** `TextLabel` above the slot grid. No custom PanelElement. Panel layout reshapes: title takes vertical space instead of horizontal space. Sort + move-matching buttons share the above-grid region with the title — implementation chooses row layout (title-left + buttons-right, or title-top + buttons-below). **Loss accepted:** the old vertical-title visual is gone.
- **Bundle variable slot count:** `.disabledWhen(slotIndex >= getEffectiveBundleSlots())` on each of 64 slots. Effective count computed purely from the client-side peek storage (no vanilla-menu dependency — simpler than current).
- **Visibility:** `Panel.setVisible(true/false)` from `PeekDecoration`'s open/close helpers. Not `.showWhen` — per Q6.
- **Recipe book:** `MenuKitClient.setRecipeBookOpen(false)` on peek open (preserving prior state); `setRecipeBookOpen(wasOpen)` on peek close.
- **Sort + move buttons inside the peek panel:** regular `Button.icon` children positioned above the slot grid. Clicks send `SortC2SPayload(peekRegionName, peekMenuSlotIndex)` and `MoveMatchingC2SPayload(..., peekMenuSlotIndex)`.

### 4.5 Sort + move-matching button decoration (allowlist)

- **Primary:** `InventoryContainerMixin.render/mouseClicked` delegate to `SortMoveButtonsDecoration`.
- **Allowlist** (`SortMoveButtonsDecoration.isAllowlisted`): `InventoryMenu`, `CreativeModeInventoryScreen.getMenu()` when `cs.isInventoryOpen()`, `ChestMenu`, `ShulkerBoxMenu`, `HopperMenu`, `DispenserMenu`. Furnace + brewing stand excluded. Modded containers excluded by default; public extension API deferred until demand.
- **Per-region adapters:** constructed per render frame. For each SIMPLE region via `HandlerRecognizerRegistry.recognize(menu)` (minus blocklist `player_inventory`, `pocket_*`), position a 2-button panel via `ScreenOriginFns.aboveSlotGrid(regionStartX, regionStartY, panelHeight=9, gap=2)`. No caching — if profiling shows render cost, optimize then.
- **Button click behavior:** sort → `SortC2SPayload(regionName)`; move-matching → `MoveMatchingC2SPayload(source, dest, destRegion, includeHotbar)` via `IPRegionGroups.forMenu(menu)` for source/dest resolution.
- **Gating:** `enableSorting && showSortButton && !isCreativeTabsView()` (per current config).
- **Move-matching auto-disable:** when `IPRegionGroups.forMenu(menu).allSimple().size() < 2`, the move-matching button's `.disabledWhen` hides it.
- **Peek integration:** `IPRegionGroups.forMenu(menu)` observes `ContainerPeekClient.isPeeking()` and includes the peek region in `containerStorage` when true.

### 4.6 Settings gear button

- **Panel:** one `Panel` with a single `Button.icon("settings", 11)` at `(0, 0)`, size 11×11.
- **Visibility:** `Panel.showWhen(() -> family.getGeneral(MenuKitClient.SHOW_SETTINGS_BUTTON))` — noting the new facade location for SHOW_ITEM_TIPS per § 0's MKItemTips relocation.
- **Click:** `family.buildConfigScreen(mc.screen, InventoryPlus.MOD_ID)` → `mc.setScreen(...)`.
- **Coords:** survival top-right above inventory frame; creative a tab-safe position. Per-variant resolution in `SettingsGearDecoration.originForScreen`.
- **Ownership convention:** IP owns the settings-gear mixin; other trevmods family members depend on IP for the button. Without IP, no gear button. Documented in IP's README.

### 4.7 Keybinds

- **Registration.** Unchanged shape — `MKKeybindExt.fromKeybind(cfg.xxxKeybind, "key...", family.getKeybindCategory())` + `MKKeybindSync.register(mapping, callback)`. Seven keybinds registered in `InventoryPlusClient.onInitializeClient()`.
- **In-screen keybinds** (sort, move-matching, lock, peek): dispatched from `InventoryContainerMixin.keyPressed` + `RecipeBookMixin.keyPressed` + `CreativeInventoryMixin` (if needed) through `KeybindDispatch.dispatch(...)`.
- **Gameplay keybinds** (pocket cycle left/right, autofill): `ClientTickEvents.END_CLIENT_TICK` polling loop. No MenuKit involvement. `PocketCycler.java` handles cycle; `InventoryPlusClient` holds the autofill tick handler.

### 4.8 Shift+double-click bulk-move

- **Mixin hook:** `InventoryContainerMixin.slotClicked` (HEAD) delegates to `BulkMove.handleSlotClicked`.
- **Detection:** `BulkMove` holds `lastClickTimeMs` + `lastClickSlot` in static fields. Double-click = `now - lastClickTime < 250 && slot == lastClickSlot && Screen.hasShiftDown()`.
- **Packet:** existing `BulkMoveC2SPayload(regionName, itemId)`.
- **Server-side:** skip locked slots via `ServerLockStateHolder.isSortLocked`. `isLocked()` branch dropped (dead).

### 4.9 Shift-click routing

All via `SlotGroup` builder options: `shiftClickIn`, `shiftClickOut`, `shiftClickPriority` (numeric). Per-slot filters via `InteractionPolicy`. Equipment's item-specific routing achieved via high group priority + per-slot filter (§ 4.1).

### 4.10 Passive behavior mixins

Eleven mixins in `mixin/`. Each rewrite is mechanical: replace `MenuKit.getContainerForPlayer(...)` with `IPPlayerAttachments.getEquipment(player).items()` / `getPockets(player).items(hotbarSlot)`. Item-level logic unchanged.

### 4.11 Cross-mod public API

`ContainerPeekClient` static accessors documented as stable:

- `isPeeking() → boolean`
- `getPeekedSlot() → int` (menu slot index, −1 when not peeking)
- `getSourceType() → PeekSourceType` (new enum: `SHULKER`, `ENDER`, `BUNDLE`)
- `getPeekTitle() → Component`
- `getPeekedItemStack() → ItemStack` (NEW: the actual peekable item, for shulker-palette to look up palette state)

Documented in IP's own `PUBLIC_API.md` + javadoc. Consumer mods import directly.

---

## 5. Cross-mod public API

§ 4.11. Five stable methods on `ContainerPeekClient`. No library mediation; consumers import directly.

---

## 6. Implementation ordering

### Layer 0a — Clean-baseline cleanup

Prerequisite for Layer 0's verification. IP's current tree imports old MenuKit APIs that Phase 5 deleted (`MKRegion*`, `MKContainer*`, `MKEvent*`, `MKSlotState*`, `MKContext`, `MKPanel`, `MKButton*`, `MKInventory`, `MKContainerSort`, `MKMoveMatching`, `MKGroupChild`). `./gradlew :inventory-plus:compileJava` fails with ~100 errors. Verification of subsequent layers requires a compiling module.

Layer 0a establishes a clean-compile baseline: IP compiles, does nothing at runtime, mixin loader has no stale entries.

Steps:

1. **Stub `InventoryPlus.onInitialize()`** to `InventoryPlusConfig.load()` only. Delete all packet registrations and event handlers; remove unused imports.
2. **Stub `InventoryPlusClient.onInitializeClient()`** to minimal family setup only (`MenuKit.family("trevmods").displayName(...).description(...).modId(...)`). Delete keybind registrations, HUD registration, sort-attachment registration, config-category, shared-panel, all MKEvent handlers; remove unused imports.
3. **Gut panel/HUD registration method bodies** to no-op: `EquipmentPanel.register()`, `PocketsPanel.register()`, `PocketHud.register()`, `PocketCycler.registerKeybinds/registerKeybindSync`, `ContainerPeek.registerContainer/registerPackets/closePeek`, `ContainerPeekClient.registerPanel/registerClientHandler/registerCloseHandler`. Keep method signatures so Layer 1/2/3 can re-populate in place.
4. **Stub feature helpers in `features/`** (AutoFill, AutoReplace, AutoRestock, AutoRoute, MendingHelper, DeepArrowSearch): replace method bodies with no-op returns. Preserve signatures for Layer 2 rewrites.
5. **Stub passive-behavior mixins in `mixin/`** (13 files): replace `@Inject` method bodies with immediate return. Preserve `@Mixin` + `@Inject` annotations and method signatures — Fabric mixin JSON wiring stays intact, Layer 2 rewrites in place.
6. **Delete files**:
   - `mixin/IPPeekClickMixin.java` (right-click peek; superseded by keybind; already not in mixins.json)
   - `mixin/IPCreativePeekMixin.java` (same)
   - Any other dead Java files not referenced by mixins.json or IP's own init code
7. **Scrub `inventory-plus.mixins.json`** for stale entries referencing now-deleted classes (Phase 5 demolition residue). Current file has 13 registered mixins (12 server + 1 client); verify each resolves to an existing class post-cleanup.
8. **Delete `isLocked()` dead-code branch** at the bulk-move handler — the call site preserved, just the dead `||` clause removed.
9. **Delete ghost helpers**: `ContainerPeekClient.getPeekedSlot/getSourceType/getPeekTitle/isPeeking` accessors can stay but return stub defaults (−1, 0, `Component.empty()`, `false`) — Layer 3 will repopulate. This keeps any cross-mod consumer API references stable in signature.
10. **Verify clean compile**: `./gradlew :inventory-plus:compileJava` passes.
11. **Commit** as a single "Layer 0a — clean baseline" commit separate from subsequent Layer 0 work.

Deliverable: module compiles, no runtime behavior (all keybinds unbound, no panels registered, no HUD, no passive behaviors firing). Mixin loader happy. Ready for Layer 0's attachment system.

### Layer 0 — Attachment foundations

Blocking for attachment-dependent work (Layer 2). Executes against the Layer 0a clean baseline.

1. `attachments/` package: `EquipmentData`, `PocketsData`, `PocketDisabledData`, `PlayerAttachedStorage`, `IPPlayerAttachments.register()`.
2. Verify NBT round-trip + client sync + basic get/set (via a debug command or test harness).
3. Answer the `PersistentStorage` question via reading MenuKitScreenHandler close path.

### Layer 1 — Attachment-independent features (parallel to Layer 0)

1. `locks/` + keybind dispatch logic (`KeybindDispatch` for lock/sort/move-matching; peek keybind comes in Layer 3).
2. `regions/IPRegionGroups` + sort server handler + move-matching server handler.
3. `SortMoveButtonsDecoration` + allowlist.
4. `decoration/BulkMove` + sort-lock server-side read path.
5. `SettingsGearDecoration` (trivially simple; no state).
6. Consolidated `InventoryContainerMixin` + `RecipeBookMixin` + `CreativeInventoryMixin` skeletons with Layer 1 features wired in. Supplementaries deferred until render/keyPressed features land.

**Visual verification per feature before advancing.**

### Layer 2 — Attachment-dependent features

Requires Layer 0 complete.

1. `EquipmentDecoration` — equipment panel + per-variant origin.
2. `PocketsDecoration` — pocket buttons (Toggle.linked) + 9 pocket panels + `openPocketIndex` state.
3. `PocketHud` — **pending § 4.3 advisor decision.**
4. `PocketCycler` server handler — reads/writes `IPPlayerAttachments.POCKETS`.
5. Autofill server handler — reads pockets attachment.
6. All 11 passive-behavior mixins rewritten to use `IPPlayerAttachments`.
7. Wire Layer 2 into the consolidated mixin dispatch.

### Layer 3 — Client-side peek

Self-contained (no Layer 0/1/2 dependency).

1. New packet types (six): `PeekOpenC2S/S2C`, `PeekMoveC2S`, `PeekSyncS2C`, `PeekCloseC2S`, `PeekErrorS2C`.
2. `ContainerPeek` rewrite — stateless server handlers, per-type branching for shulker/ender/bundle storage.
3. `ContainerPeekClient` rewrite + `PeekSession` client state + client-side Storage.
4. Peek panels (3 grids with horizontal titles + embedded sort/move buttons).
5. Peek keybind dispatch in `KeybindDispatch`, wired into `InventoryContainerMixin.keyPressed`.
6. Peek panel render in `PeekDecoration`, wired into `InventoryContainerMixin.render`.
7. Extend `SortC2SPayload` + `MoveMatchingC2SPayload` with `peekMenuSlotIndex`.
8. **Layer 3 checkpoint:** verify peek sort + peek move-matching work cleanly given peek's client-side nature. If problematic, defer the features.

### Layer 4 — Cross-mod API

1. Narrow `ContainerPeekClient`'s public surface to the five intended methods.
2. `PeekSourceType` enum.
3. `getPeekedItemStack()` accessor.
4. Write `PUBLIC_API.md` in IP's repo root.

### Layer 5 — Cleanup + integration

1. Delete `IPPeekClickMixin`, `IPCreativePeekMixin`, `isLocked()` branch, and all old-arch imports listed in § 2.1.
2. Run `/mkverify all` — all 5 MenuKit contracts pass.
3. Visual verification sweep per § 10 checklist.
4. Enable `inventory-plus` in `dev/build.gradle`.
5. Refactor report at `Design Docs/Phase 11/inventory-plus/REPORT.md`.

### Estimate

Layer 0a: ~1-2 hours (mechanical). Layer 0: ~1 day. Layer 1: ~2 days (simpler now with consolidated mixins). Layer 2: ~2-3 days. Layer 3: ~2-3 days (simpler protocol). Layer 4: ~half day. Layer 5: ~half day (most cleanup now happens in Layer 0a; Layer 5 is final scrub + verification + dev/build.gradle re-enable). **Total rough estimate: 8-11 days for IP alone.**

---

## 7. Failure modes and edge cases

### 7.1 Peek (simplified)

Any server-side rejection → `PeekErrorS2C(menuSlotIndex, Component message)` → client closes peek, shows toast. One path, no differentiation. Disconnect handled by Fabric's disconnect event; no per-player peek state to clean.

### 7.2 Attachment lifecycle

- **Access before init:** `getOrInit*` returns a default-initialized data object, persisted lazily.
- **Client-server desync:** best-effort per Fabric; next mutation resyncs.
- **Mutation during sync:** next packet supersedes; transient intermediate visible only; server authoritative.

### 7.3 Lock state across menu transitions

- WeakHashMap + explicit `clear()` on menu close. Missed event → GC cleanup eventually. No correctness issue.
- Defensive clear on each `SortLockC2SPayload` receipt when referenced menu doesn't match `player.containerMenu`.

### 7.4 Third-party modded container screens

Allowlist is strict-add-only. Modded menus get zero decoration. Acceptable cost until user-demand signal surfaces.

### 7.5 Creative-mode ItemPickerMenu

`screen.getMenu()` can be `ItemPickerMenu` while `player.containerMenu` is `InventoryMenu`. With client-side peek, this simplifies: `isPeekable` tests only the item (not its container), so hovering a peekable in either menu works naturally. Server-side peek mutations use `player.containerMenu` which is the correct one for peek addressing (the inventory menu holds the shulker).

---

## 8. Open questions (implementation-phase refinements)

1. **`PersistentStorage` requirement.** Resolved during Layer 0 by reading MenuKitScreenHandler close path.
2. **PocketHud shape (§ 4.3).** Advisor decision before Layer 2 can implement.
3. **Autofill keybind registration location.** Confirmed during Layer 1 (likely co-located with other keybinds in InventoryPlusClient).
4. **Layer 3 checkpoint for peek sort/move-matching.** Verify well-defined given peek is client-side; defer if problematic.
5. **Lock overlay sprite.** Preserve current asset path; minor art adjustments during Layer 1 if needed.

---

## 9. Out of scope (feature preservation discipline + conforming to primitives)

Phase 11 preserves user-visible behavior only. These items are **not** in the refactor:

- **Persistent lock state across menu open/close** (Q1 trigger).
- **Full-lock feature (Ctrl+click)** (dead code; re-add as feature).
- **Optimistic peek updates** — not in current IP, not added.
- **Five-mode peek error handling** — not in current IP, not added.
- **Sprite-swap on pocket buttons** — replaced by RAISED/INSET.
- **Vertical peek titles** — replaced by horizontal.
- **Pocket HUD animation** — pending § 4.3 decision; default lean: drop.
- **PeekItemSource shared abstraction** — server handlers branch on sourceType directly.
- **Per-frame adapter caching** — construct per render; optimize if profiling demands.
- **Cross-mod composition registry** — consumers import IP's API directly.
- **Third-party modded container decoration** — allowlist add-only; defer.
- **New sort methods beyond MOST_ITEMS + BY_ID** — preserve.
- **Peek of non-shulker/non-bundle/non-ender items** — preserve.

---

## 10. Verification

Each layer's completion requires (a) in-dev visual verification of each feature and (b) library contract verification via `/mkverify all`.

Final IP verification checklist (Layer 5 gate):

- [ ] `/mkverify all` — all 5 MenuKit canonical contracts pass
- [ ] Equipment panel renders + filters + ghost icons + disable-when-config-off (survival + creative)
- [ ] Equipment passive behaviors: elytra flight, totem death-save, mending, wings rendering
- [ ] Pocket buttons render as toggles; RAISED/INSET reflects open pocket; mutual exclusion works
- [ ] Pocket panels: 3 slots each, disabled-slot toggling, ghost-icon + empty-tooltip, persistence across relog
- [ ] Pocket HUD: shape per § 4.3 decision; visibility gate + predicate cheap
- [ ] Pocket cycle: left + right + disabled-slot skipping; (animation per § 4.3)
- [ ] Autofill: keybind + scan + fill
- [ ] Auto-restock + auto-replace + auto-route + deep arrow: each exercised
- [ ] Sort keybind + button: each allowlisted container, both sort methods
- [ ] Move-matching keybind + button: source/dest, hotbar include/exclude, auto-disable when 1 container
- [ ] Lock keybind + overlay + bulk-move skip (session-scoped — relog resets)
- [ ] Bulk-move via shift+double-click
- [ ] Peek: shulker + ender + bundle; each type × each allowlisted vanilla container screen
- [ ] Peek error (peekable disappears): peek closes + toast
- [ ] Peek sort + move-matching: work on peek regions (Layer 3 checkpoint)
- [ ] Settings gear: opens YACL, focused on IP tab
- [ ] All 7 keybinds + Controls sync
- [ ] Horizontal peek titles render; layout accommodates

On any failure, fix cycle within Layer 5; no new phase opens.

---

## 11. Status

Plan v2 approved. PocketHud § 4.3 resolved: conforming shape (three `ItemDisplay`s, no animation). Implementation begins with Layer 0a (clean-baseline cleanup).

Working-practice note: the Layer 0a pattern (pre-refactor cleanup pass to establish clean compile against the new architecture) applies to all four Phase 11 consumer mods. Each is in the same temp-disabled post-Phase-5 state; each will need its own Layer 0a equivalent before feature work can verify cleanly.

Refactor report at `Design Docs/Phase 11/inventory-plus/REPORT.md` at phase end.
