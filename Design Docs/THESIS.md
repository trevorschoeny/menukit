# THESIS — Navigation Index

**Status:** navigation index (post-Phase-15b transformation). Binding commitments live in the §-numbered canonical ADRs at `@ MenuKit/1 | Canon/accepted/`. This document orients new readers, names what MenuKit is and isn't, and points at the canon for each principle.

Pre-Phase-15b, THESIS.md was the canonical articulation of MenuKit's 11 design principles. Phase 15b ported each principle into a §-numbered ADR (§0019–§0029) under the unified-canon commitment in `§0018`. The narrative form is preserved historically in git; this transformed index is what the doc is going forward.

---

## What MenuKit is

MenuKit is a component library for Minecraft UI. It ships small, composable elements — buttons, text labels, toggles, slot groups, and the rest — that consumer mods combine to build UI. The elements work across **four rendering contexts**: MenuContext (panels anchored to vanilla container screen frames), SlotGroupContext (panels anchored to named slot groups), HudContext (HUD overlays during gameplay), and StandaloneContext (MenuKit-native screens or vanilla standalone screens decorated via mixin).

The shared composition unit is the **Panel**: a bounded region holding an ordered list of elements with uniform rendering, input, and visibility semantics. Panel is the ceiling of composition (per `§0036`); below it is a flat element list.

A consumer reaches for MenuKit when they want UI in Minecraft without solving four separate context problems — vanilla's slot sync protocol, the slot-group bounding-box anchor problem, the HUD render pipeline, the standalone screen lifecycle — each with its own quirks and no shared vocabulary. MenuKit provides that shared vocabulary and handles the context-specific machinery beneath it.

MenuKit does not build UI for consumers. It provides the vocabulary they build UI with.

## What MenuKit isn't

Explicitly out of scope (per `§0031`'s non-contexts list and the scope-ceiling commitments in the principles below):

- **Config UIs** — Cloth Config and similar libraries own this space.
- **Chat HUD, F3 debug overlay, world-selection / server-selection / realms screens** — narrow vanilla contexts with heavy vanilla ownership.
- **Main menu and pause menu (as categories)** — these are *instances* of vanilla standalone screens, not new contexts. Consumers decorate them via standard StandaloneContext mixin patterns.
- **Game-world rendering** — 3D rendering is not MenuKit's scope.
- **Full UI framework with its own container model and layout engine** — Owo Lib occupies that space. Panel is MenuKit's ceiling of composition; there's no nesting of panels within panels.
- **Theme system** — the library ships a narrow visual vocabulary (PanelStyles, vanilla-matched colors, vanilla slot backgrounds). Consumers wanting themed UI compose custom rendering.
- **Animation framework** — HUD notifications ship with baked-in slide/fade because animation is load-bearing there. No general animation DSL.
- **Ecosystem-wide event bus** — per `§0019`, ecosystem events use Fabric's API.
- **General drag-and-drop beyond the inventory slot drag protocol.**
- **Library-shipped testing UI** — per `§0038`, the validator owns the test surface; library exposes only `ContractVerification.runAll(player)`.

The common thread: MenuKit stops where existing ecosystem libraries do the job better, where vanilla ownership is heavy, or where the library would have to become a platform to provide the feature.

---

## The 11 Principles — Canonical Index

Each principle below is a one-line summary linking to the canonical §-numbered ADR. Read the ADR for the binding commitment, the structural test sentence, the anti-patterns, and the considered alternatives.

The principles are ordered by load-bearing weight — earlier principles override later ones when they conflict.

### §0019 — MenuKit is a Library, Not a Platform

MenuKit provides primitives. It does not take ownership of code paths it does not need. Test: *if MenuKit took ownership of this code path, could a second mod doing something similar still coexist?*

### §0020 — Subclass Vanilla Types Rather Than Wrap Them

Where MenuKit produces something whose vanilla counterpart exists, the MenuKit version *is* the vanilla type, achieved by subclassing. Wrappers and adapters re-introduce ecosystem-coordination failure modes. Test: *does an existing mod that mixes into the vanilla type still work correctly when MenuKit's version is involved?*

### §0021 — Hidden Elements Are Inert Throughout the System

Hidden means inert — not just invisible. Hidden slots return EMPTY, refuse insertion, skip quick-move, render off-screen. Hidden elements don't intercept clicks, don't tick suppliers, don't reserve layout. Test: *if the element is hidden, can any observable behavior anywhere tell it exists?*

### §0022 — Structure Declared Once; Visibility Changes at Runtime

Panel trees are built and sealed at construction. Slot groups, element lists, layout constraints are immutable after declaration. Visibility flips at runtime; structure does not. Test: *after construction, can the element tree be described as a fixed thing with one mutable dimension (visibility)?*

### §0023 — Elements Are Context-Agnostic; Containers Hold Context-Specific Machinery

An element doesn't know which context it renders in. The container holding it (inventory screen, HUD pipeline, standalone screen) handles context-specific machinery. Test: *could this element, unchanged, render correctly in any context if the container did its part?*

### §0024 — Persist State Through Vanilla's NBT Patterns

Storage format is NBT; transport is Fabric attachments / DataComponents; values stored as `Tag`, not `byte[]`. Persistent-state primitives take `Codec<T>` for storage and `StreamCodec<T>` for wire separately. Test: *if a player runs `/data get` on the owner, can they see what MenuKit has stored?*

### §0025 — Validate the Product, Not Just the Primitives

A component library's correctness isn't the sum of primitives passing isolated tests; it's whether components compose into real workflows. Validation includes consumer-shaped scenarios alongside primitive-coverage probes. Test: *does the validation pass include at least one consumer-shaped scenario?*

### §0026 — Elements Are Lenses, Not Stores

Stateful elements (Toggle, Checkbox, Slider, TextField) expose state via `supplier + callback` pairs. Element reflects consumer state; element does not own state. Persistence is consumer-owned. Test: *when this element's state changes, could the consumer's underlying state have changed by another path, and would the element reflect it correctly on the next frame?*

### §0027 — Rendering Pipelines Are Uniform; Embedding Is Context-Specific

The render pipeline (background paint, padding, element dispatch) is shared across contexts. What varies is embedding (where the panel sits, how it relates to surrounding gameplay). Test: *when a rendering behavior varies between contexts, does the variation have a named reason rooted in the screen's relationship to gameplay?*

### §0028 — Contexts Are Consumer Mental Models, Not Implementation Boundaries

Contexts factor by anchor (consumer mental model: "what am I anchoring to?"), not by render pipeline (implementation). Pipeline-sharing doesn't collapse contexts; pipeline-divergence doesn't multiply them. Test: *does this context have a distinct answer to "what am I anchoring to?" that isn't available under any existing context?*

Pairs with `§0031` (the four contexts; existence claim + three-property gate + non-contexts list).

### §0029 — Evidence Drives Primitive Scope; Rule of Three with Narrow Exhaustive-Coverage Exception

Default: Rule of Three. Wait for three concrete consumer uses before generalizing. Exception: exhaustive coverage at v1 when each entry is cheap to add AND incompleteness forces consumer migrations. The exception requires explicit invocation naming both costs. Test: *what's the marginal cost of including this entry, and what's the migration cost on omission?*

---

## Beyond the 11 Principles — Phase 15b Additions to Canon

Phase 15b also ported binding commitments from CONTEXTS.md, the M-docs (M1–M9), TESTING_CONVENTIONS.md, and Phase 14 calibration heuristics:

- **`§0030`** — Use Precise Mixin Injection, Not @Overwrite (Cairn 007 standalone port)
- **`§0031`** — The Four Rendering Contexts (CONTEXTS-port — existence + three-property gate + non-contexts list)
- **`§0032`** — HudContext Is Render-Only; Interactive Overlays Build a Standalone Screen
- **`§0033`** — Slot Identity Is Container-Relative, Not Menu-Relative (M2)
- **`§0034`** — Persistent State Lives on the Natural Owner; Metadata and Content Are Layered, Not Merged (M1 + M7)
- **`§0035`** — By-Value vs By-Reference Panel Composition (M4 §4A)
- **`§0036`** — Panel Is the Ceiling of Composition; Layout Primitives Are Build-Time Helpers (M8)
- **`§0037`** — Visible Opaque Panels Block All Input Behind Them; Modal Is Composition (M9)
- **`§0038`** — The Library Ships No Testing-UI Scaffolding; the Validator Owns the Test Surface (TESTING_CONVENTIONS-port)
- **`§0039`** — Wrap Vanilla Faithfully — Don't Cut Inherited Features; Don't Extend API Beyond Vanilla's (heuristic 6 promotion; refinement under `§0020`)

---

## Related canonical and operational references

- **`@ MenuKit/1 | Canon/accepted/`** — full §-numbered ADR set (§0001–§0039 minus §0017 in `superseded/`).
- **`@ MenuKit/1 | Canon/accepted/§0018 | MenuKit Agency Scope.md`** — the agency-scope ADR that authorized Phase 15b's unified-canon work.
- **`menukit/Design Docs/CONTEXTS.md`** — per-context machinery, injection patterns, common injection failure modes (operational guidance complementing `§0028` + `§0031` + `§0032`).
- **`menukit/Design Docs/PALETTE.md`** — the catalogue of shipped elements and their APIs.
- **`menukit/Design Docs/Mechanisms/M1.md`–`M9.md`** — per-mechanism implementation documentation (operational; binding commitments port to `§0033`–`§0037`).
- **`menukit/Design Docs/TESTING_CONVENTIONS.md`** — the validator-side test surface conventions (operational guidance complementing `§0038`).
- **`Design Docs/CONVENTIONS.md`** (workspace-level) — coding-style conventions: hybrid (`§2.1`), packet pattern (`§2.2`), defensive returns (`§2.3`), mixin naming (`§2.4`).
- **`@ MenuKit/2 | Working Files/Phase 15 Migration Audit.md`** — full Cairn migration record + drift findings + the Phase 15b authoring queue closure trail.

---

## Keybind infrastructure (adjacent subsystem)

MenuKit ships a small utility subsystem for multi-key keybinds — bindings like `Alt+Shift+K` rather than vanilla's single-key model, and the ability for consumer mods to define keybinds in their own config files. This subsystem is adjacent to the component library, not part of it. It does not follow component patterns, does not compose with panels, does not interact with the element palette.

Its inclusion in MenuKit is convenience — splitting it out would add friction without adding clarity. The keybind subsystem does not expand the component-library thesis; the palette does not include "keybind" as an element, and the contexts model does not include keybind capture as a rendering context.

---

## Summary

MenuKit is a component library for Minecraft UI. It ships small, composable elements that work across four contexts. It stops at being a library — it does not take ownership of vanilla code paths, does not compete with specialized libraries, and does not grow beyond its target contexts.

Every element that ships, every context supported, every feature added is checkable against the §-numbered ADRs in `@ MenuKit/1 | Canon/accepted/`. If a candidate doesn't survive the check, it belongs with the consumer, not with MenuKit.
