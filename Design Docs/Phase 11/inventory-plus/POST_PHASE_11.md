# Phase 11 IP — Post-Phase-11 Deferrals

Accumulator of deferred work surfaced during IP's Phase 11 refactor. Per the 2026-04-15 phase-arc reframing, Phase 11 rebuilds consumers as far as current MenuKit allows; features needing missing primitives defer to Phase 13; the primitives themselves defer to Phase 12 for design + shipping.

Organized into two sections:

- **Features** — what gets delivered. Phase 13 implements against Phase 12's primitives.
- **Mechanisms** — what gets designed. Phase 12 builds against multi-consumer evidence accumulated in Phase 11.

One mechanism may enable multiple features. Cross-references run both ways: features point to the mechanism that enables them; mechanisms list the features they unlock.

By end of Phase 11, this file (plus similar per-mod files for shulker-palette / sandboxes / agreeable-allays) is Phase 12's design input and Phase 13's delivery checklist.

---

## Features (Phase 13 delivery checklist)

### F1. Persistent player-slot lock state across sessions

**Trigger.** Bucket C Q1 filed "persistent player-slot lock state" as a post-Phase-11 reconsideration. Activation: Trevor's Layer 1 Group A verification (2026-04-15) — *"they should persist between play sessions"*; *"lock state resets. Which is undesired."*

**Clarification from git archaeology.** Old pre-Phase-5 IP also didn't persist — `MKSlotStateRegistry` was a plain `IdentityHashMap<Slot, MKSlotState>` with `cleanupMenu(menu)` on close. `MKSlotBackendState.sortLocked` was a plain boolean. User expectation is a feature wish, not a regression from the refactor.

**Current Phase 11 state.** Lock state keyed by `SlotIdentity` (M2) on both client + server. For player-inventory slots, `Player.getInventory()` is stable within a JVM session → locks survive menu transitions within a session. Disconnect/relog loses state (container references die with JVM).

**What Phase 13 delivers.** Player-inventory lock state survives disconnect/relog.

**Mechanism needed.** M1 — unified per-slot state primitive.

**Phase 13 implementation sketch.** Adapter layer in IP's `locks/` package: look up via `Player.getInventory() + slot.getContainerSlot()`, store via M1's persistent-player-attachment storage. ~30 lines once M1 ships.

---

### F2. Chest-slot lock state visible across menu reopens

**Trigger.** Layer 1 Group B1 verification (2026-04-15). User reported: *"lock doesn't stay with the world containers like chests. When I close the chest, the lock disappears."*

**Root cause.** Server-side state persists correctly via `SlotIdentity` (block entity reference stable across close/reopen). Client-side state is ephemeral — vanilla reconstructs remote-container proxy `Container` references per menu-open on the client. Client's `SlotIdentity` doesn't match across opens; `ClientLockStateHolder` loses state; no S2C sync exists to refresh from server.

**What Phase 13 delivers.** Chest / shulker / hopper / dispenser lock state visible on client across menu reopens (within a session; cross-session is F1's scope).

**Mechanism needed.** M1 — unified per-slot state primitive (covers server→client sync on menu open).

**Note.** Previously approved mid-Phase-11 as an exception (IP-specific `SortLockInitS2CPayload` packet). **Unwound** per 2026-04-15 advisor reframing — chest-lock sync becomes feature-deferred, not exception-implemented. Exception clause closed for new business; SlotIdentity was the last exception.

---

### F3. Full-lock (Ctrl+click) feature

**Trigger.** Bucket C Q1 noted `isLocked()` as dead code in old IP (the full-lock behavior from pre-Phase-5 `MKSlotClickBusMixin` was never reimplemented post-demolition). Filed for post-Phase-11 evaluation.

**What Phase 13 delivers.** Ctrl+click a slot to fully lock it — blocks ALL interactions, not just sort + shift-click-in. Semantically separate from sort-lock.

**Mechanism needed.** M1 for state storage if full-lock should persist on the same surface as sort-lock. Standalone otherwise.

**Note.** Feature addition post-Phase-11; not preservation of old behavior (old behavior was already non-functional by Phase 5).

---

### F4. Sort consolidation

**Trigger.** Layer 1 Group B1 click-sequence pivot (2026-04-15). Sort runs client-side via vanilla click semantics; consolidation (merging same-item partial stacks into max-size stacks) has complicated click-protocol interactions — vanilla's `PICKUP_ALL` collects across the whole menu, not per-region. Permutation-only sort ships; consolidation defers.

**What Phase 13 delivers.** Sort consolidates partial stacks of the same item into max-stack chunks, not just reorders.

**Mechanism needed.** None — feature-complexity deferral, not primitive-blocked. Phase 13 implements against current MK with careful click-sequence design.

---

### F5. Creative-mode sort

**Trigger.** Layer 1 Group B1 verification (2026-04-15). User reported sort doesn't work in creative inventory.

**Root cause.** Creative uses a fundamentally different slot-click path. `CreativeModeInventoryScreen` routes item manipulation through `CreativeInventoryActionC2SPacket` rather than `ServerboundContainerClickPacket`. The click-sequence pivot's `menu.clicked(...)` invocations work in survival but don't propagate to the client's `ItemPickerMenu` view in creative.

**What Phase 13 delivers.** Sort keybind works in creative inventory.

**Mechanism needed.** None — feature-scope deferral. Phase 13 adds a creative-specific code path in `SortAlgorithm` + `KeybindDispatch` emitting `CreativeInventoryActionC2SPacket`. ~50-80 lines of IP-side work.

---

### F6. Creative-mode bulk-move (if applicable)

**Trigger.** Group B2 planning — if bulk-move's shift+double-click detection + click-sequence approach doesn't work in creative (same reason as F5, creative's separate packet path), the feature is deferred there too.

**What Phase 13 delivers.** Bulk-move works in creative inventory.

**Mechanism needed.** None — same creative-specific code path as F5.

**Note.** Placeholder — confirm during Group B2 verification whether this defer is needed. Remove entry if creative bulk-move works via current architecture.

---

## Mechanisms (Phase 12 design input)

### M1. Unified per-slot state primitive

**Design driver.** Consumer mods need a way to attach per-slot state with these properties:

- Stable identity across menu-instance churn (fresh Slot objects per open).
- Stable identity across container-instance churn (client-side proxy containers for remote containers are fresh per open).
- Persistence across disconnect/relog (for state users expect to survive sessions).
- Server-authoritative with client sync — server holds truth; clients receive state on menu open + on mutation.
- Handles the container-type zoo cleanly: player inventory, block-backed (chests, hoppers, etc.), per-player non-world (ender chest, IP equipment + pockets), entity-backed, modded containers.

**Partial mechanisms already shipped.**

- **M2 (`SlotIdentity`)** handles server-side stable identity via Container-reference equality. Doesn't handle client-side identity for remote containers (client's Container is ephemeral). Doesn't handle persistence or sync.

The full primitive builds on SlotIdentity's shape + adds sync + persistence.

**Candidate design surface (Phase 12 to refine).**

- **Identity schemes per container type.** Each container-type category has its own stable-identity scheme:
  - Player inventory → `(player UUID, inventory-slot-index)`. Stable across sessions.
  - Block-backed containers → `(BlockPos + dimension ID, container-slot-index)`. Stable while block exists.
  - Per-player non-world containers (ender chest, IP-style extra containers) → `(player UUID, container-kind, container-slot-index)`.
  - Entity-backed containers (horse/llama chest) → `(entity UUID, container-slot-index)`. Loses on entity death / chunk unload.
  - Modded containers → registered via a consumer hook that provides identity-scheme + persistence strategy.
- **Authoritative server + sync-on-open.** Server holds state keyed per scheme. When a player's `containerMenu` becomes a menu whose slots have attached state, server sends an S2C packet with `(client-slot-index → state)` pairs. Client populates a local cache keyed by its local-side identity. Mutations on client emit C2S packets that the server applies + re-syncs to any other clients observing the same container.
- **Persistence storage per scheme.** Player-inventory and per-player state → Fabric attachments on the Player entity. Block-backed → block entity attachment (custom NBT or data component). Entity-backed → entity attachment.
- **Modded container hook.** `SlotStateScheme` interface: given a Container or block entity or entity, return the stable identity. Consumers register schemes for their own container types.

**Features unlocked.** F1 (persistent player-slot lock state), F2 (chest-slot lock persistence). Likely expands as remaining Phase 11 mods surface similar needs.

**Multi-consumer evidence accumulator.** As shulker-palette / sandboxes / agreeable-allays refactor, their per-slot / per-item state needs feed into this entry. Phase 12 designs once against the full evidence.

---

### M2. SlotIdentity — shipped 2026-04-15 as Phase 11 exception

**What shipped.** `com.trevorschoeny.menukit.core.SlotIdentity` — record `(Container container, int containerSlot)` + static factory `SlotIdentity.of(Slot)`. Tightly scoped: primitive only, no registry, no service, no persistence.

**Why it shipped mid-Phase-11.** Advisor-approved exception: purely additive library change, demonstrably needed by IP's Layer 1 work, tightly scoped. This exception clause is now **closed** — SlotIdentity was the only exception; future primitive needs defer to Phase 12.

**What it enables now (Phase 11).** Server-side stable slot identity (sufficient for `ServerLockStateHolder` correctly persisting chest locks via block-entity reference). Client-side stable identity for player-inventory slots (`Player.getInventory()` reference is stable). IP's `IPRegionGroups.canonicalSlot(menu, slot)` for cross-menu routing.

**What it doesn't do.** Client-side stable identity for remote containers (fresh `SimpleContainer` proxies per open). No persistence, no S2C sync, no modded-container registration. All of that is M1's scope.

**Phase 12 relationship.** M1 builds on SlotIdentity. M1 may also motivate further library primitives if Phase 11 evidence supports them — file as sub-mechanisms under M1 if needed.

**Features currently using it.** F1 (partial — within-session portion), F2 (partial — server-side portion).

---

### M3. MKFamily removal

**Trigger.** User expressed intent (2026-04-15) to remove `MKFamily` and the mod-family concept from MenuKit entirely. Per advisor, library-level removal shouldn't land mid-Phase-11 (cascades through every consumer during refactor); revisit post-Phase-11.

**Type.** Mechanism removal (cleanup), not mechanism addition. Unlike M1 which enables deferred features, M3 would require consumer-side refactor without unblocking new capabilities.

**What Phase 12 decides.** Keep `MKFamily` as-is, or remove it. If remove:

- Each consumer mod handles its own ModMenu integration standalone.
- Each consumer ships its own config screen instead of composing into a family config.
- Shared options (auto-restock, autofill, etc.) need a new home — perhaps per-consumer config files with no cross-mod aggregation, or a shared options library that's not family-specific.

**What Phase 13 implements.** Consumer-side refactor to match Phase 12's decision.

**Features affected.** Settings gear + config category in IP (Phase 11 Group B5 ships against current MKFamily). All three other consumer mods' settings integrations.

**Not currently blocking any feature.** M3 is cleanup; its resolution doesn't unblock Phase 11 work. Phase 13 touches consumer code only if M3 resolves to "remove."

---

## How to use this file

**Feature entries** capture:
- Trigger (why we know it's wanted)
- Current Phase 11 state (what works now, what's missing)
- What Phase 13 delivers
- Mechanism needed (or "none" for complexity/scope deferrals)
- Phase 13 implementation sketch

**Mechanism entries** capture:
- Design driver (why the primitive is needed)
- Partial mechanisms already shipped (cross-references to related mechanism entries)
- Candidate design surface (enough detail that Phase 12 doesn't redesign from scratch)
- Features unlocked (cross-references)
- Multi-consumer evidence accumulator section — append notes as remaining consumer mods surface similar needs

**Cross-references run both ways.** Features point to the mechanism that enables them; mechanisms list the features they unlock.

**Stub-site code comments reference entries by name.** Pattern:

```java
// DEFERRED to Phase 13 — see POST_PHASE_11.md "F2 Chest-slot lock state visible across menu reopens"
// Server-side state persists via SlotIdentity; client-side sync needs M1.
```

Cheap to write, expensive to miss when Phase 13 picks up the work.

**Scope across mods.** This file is IP-specific. shulker-palette / sandboxes / agreeable-allays maintain their own `POST_PHASE_11.md` per-mod (under each consumer's `Design Docs/Phase 11/<mod>/` directory). When a feature or mechanism surfaces in more than one consumer, cross-reference across files rather than duplicating. Phase 12 reads across all four.

**When an entry resolves.** Features resolved in Phase 13 → delete the entry (or move to a "shipped" archive section). Mechanisms shipped in Phase 12 → update to "shipped" status, keep as historical record with cross-references to the features they enabled.
