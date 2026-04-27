# Phase 14c — Close-out REPORT

**Status: complete.** M8 Layout Composition shipped as build-time helpers (Row + Column + ElementSpec + CrossAlign). Per-element `spec(...)` factories on all 10 existing element types. V9 `/mkverify` layout-math probe added (16/16 cases pass). Grid deferred to first-concrete-consumer per Principle 11 per-entry check, advisor-approved.

---

## Executive summary

Phase 14c hypothesis: consumer mods rebuild the same layout arithmetic for every UI ("position three buttons in a row with spacing"); the library has Panel + Element primitives but no help for the most common composition question. M8 fills the gap.

The architecturally load-bearing decision: **layout helpers compute child positions; they do NOT own a render pass.** Row and Column exist only at build time — they consume `ElementSpec`s, compute positions under spacing + cross-axis alignment, and emit positioned `PanelElement`s into the consumer's element list. After `.build()` returns, no Row/Column object survives. Panel renders the emitted elements directly with no indirection. **Panel stays the ceiling of composition** (THESIS principle).

What shipped:

- **`core/layout/` package** — `ElementSpec` interface, `CrossAlign` enum, `Row` + `Column` builders, internal `LayoutEntry` for nesting
- **`spec(...)` static factories** on 10 element types (Button, TextLabel, Toggle, Checkbox, Radio, Icon, ItemDisplay, ProgressBar, Divider, Tooltip)
- **V9 `/mkverify` probe** — 16 cases covering Row, Column, CrossAlign math, nested Column-of-Rows, edge cases (empty/single-element), and IAE on negative spacing
- **Design doc** at `Mechanisms/M8_LAYOUT_COMPOSITION.md` (~700 lines, single-round advisor approval)
- **Grid deferred** to first-concrete-consumer trigger (no element-grid evidence today; SlotGroup serves slot-grid use case)

---

## Architecture decisions

### Helper-not-container — structural test

Parallel to THESIS Principle 10's "what anchor?" test:

> **Does this primitive own a render pass?** If yes, it's a container; rejected. Helpers compute positions at build time; Panel owns the render pass.

Concrete rejections enforced:
- Row / Column **MUST NOT implement `PanelElement`**
- Row / Column **MUST NOT have `render(...)` method**
- Row / Column **MUST NOT exist at runtime** — discharged after `.build()`

Lifetime corollary documented: caching the builder for "later relayout" violates the compute-once model and is unsupported.

### `ElementSpec` as the integration surface

`ElementSpec` defers element construction until position is known by the layout helper:

```java
interface ElementSpec {
    int width();
    int height();
    PanelElement at(int childX, int childY);
}
```

Why: `PanelElement.getChildX()`/`getChildY()` are documented as "Fixed at construction; never mutated" (Principle 4). Helper can't mutate post-construction. `ElementSpec` lets the helper instantiate the element AT its final position — element's "fixed at construction" invariant holds because the element is constructed exactly once, by the helper.

Three alternatives considered and rejected:
- **Mutable element constructors** (Row sets childX/Y after construction) — violates Principle 4
- **Lambda `(x, y) -> PanelElement`** (no ElementSpec surface) — consumers repeat dimensions in lambda + helper declaration
- **Builder-scoped per-element-type methods** (`Row.button(...).text(...)`) — scales badly; ~11 element types × 2-3 helpers = 30+ builder methods

ElementSpec is the least-bad option. Each element type adds a ~5-line static `spec(...)` factory.

### Scope: ship Row + Column, defer Grid

Same Principle 11 per-entry discipline as M7's `blockPortable` / `entityAttached` deferrals:
- **Row** has 3 concrete consumer cases (dialog button rows, settings form rows, V7 hint rows). ✓
- **Column** has 3 concrete cases (V7 stacking, dialog body, settings sections). ✓
- **Grid** has 0 concrete element-grid cases. SlotGroup's `cols(n).rows(m)` serves slot-grid use case. Element-grid waits for evidence.

PHASES.md §14c originally named all three; living-document scope adjustment with documented rationale is part of the phase's job. Grid adopts M8 additively when first concrete consumer surfaces.

### Cross-axis alignment default: START

Matches the pre-M8 manual-layout pattern (children placed at row-/column-origin without cross-axis adjustment). For mixed-height/mixed-width consumer cases, `CENTER` is opt-in. Evidence can update the default later if `CENTER` dominates.

### Invisible elements reserve space (not collapse)

Layout positions are computed once at `.build()` time and frozen on emitted elements. Invisible elements leave gaps; siblings don't shift. Consumers wanting reflow rebuild the Panel. Matches Panel's existing auto-size semantics for hidden elements.

### Nesting via `.addRow()` / `.addColumn()`

Row.Builder and Column.Builder expose `.addRow(Consumer<Row.Builder>)` and `.addColumn(Consumer<Column.Builder>)` for nesting. Internally, nested helpers register a `LayoutEntry` whose dimensions reflect the nested layout's bounding box and whose emit-function delegates to the nested builder's `buildAt(x, y)`. Outer helpers translate the inner emission by their computed child offset.

Result: flat `List<PanelElement>` with all positions baked in. No nested helper objects survive `.build()`.

The uniform-surface alternative (Row-as-`ElementSpec`) was considered and rejected by advisor in round 1 — would require widening `ElementSpec.at()` to return `List<PanelElement>` and create minor ugliness at the single-element site.

---

## What shipped

### Library — new

| File | Role |
|---|---|
| `core/layout/ElementSpec.java` | Public interface — width/height + `at(x, y)` factory |
| `core/layout/CrossAlign.java` | Enum — START / CENTER / END |
| `core/layout/Row.java` | Public final class with nested Builder; horizontal layout helper |
| `core/layout/Column.java` | Parallel to Row, vertical axis |
| `core/layout/LayoutEntry.java` | Package-private — generalizes "thing the layout can place" for nesting |

### Library — modified (10 element types add `spec(...)`)

| Element | Spec factory shape |
|---|---|
| `Button` | `Button.spec(width, height, text, onClick)` + disabled-predicate overload |
| `TextLabel` | `TextLabel.spec(text)` (static, font-inferred dimensions) + `spec(w, h, supplier)` (dynamic, consumer-declared dimensions) |
| `Toggle` | `Toggle.spec(width, height, initialState, onToggle)` + disabled-predicate overload |
| `Checkbox` | `Checkbox.spec(initialState, label, onToggle)` + disabled-predicate overload (font-inferred width) |
| `Radio` | `Radio.spec(value, label, group)` + disabled-predicate overload (font-inferred width, generic `<T>`) |
| `Icon` | `Icon.spec(width, height, sprite)` + supplier overload |
| `ItemDisplay` | `ItemDisplay.spec(stack)` (default size, all overlays) + 3 explicit-config overloads |
| `ProgressBar` | `ProgressBar.spec(width, height, supplier)` + full-config overload |
| `Divider` | `Divider.horizontalSpec(length)` + `verticalSpec(length)` (each with explicit-color/thickness overload) |
| `Tooltip` | `Tooltip.spec(text)` (static, font-inferred dimensions) + `spec(w, h, supplier)` (dynamic, consumer-declared) |

### Library — modified (verification harness)

`verification/ContractVerification.java` — added contract 9 (M8 layout-math probe). Updated class javadoc, summary line, runAll log message ("eight" → "nine contracts"), added contract enumeration list.

V9 probe covers:
1. Row of 3 × (20, 20) at (10, 5) spacing 4 — child Xs at 10/34/58, Y=5
2. Column of 3 × (20, 10) at (0, 0) spacing 2 — child Ys at 0/12/24
3. CrossAlign.CENTER with mixed-height children (10, 20) — short child centered at Y=5, tall child at Y=0
4. Nested Column-of-Rows — 2×2 grid via composition; positions verified manually
5. Edge cases: empty Row → empty list; single-element Row at origin
6. Negative spacing → IllegalArgumentException

`/mkverify` aggregator now reports 9 contracts. V9 passed 16/16 in dev-client smoke test.

### Documentation

- `Mechanisms/M8_LAYOUT_COMPOSITION.md` — design doc (~700 lines)
- `Phases/14c/REPORT.md` — this document
- `PHASES.md` — `← current` advanced from 14b to 14c; "Current phase" footer updated

---

## What didn't ship / deferred

- **Grid** — no concrete element-grid consumer. Adopts M8 additively when surface trigger fires. Slot-grid use case stays with SlotGroup.
- **Justify-content distribution** (space-between, space-around, space-evenly) — no concrete consumer evidence; uniform `spacing(int)` only in v1.
- **Per-child spacing overrides** — no consumer evidence.
- **Baseline alignment** — elements have no baseline concept; not invented.
- **Responsive layout / reflow on resize** — positions frozen at `.build()` time per "compute once" model. Consumers needing responsive behavior rebuild the Panel.
- **Margin / per-element padding** — elements are atomic to layout.
- **Auto-sizing / flex-grow semantics** — children declare their own width/height via ElementSpec.

---

## Process notes

**One advisor design round** before implementation. Round 1 verdict was substantive across §4.1 structural test (approved as the right test sentence), §4.2 ElementSpec shape (approved over three rejected alternatives), §4.3 Grid defer (approved per Principle 11 per-entry), and the seven §10 open questions (4 advisor verdicts: Q1 defer Grid, Q3 keep `.addRow(Consumer)` over uniform-surface alternative, Q4 START default, Q6 reserve-space for invisible elements; 3 implementer pulls accepted: Q2 ElementSpec stands, Q5 edge cases as proposed, Q7 content-area-relative).

Two doc nits identified in round 1 verdict folded inline rather than triggering round 2:
- §4.1 lifetime corollary added (closes "cache the Builder for later" loophole)
- §4.2/§5.2/§6 TextLabel supplier-text spec — explicit-dimension overload documented as required path for dynamic content; single-arg `spec(text)` clarified as static-text only

Round count: one design round + inline nits + implementation. Calibration from 14b's verdict ("rounds exist to resolve things the next round can't") applied — clean execution, no round 2 ceremony needed.

---

## Verification

### Automated (`/mkverify`)

`/mkverify` aggregator now runs 9 contracts. All PASS in dev-client smoke test:

| Contract | Result |
|---|---|
| 1 Composability | mixin fired on both vanilla and MK slot types ✓ |
| 2 Substitutability | 46/46 MK slots pass `instanceof Slot` ✓ |
| 3 SyncSafety | 10 toggles, 0 inconsistencies ✓ |
| 4 Uniform | findGroup() uniform across vanilla + MK handlers ✓ |
| 5 Inertness | 4/4 hidden inert, 4/4 restored on visible ✓ |
| 6 RegionMath | 27/27 cases ✓ |
| 7 SlotState | 7/7 cases ✓ |
| 8 M7 storage | 6/6 cases ✓ |
| **9 M8 layout** | **16/16 cases ✓** |

Validator scenarios V2–V7 also green; no regressions from M8 introduction.

### Integration

Phase 14d (palette additions — ConfirmDialog, AlertDialog, text input, slider, dropdown, scroll container) will be the first real-consumer user of M8 helpers. Visual verification through Phase 14d's dialog UIs.

---

## Phase 14d entry conditions (met)

- M8 layout primitive shipped + verified via V9 probe
- All existing element types have `spec(...)` factories — Phase 14d's new dialog primitives can use Row/Column from day one
- No regressions across V2–V7 scenarios or 8 other canonical contracts
- Design doc reflects shipped reality
- PHASES.md advanced; Phase 14c REPORT lands

Phase 14d (palette additions: ConfirmDialog, AlertDialog, text input, slider, dropdown, scroll container) starts when kicked off. Per PHASES.md §14d, design-doc-first per element. Each new element gets its own mini-design-doc under `Elements/`. ConfirmDialog will be the canonical first user of `Row.at(...).add(Button.spec(...))` for its button bar.

---

## Diff summary

~16 files modified, 6 new, 0 deleted. New library surface ~600 LOC (mostly in Row.java + Column.java + per-element specs). No deletions. Pure additive phase.

**Phase 14c closed.**
