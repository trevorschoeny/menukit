# Phase 13 — Close-out REPORT

**Status: complete.** Design Docs/ reorganization shipped; canonical refresh applied; cross-reference renumbering across high-traffic docs; one major architectural decision (MKFamily deletion) surfaced and filed forward.

---

## Executive summary

Phase 13's hypothesis: the `Design Docs/` structure had grown organically across 12+ phases without intentional organization, making navigation expensive. A focused reorganization pass before continuing into Phase 14+ would pay back in every subsequent phase's cost-of-finding.

The reorganization landed cleanly:

- **6 new top-level folders** (`Mechanisms/`, `Elements/`, `Architecture/`, `Phases/`, `Mods/`, `Archived/`) replacing the previous flat-with-implicit-subfolders mix
- **47+ file moves** via `git mv` preserving rename history
- **2 new canonical docs created** (`NORTH_STAR.md` for the target-product vision; `PHASES.md` for the forward roadmap covering Phases 13–22)
- **6 mechanism docs renumbered** (cascade fill after M3 [MKFamily] deletion + M6 [client-side slots] archival)
- **2 stale docs archived** (`STORY.md` Phase-5-era interim; `PHASES_6_THROUGH_12_BRIEF.md` pre-execution plan)
- **1 new mechanism doc extracted** (`Mechanisms/M2_SLOT_IDENTITY.md` from prior inline content in `POST_PHASE_11.md`)
- **4 per-mod decision folders seeded** (`Mods/{inventory-plus,shulker-palette,sandboxes,agreeable-allays}/DECISIONS.md`)

One major architectural decision surfaced mid-phase:

- **MKFamily deletion** — Trevor decided during the doc reorg pass that the mod-family concept doesn't belong in MenuKit. Doc references updated; library code removal + 4-mod consumer migration filed as a Phase 14 work item (option to spin out as Phase 13.5 if a clean break is preferred).

---

## Final folder structure

```
Design Docs/
├── NORTH_STAR.md           ← canonical: target product (NEW this phase)
├── THESIS.md               ← canonical: principles
├── CONTEXTS.md             ← canonical: rendering contexts
├── PALETTE.md              ← canonical: element inventory
├── PHASES.md               ← canonical: phase roadmap (NEW this phase)
├── DEFERRED.md             ← canonical: consolidated deferrals
│
├── Mechanisms/             ← one file per library primitive
│   ├── M1_PER_SLOT_STATE.md
│   ├── M2_SLOT_IDENTITY.md            (NEW this phase, extracted from POST_PHASE_11.md)
│   ├── M3_VANILLA_SLOT_INJECTION.md   (renumbered from M4)
│   ├── M4_REGION_SYSTEM.md            (renumbered from M5; absorbed M4_SPECS as appendix during Phase 13 — one file per mechanism)
│   ├── M5_CHROME_AWARE_REGIONS.md     (renumbered from M7)
│   └── M6_FOUR_CONTEXT_MODEL.md       (renumbered from M8)
│
├── Elements/               ← renamed from "Element Design Docs/"
│   └── 10 element docs, _DESIGN_DOC suffix dropped
│
│                           (Architecture/ folder dissolved late in Phase 13 —
│                            injection-pattern content inlined into CONTEXTS.md as
│                            per-context subsections + a shared failure-modes appendix.
│                            CONTEXTS.md also refreshed to the four-context model.)
│
├── Phases/                 ← NEW top-level wrapper for all phase folders
│   ├── 06/REPORT.md, 07/REPORT.md, 08/REPORT.md, 09/REPORT.md, 10/REPORT.md
│   ├── 11/  (cross-mod docs + per-mod subfolders)
│   ├── 12/  (REPORT + intra-phase docs; mechanism docs moved to Mechanisms/)
│   ├── 12.5/ (REPORT + DESIGN + findings; mechanism docs moved to Mechanisms/)
│   └── 13/  (this REPORT)
│
├── Mods/                   ← NEW per-mod ongoing design canon
│   ├── inventory-plus/DECISIONS.md
│   ├── shulker-palette/DECISIONS.md
│   ├── sandboxes/DECISIONS.md
│   └── agreeable-allays/DECISIONS.md
│
└── Archived/               ← preserved historical docs no longer canonical
    ├── STORY.md                          (Phase 5 interim)
    ├── PHASES_6_THROUGH_12_BRIEF.md      (pre-execution plan)
    └── M6_CLIENT_SIDE_SLOTS.md           (dissolved Phase 12)
```

---

## M-renumbering map

| Was (pre-Phase-13) | Now | Reason |
|---|---|---|
| M1 (per-slot persistent state) | M1 | unchanged |
| M2 (SlotIdentity) | M2 | unchanged; promoted to own doc this phase |
| M3 (MKFamily) | — | DELETED per Trevor's Phase 13 decision; library code removal carry-forward to Phase 14 |
| M4 (vanilla slot injection) | M3 | renumbered down |
| M5 (region system) | M4 | renumbered down |
| M5_SPECS | (merged) | merged as appendix into M4_REGION_SYSTEM.md during Phase 13 — one file per mechanism convention |
| M6 (client-side slots, dissolved Phase 12) | — | archived to `Archived/M6_CLIENT_SIDE_SLOTS.md` |
| M7 (chrome-aware regions) | M5 | renumbered down |
| M8 (four-context model) | M6 | renumbered down |

Each renamed mechanism doc carries a "Previously Mn" subtitle for historical traceability. Cross-references in body text updated for the highest-traffic docs (mechanism doc titles, THESIS, PHASES, POST_PHASE_11, DEFERRED).

---

## Cross-reference renumbering applied

- **M-section references** (M5 § → M4 §, M7 → M5, M8 → M6) across all mechanism docs and key canonical docs
- **Phase sub-phase numbering** (13a / 13b / 13c / 13e-1 → 15a / 15b / 15c / 15e-1) per the new phase plan
- **Phase work labels** ("Phase 13 sketch", "Phase 13 consumer", etc. → "Phase 15 sketch", "Phase 15 consumer") in `POST_PHASE_11.md` since consumer-migration moved from Phase 13 (old plan) to Phase 15 (new plan)
- **Path prefixes**: `Phase 11/X` → `Phases/11/X`, `Phase 12/X` → `Phases/12/X`, `Phase 12.5/X` → `Phases/12.5/X` across high-traffic docs
- **Specific mechanism-doc paths**: `Phase 12/MX_*.md` → `Mechanisms/Mn_*.md` (with renumbering applied) in `POST_PHASE_11.md`
- **Old M6 client-side-slots path** → `Archived/M6_CLIENT_SIDE_SLOTS.md`

---

## MKFamily deletion (Phase 14 carry-forward)

**Decision (Trevor, Phase 13):** the mod-family concept doesn't belong in MenuKit. Even Layer A (identity grouping + keybind-category sharing, preserved through Phase 12.5's M3 scope-down) is being removed.

**Documentation impact (Phase 13, applied):**
- `POST_PHASE_11.md` M3 entry rewritten to flag deletion at the top, with Phase 12.5 scope-down record preserved below as historical context
- `Mechanisms/M3_*.md` slot intentionally vacant (M3 vacated; vanilla-slot-injection promoted from M4 to fill the slot in the numbering, not the namespace)
- `PHASES.md` Phase 14 scope expanded with MKFamily-removal as a third major work item
- `Mods/agreeable-allays/DECISIONS.md` notes the impact (was a pure Layer A consumer; now needs to declare own `KeyMapping` category)
- Other mod DECISIONS.md files reference Phase 15 migration sub-phases for the impact

**Library/consumer impact (carry-forward, Phase 14 or 13.5):**
- Library code: delete `MenuKit.family()`, `MKFamily` class, related types (~115 lines)
- Consumer code: 4 mods (IP, shulker-palette, sandboxes, agreeable-allays) each declare their own `KeyMapping` category directly. Small per-mod migration.
- Validator: `V8 — MKFamily Layer A scenario` is now testing a deleted feature. Either delete V8 or convert to a "verify MKFamily is absent" probe.
- Stale config files (`config/menukit-family-*.json`) on user disks become orphan after removal. Cleanup: documented as user-side concern, no library work.

**Sequencing options for Phase 14:**
- **(a)** Fold MKFamily removal into Phase 14's library completion. Coordinate with Phase 15a/15e consumer migrations for the keybind-category re-declaration.
- **(b)** Spin out as Phase 13.5 — quick clean-break sub-phase between Phase 13 and Phase 14, isolating the deletion from Phase 14's primitive-design work.

Trevor's call when Phase 14 kicks off.

---

## Residual cross-reference debt

Phase 13's reorg covered the highest-traffic docs comprehensively. Some lower-traffic docs retain stale references — flagged for Phase 18 polish:

- **`Phases/12.5/` finding docs** (V4_FINDINGS, V5_FINDING_*, V5_6_FINDING_*, V5_7_FINDING_*, V5_7_PRECHECK, V5_7_EXTEND_RESOLVER_FIX) — internal references to `Phase 11/X` and `Phase 12.5/X` paths still pointing at pre-reorg locations. Files navigable at new locations; explicit cross-links may need bumping.
- **`Phases/12.5/M8_V2_REPORT.md`** — interim REPORT with paths to old layout; not refreshed.
- **`Phases/12.5/DESIGN.md`** — phase scope doc with old paths.
- **`Phases/12.5/REPORT.md`** — partially refreshed (`Phase 11/` → `Phases/11/` done) but `Phase 12.5/` self-references via absolute paths NOT done.
- **`Phases/11/` sub-mod folders** (inventory-plus/, shulker-palette/, final-consumers/) — internal cross-refs likely stale.
- **`Phases/11/inventory-plus/POST_REPORT.md`** (renamed from POST_PHASE_11.md) — internal cross-refs may use the old name.
- **Mechanism doc body M-references** — global passes (M5→M4, M7→M5, M8→M6) renamed all matching strings; some context-bearing self-references in renamed-doc bodies may have ambiguous historical-vs-current meaning. Mechanism doc titles and high-traffic body refs are clean. The doc-internal "Previously Mn" subtitle preserves the historical tie.
- **Source code `@cairn` comments** in Java files reference design doc paths (e.g., `Design Docs/Phase 12/M4_VANILLA_SLOT_INJECTION.md`). Not updated this phase. Phase 18 polish.
- **Renamed Element doc filenames** — some external docs may reference `Element Design Docs/X_DESIGN_DOC.md` paths; not swept.

The pattern: high-impact cross-refs are clean. Long-tail residual references in lower-traffic docs are flagged for Phase 18 polish — they don't block Phase 14 work.

---

## Canonical doc refresh applied

| Doc | Refresh applied |
|---|---|
| `NORTH_STAR.md` | Created this phase. No prior content; no refresh needed. |
| `THESIS.md` | M8 → M6 references updated (Principle 10 + Principle 11 mentions of M8 reframe / §6 catalog). |
| `CONTEXTS.md` | **Substantial rewrite late in Phase 13.** Updated from three-context framing (inventory/HUD/standalone) to the post-M6 four-context model (MenuContext/SlotGroupContext/HudContext/StandaloneContext). Each context section gained an "Injection pattern" subsection inlined from the dissolved `Architecture/` folder. Added a "Common injection failure modes" appendix lifted from the inventory injection-pattern doc (applies to MenuContext + StandaloneContext alike). New cross-context summary table at the end. CONTEXTS.md is now the canonical home for both context definitions AND injection patterns; the `Architecture/` folder no longer exists. |
| `PALETTE.md` | No changes needed — element-focused, no M-number references. |
| `PHASES.md` | Created this phase. Updated mid-phase: Phase 14 scope expanded with MKFamily-removal third work item; M-renumbering note added to Phase 12 description. |
| `DEFERRED.md` | Phase 13 → Phase 15 renumbering applied; `M5_REGION` → `M4_REGION` filename updates; path prefix updates (`Phase 11/` → `Phases/11/`, `Phase 12/` → `Phases/12/`, `Phase 12.5/` → `Phases/12.5/`). |

---

## Phase 14 entry conditions (met)

- Folder structure stable for further design docs to live in
- Canonical docs reflect MKFamily deletion (in PHASES, in POST_PHASE_11)
- Mechanism docs renumbered with historical traceability via "Previously Mn" subtitles
- Per-mod decision folders ready to receive Phase 15-era design notes
- MKFamily removal scope known and filed forward

Phase 14 (Library Completion) starts with: palette gaps mini-design-docs (text input, slider, dropdown, scroll container), M1 scoped-storage primitive design pass (resolution of #7 from Phase 12.5), and MKFamily code removal (coordinated with consumer migrations or as Phase 13.5 quick sub-phase).

---

**Phase 13 closed.** Working tree carries the full reorg as 50+ git renames + canonical doc refreshes + new docs. Trevor commits when ready.
