# PALETTE

The complete intended element set for MenuKit. This document names every element the library is committed to shipping, every element it has considered and deferred, and the criterion that determines which side of the line a candidate falls on. Each entry specifies what the element does, what consumer use case it serves, which rendering contexts it applies to, why it ships (or does not), a rough API shape, and an implementation priority.

This is proactive rather than reactive. Where a reactive list would name only what current consumers already need, this palette names what a coherent component library should offer its consumers over time. Entries not yet implemented are intentional commitments, not aspirations.

---

## How to read this document

Each entry is organized as:

- **What it does** — one-line description of the element's behavior.
- **Use case** — what consumer need the element serves.
- **Contexts** — which of the three rendering contexts the element is meaningful in. Most elements work in all three; the entry flags where this is not true.
- **Ship decision** — pass or fail on the three-test conjunction: *independent consumers*, *compositional primitive*, *context-agnostic (or load-bearing to its context)*. All three must pass to ship.
- **API sketch** — a rough shape, not a detailed design. Actual API design happens in the implementation phase.
- **Priority** — *shipping* (already in the library), *next* (foundational additions), *audit* (specializations driven by current consumer needs), *deferred* (waiting for real use case), or *consumer-owned* (MenuKit does not ship this).

The priority tiers correspond to implementation phases but are coarser. Scheduling within a tier is decided in the implementation phase itself.

---

## Core primitives (shipping)

These elements exist in the library today. They are documented here so the palette is complete.

### Button

- **What it does.** An interactive rectangular region with a text label. Renders a panel-styled background, tracks hover state, and fires a callback on left-click when enabled.
- **Use case.** The canonical "user clicks this to do a thing" primitive. Every interactive UI has at least one.
- **Contexts.** Inventory menus and standalone screens (input-dispatching contexts). Renders on HUDs but has no click behavior there, per the HUD render-only boundary.
- **Ship decision.** Pass. Every consumer reaches for buttons; compositional with Icon, Tooltip, and state suppliers; context-agnostic in render.
- **API sketch.** `new Button(x, y, width, height, Component text, Consumer<Button> onClick)`. Optional disabled predicate via `BooleanSupplier`. Hover state queryable at render time.
- **Priority.** Shipping.

### TextLabel

- **What it does.** A non-interactive text element at a fixed position. Renders text with a color and an optional shadow.
- **Use case.** Static labels (panel titles, field names), supplier-driven dynamic text (item counts, status strings).
- **Contexts.** All three.
- **Ship decision.** Pass. Universal primitive; compositional; context-agnostic.
- **API sketch.** `new TextLabel(x, y, Component text)` with optional color, shadow, and a supplier-based variant for dynamic content (`new TextLabel(x, y, Supplier<Component> text)`).
- **Priority.** Shipping. A supplier-based variant is audit-surfaced work (see below) that extends the shipped element rather than adding a new one.

### SlotGroup

- **What it does.** A group of slots sharing a storage backing, an interaction policy, a quick-move participation, and a grid layout.
- **Use case.** Any inventory-menu consumer. SlotGroup is the entire vocabulary for "some slots that hold items and behave uniformly."
- **Contexts.** Inventory menus only. SlotGroups require vanilla's sync protocol and the slot subclass substitutability discipline; they do not transplant to HUDs or standalone screens.
- **Ship decision.** Pass despite being context-specific. SlotGroup is load-bearing to its context — no consumer can reasonably build it themselves because it requires deep integration with vanilla's container menu and sync protocol. This is the canonical case where a context-specific element still ships.
- **API sketch.** Declared in the `MenuKitScreenHandler.builder()` path: `.group(id, storage, policy, qmp, priority, columns, rowGap?)`. Backed by `Storage` abstractions (block-entity-backed, player-backed, ephemeral, virtual).
- **Priority.** Shipping.

---

## Foundational additions (next)

Elements a coherent component library should ship. None are directly surfaced by the current consumer mods, but all satisfy the ship criterion and fill gaps that would otherwise push consumers into ad hoc implementations.

### Toggle

- **What it does.** A two-state on/off control with visual differentiation between states and a callback on state change.
- **Use case.** Any setting with a boolean dimension: "enable this behavior," "show this panel," "auto-collect on." The general primitive from which Checkbox, Radio, and state-linked toggle buttons are specialized.
- **Contexts.** Inventory menus and standalone screens.
- **Ship decision.** Pass. Three-mod rule: settings-like UI is universal. Compositional primitive with onToggle callback. Context-agnostic in render.
- **API sketch.** `new Toggle(x, y, width, height, boolean initialState, BooleanConsumer onToggle)`. Internal state is mutable (one of the narrow exceptions to the declared-structure discipline — state changes are the element's reason for existing, and they do not affect structural shape). A state-supplier variant for consumer-owned state is audit-surfaced work.
- **Priority.** Next.

### Checkbox

- **What it does.** A toggle rendered as a small square with an optional check mark. Usually accompanied by a label.
- **Use case.** The visual convention for boolean settings in list-style UIs — settings panels, filter menus, option groups.
- **Contexts.** Inventory menus and standalone screens.
- **Ship decision.** Pass. Checkbox is a specific visual presentation of Toggle but distinct enough to ship as its own element rather than a Toggle variant — the rendering convention is specific (small box + check) and ubiquitous.
- **API sketch.** `new Checkbox(x, y, boolean initialState, Component label, BooleanConsumer onToggle)`. Auto-sizes to label width.
- **Priority.** Next.

### Radio / RadioGroup

- **What it does.** A single-selection control over a set of options. `RadioGroup` owns the selection state; individual `Radio` elements report state through the group.
- **Use case.** One-of-N choices: a mode selector, a preference between incompatible options. The natural counterpart to Checkbox for exclusive selection.
- **Contexts.** Inventory menus and standalone screens.
- **Ship decision.** Pass. Three-mod rule: exclusive choice is universal. Compositional. Context-agnostic.
- **API sketch.** `new RadioGroup<T>(initialSelection, Consumer<T> onSelect)` paired with `group.radio(x, y, T value, Component label)` for each option. The group holds the exclusivity invariant.
- **Priority.** Next.

### Divider

- **What it does.** A visual separator — a horizontal or vertical line — between content sections within a panel.
- **Use case.** Section breaks in dense panels. Grouping related controls visually without nesting.
- **Contexts.** All three.
- **Ship decision.** Pass. Ubiquitous primitive; one of the few elements so small that every consumer mod inventing their own would be wasted effort.
- **API sketch.** `Divider.horizontal(x, y, length)` and `Divider.vertical(x, y, length)`. Color and thickness as optional parameters with vanilla-matched defaults.
- **Priority.** Next.

### Icon

- **What it does.** Renders a sprite (identifier + size) at a position. No interaction.
- **Use case.** Visual indicators, decorations, and the render portion of composite elements like icon-only buttons or state-indicating toggles.
- **Contexts.** All three.
- **Ship decision.** Pass. Universal rendering primitive; composes with Button and Toggle to form specialized variants; context-agnostic.
- **API sketch.** `new Icon(x, y, Identifier sprite, int width, int height)`. A supplier-based sprite variant (`Supplier<Identifier>`) supports state-driven icon swaps.
- **Priority.** Next. Re-used by the audit-surfaced icon-only Button variant.

### ItemDisplay

- **What it does.** Renders an `ItemStack` at a position with optional count overlay and durability bar. No interaction.
- **Use case.** Showing an item without making it a full slot — recipe previews, HUD item readouts, status indicators. Distinct from a slot: no sync protocol, no interaction, no storage binding.
- **Contexts.** All three. Already shipping in HUD-specific form (`MKHudItem`); generalized under the core palette.
- **Ship decision.** Pass. Item display is a pervasive need that does not warrant the full slot apparatus. Compositional with TextLabel and Icon for captioned variants.
- **API sketch.** `new ItemDisplay(x, y, Supplier<ItemStack> stack)` with optional flags for count and durability rendering, and optional explicit size.
- **Priority.** Next. Subsumes the HUD-only item element.

### ProgressBar

- **What it does.** A rectangular fill bar driven by a float value in the range [0, 1]. Configurable fill direction, fill color, background color, and optional label.
- **Use case.** Any bounded-progress indicator: cooking time, charging level, health-like readouts, task completion.
- **Contexts.** All three. Already shipping in HUD-specific form (`MKHudBar`); generalized under the core palette.
- **Ship decision.** Pass. Universal indicator. Compositional with TextLabel for labeled variants. Context-agnostic.
- **API sketch.** `new ProgressBar(x, y, width, height, Supplier<Float> value)` with optional color, direction (left-to-right, right-to-left, top-to-bottom, bottom-to-top), and label supplier.
- **Priority.** Next. Subsumes the HUD-only progress bar.

### Tooltip

- **What it does.** A hover-triggered or always-visible informational popup. Renders a text component at the mouse position (or an anchored position) in screen space, above all other elements.
- **Use case.** Contextual help on any other element, dynamic state descriptions, item-like tooltips on non-slot elements.
- **Contexts.** Inventory menus and standalone screens (hover requires input dispatch). Static-position tooltips are meaningful in all contexts.
- **Ship decision.** Pass. Universal affordance for explaining UI; currently absent from the library despite audit demand for tooltipped buttons.
- **API sketch.** Two forms, both shipped: (1) a builder property on interactive elements — `button.tooltip(Supplier<Component>)` — that attaches a hover-triggered tooltip; (2) a standalone `Tooltip` element that renders at a declared position or follows a target element. Form (1) covers the common case; form (2) supports persistent "info box" patterns.
- **Priority.** Next. The builder-property form applies retroactively to Button and future interactive elements.

---

## Audit-surfaced specializations

Elements or variants driven by concrete needs from current consumer mods. These all build on foundational elements rather than standing alone.

### Icon-only Button variant

- **What it does.** A Button whose content is an Icon rather than a text label. Renders a small, square or near-square button with a sprite inside.
- **Use case.** Toolbar-style controls in dense UIs — shulker-palette's 9x9 mode buttons, sandboxes's 11x11 action icons.
- **Contexts.** Inherits from Button: inventory menus and standalone screens.
- **Ship decision.** Pass as a variant of Button + Icon composition. Not a new element, but a supported construction pattern and a builder shortcut.
- **API sketch.** `Button.icon(x, y, size, Identifier sprite, Consumer<Button> onClick)` — a factory producing a Button whose text is replaced by a centered Icon. Tooltip attaches via the foundational Tooltip property.
- **Priority.** Audit. Ships when Button and Icon coexist.

### State-linked Toggle variant

- **What it does.** A Toggle whose on/off state is read from a consumer-owned `BooleanSupplier` rather than held internally. Toggle's rendering reflects the supplier's current value; the onToggle callback is the consumer's signal to mutate their own state.
- **Use case.** Toggles whose state lives elsewhere — in a config object, a per-block setting, a shared mode flag. Shulker-palette's toggle buttons reflect mode state that lives on the palette itself.
- **Contexts.** Inventory menus and standalone screens.
- **Ship decision.** Pass as a variant of Toggle. Accommodates consumer-owned state without forcing the library to own it.
- **API sketch.** `Toggle.linked(x, y, width, height, BooleanSupplier state, Runnable onToggle)`. The supplier drives render; the callback signals the consumer to update their state; the next frame's supplier call reflects the new state.
- **Priority.** Audit. Ships with Toggle.

### Icon swap by state

- **What it does.** An Icon whose sprite is chosen by a boolean or enum state — "if toggled on, show this sprite; otherwise that one." Typically paired with the state-linked Toggle variant.
- **Use case.** Shulker-palette buttons that show different icons for different modes.
- **Contexts.** All three.
- **Ship decision.** Pass as a variant of Icon. The generic mechanism is the supplier-based sprite variant of Icon (`Supplier<Identifier>`); icon swap is a specific use of that mechanism.
- **API sketch.** Covered by `new Icon(x, y, Supplier<Identifier> sprite, width, height)`. No separate element.
- **Priority.** Audit. Ships with Icon's supplier variant.

### Dynamic content via suppliers

- **What it does.** Allows any element's variable content — text, tooltip, sprite, item stack, progress value — to be driven by a `Supplier<T>` rather than a fixed value. The declared-structure discipline means the element's shape is frozen; only its content varies.
- **Use case.** Pervasive. Agreeable-allays needs supplier-based text on labels. Shulker-palette needs supplier-based tooltips on buttons. Many future consumers will need similar patterns.
- **Contexts.** All three.
- **Ship decision.** Pass as a library-wide pattern. Not a new element; a consistent API convention applied across all elements that have variable content.
- **API sketch.** Every element with variable content exposes both a fixed form and a supplier form. Consistency: `Component` for fixed, `Supplier<Component>` for dynamic; `ItemStack` for fixed, `Supplier<ItemStack>` for dynamic. No per-element variation in the pattern.
- **Priority.** Audit. Applied incrementally as each element lands — foundational elements use the pattern from the start; existing Button and TextLabel gain supplier variants during this tier.

---

## HUD-specific elements

Elements the library ships that are meaningful only in the HUD context because their behavior is tied to HUD-specific machinery.

### Notification

- **What it does.** A time-bounded HUD element that slides in, displays for a duration, and fades out. Defined as a template at mod init with an anchor, style, and animation parameters; triggered at runtime by key with optional text and item data.
- **Use case.** Transient in-game alerts that the player should see without opening a screen — "diamond found," "dimension changed," "recipe unlocked."
- **Contexts.** HUDs only. Notifications depend on HUD anchoring and animated rendering that does not transplant to standalone screens (which are interactive) or inventory menus (which are centered and paused).
- **Ship decision.** Pass despite being context-specific. Notifications are load-bearing to their context in the same way SlotGroup is load-bearing to inventory menus — the animation and runtime-trigger machinery would be unreasonable for consumers to rebuild independently.
- **API sketch.** `Notification.builder(key).anchor(...).duration(ms).slideFrom(direction).style(...).build()` at init; `notify(key, text)` or `notify(key, text, itemStack)` at runtime. Already shipping.
- **Priority.** Shipping.

---

## Deliberately deferred

Elements considered and declined for now. Each has a specific reason tied to the ship criterion or to the current lack of consumer demand. Deferral is not rejection — if a real use case surfaces, the entry is reconsidered against the criterion at that time.

### Slider

- **What it does.** A continuous-value control with a draggable handle over a track.
- **Deferred because.** No current consumer. The drag-and-continuous-value interaction is nontrivial to implement well. Shipping without a real use case risks committing to an API that does not match what consumers eventually want.
- **Reconsider when.** A consumer mod needs a continuous-value setting (volume, intensity, scale) and would build one if MenuKit did not ship it.

### Dropdown

- **What it does.** A single-selection control that opens a vertical list of options on click and closes when an option is chosen or focus is lost.
- **Deferred because.** No current consumer. Dropdown's open-state overlay interacts with panel layout and input dispatch in ways that need careful design. The common use cases (one-of-N selection) are already covered by Radio / RadioGroup.
- **Reconsider when.** A consumer needs one-of-many selection with enough options that Radio buttons are impractical.

### TextField

- **What it does.** A single-line or multi-line editable text input.
- **Deferred because.** No current consumer. Text input is unusually complex — cursor positioning, selection, keyboard shortcuts, IME support, clipboard. Vanilla has this (`EditBox`) and a consumer who needs text input for a standalone screen can embed a vanilla `EditBox` directly. Shipping MenuKit's own would duplicate vanilla work without clear benefit.
- **Reconsider when.** Multiple consumers need text input inside a MenuKit panel with behavior that cannot be cleanly delegated to vanilla's `EditBox`.

### List (dynamic repeated content)

- **What it does.** A container that renders a variable number of children from a `Supplier<List<T>>` and a per-item template.
- **Deferred because.** Runtime-variable element count conflicts with the declared-structure discipline. The element count is no longer frozen at construction; it varies per frame. Consumers who need dynamic lists can declare a fixed number of placeholder elements and show/hide them, or implement `PanelElement` directly for the specific case. A HUD-specific variant currently exists (`MKHudList`); it is not generalized to core until the discipline question is answered.
- **Reconsider when.** Multiple consumers need dynamic lists and the declared-structure question has a defensible answer — likely "the list's *template* is frozen at construction; the data is dynamic," which may be acceptable under the discipline.

### Group (row/column layout)

- **What it does.** A container that arranges a list of child elements in a row or column with spacing.
- **Deferred because.** Panel is the ceiling of composition per the thesis. Introducing nested layout groups adds a layer of composition the library deliberately avoids. Consumers who need row/column layout within a panel position elements with child offsets — trivial for small counts. A HUD-specific variant currently exists (`MKHudGroup`); it is not generalized to core.
- **Reconsider when.** Multiple consumers build the same layout-group workaround independently, suggesting the manual-offset approach is genuinely insufficient.

### Scroll container

- **What it does.** A clipped region containing content larger than its bounds, with scroll input that pans the view.
- **Deferred because.** Complex implementation, no current consumer, and the common use case (long lists) is handled adequately by paginated panels. Scroll introduces clipping, scroll-state management, and input routing that conflicts with the library's simple layout model.
- **Reconsider when.** A consumer has a genuine scroll-required use case and would build a custom scroll implementation if MenuKit did not ship one.

### Tabs / TabBar

- **What it does.** A row of selectable tabs that switches visible content.
- **Deferred because.** The behavior is already expressible with Button + panel visibility. Each tab is a button that toggles one panel visible and its siblings hidden. Shipping Tabs as a dedicated element would add API surface for something consumers can compose in under a dozen lines.
- **Reconsider when.** Multiple consumers build the same Tabs workaround independently and the composed form has observable drawbacks (e.g., the visibility-swap animation that tabs conventionally have).

---

## Consumer-owned (does not ship)

Examples of elements the library explicitly does not ship, with the ship criterion applied. Not an exhaustive list — the point is to illustrate where the line is, not to enumerate everything beyond it.

- **Sort/filter buttons for inventory screens.** Fails the compositional-primitive test: a sort button embodies domain logic about what to sort and how. A Button whose onClick invokes consumer sort logic ships; a prebuilt SortButton does not.
- **Recipe viewers, crafting gauges, fuel meters.** Fail the domain test: each implies a specific domain with specific data and specific rendering conventions. Consumers compose these from ItemDisplay, ProgressBar, and TextLabel.
- **Color pickers.** Fails the three-mod test: useful in a narrow set of mods, and the rendering conventions vary significantly between use cases (HSV wheel vs. palette grid vs. named-color list).
- **Inventory browsers / item pickers.** Fail the compositional-primitive test: these are domain applications of SlotGroup plus search/filter logic, not primitives.
- **Keybind capture widgets.** Fall under the keybind infrastructure subsystem, which is adjacent to the component library rather than part of it. If a keybind-capture UI ships, it ships under that subsystem, not the palette.
- **Chat message composers, command builders, world selection widgets.** Fail the context-agnostic test combined with the scope ceilings — these belong to out-of-scope contexts.

The pattern: anything that embodies domain logic, anything that implies a specific application, and anything that requires mixing into vanilla contexts MenuKit does not target is consumer-owned. Consumers build these using the shipped primitives. The library stays small, and its boundaries stay checkable.

---

## Summary

The shipping library (core primitives) covers three elements. The next tier (foundational additions) adds eight. The audit tier (specializations of foundational elements) covers three patterns. HUD-specific elements contribute one (Notification). The deferred list names seven elements held back with specific reasons and reconsideration triggers. The consumer-owned list illustrates the other side of the criterion.

The total committed shape of the library is small by design — roughly fifteen named elements across all tiers. The restraint is the point: a library that ships fewer, more general primitives composes more reliably than a library that ships many, more specific widgets.

If the element count grows past roughly twenty-five, revisit the thesis and ask whether recent additions have been held to the three-test conjunction or rationalized past it. The threshold is not a hard cap — it is a prompt for reflection. A library can legitimately grow beyond it if each addition earns its place; a library that drifts past it without review has probably stopped checking.

Future elements are evaluated against the three-test ship criterion, against the contexts document's scope, and against the thesis's principles — in that order. An element that passes all three earns its place; anything else stays with the consumer.
