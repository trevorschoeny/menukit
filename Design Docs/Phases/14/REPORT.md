# Phase 14 — Library Completion — Close-out REPORT

**Status: complete.** Library palette finished. Three new mechanisms shipped (M7 storage taxonomy, M8 layout composition, M9 panel opacity). Three vanilla-wrap primitives shipped (TextField, Slider). One bespoke-composition primitive shipped (Dropdown) with two element-level interaction primitives (`hitTest`, `getActiveOverlayBounds`). Two dialog primitives + ScrollContainer + MKFamily removal + a comprehensive test-surface cleanup. Calibration set grew from 0 to 7 standing heuristics across the phase. Round-count discipline tightened from 3 advisor rounds (early sub-phases) to 1 round + inline (mid-phase) to 1 round + at-most-one in-implementation pivot (late sub-phases).

Phase 14 closes with the library substantively complete for consumer migrations to swing in (Phase 15).

---

## 1. Executive summary

Phase 14's hypothesis: ship every remaining MenuKit primitive and UI addition before consumer migrations begin, so Phase 15 has a stable foundation. Scope expanded mid-phase with three new mechanism candidates filed during Phase 13 planning (M7, M8, M9), and grew further during 14d execution as palette items revealed primitive gaps (lifecycle hooks for widget-wrapping, modal-region click-eat at element granularity).

The phase landed in **10 sub-phases** across 4 logical buckets:

| Bucket | Sub-phases | Output |
|---|---|---|
| Cleanup | 14a | MKFamily concept deleted from library + 4 consumer mods migrated |
| Mechanisms | 14b, 14c, 14d-2.5 | M7 Storage Taxonomy, M8 Layout Composition, M9 Panel Opacity |
| Palette | 14d-1, 14d-2, 14d-3, 14d-4, 14d-5 | ConfirmDialog/AlertDialog, ScrollContainer, TextField, Slider, Dropdown |
| Test surface | 14d-2.7 | Single test entry point (inventory Test button) replacing chat commands |

Plus **one superseded sub-phase** (14d-2.6 — wrong-shaped TestHub in `menukit/`; superseded by 14d-2.7's comprehensive cleanup).

Plus **two new element-level primitives** added to `PanelElement` during 14d execution that weren't in the entry plan:
- `onAttach` / `onDetach` lifecycle hooks (14d-3) — required for widget-wrapping elements (TextField/Slider) to register their vanilla widgets
- `hitTest` + `getActiveOverlayBounds` interaction primitives (14d-5) — required for Dropdown's popover-area exclusive-claim and structural inert-behind semantics

The unplanned primitives are the kind of thing Phase 14 was designed to surface: realistic palette work stresses the underlying interfaces in ways pure-design rounds can't. Both primitives generalize cleanly — future palette items inherit them.

---

## 2. Commit timeline

```
dd713d7  Phase 14a — MKFamily removal
f07fe7d  Validator tooling — /mkverify aggregator + hub          [adjacent infra]
0fc73b2  Phase 14b — M7 Storage Attachment Taxonomy
b975c62  Phase 14c — M8 Layout Composition
0de2897  Phase 14d-1 — ConfirmDialog + AlertDialog + modal primitive
6082aa1  Phase 14d-2 — ScrollContainer (clipped viewport with scroll input)
6bb44f0  Phase 14d-2.5 — M9 Panel Opacity Mechanism (click-through prohibition)
5d001f8  Phase 14d-2.7 — Test surface comprehensive cleanup (single entry point)
8016048  Phase 14d-3 — TextField (wraps vanilla EditBox; PanelElement lifecycle hooks)
33ac098  Update TESTING_CONVENTIONS.md for 14d-3 reality
80433cf  Phase 14d-4 — Slider (wraps vanilla AbstractSliderButton; no new primitives)
e117e85  Phase 14d-5 — Dropdown (bespoke composition + getActiveOverlayBounds primitive)
```

12 commits across the phase (10 sub-phase commits + 1 doc-refresh + 1 adjacent infra). Single-commit-per-sub-phase discipline held throughout (with the exception of 14d-3 → 14d-3-doc-refresh, where a follow-up commit cleaned the testing-conventions doc against shipped reality).

---

## 3. What shipped — sub-phase summary

### 14a — MKFamily removal (`dd713d7`)

Library code deletion (~115 lines) + 4-mod consumer migration. Mod-family concept removed from MenuKit per Phase 13 architectural decision. Consumer migrations: IP and sandboxes received real `KeyMapping.Category.register(...)` swaps; SP and AA had no surviving keybinds, so their migrations were pure deletions. Validator V8 scenario deleted (compiler enforces absence; absence-probe has no architectural value).

**Calibration:** *Trust the compiler over guard probes.* Absence-of-deleted-API is enforced by compilation; runtime probes for absence add ceremony without value.

### 14b — M7 Storage Attachment Taxonomy (`0fc73b2`)

New mechanism formalizing where slot-group contents persist by owner type. Six canonical owner types (Ephemeral, Block-scoped, Block-portable, Player-attached, Item-attached, Entity-attached) plus modded extension point. `StorageAttachment<Owner, Content>` API with library-shipped factories. Absorbed and generalized #7 (M1 storage-layer wiring filed from Phase 12.5). Ships to `Mechanisms/M7_STORAGE_ATTACHMENT.md`.

**M-numbering note:** M7 is the first new mechanism shipped under the post-Phase-13 numbering scheme (M1–M6 being the renumbered set). M7 ≠ heuristic 7.

### 14c — M8 Layout Composition (`b975c62`)

New mechanism for row/column/grid layout helpers. Three primitives: Row, Column, Grid. **Critical design framing:** layout HELPERS that compute child positions, NOT nested containers. Preserves THESIS "Panel is the ceiling of composition." Row/Column/Grid wrap a list of elements with computed child offsets that the parent Panel renders directly; no new container abstraction below Panel. Ships to `Mechanisms/M8_LAYOUT_COMPOSITION.md`.

### 14d-1 — ConfirmDialog + AlertDialog + modal primitive (`0de2897`)

Modal dialog primitives. ConfirmDialog (4-element panel: title, body, confirm, cancel). AlertDialog (3-element panel: title, body, acknowledge). M9 modal primitive scaffolding introduced here — formalized in 14d-2.5.

**Calibration heuristic 1 — *Compounding mixins → wrong layer.*** Surfaced when modal-dim implementation initially attempted via stacked mixins; refactored to a single render-pass mechanism in 14d-2.5.

### 14d-2 — ScrollContainer (`6082aa1`)

Clipped viewport with scroll input + drag-scrollbar. New `mouseScrolled` and `mouseReleased` PanelElement methods for scroll input + drag-end detection.

**Calibration heuristic 2 — *Find vanilla's existing primitive before inventing one.*** Vanilla's `ScrollPanel` and `AbstractScrollWidget` were investigated; ScrollContainer borrowed scrollbar geometry but stayed as a MenuKit-native PanelElement (vanilla widgets don't compose into PanelElement layout cleanly).

### 14d-2.5 — M9 Panel Opacity Mechanism (`6bb44f0`)

Three-flag composition (`opaque` / `dimsBehind` / `tracksAsModal`) for panel-level click-eat, hover suppression, dim-behind rendering. Modal panels block input to vanilla widgets behind them. Ships to `Mechanisms/M9_PANEL_OPACITY.md`.

**Calibration heuristic 3 — *Pre-empted dispatch owns responsibility.*** Once M9 pre-empts a click-dispatch decision, the entire downstream pipeline (slot hover, tooltip, vanilla widget) must respect it. Half-measures (some mixins respecting M9, others not) leak responsibility back to consumer code.

### 14d-2.7 — Test surface comprehensive cleanup (`5d001f8`)

Supersedes 14d-2.6 (wrong-shaped — built parallel TestHub in `menukit/` shadowed by validator's existing Hub). Single test entry point: inventory "Test" button at `RIGHT_ALIGN_TOP` runs every automated probe + opens Hub. All `/mkverify` chat commands deleted. Library/validator boundary cleaned (library exposes pure-logic `ContractVerification.runAll(player)`; validator owns visual scaffolding). Two primitive-gap fold-inlines (MenuKitScreen + MenuKitHandledScreen scroll/release dispatch).

**Calibration heuristic 4 — *Audit existing surface before building parallel one.*** 14d-2.6's TestHub was built without auditing the validator's existing Hub; the parallel surface was shadowed and required scrap. The audit-first discipline is now standing for any "build a test/probe/scaffolding surface" sub-phase.

### 14d-3 — TextField (`8016048` + `33ac098`)

Wraps vanilla `EditBox` via composition. New `PanelElement.onAttach` / `onDetach` lifecycle hooks for widget registration. Lens pattern: Consumer-only + imperative `setValue`. ScreenAccessor mixin for `Screen.addWidget` (input-dispatch only, NOT renderables).

**Calibration heuristic 5 — *Render order matters when wrapping vanilla widgets — manual in custom pipeline.*** Vanilla widgets default to renderables-list rendering, which paints BEFORE MenuKit's panel-background pass (so the panel covers the widget). Solution: register via `addWidget` only (input dispatch); render manually after panel background.

### 14d-4 — Slider (`80433cf`)

Wraps vanilla `AbstractSliderButton` via composition. Lens pattern: Supplier+Consumer over normalized [0, 1] double. `applyValue`-bypass `syncFromSupplier` path keeps per-frame supplier-pull idempotent. In-track label via `.label(DoubleFunction<Component>)`. Keyboard support inherited from vanilla.

**Calibration heuristic 6 — *Follow vanilla when wrapping — don't cut features inherited for free; don't add API vanilla doesn't have.*** Slider's two principled divergences from advisor's brief were both about following vanilla: keep the keyboard support (don't cut) + use vanilla's bake-label-into-track pattern (don't add a separate `.narrationLabel`).

### 14d-5 — Dropdown (`e117e85`)

Bespoke composition (no vanilla wrap). Architectural patterns from `CommandSuggestions.SuggestionsList` (popover dispatch + scroll) and `BelowOrAboveWidgetTooltipPositioner` (edge-flip placement). Two new element-level primitives: `hitTest` (interaction-bounds extension hook) and `getActiveOverlayBounds` (exclusive-claim modal-area). Two-pass dispatcher in all 6 dispatch points (3 dispatchers × click+scroll).

**Calibration heuristic 7 — *Modal regions need structural exclusivity, not dispatcher-order tweaks or element-claim semantics.*** Surfaced via Trevor's smoke catching clickthrough behind edge-flipped popover. Initial fix attempt (reverse element-iteration in dispatchers) flagged as a hack; pivoted to structural overlay primitive — the right abstraction level.

---

## 4. Mechanisms shipped

| Mechanism | Sub-phase | Doc | One-liner |
|---|---|---|---|
| M7 Storage Attachment Taxonomy | 14b | `Mechanisms/M7_STORAGE_ATTACHMENT.md` | Six canonical owner types + extension point for slot-content persistence |
| M8 Layout Composition | 14c | `Mechanisms/M8_LAYOUT_COMPOSITION.md` | Row/Column/Grid layout helpers (compute child positions; not containers) |
| M9 Panel Opacity | 14d-2.5 | `Mechanisms/M9_PANEL_OPACITY.md` | Three-flag composition for click-eat, hover suppression, dim-behind |

Plus **two new element-level interaction primitives** added to PanelElement during the palette work:

| Primitive | Sub-phase | Purpose |
|---|---|---|
| `onAttach` / `onDetach` lifecycle hooks | 14d-3 | Widget-wrapping elements (TextField, Slider) register/unregister vanilla widgets at screen lifecycle boundaries |
| `hitTest` interaction-bounds | 14d-5 | Default = layout bounds; override for elements with interaction beyond layout (extension hook) |
| `getActiveOverlayBounds` exclusive claim | 14d-5 | Element-level parallel to M9's panel-level click-eat — declares an exclusive screen-region routed solely to this element |

The lifecycle hooks (14d-3) and overlay primitive (14d-5) weren't in the Phase 14 entry plan; both surfaced during palette implementation as primitive gaps. The Phase 14 thesis — that palette work would surface library-level gaps — was validated.

---

## 5. Calibration heuristics — final set

The standing heuristic set grew across the phase:

1. **Compounding mixins → wrong layer** (14d-1) — when a feature requires stacking input/render mixins to compose, the abstraction is at the wrong layer. Lift to a single mechanism.
2. **Find vanilla's existing primitive before inventing one** (14d-2 / 14d-3 — discovery phase) — vanilla's source has solved most UI problems already; investigate end-to-end before designing custom.
3. **Pre-empted dispatch owns responsibility** (14d-2.5) — once a layer pre-empts an input decision, the entire downstream pipeline must respect it. Half-measures leak responsibility back to consumers.
4. **Audit existing surface before parallel one** (14d-2.7) — when adding a test/probe/scaffolding surface, audit existing consumers first; parallel surfaces get shadowed and scrapped.
5. **Render order matters when wrapping vanilla widgets — manual in custom pipeline** (14d-3) — vanilla's renderables-list paints before custom pipelines; widget-wrapping elements need manual render after panel background.
6. **Follow vanilla when wrapping — don't cut features inherited for free; don't add API vanilla doesn't have** (14d-4) — wrap-vanilla discipline applies to BOTH directions; keep what vanilla provides; don't extend the API surface beyond vanilla's.
7. **Modal regions need structural exclusivity, not dispatcher-order tweaks or element-claim semantics** (14d-5) — when a region must be inert-behind, the dispatcher enforces it structurally; element-level claim semantics break under edge-flip / overlap.

Heuristics 1, 2, 3 are dispatcher / mechanism-design discipline. Heuristics 4 is process discipline (audit-before-build). Heuristics 5, 6 are wrap-vanilla discipline. Heuristic 7 is the dispatcher-level parallel of heuristic 3 at element granularity.

Sibling pairs:
- **3 + 7** form a "click-eat at the right level" doctrine: panels for context-wide modals (3); elements for transient overlays (7).
- **2 + 5 + 6** form the wrap-vanilla doctrine: discover (2), respect render order (5), follow API surface (6).
- **1 + 4** are independent process disciplines (compose-correctly-from-the-start; audit-before-parallel-surface).

All seven are saved to project memory under `feedback_*.md` files; indexed in `MEMORY.md`.

---

## 6. Process meta — round-count discipline tightening

Phase 14 saw a measurable improvement in advisor-round efficiency across sub-phases:

| Sub-phase | Rounds | Notes |
|---|---|---|
| 14b (M7) | 3 | Heaviest mechanism; first under post-13 numbering; round 3 was mostly mechanical sweeps |
| 14c (M8) | 2 | Cleaner; round 2 was framing refinement (helpers vs containers) |
| 14d-1 | 1 + inline | Bounded scope; modal scaffolding folded inline |
| 14d-2 | 1 + inline | ScrollContainer + new PanelElement methods; cleanly bounded |
| 14d-2.5 | 1 + inline | M9 mechanism; folded out of 14d-1's modal scaffolding |
| 14d-2.6 | (superseded) | Wrong-shaped; deleted — see heuristic 4 |
| 14d-2.7 | 1 | Test surface cleanup; explicit Trevor ask |
| 14d-3 | 1 + inline | TextField wrap; lifecycle hooks fold-inline as primitive gap |
| 14d-4 | 1 + inline | Slider wrap; cleanest 14d phase before 14d-5 |
| 14d-5 | 1 + in-implementation pivot | Dropdown bespoke; smoke caught architectural assumption |

The trend: from 3-round mechanism sub-phases (early Phase 14) to 1-round-plus-inline-or-pivot (late Phase 14). Discipline that emerged:

- **Rounds exist to resolve things the next round can't.** Architectural decisions warrant a round; mechanical sweeps fold inline. (Heuristic from `feedback_advisor_round_calibration.md`.)
- **In-implementation pivots aren't round-2.** When implementation surfaces a finding (primitive gap, architectural assumption violated by smoke), file the finding, fix structurally, document — don't trigger a full advisor round unless the finding shifts scope materially.
- **Smoke catches what design discussions can't.** Edge-flipped popover overlapping a Reset button (14d-5) wasn't surfaced in any round; it took in-game testing. The validate-the-product principle held.

**14d-2.6 misstep + 14d-2.7 supersession** is the costliest process-meta event in the phase. Lesson distilled to heuristic 4. The sub-phase folder was deleted (only 14d-2.7's REPORT remains as the canonical record); the heuristic carries forward.

---

## 7. THESIS / PALETTE / canonical doc impact

**THESIS.md** — no new principles added in Phase 14. The phase shipped under existing principles (8 lens pattern, 10 four-context, 11 primitive-test-sentence). Heuristics 1–7 are below-principle calibration; they don't escalate to THESIS-level principles in v1.

**PALETTE.md** — six new palette entries shipped (TextField, Slider, Dropdown, ScrollContainer, ConfirmDialog, AlertDialog). Updates carried inline to PALETTE.md during each sub-phase.

**CONTEXTS.md** — no new contexts; clarifications during 14d-3 / 14d-4 / 14d-5 about widget-wrapping cross-context applicability (MenuContext + StandaloneContext yes; SlotGroupContext + HudContext no for interactive elements).

**DEFERRED.md** — Phase-14 deferrals consolidated under per-element subsections. Notable carry-forward items:
- Outside-click-dismiss for Dropdown (fold-on-evidence; vanilla precedent agrees with deferral)
- Keyboard navigation for Dropdown popover (fold-on-evidence)
- Visibility-driven attach/detach lifecycle (deferred from 14d-3 onward; fold when consumer surfaces need)
- Modal-with-TextField/Slider arrow-key issue (M9 keyboard mixin eats arrow keys; defer until consumer surfaces)
- `renderOverlay` separate-pass for general overlay-Z-order (single-Dropdown-per-panel via consumer discipline for v1)

**Mechanisms/** — three new mechanism docs (M7, M8, M9) authored design-doc-first per established convention.

**Elements/** — five new element design docs (TEXT_FIELD.md, SLIDER.md, DROPDOWN.md, SCROLL_CONTAINER.md, dialog docs). Each authored round-1-design pre-implementation; refined inline during sub-phase execution.

---

## 8. Test surface state

**Public test entry point:** Inventory "Test" button at `RIGHT_ALIGN_TOP` of `InventoryScreen` + `CreativeModeInventoryScreen`. Single click runs every automated probe (library contracts M1–M18 + validator scenario aggregators V0–V8) then opens the single-panel scrollable Hub for visual scenarios.

**Library contracts now passing (M1–M18):**

| Contract | Sub-phase added | Scope |
|---|---|---|
| M1–M5 (canonical guarantees) | Pre-Phase 14 | Composability, Substitutability, SyncSafety, Uniform, Inertness |
| M6 (RegionMath) | Pre-Phase 14 | Pure region resolution |
| M7 (SlotState round-trip) | Pre-Phase 14 | Per-slot state channel persistence |
| M8 (storage round-trip) | Phase 14b | M7 mechanism contract |
| M9 (layout math) | Phase 14c | M8 mechanism contract |
| M10 (modal click-eat) | Phase 14d-1 | Panel.tracksAsModal flag + dispatcher decision |
| M11 (dialog composition) | Phase 14d-1 | ConfirmDialog + AlertDialog builder validation |
| M12 (ScrollContainer math) | Phase 14d-2 | Scroll offset + viewport math + builder validation |
| M13 (modal-scroll dispatch) | Phase 14d-2 | M9 + ScrollContainer composition |
| M14 (opacity dispatch) | Phase 14d-2.5 | Multi-panel state coverage |
| M15 (lambda lifecycle) | Phase 14d-2.5 | `.activeOn` / `.deactivate` lifecycle |
| M16 (TextField builder) | Phase 14d-3 | Required fields + null guards + fluency |
| M17 (Slider builder) | Phase 14d-4 | Required fields + null guards + fluency |
| M18 (Dropdown builder) | Phase 14d-5 | Required fields + null guards + empty-list-throws + fluency |

18 library contracts total. All green at phase close.

**Validator scenario coverage:** V0 Mini-App, V1.1 Palette Matrix, V1.2 Composed, V4.1 Lifecycle, V4.2 Standalone + Inventory Deco + HUD, V5 Slot Interactions (covering V5/5.5/5.6/5.7), V7 HUD Probes, M5 Region Probes, plus dedicated standalone smoke screens for TextField (14d-3), Slider (14d-4), Dropdown (14d-5).

---

## 9. Phase 14 exit criteria — audit

| Criterion | Status |
|---|---|
| All sub-phases shipped per individual exit criteria | ✓ — 10 sub-phases; 1 superseded (14d-2.6 → 14d-2.7) |
| Inventory Test-button contracts green; new contracts added for M7/M8/Dropdown/etc. | ✓ — 18 library contracts (M1–M18); 11 added during Phase 14 (M7–M18 inclusive of new mechanism contracts and palette builder validations) |
| Phase 14 REPORT at `Phases/14/REPORT.md` | ✓ — this document |

**`/mkverify all` reference in original exit criteria is stale.** The 14d-2.7 cleanup deleted all `/mkverify` chat commands; the canonical entry point is now the inventory Test button. PHASES.md updated inline at this close.

---

## 10. Phase 14 entry conditions met for Phase 15

Phase 15 (Consumer Migration) entry checklist:

- [x] Library palette substantively complete (TextField, Slider, Dropdown, ScrollContainer, dialogs, modal flags)
- [x] Three mechanisms shipped (M7, M8, M9) with consumer-ready APIs
- [x] M5 region system mature (extended in 14d-2.5 with M9 opacity composition)
- [x] Test surface stable (single-click entry; library contracts green)
- [x] Calibration set captured (heuristics 1–7) — informs Phase 15 anti-patterns
- [x] DEFERRED.md captures every Phase-14 fold-on-evidence item — Phase 15 surfacings carry forward to Phase 16

**Phase 15 sub-phase ordering** (per PHASES.md):
- 15a (inventory-plus): Settings gear + sandboxes buttons → M5 regions; sort-lock migration to M1
- 15b (inventory-plus): F8 equipment panel; F9 pockets panels (F9 needs UI-structure clarification with Trevor first)
- 15c (inventory-plus): F15 peek panel via M4 dynamic pre-allocation
- 15d (shulker-palette): SP-F1 peek toggle via Pattern 2 injection on peek panel
- 15e (sandboxes + agreeable-allays): M3 scope-down completions; sandboxes settings-button click failure diagnosis

**Phase 15 discipline:** *surface-finding posture, not fix-in-phase.* Each sub-phase migrates one mod; if migration surfaces a library gap, file the finding for Phase 16 (Library Hardening). Don't fold library work into Phase 15 unless the gap is genuinely trivial.

**Open question to surface before 15b:** F9 pockets panels need UI-structure clarification with Trevor before implementation begins.

---

## 11. Library shape at Phase 14 close

**`menukit/` package count:** ~70 source files across `core/`, `screen/`, `inject/`, `mixin/`, `verification/`, `state/`.

**Public API surface** (entry points consumers depend on):

- `MenuKit.init()` / `MenuKitClient.onInitializeClient()` — initialization
- `MenuKitScreenHandler.builder(...)` + `Panel.builder(...)` + element constructors — screen + panel + element construction
- `ScreenPanelAdapter` (lambda + region constructors) — vanilla-screen panel injection
- `MKHudPanel.Builder` — HUD panels
- Element palette: `Button`, `TextLabel`, `Icon`, `ItemDisplay`, `Divider`, `ProgressBar`, `Checkbox`, `Radio`/`RadioGroup`, `ScrollContainer`, `TextField`, `Slider`, `Dropdown`, dialogs
- Layout helpers: `Row`, `Column`, `Grid` (M8)
- Mechanisms: `MKSlotState` + `SlotStateChannel` (M1), `MenuRegion`/`HudRegion`/`StandaloneRegion` (M5), `Panel.opaque/dimsBehind/tracksAsModal` flags (M9), `StorageAttachment` (M7)
- Test entry: `ContractVerification.runAll(ServerPlayer)`

**No deprecations / breaking changes during Phase 14** beyond MKFamily removal (14a, intentional) and the test-surface relocation (14d-2.7, intentional).

---

## 12. Carried forward (deferred to Phase 16+)

Phase 16 (Library Hardening) is the dedicated home for Phase 15 surfacings. Pre-emptively known carry-forward items from Phase 14:

- **Outside-click-dismiss for Dropdown** (14d-5) — fold-on-evidence; vanilla precedent agrees
- **Keyboard navigation for Dropdown popover** (14d-5) — fold-on-evidence
- **Visibility-driven attach/detach lifecycle** for widget-wrapping elements (14d-3 onward) — fold when consumer surfaces need
- **Modal-with-TextField/Slider arrow-key issue** (14d-3 / 14d-4) — M9 keyboard mixin eats arrow keys; fold-on-evidence
- **`renderOverlay` separate-pass for overlay Z-order** (14d-5) — current discipline = "Dropdown declared LAST"; fold if multi-Dropdown panels become common
- **`getActiveOverlayBounds` reuse audit** (14d-5) — when 2nd consumer surfaces (context menu, hover popup with actions), validate API shape against multi-overlay collision semantics
- **Range / discrete-step builder convenience** for Slider (14d-4) — fold-on-evidence; v1 paths exist
- **Multi-select / autocomplete / Combobox** (14d-5) — separate primitives; defer

---

**Phase 14 closed.** Library palette complete. Ready for Phase 15 (consumer migrations).
