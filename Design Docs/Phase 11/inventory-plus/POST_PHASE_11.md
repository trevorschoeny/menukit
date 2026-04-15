# Phase 11 IP — Post-Phase-11 Demand Signals

Reconsideration triggers filed during Bucket C triage that activated in Phase 11 testing or surfaced as new demand signals during implementation. Each entry captures the trigger, the evidence that activated it, and a sketched design path — enough that post-Phase-11 work can pick up without re-deriving context.

This file accumulates demand signals from IP specifically. The other three Phase 11 consumer mods (shulker-palette, sandboxes, agreeable-allays) may maintain their own `POST_PHASE_11.md` files or share this one — decided when their turn comes.

---

## 1. Persistent player-slot lock state

**Trigger.** Bucket C Q1 filed "persistent player-slot lock state" as a post-Phase-11 reconsideration trigger at the close of audit review. Activation condition: users report wanting lock state to persist across menu open/close.

**Activation evidence.** Trevor's in-dev verification of Layer 1 Group A (2026-04-15) reported: *"they should persist between play sessions"* and *"lock state resets. Which is undesired."* Concrete user demand from the mod's author/primary user. Not speculative.

**Clarification from git archaeology.** The pre-Phase-5 IP tree did **not** persist lock state either. `MKSlotStateRegistry` (the storage the old IP used) was a plain `IdentityHashMap<Slot, MKSlotState>` with a `cleanupMenu(menu)` method called on close. `MKSlotBackendState.sortLocked` was a plain boolean field with no save/load methods. So:

- User's expectation (persistent lock state) is a **feature wish**, not a regression from the Phase 11 refactor.
- The old IP was also session-scoped; the user was living with the same behavior and wanting it fixed.
- Phase 11's "preserve user-visible behavior" discipline doesn't require implementing persistence; the current session-scoped behavior matches what shipped.

This clarification matters because it keeps the scope-creep conversation honest: persistence is a feature addition, not a bug fix.

## Sketched design path

### Scope decision: player-inventory slots only

The slot-identity-across-sessions question is the gnarly part. Two categories:

**Player-inventory slots — clean identity.** `InventoryMenu` slots 0–8 (hotbar), 9–35 (main), 36–39 (armor), 40 (offhand) have stable meaning within a single player's inventory across sessions. Slot index 15 is always "main inventory row 1 column 7" for the same player. Persistence keyed by `(player UUID, inventory slot index)` works unambiguously.

**Container slots — no stable identity in vanilla.** A chest at `(x, y, z)` could be moved by a piston, destroyed and replaced, or the block entity could change type (chest → trapped chest). Block-position-keyed lock state has failure modes with no clean answer. Keeping a `WeakHashMap<BlockPos, Set<Integer>>` accumulates garbage and gives wrong results when containers move. Shulker-box slots are worse (no position at all — item in player inventory that happens to have a container component).

**Scope decision: persist lock state on player-inventory slots only. Chest/shulker/hopper/dispenser slots stay session-scoped.** This matches where "I expect this to persist" intuition actually makes sense — the player knows their own inventory layout persists; they don't expect a specific chest slot to remember its lock when they didn't remember locking that chest.

If post-Phase-11 evaluation decides container-slot persistence is worth the complexity, it's a separate feature on top of the player-inventory-slot work. Same design note section, just unresolved.

### Architectural shape: Fabric attachment

Sibling to `EquipmentData` + `PocketsData`:

```java
// attachments/LockStatesData.java
public final class LockStatesData {
    // Bitmask over InventoryMenu slot indices 0..40 (41 total).
    // 41 bits fit in one long with room to spare.
    public static final int MAX_SLOTS = 41;
    private long bitmask;

    public boolean isLocked(int inventorySlotIndex);
    public void setLocked(int inventorySlotIndex, boolean locked);
    public void toggle(int inventorySlotIndex);

    public static final Codec<LockStatesData> CODEC = Codec.LONG.xmap(
            LockStatesData::new, d -> d.bitmask);
    public static final StreamCodec<ByteBuf, LockStatesData> STREAM_CODEC =
            ByteBufCodecs.VAR_LONG.map(LockStatesData::new, d -> d.bitmask);

    public void markDirty(Player player) {
        player.setAttached(IPPlayerAttachments.LOCK_STATES, this);
    }
}
```

Registration adds to `IPPlayerAttachments`:

```java
public static final AttachmentType<LockStatesData> LOCK_STATES =
        AttachmentRegistry.<LockStatesData>builder()
                .persistent(LockStatesData.CODEC)
                .syncWith(LockStatesData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
                .initializer(LockStatesData::new)
                .buildAndRegister(Identifier.fromNamespaceAndPath("inventory-plus", "lock_states"));
```

### Lock-state read/write shape

The current `ClientLockStateHolder` + `ServerLockStateHolder` use `Slot` identity (session-scoped). Post-Phase-11 conversion:

**For player-inventory slots:** lookup happens via the slot's `container instanceof Inventory` check, then `slot.getContainerSlot()` gives the inventory slot index (which maps stably across sessions). Read from / write to the player's `LockStatesData` attachment.

**For non-player slots:** keep the existing `Slot`-keyed `WeakHashMap` lock state. No attachment. Session-scoped.

Unified accessor in the refactored holders:

```java
public static boolean isSortLocked(Player player, Slot slot) {
    if (slot.container instanceof Inventory) {
        return IPPlayerAttachments.getOrInitLockStates(player)
                .isLocked(slot.getContainerSlot());
    }
    // Fall through to session-scoped WeakHashMap for non-player slots.
    return WEAK_MAP.getOrDefault(slot, DEFAULT).isSortLocked();
}
```

The server-side `SortLockC2SPayload` handler branches the same way: player-inventory slot → attachment; other slot → session map.

### Sync behavior

Fabric attachment sync happens automatically on `setAttached`. The current `SortLockC2SPayload` sends the slot index + lock value; post-Phase-11, the server-side handler decides attachment-vs-session-map based on the slot kind, writes to attachment, which syncs to the client. The client reads from its local synced copy.

No additional client-side sync work. Fabric does the plumbing.

### Estimated work

Small-to-medium:
- ~100 lines for `LockStatesData` (mirrors `PocketDisabledData`'s shape)
- ~40 lines of attachment registration in `IPPlayerAttachments`
- ~60 lines of dual-path read/write in the holders
- ~20 lines of branching in the server-side packet handler
- Total: ~220 lines of code + maybe 30 lines of test via `/ip_attach_probe` extension

**Dependencies.** None beyond the existing attachment layer. Doable any time post-Phase-11.

**Risks.** One subtlety — client-side `ClientLockStateHolder` needs to know "is this Slot a player-inventory slot" to decide which store to read. The check is cheap (`slot.container instanceof Inventory`), but it's a per-read check, not a one-time classification. Also: during a chest-open session, player-inventory slots appear in the `ChestMenu` as different `Slot` instances from their `InventoryMenu` counterparts, but they still have the same `container instanceof Inventory` + `getContainerSlot()` identity, so the attachment-keyed lookup gives the same answer. Verified mentally; worth empirical test during implementation.

---

## 2. SlotIdentity — shipped mid-Phase-11 as an explicit discipline exception

**Trigger.** Group B1 verification surfaced two related UX issues: sort in creative inventory didn't work; lock state disappeared on chest open/close + on creative↔survival transitions. Root cause analysis identified missing cross-menu stable slot identity — vanilla creates fresh Slot instances per menu open even when backing storage is shared.

**Advisor decision (2026-04-15).** Ship `com.trevorschoeny.menukit.core.SlotIdentity` as a MenuKit library primitive now, as an explicit exception to the "no library changes mid-consumer-mod work" discipline. Rationale: additions are asymmetric to removals. MKFamily removal (filed above) would cascade through every consumer during their refactors; SlotIdentity addition is purely additive — consumers that don't use it are unaffected. And IP's need is demonstrated. Doing a consumer-side workaround and then redoing it as a library primitive later would be throwaway work.

**Shipped shape.** Record `SlotIdentity(Container container, int containerSlot)` + static factory `SlotIdentity.of(Slot)`. Nothing else — no registry, no state-management service, no cross-menu enumeration helpers, no persistence. Consumers manage their own state keyed by the primitive; library-not-platform discipline holds.

**Established pattern.** Library additions mid-Phase-11 are acceptable iff:
1. Purely additive — doesn't disturb existing consumers.
2. Demonstrably needed by current consumer-mod work (not speculative).
3. Tightly scoped — a primitive, not a service.

Library removals or changes to existing surfaces remain out of scope until post-Phase-11 (see MKFamily entry).

**Reconsideration triggers still active** (tracked here for post-Phase-11):

- If shulker-palette, sandboxes, or agreeable-allays independently reach for SlotIdentity-keyed state during their refactors, that's multi-consumer evidence for further slot-related primitives (SlotIdentity-keyed library helpers, cross-menu slot enumeration, etc.). IP's usage alone is single-consumer evidence — keeps the primitive small.
- IP's `IPRegionGroups.canonicalSlot(menu, slot)` — finds the slot in a different menu with matching SlotIdentity. Currently IP-side only. If other consumers need the same pattern, promote to MenuKit as `SlotIdentity`-keyed lookup helper.

---

## How to use this file

- Each demand signal gets its own section with:
  - Trigger (what Bucket C / equivalent earlier process filed)
  - Activation evidence (what firing looked like)
  - Clarification notes (e.g., git archaeology, scope corrections)
  - Sketched design path (enough for later implementation not to re-derive)
  - Estimated work
- Keep entries short. Full design docs land when implementation actually begins.
- If a signal activates and is deferred, update its entry with the activation date + evidence.
- If a signal activates and gets implemented mid-phase (in deliberate scope expansion), remove the entry after the implementation lands.
