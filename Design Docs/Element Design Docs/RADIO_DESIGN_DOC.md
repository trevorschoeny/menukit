# Radio / RadioGroup — design doc

**Element purpose.** Single-selection control over a set of options. `RadioGroup<T>` owns the selected value and fires callbacks on change; individual `Radio<T>` elements render their state by comparing their value against the group's selection.

**First cross-element composition.** Previous Phase 8 elements have been independent. Radio introduces a coordination pattern — multiple PanelElements (the Radios) share state through a non-element coordinator object (the Group). The thesis's "Panel is the ceiling of composition" still holds — the group isn't a containing element, just a state coordinator.

**Works in input-dispatch contexts.** Inventory menus and standalone screens.

---

## The coordination model

Two related types:
- **`Radio<T>`** — a `PanelElement`. One per selectable option. Renders checked or unchecked based on comparison of its value against the group's current selection.
- **`RadioGroup<T>`** — NOT a `PanelElement`. A plain coordinator object holding the group's current selected value and the onSelect callback. Parameterized by the value type T (typically an enum).

Radios reference their group at construction. On click, a Radio sets the group's selection to its own value. The group fires `onSelect` with the new selection. All Radios reading from the group update visually on the next render.

**Why RadioGroup is not a PanelElement.** Radios render independently in panel layout — positioned with absolute childX/childY like other elements. If RadioGroup were a container element holding Radios as children, it would introduce nested composition (conflicting with "Panel is the ceiling"). Instead, RadioGroup is a pure state object; Radios live in panels directly, layouted like any other element.

This matches how common UI toolkits model radio groups (a `ButtonGroup` in Swing isn't a visual container).

### Precedent-setting statement

**RadioGroup-as-coordinator establishes the pattern for any future cross-element coordination: coordinators are plain objects, not PanelElements, preserving "Panel is the ceiling" as a load-bearing principle.** Future elements with similar coordination needs (dropdowns with options, tab bars with tab panels, segmented controls with segments) should follow this pattern rather than proposing container-element alternatives.

The precedent matters beyond Radio. Any element that *could* have justified an exception to "Panel is the ceiling" points to Radio now. Radio's coordinator-not-element choice means future elements have to justify their architectural choices against a held-line precedent rather than a broken one.

---

## Conventions pressure-test

### Convention 1 — Constructor shape `(childX, childY, [width, height,] content, [callback])`

Applies with auto-sizing (like Checkbox). Radio doesn't take width/height — sizes from its label. The radio square is fixed 10×10; total width = `10 + 4 + fontWidth(label)`.

```java
new Radio<T>(int childX, int childY, T value, Component label, RadioGroup<T> group);
```

Reading order: where (childX, childY), what value this radio represents, what the label reads, which group this radio belongs to.

**Refinement to Convention 1 (coordinator-owned callback):**

*"When an element's behavior callback is owned by an external coordinator rather than the element itself, the coordinator reference takes the callback-position slot in the constructor. Radio's RadioGroup parameter is the one Phase 8 case."*

Radio has no element-level callback — the callback lives on the group, shared across all Radios. The group takes the callback-position slot directly, preserving the constructor reading order (`where → what content → what handles 'what happens'`). Only Radio exercises this refinement in Phase 8.

### Convention 2 — Supplier variants for variable content

Applies to the label (same as Checkbox — variable content). Ship both fixed and supplier forms.

```java
new Radio<T>(x, y, T value, Component label, RadioGroup<T> group);
new Radio<T>(x, y, T value, Supplier<Component> label, RadioGroup<T> group);
```

Value is configuration (the radio's identity, fixed at construction). Group is wiring. Only the label is variable content.

### Convention 3 — Render-only inherits defaults

Partially applies — Radio is interactive. Overrides `mouseClicked`, inherits `isVisible` and `isHovered` defaults.

### Convention 4 — One builder method per element

Applies. `.radio(...)` on PanelBuilder — generic method parameterized by T.

```java
public <T> PanelBuilder radio(int childX, int childY, T value,
                              Component label, RadioGroup<T> group);
```

One builder method. After Radio: 17 + 1 = 18 methods on PanelBuilder.

Note: RadioGroup is constructed separately by the consumer before the builder chain; it's not added to the builder itself. Panel doesn't hold RadioGroups; it holds Radios.

### Convention 5 — No factory methods except direction

Applies. No factories on Radio or RadioGroup.

### Convention 6 — Vanilla textures for MenuKit defaults

**Verification performed: no suitable vanilla sprite for radio-button selection.** Searched `assets/minecraft/textures/gui/sprites/icon/` and related paths in the 1.21.11 jar. Candidates reviewed:

- `icon/unseen_notification.png` (10×10) — semantically "new/unseen indicator," typically red dot. Resource-pack retextures as notification badge would appear as red dots on selected radios. Wrong semantics; rejected.
- No `icon/radio`, `icon/dot`, `icon/bullet`, or similar.
- Advancement/container sprites (`button_selected`, tab selectors) are context-specific visual containers, not indicators.

Vanilla's own UI rarely uses radio buttons (segmented buttons and tabs are the vanilla-game convention for single-select). No general-purpose radio-selector sprite exists.

**Decision: drawn filled rectangle via `graphics.fill()`.** No custom texture shipped. Small filled rectangle (4×4 px) centered inside the INSET background indicates selection. Same fill-based pattern as Divider and ProgressBar. Convention 6 N/A (no MenuKit default visual beyond fills — same reasoning as other fill-based elements).

Documented in the implementation: *"verified no suitable vanilla sprite exists (2026-04-14); drawn fill is the correct fallback."*

---

## Shared mutable-state exception — scope extension

The mutable-state exception was scoped in Toggle's class javadoc as applying to "Toggle, Checkbox, Radio." RadioGroup holds the mutable state (the currently selected value), and Radios read it at render time.

Legitimacy check: does mutating RadioGroup's selection affect structural shape? No — it only changes which of the registered Radios renders as "checked." No elements added or removed, no layout change. Inside the scope of the exception.

The exception is restated at the group level rather than the element level. RadioGroup's javadoc documents the exception; Radio's javadoc references RadioGroup's doc (which references Toggle's canonical doc for the full rationale).

---

## Visual behavior

| State | Rendering |
|---|---|
| Unselected | INSET square (10×10) + label text |
| Selected | INSET square + small filled rectangle (4×4, centered) + label text |
| Hover, enabled | INSET square + translucent white highlight on the square |
| Disabled | DARK square; label in muted color |

The 4×4 filled interior uses medium gray (`0xFF606060`) — visible against the INSET dark interior but not aggressively colored.

Clicking anywhere within the element bounds (square OR label) selects this radio's value in the group.

---

## API

```java
public class RadioGroup<T> {

    public RadioGroup(T initialSelection, Consumer<T> onSelect);

    public T getSelected();

    /**
     * Sets the selection programmatically. Fires onSelect if the new
     * selection differs from the current (per .equals()); no-op otherwise.
     */
    public void setSelected(T value);
}

public class Radio<T> implements PanelElement {

    /** Size of the radio square, in pixels. */
    public static final int BOX_SIZE = 10;

    /** Gap between the radio square and the label. */
    public static final int LABEL_GAP = 4;

    /** Default label color. */
    public static final int DEFAULT_LABEL_COLOR = 0xFF404040;

    /** Muted label color when disabled. */
    public static final int DISABLED_LABEL_COLOR = 0xFF808080;

    /** Size of the selection indicator inside the radio box. */
    public static final int INDICATOR_SIZE = 4;

    /** Color of the selection indicator. */
    public static final int INDICATOR_COLOR = 0xFF606060;

    // ── Constructors ─────────────────────────────────────────────────
    public Radio(int childX, int childY, T value,
                 Component label, RadioGroup<T> group);

    public Radio(int childX, int childY, T value,
                 Supplier<Component> label, RadioGroup<T> group);

    public Radio(int childX, int childY, T value,
                 Component label, RadioGroup<T> group,
                 @Nullable BooleanSupplier disabledWhen);

    public Radio(int childX, int childY, T value,
                 Supplier<Component> label, RadioGroup<T> group,
                 @Nullable BooleanSupplier disabledWhen);

    // ── PanelElement ────────────────────────────────────────────────
    public int getChildX();
    public int getChildY();
    public int getWidth();   // BOX_SIZE + LABEL_GAP + fontWidth(label)
    public int getHeight();  // BOX_SIZE

    public void render(RenderContext ctx);
    public boolean mouseClicked(double mouseX, double mouseY, int button);

    // ── Queries ──────────────────────────────────────────────────────
    public T getValue();
    public boolean isSelected();      // Objects.equals(group.getSelected(), this.value)
    public boolean isDisabled();
    public boolean isHovered();
}
```

Builder addition:
```java
public <T> PanelBuilder radio(int childX, int childY, T value,
                              Component label, RadioGroup<T> group);
```

One builder method. Supplier-label and disabled variants via `.element(new Radio<>(...))`.

---

## Value equality semantics

RadioGroup compares selections via `Objects.equals` (null-safe). Enum values work naturally (enum.equals is reference equality). Other value types need proper `equals`/`hashCode`.

**Documented in RadioGroup's class javadoc:** *"RadioGroup compares selections via Objects.equals. Values should implement equals/hashCode (enums do this by default). Null is supported as a valid selection."*

Null selections are supported — `null` is a valid initial selection. Consumers wanting "nothing selected initially" pass `new RadioGroup<>(null, onSelect)`. All Radios render unselected until one is clicked.

---

## Usage pattern

```java
enum GameMode { CREATIVE, SURVIVAL, ADVENTURE }

RadioGroup<GameMode> modeGroup = new RadioGroup<>(
    GameMode.CREATIVE,
    mode -> applyMode(mode));

MenuKitScreenHandler.builder(MY_MENU_TYPE)
    .panel("modes", p -> p
        .text(0, 0, Component.literal("Game mode:"))
        .radio(0, 12, GameMode.CREATIVE, Component.literal("Creative"), modeGroup)
        .radio(0, 26, GameMode.SURVIVAL, Component.literal("Survival"), modeGroup)
        .radio(0, 40, GameMode.ADVENTURE, Component.literal("Adventure"), modeGroup))
    .build(syncId);
```

Consumer constructs the group once; Radios reference it via the builder's `.radio(...)` call. The group's `onSelect` fires on every user-driven selection change.

---

## Scope boundary — what Radio / RadioGroup do not do

- **No "none selected" special state beyond null** — RadioGroup constructor requires an initial selection; pass `null` if no radio should start selected. Not an explicit unselected-state enum; just a valid null value.
- **No automatic layout** — Radios are positioned individually by the consumer (childX, childY). No "stack these Radios vertically" convenience.
- **No visual group boundary** — the group is conceptual, not visual. Consumers wanting a visible group frame add a panel background or Divider.
- **No keyboard traversal (arrow keys between radios)** — Phase 8 doesn't wire keyboard routing for element groups. Mouse-click selection only. If focus management across elements becomes a consumer need, revisit in a future phase.
- **No multi-select** — exactly one radio is selected at a time. For multi-select, use Checkboxes.

---

## Dynamic-width rule (inherited from Checkbox)

Same rule: *auto-sizing elements with supplier-based variable content cannot guarantee layout stability.* Documented in Radio's class javadoc.

---

## Convention refinements (roll forward)

One refinement to Convention 1:

*"When an element's behavior callback is owned by an external coordinator rather than the element itself, the coordinator reference takes the callback-position slot in the constructor. Radio's RadioGroup parameter is the one Phase 8 case."*

This preserves the constructor reading order (`where → content → 'what handles what happens'`) while acknowledging the coordinator-owned-callback case cleanly.

Other conventions confirmed without change.

---

## Summary

Two classes in this design: `Radio<T>` (a PanelElement) and `RadioGroup<T>` (a coordinator, not a PanelElement). Radio ~110 lines; RadioGroup ~40 lines. One builder method added.

First cross-element composition in Phase 8. Mutable-state exception scope extended to cover RadioGroup (where the state actually lives). Convention 1 refined for coordinator-owned callbacks. Coordinator-not-element pattern established as precedent for any future cross-element coordination.

Files:
- `menukit/src/main/java/com/trevorschoeny/menukit/core/Radio.java`
- `menukit/src/main/java/com/trevorschoeny/menukit/core/RadioGroup.java`

**Approved 2026-04-14.** Implementation proceeds.
