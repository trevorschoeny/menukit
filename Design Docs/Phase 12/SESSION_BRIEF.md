# Phase 12 — Session Brief (Phase 13 Handoff)

Compaction-safe handoff anchor. Read this first when picking up Phase 13 feature work.

---

## Read order

1. **This doc** (Phase 12/SESSION_BRIEF.md) — handoff context, first action, open questions.
2. **`Phase 12/REPORT.md`** — what Phase 12 shipped, the architectural findings, pattern distilled. The "what to carry forward" source.
3. **`Phase 11/POST_PHASE_11.md`** — M-entries (mechanism statuses) + F-entries (feature deferrals). Updated at Phase 12 close-out. The library's state of record.
4. **Design docs (per-mechanism)** — read only for the mechanism you're touching:
   - `Phase 12/M1_PER_SLOT_STATE.md` (for 13e-* feature work)
   - `Phase 12/M5_REGION_SYSTEM.md` + `M5_REGION_SPECS.md` (for 13a migration)
   - `Phase 12/M4_VANILLA_SLOT_INJECTION.md` — start with the "Implementation findings" section at the top, not the original design (doc drift corrected there)
   - `Phase 12/M6_CLIENT_SIDE_SLOTS.md` — dissolution record, historical context only
5. **`THESIS.md`** — library principles (six, including new §6 on NBT persistence).
6. **`Phase 11/COMMON_FRICTIONS.md`** — phase-spanning API-friction log (1.21.11 findings). Consult before hitting the same walls.
7. **`Phase 11/inventory-plus/REPORT.md`** — IP's consumer evidence from Phase 11, still the primary reference for Phase 13 feature scopes.

---

## Working principles (inherited from Phase 11+12)

- **Library not platform.** MenuKit ships primitives; consumers compose them. No library-owned code paths a second mod would conflict with.
- **Conform to primitives.** When implementation needs something the library doesn't provide, defer + file, don't hand-roll a workaround. Phase 11 F15 hand-rolled-reverted is the canonical cautionary tale.
- **Architectural discipline first, functionality second.** A feature that's deferred cleanly — gap named, missing primitive filed, no workaround — is a better deliverable than one that ships by cheating on the architecture.
- **Match vanilla's persistence patterns** (THESIS §6). NBT/Tag throughout persistence; `/data get` inspectability is the test.
- **By-value vs by-reference composition** (M5 §4A). Regions model by-value (stackable); by-reference uses lambda + shared constants. Don't mix.
- **Read source before theorizing.** When vanilla behaves unexpectedly, read the actual code path. Don't write analytical reports about what it "probably" does.
- **Batch diagnostics.** Relaunch cycles are ~5 min end-to-end. Group multiple speculative fixes into one rebuild.
- **`/mkverify all` is the regression gate.** Seven canonical contracts; run it at every non-trivial primitive-touching change. Visual spot-checks don't replace it (see the 12a `getItem` regression for why).

---

## Primitives inventory — what's shipped

| Primitive | Location | Status |
|-----------|----------|--------|
| M1 (per-slot persistent state) | `core/MKSlotState.java` + `state/` + `network/` | Shipped. `/mkverify` contract 7 covers. PlayerInventory + BlockEntity full; EnderChest needs server-explicit API; Entity + Modded stubs. |
| M4 (vanilla menu slot grafting) | Pattern — consumers write their own mixins. Library provides `StorageContainerAdapter` + `MKHasClickedOutside*Mixin` family + `SlotRendering`. | Mechanism shipped, F8 evidence live in IP. Visual layer uses M5 §5.6 pattern. |
| M5 (context-scoped region system) | `core/InventoryRegion.java` + `HudRegion.java` + `StandaloneRegion.java` + `RegionMath.java` + `inject/RegionRegistry.java` + adapter/builder overloads | Shipped. `/mkverify` contract 6 covers all 25 regions. Standalone solver deferred. |
| M2 (SlotIdentity) | `core/SlotIdentity.java` | Shipped Phase 11. |
| M3 (MKFamily disposition) | `config/MKFamily.java` (still in tree) | **Decision pending** — see `POST_PHASE_11.md` M3 entry for what it is + candidate dispositions. |
| M6 (client-side slot primitive) | — | Dissolved during Phase 12 verification. See `M6_CLIENT_SIDE_SLOTS.md` for history. |

---

## First action — 13e-1 + 13a interleave

### Primary track: 13e-1 — IP sort-lock migration to M1

**What it does.** Replaces IP's `ClientLockStateHolder` + `ServerLockStateHolder` + `SortLockC2SPayload` + `SlotLockState` with a single `IPSlotState.SORT_LOCK` channel declaration against M1. F1 (player-slot lock persistence across sessions) and F2 (chest-lock visibility across reopens) fall out automatically — no additional IP-side code.

**Why first.** M1 is the higher-risk primitive to validate:
- Novel Tag storage path
- Multi-hook snapshot delivery (openMenu + login + respawn)
- Session cache + persistent storage layering

Failure modes are subtle — state could look persisted in UI but actually fail to deliver on reconnect, or work for BE slots but break on player inventory. Catching that early matters before F3, F5/F6, and future M1 consumers stack on top.

**Migration steps (summary — full plan in `M1_PER_SLOT_STATE.md` §8):**
1. Create `inventory-plus/locks/IPSlotState.java` with `SORT_LOCK` channel registration.
2. Replace `ClientLockStateHolder.isSortLocked(slot)` → `IPSlotState.SORT_LOCK.get(slot)` at every call site (sort algorithm, move-matching, lock overlay, scope resolvers).
3. Replace `KeybindDispatch.handleLock`'s toggle chain with `IPSlotState.SORT_LOCK.set(slot, !IPSlotState.SORT_LOCK.get(slot))`. Delete the `SortLockC2SPayload.send(...)` call.
4. Delete `ClientLockStateHolder.java`, `ServerLockStateHolder.java`, `SortLockC2SPayload.java`, `SlotLockState.java`, the `SortLockC2SPayload` receiver registration in `InventoryPlus.init`.
5. Visual verify: lock a slot → disconnect → reconnect → lock overlay still present (F1). Place a chest → lock a slot → unload chunk → return → lock still there (F2).

**Verification:** `/mkverify all` should report all seven contracts passing (nothing here should regress any of them). The F1 + F2 dev-client tests are manual.

### Interleave track: 13a — gear + sandboxes to M5 regions

**What it does.** Migrates IP's settings gear and sandboxes' sandbox/return buttons to `InventoryRegion.TOP_ALIGN_RIGHT`. Gear gets first registration (stacks rightmost); sandboxes button stacks to its left. Sandboxes also adds `"depends": {"inventory-plus": "*"}` to its `fabric.mod.json` so registration order is explicit, not alphabetical-by-accident.

**Why interleave.** 13a has one Trevor-sync blocker: gear position screenshot sign-off (per M5 §5.1). That can happen async during 13e-1 work — no wasted cycles. And 13a's failures are visually obvious (you can't miss a misplaced panel), so it's low-risk to context-swap into and out of.

**Migration steps:**
1. IP `SettingsGearDecoration` — change the static `ADAPTER` declaration from `ScreenOriginFns.fromScreenTopRight(11, -4, -16)` to `InventoryRegion.TOP_ALIGN_RIGHT`.
2. Sandboxes `SandboxInventoryDecoration` — change each of the three adapters to `InventoryRegion.TOP_ALIGN_RIGHT`. Add the depends entry to sandboxes' `fabric.mod.json`.
3. Build + relaunch.
4. **Pause for Trevor screenshot review.** Gear moves from inside-the-frame-top-right to outside-above-the-frame. That's a real visual change; Trevor wants to eyeball before commit lands.
5. Commit when approved.

---

## Pending Trevor asks

- **Gear position screenshot sign-off** (M5 §5.1). Blocks 13a's commit. Can happen async.
- **F9 UI structure clarification.** Blocks 13b (F8/F9 visual layer). Advisor's working reading: 9 `Toggle.linked` buttons hand-positioned above each hotbar slot (lambda path, hotbar-anchored) + 1 conditional 3-slot pocket panel. Trevor to confirm or redirect. Not blocking any current feature work.
- **M3 MKFamily disposition.** See `POST_PHASE_11.md` M3 entry for the decision. Advisor can resolve in one exchange. Not blocking feature work; shapes consumer migration scope.

---

## Handoff protocol

A fresh agent picking up Phase 13 should:

1. Read this file first.
2. Read `Phase 12/REPORT.md` for the architectural findings.
3. Read `Phase 11/POST_PHASE_11.md` M-entries + F-entries relevant to the feature track they're starting.
4. Confirm with Trevor which track to start with (default: 13e-1 + 13a interleave per above).
5. Before touching M1, M4, or M5 library code, re-read the relevant design doc's §10 "Resolved decisions" — those are locked commitments. Any deviation triggers a follow-up review.

Don't start implementation without checking that `/mkverify all` is green on the current HEAD. That's the baseline — if contracts fail at the start, something upstream needs investigation first.
