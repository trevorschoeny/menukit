# Phase 14b — Close-out REPORT

**Status: complete.** M7 Storage Attachment Taxonomy shipped with four v1 factories + custom extension point + library-shipped drop-on-break semantics for block-scoped content. V5.7's Gate B unnarrowed end-to-end; Phase 12.5 finding #7 closed. Library-owned persistence discipline replaces IP-style hand-rolled attachment scaffolding as the canonical pattern going forward.

---

## Executive summary

Phase 14b's hypothesis: consumer mods needing slot-group content persistence had no library-provided path, leading to per-consumer hand-rolled attachment scaffolding (IP's `PlayerAttachedStorage` at ~140 LOC per variant × 3 variants). The library's existing `Storage` interface had save/load methods that the handler lifecycle never invoked — `BlockEntityStorage` was a Phase-3 TODO stub, `PlayerStorage` complete-but-unwired. Phase 12.5 filed this as finding #7.

M7 shipped as the canonical "where slot-group content lives" primitive:

- **Four v1 factories** (`ephemeral`, `playerAttached`, `blockScoped`, `itemAttached` + `itemContainer` convenience)
- **Custom extension point** (`StorageAttachment.custom(spec)`) for modded owner types + decorator-path escape hatch
- **Layered architecture** on M1's `PersistentContainerKey` infrastructure without merging — M1 keeps its typed-channel ergonomics, M7 gets content persistence
- **Fabric-attachment-backed auto-persistence** via `ItemContainerContents.CODEC` — reads/writes flow through `attachment.bind(owner)` directly, no explicit save/load lifecycle
- **Library-shipped drop-on-break** for `blockScoped` — fulfills vanilla-container-block contract (THESIS Principle 2 vanilla-substitutability)

Two design artifacts landed that shape Phase 15+:
- **Design doc** at `Mechanisms/M7_STORAGE_ATTACHMENT.md` (~750 lines, three advisor review rounds)
- **V5.7 validator scenario** unnarrowed: Gate B now verifies cross-session persistence for block-scoped grafted slots, and drop-on-break behavior matches vanilla

---

## What shipped

### Library (menukit)

**New files:**
- `core/StorageAttachment.java` — public API with five factories and `bind(owner)` / `bind(Supplier<owner>)` methods
- `core/attachment/StorageAttachments.java` — internal registry with CACHE (all attachments) + BLOCK_SCOPED_ATTACHMENTS (drop-on-break dispatch subset)
- `core/attachment/CustomAttachmentSpec.java` — public interface for modded owner types + decorator-path escape hatch
- `core/attachment/BlockScopedDropHandler.java` — internal helper that iterates block-scoped attachments on a BE and drops items via `Containers.dropItemStack`
- `mixin/M7BlockDropMixin.java` — library mixin at `BlockEntity.preRemoveSideEffects` HEAD that dispatches drop-on-break

**Modified:**
- `core/Storage.java` — javadoc refreshed (stale stub references removed, `StorageAttachment` named as canonical persistent path)
- `core/PlayerStorage.java` — trimmed from ~109 lines concrete class to marker interface (`PlayerStorage extends Storage`). Preserved because `MenuKitScreenHandler`'s shift-click routing uses it as a type marker to distinguish player-side vs container-side groups. M7's `playerAttached` factory returns a `PlayerFabricStorage` that implements this marker.
- `core/StorageContainerAdapter.java` — unchanged (bridges Storage → Container for slot construction)
- `screen/MenuKitScreenHandler.java` — javadoc updated to show M7-based builder example. No runtime lifecycle hooks needed: Fabric attachments auto-persist.
- `verification/ContractVerification.java` — added contract 8 (M7 round-trip probe, 6 cases) and updated javadoc / summary to "eight contracts"
- `menukit.mixins.json` — registered `M7BlockDropMixin`

**Deleted:**
- `core/BlockEntityStorage.java` (110 lines, Phase-3 TODO stub)
- `core/ItemStackStorage.java` (137 lines, CONTAINER-hardcoded — replaced by `itemContainer(int)` factory)
- `core/PersistentStorage.java` (30 lines, never invoked; consumers who want SavedData-backed storage use `custom(spec)` instead)

### Validator (V5.7 migration)

**Modified:**
- `scenarios/v5/V5_7GraftedStorage.java` — static `EphemeralStorage.of(1)` replaced with static `StorageAttachment.blockScoped("mkvalidator", "v5_7_grafted", 1)`. Added per-menu binding helpers (`adaptServerSide(be)` and `adaptClientSide()`) plus `GraftedSlot` marker subclass.
- `mixin/V5_7FurnaceMenuMixin.java` — per-BE binding via slot-0 container inspection (server = `BlockEntity`, client = `SimpleContainer`). Explicit `method = {...}` with `require = 2` to match both FurnaceMenu constructors (critical bug: unsigned `method = "<init>"` with `"defaultRequire": 1` only matched the 2-arg client-side ctor — see §Bugs-found-during-execution below).
- `scenarios/v5/V5_7Categories.java` — resolver filter changed from container-reference equality (`s.container == V5_7GraftedStorage.CONTAINER`) to `s instanceof V5_7GraftedStorage.GraftedSlot` marker class identity. Per-BE binding means each menu has a different Container instance, so reference equality no longer works.
- `scenarios/v5/V5_7Decoration.java` — state read changed from `V5_7GraftedStorage.STORAGE.getStack(0)` (static singleton lookup) to `Minecraft.getInstance().player.containerMenu.slots.stream()...instanceof GraftedSlot` (per-menu lookup via vanilla slot array). Javadoc updated to reflect Gate B unnarrow.

---

## Architecture decisions

### Layered, not merged (M1 + M7)

M1 persists per-slot metadata (typed-channel bags); M7 persists per-slot-group content (ItemStacks). Both use the `PersistentContainerKey` owner-resolution infrastructure M1 established, but their storage shapes differ:

| | M1 | M7 |
|---|---|---|
| Data | Sparse channel bags (`Tag` per channel per slot) | Dense content (`NonNullList<ItemStack>` per slot group) |
| Sync | Custom snapshot + update packets | Vanilla's slot-sync protocol |
| Ergonomics | Register channel once, call anywhere | Register attachment once, bind to owner instance at menu construction |

M1 could technically be expressed as a `StorageAttachment<Owner, SlotStateBag>` but would lose its typed-channel ergonomics. Kept layered.

### Principle-11 per-entry scope (advisor round 1)

Initially proposed six factories matching vanilla's six persistence surfaces, invoking Principle 11 exhaustive-coverage at the catalog level (M6's precedent). Advisor pushback: M6's 43-category catalog had uniform per-entry cost; M7's factories don't — `blockPortable` has distinct risk (Fabric-API hook shape unverified) and `entityAttached` has distinct edge-case surface (mount death, dimension crossing). Scope trimmed to four v1 factories + custom extension; the other two deferred to first-concrete-consumer trigger.

### Two lifecycle paths (advisor round 2)

Initial framing: "any menu using M7-backed storage needs a MenuKit-owned handler." Advisor pushback: too strong — `CustomAttachmentSpec` already provides a decorator-path escape hatch for consumers whose save/load doesn't route through a MenuKit handler. Documented both paths:

- **Default path:** handler-owned + factory → automatic via Fabric attachment lifecycle
- **Decorator path:** `CustomAttachmentSpec` + consumer-owned save/load trigger from their decorator mixin (server-side on `AbstractContainerMenu.removed(Player)` — fabric attachments must write server-side)

### Drop-on-break as part of the contract (post-smoke)

Phase 14b's first-draft design doc §4.6 said block-scoped content is "lost on break — intended, the block's gone." Trevor's smoke-test observation (items don't drop when furnace is broken) surfaced that this diverges from vanilla container-block semantics. Consumers mentally compare M7 `blockScoped` against vanilla chests/furnaces; silent-loss violates their expectation.

Advisor verdict: the architecture is sound; the contract was under-specified. Shipping library-owned drop-on-break via a `BlockEntity.preRemoveSideEffects` HEAD mixin fulfills the implied contract rather than adding a feature. Consumers who genuinely want silent-loss use `custom(spec)` with no drop hook — the escape hatch.

This is a THESIS Principle 7 landing (validate-the-product, not just primitives). Smoke testing caught what design review accepted.

---

## Bugs found during execution

Three real bugs surfaced and resolved:

### 1. Mixin container-detection off-by-38

V5_7FurnaceMenuMixin read `this.slots.get(this.slots.size() - 1).container` to distinguish server-side (BlockEntity) from client-side (SimpleContainer) construction. But by `<init>` RETURN, FurnaceMenu has 39 slots (3 furnace + 36 player inventory), and slot 38 is a player-inventory slot whose container is `Inventory` on both sides — useless for detection. Both sides fell through to `adaptClientSide()` → ephemeral. Fixed by reading slot 0 (the furnace input slot) instead.

### 2. Mixin unsigned @Inject only matched one constructor

With `method = "<init>"` unsigned + mixin config's `"defaultRequire": 1`, the injector matched the 2-arg FurnaceMenu constructor (client-side factory) but NOT the 4-arg constructor (server-side `FurnaceBlockEntity.createMenu` path). Server-side mixin never fired; server had 39 slots, client had 40 — vanilla silently ignored clicks on the nonexistent server slot 39, producing the "cursor duplicate" symptom Trevor reported.

Fixed by using explicit `method = {<2-arg sig>, <4-arg sig>}` with `require = 2` to force both matches at mixin-apply time. The sibling V5CraftingMenuMixin already used this signed pattern — should have been consistent from the start.

### 3. Mixin targeted non-existent `onRemove`

First drop-on-break implementation targeted `BlockBehaviour.BlockStateBase.onRemove`. Mixin apply failed at startup: method doesn't exist in 1.21.11. Minecraft 1.21.11 split the old monolithic `onRemove` into `Block.affectNeighborsAfterRemoval` (neighbor signal updates) and `BlockEntity.preRemoveSideEffects` (inventory drops etc.). Retargeted to `BlockEntity.preRemoveSideEffects` — same injection point vanilla's `BaseContainerBlockEntity` uses for its own drop-contents.

All three bugs were caught by in-game smoke test (Trevor's gameplay → log reading → fix → rebuild loop). Primitive-level contract probes passed throughout; the bugs lived at integration seams that only a consumer-shaped test could surface.

---

## V5.7 Gate B — unnarrow confirmed

Pre-14b V5.7 had Gate B narrowed to "within-session only" per Phase 12.5 finding #7. Phase 14b's M7 primitive unnarrows:

| Gate | Pre-14b | Post-14b |
|---|---|---|
| A (M4 alone) | PASS | PASS |
| B (M4 × M1) — within session | PASS | PASS |
| B (M4 × M1 × M7) — cross session | *Narrowed out* | **PASS** — disconnect/reconnect preserves grafted-slot contents |
| B (block break) | *Narrowed out* | **PASS** — item drops as entity |
| C (M4 × M8 via β) | PASS | PASS |
| D (M8 × M1 within-session) | PASS | PASS |

Smoke-test verdicts:
- Grafted slot holds item; close + reopen furnace → still there (within-session)
- Item placed, disconnect, reconnect, open furnace → still there (cross-session — the headline Gate B)
- Item placed, break furnace → item drops as ItemEntity alongside vanilla furnace contents
- Place new furnace at broken position → grafted slot empty (new BE, new attachment — correct)
- Red/green indicator reflects slot state (Gate D)
- `/mkverify` all contracts pass including new M7 probe (8/8 via aggregator)

---

## Process — advisor rounds

Three design rounds before implementation:

- **Round 1:** three pushbacks landed (scope trim to 4 v1 factories, two-path lifecycle, §6 stale line) + Trevor's scope-size call on deferral.
- **Round 2:** three doc-consistency findings (stale "six" references after scope trim, §4.6 deferred-type behavior tagging, §5.3 client/server confusion in example).
- **Round 3 (meta):** advisor rendered a process-calibration verdict alongside round 2 close: "rounds exist to resolve things the next round can't. Mechanical fixes fold inline — don't burn a round on them." Saved to personal memory as `feedback_advisor_round_calibration.md`.

Post-implementation smoke test surfaced a fourth architectural finding (drop-on-break) that needed a verdict (library-shipped auto-drop vs consumer-wired helper vs leave-as-is). Advisor approved library-shipped auto-drop as fulfilling an under-specified contract, not adding scope. Folded inline into the commit — no separate round.

Round count: three design rounds + one implementation-time architectural verdict. Four advisor-visible checkpoints; smaller than M7's inherent design complexity warranted.

---

## Design doc

`Mechanisms/M7_STORAGE_ATTACHMENT.md` — final shape:

- §1–3: purpose, consumer evidence (IP equipment/pockets, V5.7 grafted slot), scope
- §4: design decisions (owner type catalog, API shape, relationship to M1, custom extension, V5.7 finding resolution, sync + lifecycle including drop-on-break)
- §5: consumer API before/after (IP, V5.7, decorator-path example)
- §6: library surface (new/modified/deleted files)
- §7: migration plan (14b = V5.7, 15a = IP)
- §8: verification (automated probe + integration smoke test)
- §9: library vs consumer boundary
- §10: open questions — all resolved or deferred
- §11: non-goals / out-of-scope
- §12: summary

Seven original §10 open questions resolved; Q5 (block-portable Fabric-API hook) deferred with block-portable itself per §4.1 Principle-11 per-entry check.

---

## What didn't ship / deferred

Per §4.1 and §11:

- **`blockPortable` factory** (shulker-box-style item-form-traveling content) — deferred to first-concrete-consumer trigger. Carries distinct risk (Fabric-API hook shape for BE↔item attachment copy requires verification); no consumer today. §10 Q5 is the gate.
- **`entityAttached` factory** (donkey, minecart-with-chest) — deferred to first-concrete-consumer trigger. Edge-case surface (mount death, dimension crossing, entity despawn) wants real consumer evidence before commit.
- **M1 → M7 unification.** Technically feasible but would regress M1's typed-channel ergonomics. Kept layered.
- **IP migration.** Phase 15a work per PHASES.md — consumer migration follows library completion.

---

## Phase 14c entry conditions (met)

- M7 library primitive shipped + verified via `/mkverify` round-trip probe
- V5.7 cross-session persistence confirmed in dev client
- Drop-on-break behavior matches vanilla container-block contract
- Phase 12.5 finding #7 formally closed (M1 storage-layer wiring now provided by M7)
- Design doc reflects shipped reality including round 1/2/3 refinements and post-smoke drop-on-break addition

Phase 14c (M8 Layout Composition — Row/Column/Grid helpers) starts when kicked off. Per PHASES.md §14c, design-doc-first; layout HELPERS (not nested containers) per THESIS "Panel is the ceiling of composition."

---

## Diff summary

~13 files modified, 4 new, 3 deleted (stubs). ~460 LOC deleted, ~340 LOC added net of new files. Library surface net-shrunk by ~120 LOC while adding a substantial new primitive — removals (obsolete stubs + V5.7 scenario scaffolding) outweighed additions.

**Phase 14b closed.**
