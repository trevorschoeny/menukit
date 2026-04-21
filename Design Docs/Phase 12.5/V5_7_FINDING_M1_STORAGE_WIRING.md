# V5.7 finding — M1 storage-layer wiring incomplete

Seventh primitive gap caught by Phase 12.5's validator-consumer pattern. Surfaced during V5.7 scaffolding survey, after the V5.7 pre-check had already passed its scoped question.

**Status: filed-and-deferred.** V5.7 proceeds under narrowed Gate B/D scope (within-session only). Cross-session M4-grafted-slot persistence is Phase 13 work, pending either BE-storage wiring completion or a third consumer case triggering library lift of IP's attachment pattern.

---

## 1. The framing — not "M1 broken," layered architecture with one layer incomplete

M1 has a layered architecture. Accurate reading:

| Layer | Purpose | Status |
|---|---|---|
| `SlotIdentity` | Session-scoped identity — `(container ref, containerSlot)` tuple | **Solid.** Pre-check 1 verified stability for M4-grafted slots. Used operationally by inventory-plus's lock/annotation features. |
| `PersistentContainerKey` | Cross-session identity — player UUID, block pos + dimension, entity UUID, or modded identifier | **Solid as a type.** Sealed interface, five variants, well-specified in javadoc. |
| Storage implementations that bridge Identity + ContainerKey | Read/write an `ItemStack[]` tied to a container whose state survives session boundaries | **Incomplete.** Details in §2. |

The identity layer is solid. The storage-layer *wiring* — from `PersistentContainerKey` variant to an implementation that actually persists across disconnect/reconnect/world-rejoin — is where the gap lives.

This framing matters: "M1 broken" would be a library-alarm claim requiring immediate response. The actual situation is scoped — specific wiring work deferred from the Phase 3 migration, filable as a completion task rather than a regression.

## 2. Evidence — where the storage layer is stub vs. working

### `BlockEntityStorage` — explicit Phase 3 TODO stub

[BlockEntityStorage.java:56-59](menukit/src/main/java/com/trevorschoeny/menukit/core/BlockEntityStorage.java:56):

```java
// Fallback storage — block entity resolution happens at the handler
// level during binding. Direct block entity access will be wired
// in Phase 3 when the handler owns the binding lifecycle.
return fallback[localIndex];
```

[BlockEntityStorage.java:74-76](menukit/src/main/java/com/trevorschoeny/menukit/core/BlockEntityStorage.java:74):

```java
@Override
public void markDirty() {
    // Phase 3: will mark the block entity dirty via the level
}
```

Items are stored in a local `fallback[]` array. The class implements `PersistentStorage` (with `save`/`load` for SavedData integration), but the library's handler lifecycle *doesn't call save/load on storages* (see §2.3). So the implementation is an island — complete as a type, operationally a no-op for persistence.

### `PlayerStorage` — complete as a type, not auto-wired

[PlayerStorage.java](menukit/src/main/java/com/trevorschoeny/menukit/core/PlayerStorage.java) has full `save(ValueOutput)` / `load(ValueInput)` with dirty tracking. But nothing in the library calls these — the class is a storage shape waiting for a lifecycle owner.

### The library's handler lifecycle doesn't invoke Storage save/load

[PlayerAttachedStorage.java:37-40](inventory-plus/src/main/java/com/trevorschoeny/inventoryplus/attachments/PlayerAttachedStorage.java:37) — IP's reading of the library, verified by Layer 0 of its Phase 11 refactor:

> Implements plain Storage (not PersistentStorage) — Layer 0 verified that MenuKit's handler lifecycle doesn't call save/load on storages, so persistence is handled exclusively by Fabric's attachment lifecycle.

That is: the library's `PersistentStorage.save` / `load` methods exist as contract, but no library code invokes them. Consumer-side persistence today means bypassing `PersistentStorage` and hand-rolling attachments directly.

### Inventory-plus sidesteps the library entirely

IP's `PlayerAttachedStorage` implements plain `Storage` — NOT `PersistentStorage`. Its factories (`forEquipment`, `forPockets`, `forPocketSlice`) wrap Fabric attachments (`EquipmentData`, `PocketsData`) and expose them as `Storage`. Persistence + sync happen transparently via Fabric's attachment lifecycle, entirely outside the library's `PersistentStorage` surface.

This works for IP. It's also ~140 lines of IP-private scaffolding per-container-type. Each consumer wanting cross-session persistence reinvents this shape.

## 3. What "completing the wiring" would require

Two plausible library-side shapes:

### Option A — complete `BlockEntityStorage`

Finish the deferred Phase 3 work:
- BE-anchored storage reads/writes the live block entity's inventory (or a companion attachment) instead of the local `fallback[]` array.
- `markDirty()` actually marks the BE dirty, triggering vanilla's BE save pipeline.
- Chunk unload/reload rehydrates state via the BE's save/load pipeline (already part of vanilla's BE contract).
- Handler lifecycle owns the binding — resolving BE on menu open, snapshotting state on menu close.

Rough scope: 150–250 lines across BlockEntityStorage, a mixin or helper for BE side-data (if the grafted slot's items don't live inside the existing vanilla BE's own inventory), handler-lifecycle hooks, and tests. Real design surface on the BE-side-data question: does `BlockEntityStorage` extend the vanilla BE's inventory, or does it live in a parallel BE-attached side-car? Depends on whether the grafted slot's items are "part of the BE's canonical contents" (extending) or "a mod's sidecar" (side-car). Design-doc ambiguity, not resolved by existing docs.

### Option B — lift IP's attachment pattern into the library

Promote `PlayerAttachedStorage`'s shape to a library primitive (`LibraryAttachedStorage` or similar):
- Generic over the attachment's backing type.
- Factory methods for common shapes (player-attached, BE-attached).
- Documented pattern for consumer-registered attachments (extractor + markDirty functions).

Rough scope: 100–150 lines of new library code + migration of IP's usage to the library primitive + V5.7 usage of the same. IP's migration is mostly mechanical.

### Why neither is Phase 12.5 scope

Both options are primitive-completion work, not the 20-line additive pattern of β (gap #6). The `// Phase 3` comment in BlockEntityStorage has been sitting since the original migration — this is deferred TODO work, not a phase-12.5-discovered gap that's cheap to close in-phase.

**Rule of Three applies.** Concrete consumers wanting M1 × M4 cross-session storage today:
1. IP's `PlayerAttachedStorage` (F8 equipment + F9 pockets) — one case, operational.
2. V5.7 — two cases, wanting it.

Need a third real consumer case before library lift. Phase 13 scope: either consumer demand triggers the lift, or the BE-wiring completion lands as a general library-completion task.

## 4. Impact on V5.7 — Gate B and Gate D narrow to within-session

V5.7's original four-gate briefing:
- **Gate A** — M4 alone. Unchanged.
- **Gate B** (original) — M4 × M1: item survives menu close/reopen, disconnect/reconnect, world rejoin.
- **Gate C** — M4 × M8: SlotGroupContext decoration fires on grafted slot via β. Unchanged.
- **Gate D** (original) — M8 × M1: decoration reflects persistent state during first-frame pre-sync.

Under (v) narrowing:
- **Gate B (narrowed)** — M4 × M1 within-session only: item survives menu close and reopen in the same session. Disconnect/reconnect and world rejoin are NOT tested. Storage is `EphemeralStorage` (same as V5.6) or `PlayerStorage` with no attachment wiring.
- **Gate D (narrowed)** — M8 × M1 within-session: decoration reads within-session state (ItemStack in the grafted slot via `Slot.getItem()`).

The scenario's javadoc explicitly names the narrowing and points at this finding. V5.7 still tests three-way intersection at within-session scope — the "natural home" claim at M8 §10.560 is validated by Gate C, and the β `extend` API is exercised end-to-end.

Cross-session persistence for M4-grafted slots is explicitly out of V5.7's scope, tagged as Phase 13 pending this finding's resolution.

## 5. Pre-check discipline — scope boundary note

Pre-check 1 asked: *"Does `SlotIdentity` produce stable keys for M4-grafted slots?"* Answer: **yes, within-session.** The pre-check answered its scoped question correctly.

This finding (storage-layer wiring) is a **separate layer one step down** — a storage-implementation question, not an identity-stability question. Pre-check 1 did not ask about storage, and correctly didn't report on it.

**Scope boundary generalizes:** pre-checks verify scoped, narrow questions — typically "does this library API return the expected shape under condition X?" They do not audit downstream layers the scoped question builds on. Primitive gaps one layer down from the pre-check's question surface during *scaffolding*, not *pre-check reading*.

Naming this explicitly matters for future pre-check discipline: don't demand that pre-checks preemptively audit every downstream layer, and don't self-critique a correct pre-check answer when a downstream stub surfaces later. The two are different classes of finding.

**For the close-out REPORT.** Gap #6 (β `extend`) surfaced during pre-check *reading* — a design-claim-to-source traceability finding, same layer as the pre-check's question. Gap #7 (this one) surfaced during *scaffolding survey* — a downstream-layer-stub finding, one layer deeper than the pre-check's question. Distinct sub-shapes within the "pre-check-surfaced" category.

## 6. Close-out REPORT slotting

Phase 12.5's primitive-gap catalog updated:

| # | Gap | Surfaced | Disposition |
|---|---|---|---|
| 1 | `ScreenPanelAdapter` completeness (V4) | scenario execution | fixed in-phase |
| 2 | Tooltip layering (V2 `MenuKitPanelRenderMixin`) | scenario execution | fixed in-phase |
| 3 | Chrome awareness (M7) | scenario execution | fixed in-phase |
| 4 | `PanelBuilder.pairsWith` exposure (V5.1) | scenario execution | fixed in-phase |
| 5 | Recipe-book render pipeline coverage (V5.6) | scenario execution | fixed in-phase |
| 6 | `SlotGroupCategories.extend` (β) | pre-check reading | fixed in-phase |
| 7 | M1 storage-layer wiring (this finding) | scaffolding survey | **filed-and-deferred to Phase 13** |

Two distinct pre-check-surfaced shapes (#6 reading, #7 scaffolding-survey), one fixed-in-phase and one filed-deferred. Both are Phase 12.5 deliverables — evidence that the validator-consumer pattern catches incompleteness beyond what scenario execution alone surfaces, and that pre-check discipline has a productive scope boundary.

---

**Next:** V5.7 scaffolding resumes under the narrowed Gate B/D scope. FurnaceMenu target unchanged. Consumer storage uses `EphemeralStorage` (consistent with V5.6's pattern); scenario javadoc names the narrowing + points here.
