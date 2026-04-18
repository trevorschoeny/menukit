# Phase 12 — Close-out REPORT

Library-primitive phase. Phase 11 filed four mechanism candidates with
consumer-mod evidence (M1, M4, M5, M6); Phase 12 designed and shipped them
(minus M6, which dissolved during verification). This report captures the arc,
the architectural findings that should carry forward, and the state of record.

---

## 1. Arc summary

**Original plan (late Phase 11):** M6 → M4 → M1 → M5, serial. The sequence shifted significantly during execution:

- **M6 dissolved first** during verification of the client-side slot primitive. Peek slots need vanilla's full slot protocol (drag, bidirectional shift-click, native cursor management); a client-side primitive with custom click dispatch couldn't deliver. Without peek as a consumer, M6 had no evidence to ship. The rendering analysis (`SlotRendering` utility) carried forward into M4's visual layer.
- **M4 mechanism landed** in the Phase 12 checkpoint (`4ed9793`) — grafting equipment slots onto `InventoryMenu.<init>` RETURN via `addSlot()`, verified end-to-end (filter, max stack, shift-click routing, persistence via `PlayerAttachedStorage`). The original visual-layer plan (SlotRendering in a mixin render hook) failed and was replaced by a by-reference-to-slot-coordinates pattern via `ScreenPanelAdapter` + shared constants, settled in M5's design.
- **12a stabilization** between M4 and M5: the hasClickedOutside discovery (vanilla misclassifies clicks on slots outside the container frame as THROW, not PICKUP) surfaced during F8 verification; the fix landed with three-screen coverage (`AbstractContainerScreen`, `AbstractRecipeBookScreen`, `CreativeModeInventoryScreen`). Library dissolution: `SlotInjector`, `GraftedRegion`, `AbstractContainerMenuAccessor` all unused, all deleted. `MenuKitSlot.getItem()` override briefly removed then restored after `/mkverify` flagged the inertness regression.
- **M5 shipped next** — Context-Scoped Region System. Three per-context enums (InventoryRegion 8, HudRegion 9, StandaloneRegion 8), `RegionMath` pure resolver, `RegionRegistry` internal panel list, `ScreenPanelAdapter` + `MKHudPanel.Builder` overloads, `PanelPosition.IN_REGION` reserved API. The §4A "by-value vs by-reference panel composition" framing crystallized during advisor round-2 and became load-bearing for both M5 and the grafted-slot visual layer in M4.
- **M1 shipped last** — per-slot persistent state. Typed channels, Tag-native storage (codified as THESIS principle 6), Fabric attachments on Player / BlockEntity / Entity, two snapshot paths (menu-open + player-join/respawn), per-player private storage with V2 shared-state migration path baked into the BE-hosted `Map<UUID, Bag>` shape.

**What the phase actually shipped:** three library primitives (M4 mechanism, M5, M1), one mechanism dissolution (M6), one architectural principle (THESIS §6 on vanilla persistence patterns), and the 6th + 7th `/mkverify` contracts that will catch regressions on the new primitives at every future phase boundary.

---

## 2. Commit timeline

```
a448572  Phase 12: M6 dissolved — peek requires M4 vanilla slot injection
4ed9793  Phase 12 session checkpoint — M4 mechanism confirmed, visual layer in progress
d22bdf8  MK: Phase 12 12a — hasClickedOutside full scope + library surface dissolution
0e406d8  IP: Phase 12 12a — InventoryContainerMixin render hook back to Phase 11 state
21c935d  MK: Phase 12 M5 scaffolding — regions, math, registry, adapter + builder overloads
03b2a1a  MK: restore MenuKitSlot.getItem override — closes 12a inertness regression
2222035  Docs: Phase 11 close-out refresh + M5 region specs + advisor handoff
9cdc553  MK: Phase 12 M1 — per-slot persistent state primitive + THESIS principle 6
```

Plus Phase 11 close-out docs committed during this arc (`a031bd6`, `5ee49d1`, `b5e4dc3` etc. from Phase 11).

---

## 3. What shipped, what dissolved

### Shipped — library primitives

| Primitive | Commit | Consumer exposure |
|-----------|--------|-------------------|
| `hasClickedOutside` three-screen fix | `d22bdf8` | Everywhere — unblocks any consumer with slots outside the menu frame |
| `MKClickOutsideHelper` (shared helper) | `d22bdf8` | Internal |
| `MenuKitSlot` restructure (`getItem` preserved) | `4ed9793`, `03b2a1a` | MenuKit-native screens — inertness contract holds |
| `StorageContainerAdapter` extracted to core/ | `4ed9793` | M4 consumers (IP's `InventoryMenuMixin`) |
| `InventoryRegion` / `HudRegion` / `StandaloneRegion` | `21c935d` | Phase 13a consumer migrations |
| `RegionMath` (pure) | `21c935d` | Internal — tested by `/mkverify` contract 6 |
| `RegionRegistry` | `21c935d` | Internal |
| `ScreenPanelAdapter(Panel, InventoryRegion)` overload | `21c935d` | Phase 13a |
| `MKHudPanel.Builder.region(HudRegion)` | `21c935d` | Future HUD consumers |
| `PanelPosition.IN_REGION` (reserved) | `21c935d` | Standalone-screen consumers (deferred impl) |
| `Panel.getWidth/getHeight/size(w,h)` | `21c935d` | Any panel that uses region stacking |
| `ScreenOrigin.OUT_OF_REGION` sentinel | `21c935d` | Internal |
| `MKSlotState` + `SlotStateChannel` + `PersistentContainerKey` | `9cdc553` | Phase 13 consumer migrations |
| `SlotStateBag` / `PerPlayerSlotStateBag` (NBT-native) | `9cdc553` | Internal |
| 4 `SlotStateAttachments` types (Player/EnderChest/BE/Entity) | `9cdc553` | Internal |
| Snapshot + update packet types | `9cdc553` | Internal |
| `PlayerOpenMenuMixin` + `PlayerListRespawnMixin` + JOIN handler | `9cdc553` | Internal |

### Dissolved — library artifacts deleted

| Artifact | Reason |
|----------|--------|
| `SlotInjector` | No consumer; IP's mixin calls `addSlot` directly via `@Mixin`-generated superclass |
| `GraftedRegion` | No consumer; IP tracks its own index range |
| `AbstractContainerMenuAccessor` | No consumer; `SlotInjector` was the only caller |

### Dissolved — mechanism candidate

| Entry | Reason |
|-------|--------|
| M6 (client-side slot primitive for decoration panels) | Peek needs vanilla's full slot protocol; a client-side primitive can't deliver. Rendering analysis (`SlotRendering`) carried forward to M4 |

### THESIS.md additions

| Principle | Source |
|-----------|--------|
| §6 "Match vanilla's persistence patterns" | M1 advisor round-1 feedback — NBT/Tag throughout persistence, dual-codec pattern, `/data get` inspectability |

---

## 4. Architectural findings

### Finding 1: `hasClickedOutside` misclassifies out-of-frame slots

Vanilla's `AbstractContainerScreen.mouseClicked` overrides the slot index to -999 when `hasClickedOutside` returns true — changing the click type from PICKUP to THROW and calling `player.drop(...)`. Slots grafted outside the container frame (equipment column at `x = -22`, pockets below the main inventory, etc.) hit this misclassification because the frame-bounds check ignores slot positions that are correctly found by `getHoveredSlot`.

Fix: three-screen mixin coverage (Abstract / RecipeBook / Creative), shared `MKClickOutsideHelper.clickLandsOnActiveSlot` with 1px tolerance. Creative shadows the private field to keep state coherent. Ships unconditionally — any future out-of-frame slot benefits.

**Carries forward:** Phase 13c (F15 peek) will want to confirm this covers peek slots too; the fix is screen-class-based, not feature-based, so it should just work.

### Finding 2: Two-layer model for grafted slots

M4 grafted slots have two layers that look related but aren't coupled:

- **Handler layer** — real vanilla `Slot` instances added via `addSlot(...)` during `<init>` RETURN. Vanilla owns click dispatch, drag, shift-click, cursor, sync. Slot `(x, y)` is fixed at construction.
- **Visual layer** — the backdrop Panel drawn behind the slots. Panel rendering is a separate pipeline via `ScreenPanelAdapter`. Its position must trace the handler-layer slot coordinates, not participate in stacking flows that would drift them apart.

The failed mixin-render-hook approach conflated the two. The shipped pattern (M5 §5.6) separates them cleanly: shared-constants file holds the coordinates, both layers read from it.

### Finding 3: By-value vs by-reference composition (M5 §4A)

Two composition models coexist in MenuKit and they don't mix. Named during M5 advisor round-2:

- **By-value composition (stackable).** Decoration panels that can move without breaking anything — settings gear, sandboxes button, pocket-preview HUD. Stacking reorganizes them per-frame. Panels flow and re-layout, like CSS flexbox. **Regions model this.**
- **By-reference composition (fixed-anchor).** Panels whose coordinates are referenced by another system — grafted-slot backdrops (foreign key to `Slot.x/y`), vanilla-hotbar-anchored overlays (foreign key to vanilla HUD layout). The Panel's position is a reference; moving it out from under its reference-holder causes drift. **Regions don't model this — use `ScreenOriginFn` lambdas with shared constants.**
- **By-reference-to-owner composition (M1 §4.1).** State whose home is determined by the structural owner of the slot (player UUID, block entity, entity). Owner dies → state dies. Structural ownership means structural cleanup, no library-level GC.

The framing makes it possible to tell whether a new consumer requirement fits regions or not. Sort-jitter-on-dynamic-size, grafted-slot drift, and state-leak-on-hidden-panels are all instances of mixing the patterns. Future consumers that reach for a region to solve a by-reference problem should be pushed to the lambda path; consumers that reach for a lambda for a genuinely stackable decoration should be migrated.

### Finding 4: Match vanilla's persistence patterns (THESIS §6)

M1 round-1 shifted the storage path from opaque `byte[]` to NBT-native `Tag` throughout. Codified in THESIS as principle 6. Rationale:

- `/data get` on the owner sees what the library stored. NBT editors parse it.
- Storage and wire are separate concerns — `Codec<T>` for NBT persistence, `StreamCodec<T>` for binary wire. Dual-codec matches vanilla's DataComponents pattern.
- `CompoundTag` (not `byte[]`) is the correct opaque-payload shape for modded-resolver extensions. The library stores opaquely; consumer defines the shape; vanilla tooling still parses it.

The principle constrains future primitives — anything that persists state answers the `/data get` test before landing.

### Finding 5: `MenuKitSlot.getItem()` override is load-bearing for inertness

The 12a checkpoint removed it on the theory that behavioral inertness (`isActive` / `mayPlace` / `mayPickup`) was sufficient. `/mkverify all` disagreed — behavioral methods block interaction but not observation, and hidden-panel content leaked via `broadcastChanges`, foreign `Slot.getItem` mixins, and `menu.slots` iteration. Restored in `03b2a1a`.

The javadoc on the override now documents why it's load-bearing — future-us should resist removing it again. General principle: the canonical contracts (`/mkverify all`'s five original probes) are measurements of library semantics; removing internal code that seems defensive should be verified against the probes, not against visual spot-checks.

### Finding 6: The phase-sequence discipline held

Four mechanism candidates landed as three shipped + one dissolved, with no mid-phase hacks that bled into other primitives. The "library-not-platform + conforming-to-primitives + defer-don't-workaround" discipline from Phase 11 continued to do work. Specific instances:

- M6's dissolution was a discipline win — we tested, saw it couldn't deliver, and dropped it rather than shipping a primitive without evidence.
- The visual-layer render-hook failure in M4 was deferred (not hacked around) until M5 provided the framing for how decoration panels should position.
- The `MenuKitSlot.getItem()` regression was caught because `/mkverify` holds the canonical contracts as a regression gate, not a best-effort check. That gate is now a cross-phase property.

---

## 5. Cross-reference map

Design docs (authoritative per-mechanism):
- `M1_PER_SLOT_STATE.md` — M1 design, resolved decisions, §10 locked positions
- `M4_VANILLA_SLOT_INJECTION.md` — M4 design + implementation findings section at top
- `M5_REGION_SYSTEM.md` — M5 design, §4A composition framing, §5.6 shared-constants pattern
- `M5_REGION_SPECS.md` — M5 authoritative region catalog (Trevor-authored)
- `M6_CLIENT_SIDE_SLOTS.md` — M6 dissolution record

Status + evidence:
- `Phase 11/POST_PHASE_11.md` — M-entry index, feature deferrals, updated in this commit
- `THESIS.md` — library principles including new §6
- `Phase 11/COMMON_FRICTIONS.md` — phase-spanning API-friction log (1.21.11 related)

Per-consumer evidence (Phase 11 output, still relevant):
- `Phase 11/inventory-plus/REPORT.md`
- `Phase 11/shulker-palette/REPORT.md`
- `Phase 11/final-consumers/REPORT.md`

---

## 6. Pattern distilled — what makes phase boundaries productive

Phase 11→12 worked because phase boundaries forced explicit documentation of what carries forward and what doesn't. Phase 12 inherits this:

- **Fresh writeups at the boundary.** This REPORT captures findings while they're live in memory. Three months from now, an agent or Trevor picking up Phase 13 reads this and gets the design rationale, not reconstructed guesses.
- **Design doc drift gets corrected at close-out.** M4's "Implementation findings" section exists because the doc drifted during execution; future readers start from the section that reflects shipped reality, not the approved design.
- **Advisor review is recorded via §10 "Resolved decisions" in each design doc.** The decisions plus the open-questions-they-came-from are both preserved — future readers see not just what was decided but what alternatives were considered.
- **`/mkverify all` catches primitive regressions at every phase boundary.** The 6th + 7th contracts added this phase ensure M5 region math and M1 persistence don't silently break during Phase 13 feature work.

Phase 13's close-out (eventually) should include analogous artifacts: per-feature REPORT sections, design-doc drift corrections for any F-entry that took an unexpected turn, and `/mkverify` additions if new contracts emerge.

---

## 7. Status of record

- **Phase 12 complete.** Primitives shipped, contracts holding, discipline intact.
- **M3 (MKFamily disposition)** still open — decision, not design. Resolvable in one advisor exchange; blocks no feature work.
- **Phase 13 feature work ready to begin.** Primary entry points: IP sort-lock migration to M1 (F1 + F2), gear + sandboxes migration to M5 regions (13a).
- **Pending Trevor asks:** gear position screenshot sign-off (M5 §5.1), F9 UI structure clarification (blocks 13b).
