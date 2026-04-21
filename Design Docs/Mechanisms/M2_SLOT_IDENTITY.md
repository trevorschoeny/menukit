# M2 — SlotIdentity

*(Originally documented within `Phases/11/POST_PHASE_11.md` Mechanisms section; promoted to its own Mechanisms/ entry during Phase 13 doc reorganization for parity with M1, M3, M4, M5, M6.)*

**Phase 11 mechanism — identity-shaped.**

**Status: shipped during Phase 11 inventory-plus refactor.**

**Enables:** M1 (per-slot persistent state), inventory-plus's lock + annotation features, any consumer needing cross-menu stable slot identity.

---

## 1. Purpose

Consumer mods need a way to refer to "the same slot" across menu transitions. The same player-inventory slot is exposed in `InventoryMenu`, `ChestMenu` (when a chest is open), `ShulkerBoxMenu`, etc. — all backed by the same `Container` instance with the same `getContainerSlot()` index. A consumer wanting per-slot state needs identity that survives menu close/reopen, container-swap, and screen transitions.

`SlotIdentity` provides this with a zero-dependency primitive.

## 2. Shape

```java
public record SlotIdentity(Container container, int containerSlot) {

    public static SlotIdentity of(Slot slot) {
        return new SlotIdentity(slot.container, slot.getContainerSlot());
    }
}
```

The identity is `(container reference, slot.getContainerSlot())`. Both components are direct vanilla concepts:

- `Container` is the backing storage (player inventory, block-entity inventory, etc.).
- `getContainerSlot()` returns the slot's stable position within the container — distinct from `Slot.index` which is the slot's position within the menu's `slots` list.

## 3. Properties

- **Cross-menu stable.** Player-inventory slot 9 has the same `SlotIdentity` whether observed from `InventoryMenu` or from `ChestMenu`'s player-inventory section.
- **Mod-graft resilient.** Adding slots to a menu via M3 (vanilla slot injection) shifts `slot.index` (menu-list position) but NOT `getContainerSlot()` (container-list position). M3-grafted slots get their own stable identity rooted in their backing container reference.
- **Session-scoped.** Container references die with the JVM. `SlotIdentity` is stable WITHIN a session; persistence across sessions uses M1's `PersistentContainerKey` (a heavier primitive layered above SlotIdentity).
- **Equality + hash.** Standard record semantics. Use as a `Map` key without custom hashing.

## 4. Use cases

- **In-flight per-slot state.** Sort-lock highlight, hover state, ephemeral marks. Consumer holds `Map<SlotIdentity, T>`; values die with the session naturally.
- **Cross-menu queries.** "Does this slot have a marker, regardless of which menu I'm viewing it through?"
- **M1 persistence layer.** M1 uses SlotIdentity at the runtime/read layer; persistence keys through `PersistentContainerKey` separately. The two layers are deliberately distinct.

## 5. Library vs consumer boundary

**Library provides:** the `SlotIdentity` record + factory. Stays minimal — no extra methods, no helpers.

**Consumers provide:** the policy (what state, what semantics). M1 is the library-shipped consumer of SlotIdentity; consumer mods register their own state-keyed maps using SlotIdentity directly.

## 6. Related primitives

- **M1** (per-slot persistent state) — builds on M2. Cross-session persistence; uses `PersistentContainerKey` for its identity layer above SlotIdentity.
- **`SlotPositionAccessor`** — Phase 11 utility making `Slot.x`/`Slot.y` mutable for runtime-position updates. Separate primitive, not part of SlotIdentity.

## 7. Status

Shipped during Phase 11 inventory-plus refactor as the foundation for IP's lock state. M1 (Phase 12) built persistence on top. No subsequent changes; the shape is intentionally minimal and stable.
