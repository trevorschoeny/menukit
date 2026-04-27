# M8 — Layout Composition

**Phase 14c mechanism — build-time helper** (per `PHASES.md` §14c).

**Status: draft — round 1, awaiting advisor review.**

**Load-bearing framing (PHASES.md §14c verbatim):**

> Layout HELPERS that compute child positions, NOT nested containers. Preserves THESIS "Panel is the ceiling of composition." Row/Column/Grid wrap a list of elements with computed child offsets that the parent Panel renders directly; no new container abstraction below Panel.

This is the architectural thesis. Every design decision below checks against it. If the proposed API drifts toward "Row owns a render pass," the thesis is broken and the design needs to reshape.

---

## 1. Purpose

Consumer mods building realistic UI — settings screens, dialogs, HUD stacks, equipment panels — repeatedly hand-roll the arithmetic that places sibling elements next to each other with spacing:

```java
// Today, consumer-side manual layout arithmetic
int x = 10, y = 20, spacing = 4;
Button ok = new Button(x, y, 60, 20, Component.literal("OK"), this::onConfirm);
Button cancel = new Button(x + 60 + spacing, y, 60, 20, Component.literal("Cancel"), this::onCancel);
```

This hand-rolling fails Principle 7 (validate-the-product) in a subtle way: the library provides the Panel + Element primitives, but offers no help for the most common question a consumer has ("how do I put these three things in a row with even spacing?"). Every consumer reinvents the same loops.

M8 provides the missing primitive: a build-time layout helper that takes a sequence of elements (or element specs) with declared dimensions, computes per-child positions under a spacing/alignment policy, and emits positioned elements into the parent Panel. The helper disappears at runtime — the Panel renders positioned elements directly, with no intermediate container in the render chain.

---

## 2. Consumer evidence

### Row — horizontal layout

- **ConfirmDialog button row** (Phase 14d): the canonical "OK / Cancel" two-button pattern. Multiple dialog variants need it.
- **Settings panel form rows**: label-on-left + control-on-right pairs. Future IP config screen, any consumer building settings UI.
- **V7-style HUD hint row**: icon + text pairs stacked horizontally. Current V7 manually computes positions; Row formalizes.

### Column — vertical layout

- **V7 HUD stacking**: multiple text labels stacked vertically with uniform spacing. Current code manually assigns childY; Column replaces the arithmetic.
- **ConfirmDialog body**: title, description, button-row stacked vertically. Column of (TextLabel, TextLabel, Row).
- **Multi-section settings panels**: IP config likely ships with a vertical arrangement of toggle-rows.

### Grid — 2D arrangement

- **IP equipment panel slot layout**: 10 slots in a configurable grid — BUT this is a SLOT grid, served by SlotGroup's existing `cols(n).rows(m)` API. M8's Grid would be for elements (not slots).
- **Hypothetical: icon palettes, rune selection grids, 2D stat boards** — no concrete consumer today.

**Rule of Three check:**
- Row: 3 concrete cases (dialog buttons, settings form rows, V7 hint rows). ✓
- Column: 3 concrete cases (V7 stacking, dialog body, settings sections). ✓
- Grid: 0 concrete element-grid cases. SlotGroup serves the slot-grid pattern; general element-grid has no consumer demand today.

**Per §4.3 below, v1 ships Row + Column only.** Grid defers to first-concrete-consumer per Principle 11 per-entry check (same discipline as M7's blockPortable/entityAttached). This diverges from PHASES.md §14c's "Row / Column / Grid" naming — see §4.3 for the rationale and advisor-verdict request.

---

## 3. Scope

- **Build-time layout helpers** — Row and Column in v1. No runtime helper objects; no render passes below Panel.
- **Take element specs with intrinsic dimensions + origin + spacing policy → emit positioned PanelElements.**
- **Nesting is compositional** (Row in Column in Row) — each helper emits flat positioned-element lists; outer helpers translate inner lists by their child offsets.
- **Minimum alignment primitives** — cross-axis START/CENTER/END; no justify-content distribution in v1 (add on evidence).
- **Does not supersede SlotGroup's slot grid.** SlotGroup continues to own slot layout; M8 helpers operate on elements only.
- **No mutation of existing element APIs** — element childX/childY stay "fixed at construction; never mutated" per PanelElement's doc. M8 constructs elements AT their computed position, not post-hoc.

---

## 4. Design decisions

Each decision is a draft position. §4.1 (helper-not-container framing) and §4.2 (API shape) are load-bearing.

### 4.1 Helper-not-container — the structural test

**Structural test sentence (parallel to Principle 10's "what anchor?" test):**

> *Does this element own a render pass?* A helper computes positions at build time; Panel owns the render pass. A container would itself render children. If a proposed layout primitive requires a runtime render dispatch to paint its children, it is a container, and the design fails — Panel stops being the ceiling of composition.

**Concrete application:**
- Row/Column **MUST NOT implement `PanelElement`**. They are not elements.
- Row/Column **MUST NOT have a `render(...)` method**.
- Row/Column **MUST NOT exist as objects at runtime**. They are construction-time scaffolding that produces positioned elements and is discarded.
- Panel.getElements() returns the same `List<PanelElement>` shape it returns today; Row/Column are invisible at that layer.

**What passes the test:** a helper that, given a list of element specs + spacing config + origin, returns a list of `PanelElement` instances with their `childX`/`childY` already set. These instances are added to the Panel in the standard way. Panel iterates and renders them directly. No indirection.

**What fails the test:** a "LayoutPanel" or "LayoutContainer" that wraps elements and dispatches render calls to them. Even if the wrapper is described as "Panel-compatible," it's a sub-container. Rejected.

**Lifetime corollary:** the Row / Column builder exists during the construction call chain. After `.build()` returns, no Row or Column reference should be retained — the helper has discharged its job, and its internal state is dead. Caching the builder for later "relayout" or attempting to mutate the emitted element list violates the compute-once model and undermines the structural test.

This test appears in §11 as a non-goal explicitly.

### 4.2 API shape — `ElementSpec` + helper builder

**Core surface** (v1 proposal):

```java
public interface ElementSpec {
    int width();
    int height();
    /** Construct the element positioned at (childX, childY). */
    PanelElement at(int childX, int childY);
}
```

Each element type provides a static `spec(...)` factory returning an `ElementSpec`:

```java
// In Button.java — added in Phase 14c
public static ElementSpec spec(int width, int height, Component text, Consumer<Button> onClick) {
    return new ElementSpec() {
        @Override public int width() { return width; }
        @Override public int height() { return height; }
        @Override public PanelElement at(int x, int y) {
            return new Button(x, y, width, height, text, onClick);
        }
    };
}
```

Row and Column take a sequence of ElementSpecs and emit positioned PanelElements:

```java
// In core/Row.java (new)
public final class Row {
    public static Builder at(int originX, int originY) { ... }

    public static final class Builder {
        public Builder spacing(int px) { ... }
        public Builder crossAlign(CrossAlign align) { ... }  // START, CENTER, END
        public Builder add(ElementSpec spec) { ... }
        public List<PanelElement> build() { ... }
    }
}
```

**Usage:**

```java
List<PanelElement> buttonRow = Row.at(20, 30).spacing(4)
    .add(Button.spec(60, 20, Component.literal("OK"), this::onConfirm))
    .add(Button.spec(60, 20, Component.literal("Cancel"), this::onCancel))
    .build();

// Pass to Panel constructor alongside any other elements
Panel p = new Panel("confirm-dialog", buttonRow, true, PanelStyle.RAISED, ...);
```

Or composed with other elements:

```java
List<PanelElement> dialogContents = new ArrayList<>();
dialogContents.add(new TextLabel(0, 0, Component.literal("Delete this sandbox?")));
dialogContents.addAll(Row.at(0, 30).spacing(4)
    .add(Button.spec(60, 20, Component.literal("OK"), confirm))
    .add(Button.spec(60, 20, Component.literal("Cancel"), cancel))
    .build());
Panel p = new Panel("confirm", dialogContents, ...);
```

**Why this shape passes the structural test:**
- `Row.at(...).build()` returns `List<PanelElement>` — a plain list of elements with final positions.
- Row exists only during the `.build()` chain call. After `.build()` returns, no Row object survives.
- Panel receives a standard element list. Panel.render iterates as always — no knowledge of "Row."
- No runtime indirection; no render-pass-below-Panel.

**Why ElementSpec (rather than passing already-constructed elements):**
- Button's `childX`/`childY` are final (PanelElement doc: "Fixed at construction; never mutated").
- If Row took pre-constructed Buttons and tried to "reposition" them, either (a) mutate final fields (violates Principle 4 declared-structure), or (b) construct new Buttons from old ones (complicates every element type with a copy-with-position constructor).
- ElementSpec defers the element construction until the position is known. Element's "fixed at construction" invariant holds — the element is constructed ONCE, at its final position, by Row.
- Cost: every element type that wants to be usable in Row/Column gets a `spec(...)` static factory. Mild surface growth; predictable per-element addition.

### 4.3 Ship v1: Row + Column. Defer Grid.

PHASES.md §14c says "Row / Column / Grid." Consumer evidence (§2) supports Row and Column clearly; Grid has no concrete element-grid consumer (slot grids are served by SlotGroup's existing `cols(n).rows(m)` API).

**Proposed v1 scope:**
- Row (horizontal layout)
- Column (vertical layout)

**Deferred to first-concrete-consumer trigger:**
- Grid (2D element layout)

**Rationale:** same Principle 11 per-entry discipline applied in M7 (blockPortable + entityAttached deferred for lack of concrete consumer evidence). Grid's per-entry cost is modest (could be expressed as Row-of-Columns internally) but shipping it without consumer evidence violates Rule-of-Three. When a real consumer wants element-grid layout (e.g., an icon picker, a 2D stat board), Grid adopts M8 additively — the framework shipped with Row/Column makes Grid a small extension.

**Counterargument:** PHASES.md §14c named all three. Scope-narrowing diverges from the phase plan.

**Counter-counterargument:** PHASES.md is explicitly living document (§0-ish — "Living document" callout). Deviating with documented rationale is part of the phase's job. M7's Phase 14b shipped 4 factories instead of 6 with advisor approval; same discipline applies here.

**Request for advisor:** is scope-narrowing Grid acceptable under Principle 11, or does the PHASES.md commitment + "catalog completeness" argument (Row+Column without Grid feels half) warrant shipping all three?

### 4.4 Spacing and alignment primitives — minimum v1

**Spacing:** uniform integer pixel spacing between adjacent children. Declared via `.spacing(int px)` on the helper builder.

**Cross-axis alignment:** for Row, cross-axis is vertical; for Column, cross-axis is horizontal. Three values shipped v1:
- `START` — children align to the low edge (Row: top of bounding row; Column: left edge)
- `CENTER` — children center on the cross-axis
- `END` — children align to the high edge

Default is `START` (matches current manual-layout patterns).

**Explicit non-goals for v1** (add on evidence):
- Justify-content-style distribution (space-between, space-around, space-evenly) — no concrete consumer case
- Per-child spacing overrides — no concrete consumer case
- Baseline alignment — elements have no baseline concept; don't invent one
- Margin/padding on individual elements — elements are atomic to layout

### 4.5 Nesting semantics

Row and Column can nest arbitrarily. A Row inside a Column inside a Row composes because each helper emits a flat list of positioned elements, relative to its own origin. Outer helpers translate the inner list by the outer layout's computed child offset before emitting.

**Example — Column of two Rows:**

```java
Column.at(10, 20).spacing(8)
    .addRow(r -> r.spacing(4)
        .add(Button.spec(40, 20, Component.literal("A"), a))
        .add(Button.spec(40, 20, Component.literal("B"), b)))
    .addRow(r -> r.spacing(4)
        .add(Button.spec(40, 20, Component.literal("C"), c))
        .add(Button.spec(40, 20, Component.literal("D"), d)))
    .build();
```

**How it works internally:**
1. Inner Row-1 (at 0, 0 relative, spacing 4) computes child positions: A at (0, 0), B at (44, 0). Row-1 returns its bounding dimensions: (84, 20).
2. Inner Row-2 likewise: C at (0, 0), D at (44, 0). Bounding: (84, 20).
3. Column places Row-1 at (10, 20), Row-2 at (10, 48) (20 + row-1 height 20 + spacing 8 = 48).
4. Column translates Row-1's elements by (10, 20): A at (10, 20), B at (54, 20).
5. Column translates Row-2's elements by (10, 48): C at (10, 48), D at (54, 48).
6. Column returns flat list [A@(10,20), B@(54,20), C@(10,48), D@(54,48)].

Panel receives 4 positioned Buttons. No Row or Column objects at runtime.

**Implementation:** the helper builder methods (`.addRow(Consumer<Row.Builder>)`, `.addColumn(...)`) let helpers compose while preserving the flat-output guarantee.

### 4.6 Relationship to SlotGroup

SlotGroup has a 2D grid layout via `cols(int).rows(int)`. This is a SLOT grid — it places `MenuKitSlot` instances (vanilla's `Slot` subclass) in a grid pattern for the slot-sync protocol. M8's Row/Column are ELEMENT helpers — they place `PanelElement` instances (buttons, text labels, icons).

**They don't overlap:**
- SlotGroup's layout is for slots (vanilla types in vanilla's sync protocol).
- Row/Column is for elements (MenuKit's PanelElement, rendered in Panel's element layer).

**They don't compose directly:** you cannot put a SlotGroup inside a Row. SlotGroups live on the handler (per-panel, via `MenuKitScreenHandler.getGroupsFor(panelId)`); elements live on the Panel. They're at different architectural layers.

**Grid (deferred)** — when Grid eventually ships, it's for ELEMENTS only. If a consumer wants a grid of slots, they keep using `SlotGroup.cols(n).rows(m)`. If they want a grid of elements (icons, buttons), they use M8.Grid. No naming collision.

### 4.7 Relationship to Panel auto-sizing

Panel.getWidth() / getHeight() compute bounding-box extent from visible elements. This still works post-M8: the elements Row/Column emit have real childX/childY values, so Panel's auto-size math picks them up naturally. No Panel-side changes needed.

For pinned-size panels (via `panel.size(w, h)`) — M8 layout produces positions inside the panel's content area; if layout extends beyond pinned bounds, overflow behavior is the same as today (elements just render past panel edges; pinned size is for stacking math only).

---

## 5. Consumer API — before / after

### 5.1 ConfirmDialog button row (Phase 14d consumer; canonical Row case)

**Before** (hypothetical, pre-M8):

```java
int buttonY = 30, buttonW = 60, buttonH = 20, spacing = 4;
int totalW = buttonW * 2 + spacing;
int startX = (panelWidth - totalW) / 2;  // consumer computes centering
Button ok = new Button(startX, buttonY, buttonW, buttonH,
        Component.literal("OK"), this::onConfirm);
Button cancel = new Button(startX + buttonW + spacing, buttonY, buttonW, buttonH,
        Component.literal("Cancel"), this::onCancel);
List<PanelElement> elements = List.of(titleLabel, bodyLabel, ok, cancel);
```

**After (M8):**

```java
List<PanelElement> elements = new ArrayList<>();
elements.add(titleLabel);
elements.add(bodyLabel);
elements.addAll(Row.at(centeredX, 30).spacing(4)
    .add(Button.spec(60, 20, Component.literal("OK"), this::onConfirm))
    .add(Button.spec(60, 20, Component.literal("Cancel"), this::onCancel))
    .build());
```

Consumer still computes the origin (`centeredX`) but stops doing per-child X-stepping. Real savings at Column-of-Rows depth — dialog body + button row composes naturally.

### 5.2 V7 HUD vertical stack (Column canonical case)

**Before:**

```java
int lineH = 10, spacing = 2;
TextLabel line1 = new TextLabel(0, 0, Component.literal("Line 1"));
TextLabel line2 = new TextLabel(0, lineH + spacing, Component.literal("Line 2"));
TextLabel line3 = new TextLabel(0, (lineH + spacing) * 2, Component.literal("Line 3"));
```

**After:**

```java
List<PanelElement> lines = Column.at(0, 0).spacing(2)
    .add(TextLabel.spec(Component.literal("Line 1")))
    .add(TextLabel.spec(Component.literal("Line 2")))
    .add(TextLabel.spec(Component.literal("Line 3")))
    .build();
```

`TextLabel.spec(Component)` (static text) infers width/height from font metrics at spec construction time (width = font.width(text); height = font.lineHeight). For **dynamic / supplier-driven text**, use the explicit-dimension overload `TextLabel.spec(width, height, supplier)` — the consumer declares max-width up front so Row/Column layout stays stable as text values change at runtime.

### 5.3 Settings form row (future IP config screen)

Hypothetical, illustrative:

```java
// Each row: label on left, toggle on right
List<PanelElement> row = Row.at(0, 0).spacing(8).crossAlign(CrossAlign.CENTER)
    .add(TextLabel.spec(Component.literal("Sort lock enabled")))
    .add(Toggle.spec(16, 16, ipConfig::sortLockEnabled, ipConfig::setSortLockEnabled))
    .build();
```

`CrossAlign.CENTER` vertically centers the toggle against the TextLabel (which has different height).

---

## 6. Library surface

**New files:**

- `core/layout/ElementSpec.java` — public interface. Width + height + `at(x, y)` factory.
- `core/layout/Row.java` — public final class with nested `Builder`. `Row.at(originX, originY)` entry point; chainable `.spacing(int)`, `.crossAlign(CrossAlign)`, `.add(ElementSpec)`, `.addRow(Consumer<Row.Builder>)`, `.addColumn(Consumer<Column.Builder>)`, `.build() → List<PanelElement>`.
- `core/layout/Column.java` — parallel to Row, vertical axis.
- `core/layout/CrossAlign.java` — enum: START, CENTER, END.

**Modified files (per-element spec factories):**

Each existing element type adds a static `spec(...)` factory method returning `ElementSpec`:

- `core/Button.java` — `Button.spec(width, height, text, onClick)` and overloads.
- `core/TextLabel.java` — two specs:
  - `TextLabel.spec(text)` with `Component` static text — width/height inferred from font metrics at spec time. **Static-text only.**
  - `TextLabel.spec(width, height, supplier)` with `Supplier<Component>` — width/height declared explicitly by the consumer. **Required path for dynamic / supplier-driven text** because supplier values can vary frame-to-frame; auto-inferred width from a single supplier evaluation would freeze layout against a stale snapshot. Consumer declaring max-width up front gives Row/Column stable layout that doesn't reflow when text changes.
- Element design docs (`Toggle.java`, `Checkbox.java`, `Radio.java`, `Icon.java`, `ItemDisplay.java`, `ProgressBar.java`, `Divider.java`, `Tooltip.java`) — each gains a `spec(...)` static factory. Low per-element surface addition.

**No changes to Panel, PanelElement, or any render-pipeline class.** M8 is purely additive at the construction-API surface.

---

## 7. Migration plan — Phase 14c

14c ships:
- Layout helpers (Row, Column, CrossAlign, ElementSpec) as new library files
- Per-element `spec(...)` factories added alongside existing constructors (no deprecation; current constructor shape stays)
- No consumer migration — M8 is additive; existing consumer code continues to work.

Phase 14d (palette additions: ConfirmDialog, AlertDialog, text input, slider, dropdown, scroll container) is the first real-consumer user of M8 helpers. Some Phase 14d designs will use Row/Column internally; those doc specs come when 14d kicks off.

---

## 8. Verification plan

### 8.1 `/mkverify` aggregator probe — M8 layout math

New validator scenario: **V9 — M8 layout math probe.** Similar shape to M7's round-trip probe (§8.1 of M7 doc).

- Register a test Panel with Row-of-three elements (`Button.spec(20, 20, ...)` × 3, spacing 4).
- Assert: first button at (originX, originY); second at (originX + 24, originY); third at (originX + 48, originY).
- Register a Column-of-three; assert Y stepping.
- Register a nested Column-of-Rows; assert flat output positions match manual calculation.
- Register a CrossAlign.CENTER Row with mixed-height children; assert cross-axis centering math.

Runs programmatically; no live screen.

### 8.2 Integration-level (dev client)

When Phase 14d's ConfirmDialog uses M8 Row for its button bar, smoke testing the dialog visually verifies M8 end-to-end. No separate visual probe needed.

---

## 9. Library vs consumer boundary

**M8 provides:**
- Build-time layout helpers (Row, Column) that compute child positions.
- CrossAlign enum for cross-axis alignment policy.
- ElementSpec interface for the helper's input surface.
- Per-element `spec(...)` factories on library-shipped element types.

**Consumers provide:**
- Element specs (via `Button.spec(...)`, etc.) or their own `ElementSpec` implementations for custom elements.
- Origin coordinates (where the layout block starts within the Panel's content area).
- Spacing and alignment choices.

**Library does NOT provide:**
- A runtime Row/Column/Grid object. Helpers exist only at build time; no render pipeline below Panel.
- Layout recalculation APIs — positions are computed once at build time and frozen on the emitted elements (consistent with PanelElement's "fixed at construction" contract).
- Responsive-layout primitives — no media queries, no breakpoints, no auto-reflow on panel resize. Consumers who want responsive behavior rebuild the Panel on resize events.
- Grid (deferred per §4.3).
- Justify-content distribution (deferred per §4.4).

---

## 10. Open design questions — round 1

1. **Scope: ship Grid or defer?** §4.3 argues for defer under Principle 11 per-entry discipline (no concrete element-grid consumer today; SlotGroup serves slot grids; Grid can add additively later). PHASES.md §14c explicitly names Grid. **Advisor input:** is scope-narrow Grid acceptable, or does "catalog completeness" (Row+Column without Grid feels partial) outweigh the strict Principle 11 reading? My pull: defer, with a §11 note naming Grid as "filed, deferred" identical to M7's blockPortable/entityAttached deferrals.

2. **ElementSpec vs alternative shapes.** §4.2 proposes ElementSpec as the helper's input — every element type grows a `spec(...)` factory. Alternatives considered:
   - **(a) Mutable element constructors** — Row takes `new Button(0, 0, ...)` and mutates childX/childY. Rejected: violates Principle 4 (declared structure, no mutation).
   - **(b) Row takes lambdas `(x, y) -> PanelElement`** — no ElementSpec surface, but consumers repeat width/height in both the lambda and Row's dimension declarations. Ugly.
   - **(c) Builder-scoped element construction** — `Row.at(...).button(60, 20, text, onClick)` with Row exposing per-element-type builder methods. Scales badly as palette grows (~11 element types × Row + Column + future Grid = a lot of methods).
   ElementSpec is the least-bad option. **Advisor input:** does the design justify itself, or is there a fourth shape I missed?

3. **Nesting via `.addRow` / `.addColumn` vs accepting `ElementSpec` from nested helper.** §4.5 shows `.addRow(Consumer<Row.Builder>)` as the nesting API. Alternative: nested Row/Column implements `ElementSpec` (width = bounding width, height = bounding height, `at(x, y)` translates the nested layout). Cleaner uniformity — nested Row/Column is just another ElementSpec. **My pull:** alternative. Uniform surface, fewer special-case methods on the builder. Would make §4.5's example become `.add(Row.at(0,0).spacing(4).add(...).build_as_spec())` — wordier but structurally consistent.

4. **Cross-axis alignment default.** §4.4 proposes `START` default. For most Row cases (button bars with uniform-height children), alignment doesn't matter. For Row with mixed-height children (label + control), `CENTER` feels right. **Advisor input:** START vs CENTER as default — which better matches consumer intent?

5. **Error handling for zero-element or zero-spacing cases.** Row with empty `.add(...)` sequence → empty list. Row with one element → single positioned element (no spacing applied). Row with negative spacing → undefined? v1 proposal: zero-element returns empty list; one-element returns `[spec.at(originX, originY)]`; negative spacing rejected with IllegalArgumentException at `.spacing(...)` call. **Advisor input:** any edge cases worth hardening explicitly vs leaving to consumer judgment?

6. **Interaction with supplier-driven visibility.** Elements have `isVisible()` defaulting true; consumers may override for supplier-gated visibility. Panel's auto-size skips invisible elements. Does Row/Column respect `isVisible()` for spacing purposes — i.e., does an invisible element still take layout space, or does it collapse to zero-width?
   - **(a) Invisible elements collapse:** Row recomputes positions each frame based on current visibility. But positions are frozen at construction per §4.2. Collapsing requires re-layout, which contradicts the "compute once" model.
   - **(b) Invisible elements reserve space:** positions stay fixed; hidden elements leave gaps. Predictable layout; the whole row doesn't shift when one element toggles.
   **My pull: (b).** Matches the "positions computed once, frozen" model; consumers who want dynamic reflow rebuild the Panel. **Advisor input:** acceptable, or do consumers reasonably expect collapsing?

7. **Should Row/Column care about Panel padding?** Panel applies its own padding (via PanelStyle) outside the content area. Row at origin (0, 0) places children at the very start of the content area (post-padding). No Panel-side adjustment needed — Row is ignorant of Panel's outer chrome. **My pull:** yes, Row is content-area-relative. This matches PanelElement's existing childX/childY contract. No design question here — flagged for completeness.

---

## 11. Non-goals / out of scope

- **Grid (v1).** Deferred per §4.3. Ships on first concrete element-grid consumer; slot-grid use cases stay with SlotGroup.
- **Justify-content distribution** (space-between / space-around / space-evenly). No concrete consumer case. Add on evidence.
- **Responsive layout / reflow on resize.** Positions are computed once at build time. Panels that need responsive behavior rebuild on resize events (consumer work).
- **Nested-container abstraction.** The structural test in §4.1 explicitly rejects "LayoutPanel" or "sub-Panel" entities. Helpers only.
- **Baseline alignment.** Elements have no baseline concept; don't invent one. Consumers that need fine vertical tuning use `.crossAlign(CENTER)` plus per-element padding.
- **Margin / per-child spacing overrides.** Uniform `spacing(int)` only in v1. Per-child spacing ships on evidence.
- **Auto-sizing of layout elements.** Children declare their own width/height via ElementSpec. Row/Column does not stretch/shrink children to fit a container. If a consumer wants a fill-the-width button, they declare its width explicitly matching the container width.
- **Flex-box semantics** (grow/shrink factors, flex-basis). Overkill for the library's scope.
- **Animation or transitions between layouts.** Layouts are static. Per THESIS scope ceiling "no animation framework beyond notifications."

---

## 12. Summary

M8 ships two build-time layout helpers — **Row** and **Column** — as pure positioned-element emitters. Helpers compute `childX`/`childY` for a sequence of `ElementSpec`s under a spacing + cross-axis alignment policy and return `List<PanelElement>` to the consumer. The emitted elements go directly into the Panel's element list; Panel renders them as it always has. **Helpers do not exist at runtime** — they're construction-time scaffolding that disappears after `.build()` returns.

This preserves "Panel is the ceiling of composition" (THESIS): no sub-container, no render pass below Panel, no runtime indirection. The structural test (§4.1) — "does this primitive own a render pass?" — fails for any container proposal and passes for the helper shape proposed here.

**Grid** is deferred to first-concrete-consumer trigger per §4.3 — element-grid has no current evidence; slot-grid is already served by SlotGroup. Diverges from PHASES.md §14c's naming; advisor verdict requested in §10 Q1.

**Per-element `spec(...)` factories** are the integration surface: ~11 element types add a ~5-line static factory each. No changes to existing element constructors or PanelElement interface. No migration for existing consumers.

**Status: round 1 draft.** Seven open questions in §10, four of which need advisor input (Q1 Grid scope, Q2 ElementSpec justification, Q3 nesting API shape, Q4 cross-align default). Three are implementer-position pulls (Q5 edge-case errors, Q6 invisible-element layout space, Q7 Panel padding awareness).
