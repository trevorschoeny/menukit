# MenuKit Phase Plan

Living document. Captures the phase sequence for MenuKit from its current state (Phase 13 in progress) through initial public release (Phase 22). Each phase names scope, inputs, outputs, and exit criteria. Phases check against [NORTH_STAR.md](NORTH_STAR.md) — every phase moves the library toward golden or names why it doesn't.

This document is the authoritative phase roadmap. Individual phase design docs flesh out tactical detail; this document holds strategic sequencing.

---

## Completed phases

**Phases 1–5 (First Migration — Inventory Menu Architecture).** Moved inventory-menu subsystem from god-class architecture to clean architecture. Five canonical guarantees verified (composability, vanilla-slot substitutability, sync-safety, uniform abstraction, inertness).

**Phases 6–12 (Second Migration — Component Library Identity).**
- **6:** Thesis / Contexts / Palette locked as governing docs.
- **7:** Context generalization — Panel + Element cross-context.
- **8:** 8 foundational elements shipped (Toggle, Checkbox, Radio, Divider, Icon, ProgressBar, Tooltip, ItemDisplay).
- **9:** Audit-surfaced specializations (`Button.icon`, `Toggle.linked`).
- **10:** Three injection-pattern docs.
- **11:** Consumer mods rebuilt against new architecture; surfaced M1 / M4 / M5 / M6 candidates.
- **12:** Four library primitives shipped (M1 per-slot state, M2 SlotIdentity, M3 slot grafting, M4 region system). *(M-numbers reflect Phase 13 renumbering: shipped as M1/M2/M4/M5; renumbered after MKFamily-as-M3 was deleted from the library.)*

**Phase 12.5 — Validation Interstitial.** Built synthetic-consumer validator mod; ran V0–V8 scenarios; surfaced 7 primitive gaps, fixed 6 in-phase, filed 1 (#7 M1 storage-layer wiring) for Phase 14. Shipped M5 (chrome-aware regions) and M6 (four-context model) as in-phase library work. *(Renumbered from M7/M8 during Phase 13 doc reorg.)* Landed THESIS Principles 7, 8, 9, 10, 11.

---

## Phase 13 — Doc Reorganization ← current

**Scope.** Restructure `Design Docs/` into a navigable hierarchy; archive stale docs; renumber cross-references to match the new phase numbering (Phase 13a–13e consumer-migration references from `Phase 11/POST_PHASE_11.md` → 15a–15e); refresh canonical docs (NORTH_STAR, THESIS, CONTEXTS, PALETTE) for any drift from Phase 12 / 12.5 shipped work.

**Inputs.**
- Current `Design Docs/` content (many phase folders + scattered mechanism docs + stale references).
- Phase 12.5 close-out REPORT + NORTH_STAR.md (just landed).
- This phase plan.

**Outputs.**
- Restructured `Design Docs/` with clear canonical / mechanisms / phases / archived divisions.
- All cross-references internally consistent.
- Canonical docs accurate to shipped reality.
- Phase 13 REPORT naming what shipped.

**Exit criteria.**
- Trevor approves target folder structure (mid-phase checkpoint before execution).
- All `git mv`s land; no orphaned links surface during spot-checks.
- Canonical docs pass a quick audit against current library state.

---

## Phase 14 — Library Completion

**Scope.** Ship remaining MenuKit primitives and UI additions before consumer migrations swing in. Expanded during Phase 13 planning to incorporate two new mechanisms (M7 Storage Attachment Taxonomy + M8 Layout Composition) plus modal/dialog primitives alongside the original palette gaps. Organized into four sub-phases for tractability.

### Sub-phase 14a — MKFamily removal

Clean break before larger work. Carries forward from Phase 13 doc reorg decision.

- **Library:** delete `MenuKit.family()`, `MKFamily` class, related types (~115 lines). The mod-family concept doesn't belong in MenuKit.
- **Consumer migration** (4 mods): each declares its own `KeyMapping` category directly instead of going through `family.getKeybindCategory()`. Small per-mod migration.
- **Validator:** delete or repurpose V8 scenario (currently tests MKFamily Layer A — the feature is going away).
- **Stale-config note:** users may have `config/menukit-family-*.json` files orphaned on disk after removal; no runtime effect, left alone.

### Sub-phase 14b — M7 Storage Attachment Taxonomy

New mechanism formalizing where slot-group contents persist, by owner type.

- **Six canonical owner types** plus consumer extension point:
  - **Ephemeral** — session-scope only; dies with menu close. (Exists as `EphemeralStorage`.)
  - **Block-scoped (dies-with-block)** — chest, hopper, dispenser, furnace. NBT on BlockEntity.
  - **Block-portable (item-form-traveling)** — shulker box. NBT on BlockEntity, copied to ItemStack `BlockEntityTag` when broken, restored on placement.
  - **Player-attached** — player inventory, ender chest, equipment, pockets. Fabric attachment on Player.
  - **Item-attached** — bundle. Component on ItemStack. Travels with item.
  - **Entity-attached** — donkey/horse/llama saddle bags, minecart-with-chest. Fabric attachment on Entity.
  - **Modded extension point** — consumer-defined.
- **API shape:** `StorageAttachment<Owner, Content>` with library-shipped factories per owner type. Consumer declares an attachment spec; library handles NBT serialization, attachment registration, save/load lifecycle hooks.
- **Absorbs and generalizes #7** (M1 storage-layer wiring, filed from Phase 12.5). M1 covered per-slot METADATA persistence; M7 covers per-slot CONTENT persistence. They share owner-attachment infrastructure. See `Phases/12.5/V5_7_FINDING_M1_STORAGE_WIRING.md` for the finding that led here.
- Design-doc-first. Ships to `Mechanisms/M7_STORAGE_ATTACHMENT.md`.

### Sub-phase 14c — M8 Layout Composition

New mechanism for row/column/grid layout helpers.

- **Three primitives:**
  - **Row** — horizontal arrangement of child elements with declared spacing
  - **Column** — vertical arrangement
  - **Grid** — N×M arrangement
- **Critical design framing:** layout HELPERS that compute child positions, NOT nested containers. Preserves THESIS "Panel is the ceiling of composition." Row/Column/Grid wrap a list of elements with computed child offsets that the parent Panel renders directly; no new container abstraction below Panel.
- Design-doc-first. Ships to `Mechanisms/M8_LAYOUT_COMPOSITION.md`.

### Sub-phase 14d — Palette additions

UI-focused additions — V0 palette-gap inventory from Phase 12.5 plus dialog primitives.

- **Text input** — single-line editable text field
- **Slider** — continuous-value control with draggable handle
- **Dropdown** — single-selection opener list
- **Scroll container** — clipped region with scroll input
- **ConfirmDialog** — modal confirm/cancel
- **AlertDialog** — modal acknowledge

Each gets its own mini-design-doc under `Elements/`. Each follows design-doc-first.

### Phase 14 exit criteria

- All four sub-phases shipped per their individual exit criteria.
- `/mkverify all` contracts remain green; new contracts added for M7 (attachment round-trips) and M8 (layout-computation correctness).
- Phase 14 REPORT at `Phases/14/REPORT.md`.

### Phase 14 sequencing rationale

- **14a first** — clears MKFamily before larger work; consumer migrations in Phase 15 have less to navigate.
- **14b before 14c** — storage taxonomy is foundational infrastructure; layout primitives are UI-focused. Different design-surface shapes; parallel work would thrash attention.
- **14c before 14d** — layout primitives are infrastructure some palette additions (dialogs with button groups, scroll-container arrangement) benefit from using. Not a hard dependency, but natural ordering.

### Out of Phase 14 scope (explicit decisions)

- **Focus / keyboard navigation** — skipped.
- **Form patterns** — skipped (explicit, not deferred).
- **Element state machines** — deferred (evidence-driven addition when surfaced).
- **Theme / styling overrides** — non-goal per THESIS scope ceilings.
- **Animation beyond notifications** — non-goal per THESIS scope ceilings.

---

## Phase 15 — Consumer Migration

**Scope.** Migrate the four consumer mods against the completed library. Each mod is its own sub-phase. Anticipated migrations from `Phase 11/POST_PHASE_11.md`:

- **15a (inventory-plus):** Settings gear + sandboxes buttons → M5 regions. IP sort-lock migration to M1 (collapses `ClientLockStateHolder`/`ServerLockStateHolder`/packet into one channel; F1 + F2 fall out automatically).
- **15b (inventory-plus):** F8 equipment panel visual layer + F9 pockets panels (F9 needs UI-structure clarification with Trevor before implementation).
- **15c (inventory-plus):** F15 peek panel UI via M4 option (a) dynamic pre-allocation.
- **15d (shulker-palette):** post-Phase-15c — SP-F1 peek toggle via Pattern 2 injection on peek panel.
- **15e (sandboxes + agreeable-allays):** M3 scope-down migration completions; sandboxes settings-button click failure diagnosis + fix.

**Exit criteria per sub-phase.**
- Consumer mod re-enabled in `dev/build.gradle`.
- In-game visual verification.
- Sub-phase REPORT naming deliverables + any surfaced MenuKit gaps (carry to Phase 16).

---

## Phase 16 — Library Hardening

**Scope.** Fix gaps surfaced during Phase 15 consumer migrations. Phase 11's pattern is predicted to repeat — realistic consumer work will stress the library in ways Phase 14's isolated primitive testing won't.

**Contents are surface-driven.** Can't enumerate up front; depends on what Phase 15 surfaces.

**Exit criteria.**
- All Phase 15 surface findings dispositioned (fix-in-phase / file-and-defer / non-gap).
- Hardening commits pass `/mkverify all` contracts + any new contracts added.
- Phase 16 REPORT.

---

## Phase 17 — Consumer Re-do

**Scope.** Update consumer mods against the hardened library from Phase 16. Per-mod, small scope — incremental updates to pick up hardening changes.

**Exit criteria.** All consumer mods build + run cleanly against the hardened library; no regressions.

---

## Phase 18 — Polish Pass

**Scope.** Internal-facing polish before public-release work begins.

- **Cairn rewrite** — architectural knowledge management. Captures full knowledge from both migration arcs.
- **Dead-code sweep** — unused imports, unreferenced methods, stale mixins.
- **Doc accuracy pass** — every design doc checked against shipped reality.
- **Performance check** — once-over for any obvious bottlenecks (per-frame allocations, quadratic loops).
- **Visual polish** — address any vanilla-mismatch nits (padding, colors, sprite fidelity).

**Exit criteria.**
- Cairn rewrite lands.
- `/mkverify all` still green.
- Visual smoke across all four contexts shows vanilla-indistinguishable output.

---

## Phase 19 — Repo Split

**Scope.** Split the monorepo into per-mod repositories.

- MenuKit → own repo (most visible, most complex).
- inventory-plus, shulker-palette, sandboxes, agreeable-allays → each own repo.
- Gradle build decision: independent per-repo builds vs shared parent build? Decide when we get here.
- Preserve git history per-mod via `git filter-repo` or equivalent.
- Inter-mod dependencies (if shulker-palette depends on inventory-plus): resolve via published artifacts.

**Exit criteria.**
- Each repo builds independently.
- Dev-client workflow equivalent to current monorepo setup (or documented alternative).
- Original monorepo archived with pointer to new homes.

---

## Phase 20 — MenuKit Public Docs

**Scope.** Prepare MenuKit's public-facing documentation. Audience: mod authors considering MenuKit.

- GitHub README — what it is, how to get started, links to deeper docs.
- Modrinth page — same substance, Modrinth-shaped.
- Library API docs — javadoc comprehensiveness; any HTML generation needed.
- Keybind-category-sharing review (from Phase 12.5 carry-forward) — decide whether Layer A keybind-sharing remains or further shrinks for non-Trevor authors.

**Exit criteria.**
- Public docs ready for release.
- README smoke-tested by pointing a non-Trevor reader at it.

---

## Phase 21 — Sibling-Mod Public Docs

**Scope.** Prepare player-facing documentation for inventory-plus, shulker-palette, sandboxes, agreeable-allays.

- GitHub README per mod — what it does, keybinds, features.
- Modrinth page per mod — feature highlights, screenshots, changelog.

**Exit criteria.** All four mods have complete public docs.

---

## Phase 22 — Modrinth Publish

**Scope.** Initial public release.

- MC version matrix decision — which MC versions are supported? 1.21.11 only or wider?
- Release MenuKit first (dependency for consumer mods).
- Release consumer mods after MenuKit is live and indexed.
- Version numbering scheme decision (if not already established).

**Exit criteria.** All five mods live on Modrinth. Initial release announcement.

---

## Sequencing notes

- **Phase 12.5 → 13 → 14 → 15** is the tightest chain. Each depends on the prior phase's outputs.
- **Phase 16 / 17** are tightly coupled to 15; could be considered 15b in some framings, but splitting them gives clearer "library stabilized here" markers.
- **Phase 18 (polish)** is a discrete quality gate before public-release work. Don't skip.
- **Phase 19 (repo split)** could technically happen earlier but delaying until code + docs are stable minimizes churn across repos.
- **Phase 20 / 21 / 22** are public-release sequence and can't reorder meaningfully.

## Living-document note

This plan is not frozen. Findings during any phase can trigger scope adjustments in later phases. Adjustments are recorded here as they happen; this doc is the canonical roadmap. Individual phase design docs (inside the corresponding phase folders) hold tactical detail.

---

**Current phase: 13 (Doc Reorganization) — step 1 of mini-plan complete (this doc).**
