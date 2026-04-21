# Phase 11 IP — Session Brief

Stable handoff anchor for agents picking up IP's Phase 11 work across compactions. Read this first; cross-reference `AUDIT.md`, `REFACTOR_PLAN.md`, `POST_PHASE_11.md`, `REPORT.md`, and `../COMMON_FRICTIONS.md` as needed.

---

## What to read first

1. **This file** — strategic + tactical state.
2. **`REPORT.md`** — Phase 11 IP close-out (what shipped, what deferred, open questions).
3. **`POST_PHASE_11.md`** — feature (F\*) + mechanism (M\*) entries with cross-references.
4. **`../COMMON_FRICTIONS.md`** — accumulated Fabric / 1.21.11 vanilla frictions (currently 5 entries).
5. **`REFACTOR_PLAN.md`** v2 — the spec IP was implemented against; some sections superseded by the three-phase arc.
6. **`AUDIT.md`** — archaeology of what IP needed.
7. **Recent commits on main** — `git log --oneline`.

---

## Framing (critical)

The original Phase 11 plan was "rebuild consumer mods fully against current MenuKit." Mid-phase this reframed (2026-04-15) into a three-phase arc:

- **Phase 11** — rebuild consumers as far as current MenuKit allows. When a feature needs a primitive that doesn't exist, **defer the feature and file the primitive**. Don't try to ship the primitive mid-phase.
- **Phase 12** — design and ship MenuKit primitives using multi-consumer evidence.
- **Phase 13** — complete deferred consumer-mod features against Phase 12's primitives.

The exception clause that authorized `SlotIdentity` mid-phase is **closed for new business**. `SlotIdentity` shipped and stays; no new library additions during Phase 11. Default response to "we need a primitive that doesn't exist" is **defer feature + file mechanism entry + document limitation + move on**.

This framing keeps MenuKit stable while consumer work surfaces real architectural gaps. Case-by-case "is this small enough to be an exception" decisions are not allowed.

---

## IP state (Phase 11 close-out)

All Layer 0 + Layer 1 Group A/B1/B2/B3/B5 work shipped. Working tree clean at `d967165`. See `REPORT.md` for the full list.

**Shipped features (Phase 11 IP):**

- Attachment foundations (`IPPlayerAttachments`).
- Lock keybind + overlay (Group A).
- Sort via client-side click sequence (Group B1).
- Shift+double-click bulk-move (Group B2) — click-sequence approach; survives locks.
- **Sort-lock blocks auto-routing in both directions** (B2 addition; four new mixins).
- Move-matching keybind (Group B3) — pulls source-region stacks whose type appears in destination region.
- Settings gear + unified YACL config screen (Group B5).
- `SlotIdentity` primitive (MenuKit library, shipped mid-phase as the one advisor-approved exception).

**Deferred features (seven entries in `POST_PHASE_11.md`):**

| ID | Feature | Blocker |
|----|---------|---------|
| F1 | Persistent player-slot lock state across sessions | M1 (unified per-slot state primitive) |
| F2 | Chest-slot lock visible across menu reopens | M1 |
| F3 | Full-lock (Ctrl+click) feature | feature-addition |
| F4 | Sort consolidation | feature-complexity |
| F5 | Creative-mode sort | feature-scope (creative packet path) |
| F6 | Creative-mode bulk-move | feature-scope (same as F5) |
| F7 | Bulk-move within a single player-inventory region (no container open) | feature-complexity (vanilla click-protocol edge case) |

**Mechanisms filed for Phase 12 (`POST_PHASE_11.md`):**

| ID | Mechanism | Status |
|----|-----------|--------|
| M1 | Unified per-slot state primitive | design input — enables F1 + F2 |
| M2 | `SlotIdentity` | **shipped**; M1 builds on it |
| M3 | MKFamily removal (or keep) | cleanup mechanism; not blocking |

**Not-yet-shipped-but-planned (non-deferral):**

- Layer 2 — attachment-dependent features (autofill, auto-restock, mending helper, pocket cycle, etc.). All stubbed to no-op; mixin signatures preserved for in-place re-population.
- Layer 3 — client-side peek (shulker / ender / bundle panels, peek keybind).
- Sort + move-matching per-region buttons (§ 4.5 of refactor plan) — keybinds cover the use case; buttons are an alternative UI surface.

These are sequenced layers, not blocked.

---

## Architectural decisions that govern future work

1. **Click-sequence architecture holds.** Sort, bulk-move, and move-matching all use `ClickSequenceC2SPayload`. Client computes a sequence of simulated slot clicks; server replays via `menu.clicked(...)`. `mayPlace`, filters, events, and cross-mod slot-click mixins fire automatically. Future click-model features (consolidation F4, Ctrl+click full-lock F3) should extend this pattern rather than reimplement mutation paths.
2. **Canonical-slot routing via `SlotIdentity`** solved creative's two-menu split. Route through `player.containerMenu` + `IPRegionGroups.canonicalSlot` for any feature that mutates through click sequences.
3. **Sort-lock is auto-routing-scoped.** `moveItemStackTo` + `QUICK_MOVE` destinations respect locks via the `ServerLockGuard` ThreadLocal + `IPSlotMayPlaceMixin` + `IPSlotGetItemMixin` pair. Direct `PICKUP` clicks unaffected — users can still hand-place into locked slots. F3's full-lock is a separate, broader primitive; don't merge them.
4. **Three-mixin inventory injection surface:**
   - `InventoryContainerMixin` — primary on `AbstractContainerScreen`.
   - `RecipeBookMixin` — supplementary on `AbstractRecipeBookScreen`, gated `instanceof InventoryScreen` (Phase 10 failure mode #2 fix).
   - `CreativeInventoryMixin` — supplementary on `CreativeModeInventoryScreen` (Phase 10 failure mode #1 fix).
   Each feature adds hooks to the same three classes.
5. **Vanilla shift+double-click is dispatched from `mouseReleased`, not `mouseClicked`.** Vanilla's `doubleclick` flag is set during mouseClicked but PICKUP_ALL fires during mouseReleased. To fully intercept, hook both: mouseClicked HEAD for detection + cancel, and mouseReleased HEAD paired-suppression (set a flag on fire, consume it on next release).
6. **Conforming-to-primitives discipline holds.** Plan v2 stripped consumer-side complexity that preserved old visuals at the cost of non-conforming primitives. If tempted to add custom code to preserve a specific visual or behavior, default to the conforming primitive. Surface to advisor only when the loss is genuinely significant.

---

## Working pattern

When a feature needs a primitive that doesn't exist:

1. Stop the feature work.
2. Surface to advisor with: what the feature needs, what's missing, what the primitive would look like.
3. Default expected outcome: defer feature + file mechanism entry in `POST_PHASE_11.md` + document limitation.
4. Continue with other in-scope work.

When stubbing a deferred feature, leave a comment at the stub site referencing the `POST_PHASE_11.md` entry by name:

```java
// DEFERRED to Phase 13 — see POST_PHASE_11.md "<entry name>"
// <one sentence about what's missing and why>
// <one sentence about partial functionality if any>
```

---

## Evolution since mid-session briefing

The mid-session briefing (captured above in "Framing" + "Working pattern") was accurate as a strategic foundation. Tactical evolution since:

- **B2 hook location finalized** at `mouseClicked` HEAD + `mouseReleased` HEAD (paired suppression). `slotClicked` HEAD alone was insufficient because vanilla's release-time PICKUP_ALL dispatch happens after mouseClicked completes.
- **Sort-lock-blocks-in semantic added during B2** — not in the original brief. Auto-routing scope (not a freeze). Four new mixins + `ServerLockGuard` ThreadLocal.
- **F7 added** — hotbar↔main no-container bulk-move residue. Vanilla `doubleclick` / `lastQuickMoved` interaction produces a one-stack-back-to-anchor bug. Low priority; player↔container works.
- **Move-matching semantic corrected** — match set is "every item type already in the destination region," not "the hovered item's type." Hovered slot only selects the destination region; contents of that region define what to pull.
- **No-container move-matching intentionally no-op** — user confirmed hotbar↔main isn't a compelling use case.
- **B5 gear coordinate collision** — MenuKit's Phase-10 `ExampleInventoryCornerButton{,RecipeBookRender}Mixin` occupied the same `fromScreenTopRight(11, -4, -16)` anchor. Disabled in dev-only `menukit-examples.mixins.json` for clarity; other Phase-10 examples remain loaded.
- **COMMON_FRICTIONS #5** added — `Screen.hasShiftDown()` removal in 1.21.11 (use `InputConstants.isKeyDown(window, KEY_L/RSHIFT)` outside event contexts).
- **Keybind-in-config + multi-keybind** noted as future MenuKit primitives. Library-level concerns, not IP-specific; not filed in `POST_PHASE_11.md` (which tracks IP-surfaced deferrals).

---

## Where I am now (checkpoint)

- **Last commit:** `d967165` — `MK: Phase 11 IP — close-out REPORT.md`.
- **Working tree:** clean.
- **Phase 11 IP:** complete. All Layer 0 + Layer 1 A/B1/B2/B3/B5 deliverables landed, seven deferrals filed, REPORT.md captures the full close-out.
- **Next concrete action:** start shulker-palette Phase 11 refactor (task #23). Same framing — rebuild as far as current MenuKit allows; defer + file what needs Phase 12 primitives. Advisor estimate: 2–3 days. Expected to surface per-item state as a new mechanism candidate (M1 sibling or extension).
- **Half-formed thoughts not yet surfaced:**
  - Sort+move-matching per-region buttons (§ 4.5 of refactor plan) are still unshipped for IP. Keybinds cover the functionality; buttons are secondary UI. Could land as a B6 slice before shulker-palette, or skip for now and revisit post-Phase-11.
  - `SettingsGearDecoration` uses IP's existing `settings.png` sprite inside a standard raised-button background. User noted this is "polish stuff later" — worth revisiting if MenuKit grows an explicit borderless-icon-button variant.
  - F7 investigation path (full-lock F3 may surface the same vanilla-click-protocol state fields) could resolve F7 as a byproduct. Worth a sentence in F3's entry when that work starts.

---

## Handoff protocol for future compactions

Before compacting, do these three things (this session is the template):

1. **Update this file** — add a dated "evolution" subsection with anything that changed since last checkpoint.
2. **Commit or stash in-flight work** — compaction preserves working tree but loses *why* something is uncommitted. WIP commits with clear messages beat partial state.
3. **Update "Where I am now"** — 3–5 lines. Last commit, last file edited, next concrete action, half-formed thoughts.

After compaction, point the agent at this file first. Other consumer mods under the same framing (shulker-palette, sandboxes, agreeable-allays) should maintain parallel `SESSION_BRIEF.md` files in their own design-doc directories.
