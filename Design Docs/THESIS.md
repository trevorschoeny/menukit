# THESIS

MenuKit's identity as a component library for Minecraft UI. This document states what the library is, what distinguishes it, what principles govern its design, where the line between library and consumer work is drawn, and what MenuKit deliberately does not do. Every decision made elsewhere in the library should be checkable against this document.

---

## What MenuKit is

MenuKit is a component library for Minecraft UI. It ships small, composable elements — buttons, text labels, toggles, slot groups, and the rest — that consumer mods combine to build UI. The elements work across three rendering contexts: inventory menus, HUDs, and standalone screens. The shared composition unit is the **Panel**: a bounded region holding an ordered list of elements with uniform rendering, input, and visibility semantics.

A consumer reaches for MenuKit when they want to build UI in Minecraft without solving three separate context problems — the vanilla slot sync protocol, the HUD rendering pipeline, and the standalone screen lifecycle — each with its own quirks and no shared vocabulary. MenuKit provides that shared vocabulary and handles the context-specific machinery beneath it. The consumer declares elements inside panels; the library takes care of the rest.

MenuKit does not build UI for consumers. It provides the vocabulary they build UI with.

## What it is not

MenuKit is deliberately narrower than an all-purpose UI framework, and deliberately broader than a single-context widget kit.

It is **not** a config-UI library. Config UIs are a solved problem in the ecosystem — Cloth Config ships opinionated widgets for typed configuration entries, and overlap would be harmful to both libraries. Consumers building config screens use Cloth Config; consumers building their own standalone screens with MenuKit's elements can do config-like work if they want to, but the library ships no "field entry" widgets.

It is **not** a full UI framework with its own container model, layout engine, and widget tree. Owo Lib occupies that space. MenuKit's composition unit is Panel — flat, explicitly positioned, with constraint-based layout between panels. There is no nesting of panels within panels, no flexbox, no grid system beyond what SlotGroup provides for slot grids. The library stays small on purpose: Panel is the ceiling of composition, and it is a low ceiling.

It is **not** a theme system. MenuKit ships a narrow visual vocabulary — a handful of PanelStyles (raised, dark, inset, none), vanilla-matched text colors, the vanilla slot background sprite. Consumers who want a themed look build it themselves using custom renderers. The library's visual defaults match vanilla closely enough that MenuKit UI feels native in any modpack.

It is **not** a mixin toolkit for injecting into vanilla UI. Consumer mods that want to decorate vanilla screens or the vanilla HUD write their own mixins and compose MenuKit elements inside them. The library can provide ergonomic helpers and documented patterns for this, but it does not ship mixins that inject into vanilla contexts on consumers' behalf. This restraint is central to the next section.

## Design principles

Eight principles govern every design decision in MenuKit. They are ordered by load-bearing weight — the earlier principles override the later ones when they conflict.

### 1. Library, not platform

MenuKit provides primitives. It does not take ownership of code paths it does not need.

Consumer mods build UI *with* MenuKit; they do not live *inside* MenuKit. A consumer that wants to add a button to every chest screen writes their own mixin into the vanilla screen class and composes a MenuKit Button inside their mixin's render callback. The library does not ship a general "inject a button everywhere" framework, because doing so would require MenuKit to take ownership of vanilla's input dispatch, rendering pipeline, and screen lifecycle — ownership it cannot hold without conflicting with other mods doing the same thing.

This principle forces specific decisions: MenuKit never mixins into vanilla screen classes to register event callbacks. MenuKit never replaces vanilla's HUD renderer. MenuKit does not provide a global event bus, because a library-scoped bus would require library-scoped mixins into every event source. Where consumers need ecosystem-wide events, they use Fabric's existing API or write their own — both are consumer-scoped solutions that don't require MenuKit to own anything it doesn't need to.

The test for this principle: *if MenuKit took ownership of this code path, could a second mod doing something similar still coexist?* If the answer is no, MenuKit does not take ownership.

### 2. Vanilla substitutability

Where MenuKit produces vanilla-typed things, they *are* those things — not wrappers, not adapters, not lookalikes.

A MenuKit slot is a `net.minecraft.world.inventory.Slot`, subclassed rather than composed. Ecosystem mixins into `Slot` affect MenuKit slots identically. A MenuKit inventory screen is a proper `AbstractContainerScreen`; vanilla's slot sync, hover highlight, and tooltip pipeline all work. A MenuKit HUD overlay renders through the same `GuiGraphics` API and the same HUD rendering callbacks vanilla uses.

This principle forces decisions about where MenuKit's abstraction sits. Where a vanilla type is the correct boundary (Slot, Screen, GuiGraphics), MenuKit subclasses or uses it directly. Where a vanilla type is too narrow to express what the library needs (there is no vanilla type for "a group of slots with a shared policy"), MenuKit introduces its own abstraction above the vanilla type, not one that replaces it.

The test for this principle: *does an existing mod that mixins into the vanilla type for its own reasons still work correctly when MenuKit's version of the type is involved?* If no, MenuKit's abstraction is in the wrong place.

### 3. Inertness when hidden

Hidden elements do not exist. Not just "don't render" — do not exist, as far as the rest of the world is concerned.

A hidden slot returns EMPTY when asked for its item, refuses insertion, skips quick-move routing, and goes off-screen so vanilla's hover pipeline cannot find it. A hidden button does not intercept clicks, does not occupy tab-focus, does not contribute hover state. A hidden HUD panel does not tick its dynamic suppliers, does not dirty its layout, does not consume budget. The world sees through hidden elements as if they were not there.

This principle forces symmetry: showing and hiding must be cheap in both directions and must not leak state across the transition. It also forces sync-safety for anything with a server-side presence — hiding an inventory slot must sync EMPTY to the client so the client and server agree on what the player sees.

The test for this principle: *if the element is hidden, can any observable behavior anywhere in the system tell it exists?* If yes, the element is not inert enough.

### 4. Declared structure, mutable visibility

Structure is decided once, at construction time, and frozen. Visibility is the one thing that changes.

Panel trees are built and sealed. Slot groups are fixed in count, size, and storage binding. Element lists are immutable. Layout constraints are declarative. What changes at runtime is which of those declared things are currently visible, and the dynamic content that suppliers feed into elements (text strings, icon identities, progress values).

This principle forces a clean split between setup code and runtime code. Setup code builds the full tree of everything that might ever appear, with every element in its final shape. Runtime code toggles visibility and updates suppliers. There is no "add a panel later" API, no "mutate this element's position at runtime" hook. If something might need to appear later, it is declared up front and hidden until its moment.

This principle originates in the sync-safety requirement for inventory menus — vanilla's sync protocol cannot tolerate structural mutation mid-session. The library holds it uniformly across all three contexts, including those without a sync protocol, because consistent construction-time declaration is what allows elements to compose identically across contexts. If Panel semantics were frozen in inventory menus but mutable in HUDs, elements written against one context would not transplant cleanly to the other, and the context-agnostic-elements principle would collapse. The discipline generalizes because uniformity generalizes.

The test for this principle: *after construction, can the element tree be described as a fixed thing with a single mutable dimension (visibility)?* If no, something is leaking runtime state into structure.

### 5. Context-agnostic elements, context-specific containers

Elements work across rendering contexts. Context-specific machinery lives in the container subclasses that hold them, not in the elements themselves.

A Button does not know whether it is rendering inside an inventory screen, a HUD panel, or a standalone screen. It receives a GuiGraphics context and absolute screen coordinates, renders itself, and reports whether a mouse click lands on it. The container holding the Button — the inventory screen, the HUD pipeline entry, the standalone screen — handles the rest of the machinery for its context.

This principle forces the element API surface to stay narrow. Elements cannot reach for context-specific APIs (no "get my containing screen handler," no "read the sync queue"). When an element needs context-specific behavior, either the behavior is expressible through supplier injection (the consumer supplies a `Supplier<Component>` that reads whatever it needs) or the element is context-specific and the palette marks it as such (SlotGroup is the clearest case).

The test for this principle: *could this element, unchanged, render correctly in any of the three contexts if the container holding it did its part?* If no, the element has context-specific logic that should be pushed down into the container.

### 6. Match vanilla's persistence patterns

When MenuKit persists state, it uses NBT as the storage format, Fabric attachments as the transport, and keeps internal values as `Tag` rather than opaque `byte[]`.

Every vanilla system persists state the same way: ItemStack components via `DataComponentPatch`, BlockEntity custom data, Player / Entity additional save data, ender chest contents — all NBT-backed, all serialized through the same pipeline, all inspectable via `/data get`. The payoff of conforming to this is more than consistency: state becomes legible to NBT editors, round-trippable through existing tooling, and debuggable with the same commands players and modpack authors already know. MenuKit's persisted data should never be the one opaque blob in an otherwise legible NBT tree.

The wire protocol is a separate concern. Binary `ByteBufCodecs` is correct for packets, mirroring vanilla's own packet pattern. The dual `Codec<T>` (persistence, NBT-bound) + `StreamCodec<T>` (wire, binary) shape matches how vanilla handles DataComponents — `Codec` into a `Tag` for storage, `StreamCodec` into bytes for transport. Each half of the pair pulls its own weight; neither replaces the other.

This principle forces specific decisions: persistent-state primitives take `Codec<T>` at registration and store values as `Tag` internally. Wire protocols use `StreamCodec<T>` separately. Opaque-payload escape hatches — the "mod provides its own serialization" case — use `CompoundTag`, not `byte[]`. The library stays narrow by not inventing its own serialization format, and stays inspectable by not obscuring state that vanilla would expose.

The test for this principle: *if a player runs `/data get` on the owner (player / block entity / entity), can they see what MenuKit has stored there?* If no, MenuKit is stashing state in a format vanilla can't see, and the library has drifted from vanilla's own pattern.

### 7. Validate the product, not just the primitives

A component library's correctness isn't the sum of its components passing isolated tests; it's whether the components compose into real workflows cleanly, reuse across contexts without friction, and express the palette real consumers expect.

Primitive-coverage tests catch the easy bugs — per-element render bounds, individual codec round-trips, visibility-flag flips. Composition and cross-context tests catch the real ones — supplier-driving-across-elements, context-swap state leakage, palette gaps that force consumers to reinvent the things the library was supposed to save them from reinventing. A validation phase that skips the latter is only measuring what the library is made of, not what the library delivers.

This principle forces validation structure: every validation pass includes at least one scenario where the harness acts as a consumer building a realistic feature end-to-end — not a scenario that exercises one primitive at a time. Palette completeness is evaluated against the standard UI shapes real consumers need (text input, sliders, dropdowns, scroll containers for settings screens; compositional state flow; cross-context element reuse). Gaps surfaced here become evidence that feeds future phases — whether toward library additions or toward explicit non-goal documentation.

Without this discipline, validation regresses to primitive-coverage over time. Each phase's new primitives get a set of probes that test them in isolation; the library's product identity — "a component library that makes it easy to build real UI" — erodes into "a collection of primitives that pass their individual contracts." The difference is visible to consumers long before it's visible to the library.

The test for this principle: *does the validation pass include at least one consumer-shaped scenario — where the harness builds something a real consumer would build — alongside its primitive-coverage scenarios?* If no, validation is measuring the library's parts, not the library's product.

### 8. Elements are lenses, not stores

Interactive elements with state-like behavior — Toggle, Checkbox, Radio, any future Slider / TextInput / Dropdown — expose their state via `supplier + callback` pairs. The element reflects consumer state; the element does not own state. Persistence, serialization, and cross-session durability are consumer concerns. The library provides lenses; consumers provide stores.

The instinct to have a Toggle "remember its last state" or a Checkbox "save its value" is the instinct to move consumer concerns into the library. What actually persists in those scenarios is not the element's state — it is the consumer's feature flag, config value, or domain state that the element happens to be a visual handle for. "Should feature X be enabled" is the consumer's question, not MenuKit's. The consumer's mod knows what feature X is, what its persistence format should be, when it should be saved, and what happens when another part of the consumer's code mutates the same state by another path. MenuKit cannot know those answers for every consumer without becoming a platform that forces one set of answers on all of them.

M1 is the exception that proves the rule. Per-slot state has a library-shaped persistence primitive because slots are library-owned entities with identity, lifecycle, and synchronization semantics MenuKit already handles. Non-slot state — feature flags driven by Toggles, numeric ranges driven by Sliders, enumerations driven by Radios — lives in the consumer's chosen store (config files, attachments, block-entity data, whatever the consumer's mod is already using). The library ships no "element state persistence" layer because every mechanism it could ship would force a particular storage model on consumers, exactly the library-not-platform failure mode that principle 1 rejects.

This principle forces two specific decisions. First, every stateful element ships a `linked`-style factory at introduction time — `Toggle.linked(supplier, callback)`, `Checkbox.linked(supplier, callback)`, and so on — so consumers never need to work around an element that owns its own state when they want to wire it to their own store. Second, when the library encounters pressure to "just add persistence for this one element type," the answer is to sharpen the lens (improve the supplier/callback ergonomics, reduce boilerplate, ship examples) rather than to add library-owned storage.

The test for this principle: *when this element's state changes, could the consumer's underlying state (config, attachment, field) have changed by a completely different code path, and the element still reflect it correctly on the next frame?* If no, the element is owning state instead of lensing over it.

## The ship-vs-consumer line

Every candidate element faces the same question: does MenuKit ship this, or do consumers build it themselves using MenuKit's primitives?

The criterion is a conjunction of three tests.

**Would three mods with unrelated purposes independently reach for this?** A button ships because mods of all kinds need buttons. A "sort inventory" button does not ship, because only one kind of mod needs it. Multiple independent consumers is the signal that an element is a library primitive rather than a domain widget.

**Is this a compositional primitive or a widget with its own domain?** Compositional primitives combine with other primitives to form richer UI. A Toggle combines with an Icon to form a toggle-with-icon; a Button combines with a Tooltip-as-element to form a tooltipped button; a ProgressBar combines with a TextLabel to form a labeled progress bar. Widgets with their own domain — a "shulker color picker," a "furnace fuel gauge" — are not compositional; they are self-contained applications of domain logic. Primitives ship; domain widgets do not.

**Is this essentially context-agnostic?** If an element's shape only makes sense in one rendering context, it still *may* ship — SlotGroup is the clearest case of a context-specific element the library must provide because consumers cannot reasonably build it themselves. But the bar is higher. Context-specific elements must be load-bearing primitives of their context (SlotGroup is load-bearing to inventory menus; nothing else ships this property).

An element that passes all three tests ships in MenuKit. An element that fails any of them stays with the consumer. Borderline cases are decided by the library-not-platform principle — when in doubt, don't ship. The consumer can always build it; the library cannot easily unship it later without breaking consumers.

This criterion is sharper than "useful elements ship." Usefulness alone would grow the library unboundedly. The three-test conjunction forces library additions to earn their place by being general primitives that multiple independent consumers would independently build if MenuKit did not exist.

## Scope ceilings

MenuKit deliberately does not do the following. These are not gaps to be filled later; they are intentional limits on the library's scope.

**Config UIs.** Out of scope. Cloth Config owns this space. Consumer mods building config screens use Cloth Config. Consumers who want an unusual config experience can build a standalone screen with MenuKit's elements, but the library ships no field-entry widgets, no config-file bindings, no config-page scaffolding.

**Chat, F3, world-selection, main menu.** Out of scope. These are narrow vanilla contexts with little cross-consumer demand and significant vanilla ownership. MenuKit does not generalize to them, does not provide injection helpers for them, does not recognize them as target contexts.

**Full-screen replacement UIs.** Out of scope in the sense that MenuKit does not ship a "replace the main menu" or "replace the pause menu" framework. Consumers who want this build a standalone screen with MenuKit's elements and register it however their use case demands.

**Animation framework.** Out of scope. HUD notifications ship with baked-in slide and fade animations because notifications are the one case where animation is load-bearing to the element's purpose. Beyond that, MenuKit does not provide a general animation DSL, tweening library, or transition system. Consumers who want animated UI supply their own tween state and pass it through suppliers.

**Theme system.** Out of scope. MenuKit ships a narrow visual vocabulary — four PanelStyles, vanilla-matched colors, vanilla slot backgrounds. Consumers who want themed UI implement custom rendering in their elements. The library does not provide a theme registry, color swap, or style override mechanism.

**Ecosystem-wide event bus.** Out of scope, per the library-not-platform principle. MenuKit provides per-screen event listeners for events scoped inside a specific MenuKit screen. Consumers wanting ecosystem events use Fabric's event API or their own.

**General drag-and-drop.** Out of scope beyond the inventory-menu drag protocol (which is a specific case of slot-to-slot interaction that already has vanilla precedent). MenuKit does not ship a "drag this element to that element" framework. The slot drag protocol exists because slots have a shape where drag is load-bearing; other drag use cases are consumer work.

**Networking infrastructure beyond inventory menus.** Out of scope. Inventory menus use vanilla's AbstractContainerMenu sync protocol; MenuKit integrates with it. HUDs and standalone screens are client-local by default. Consumers needing server communication in their HUDs or standalone screens use Fabric's networking API directly.

The common thread: MenuKit stops where existing ecosystem libraries do the job better, where vanilla ownership is heavy, or where the library would have to become a platform to provide the feature. Where those conditions don't hold, MenuKit expands — but only within the three rendering contexts it targets.

## Guarantees across contexts

MenuKit holds five architectural contracts for the inventory-menu context. These contracts were empirically verified in the library's first phase of work. They are:

1. **Composability.** MenuKit plays well with other mods because it never takes ownership of code paths it does not need.
2. **Vanilla-slot substitutability.** A MenuKit slot is a vanilla slot. Ecosystem mixins into `Slot` affect MenuKit slots identically.
3. **Sync-safety.** Structure is decided once and frozen; visibility syncs via vanilla's C2S mechanism; rendering is client-local. The protocol cannot desync.
4. **Uniform abstraction.** `SlotGroupLike` is the type consumers program against; native SlotGroups and observed VirtualSlotGroups both implement it.
5. **Inertness.** Hidden is invisible to the world, not just to rendering. Phantom items don't exist.

These five guarantees are specific to the inventory-menu context — they all concern the sync protocol and vanilla slot compatibility. They do not transplant directly to HUDs and standalone screens, because those contexts have no sync protocol and no vanilla slot type.

The **discipline** behind the guarantees, however, carries forward across all contexts:

- **Composability** applies everywhere. No context takes ownership of code paths it doesn't need.
- **Substitutability** applies wherever MenuKit produces a vanilla-typed thing — standalone screens are `net.minecraft.client.gui.screens.Screen` subclasses; HUD overlays render through the same GuiGraphics pipeline vanilla uses.
- **Declared structure, mutable visibility** (a restatement of the sync-safety discipline stripped of its sync specifics) applies everywhere.
- **Inertness** applies everywhere: hidden HUD panels don't tick, hidden standalone-screen elements don't render or intercept input, hidden inventory slots are EMPTY and inert.
- **Uniform abstraction** applies wherever the library exposes polymorphic element types.

The contexts document spells out the concrete guarantee analogues. The thesis commits the library to five disciplines that the context-specific guarantees enforce: **composability**, **substitutability**, **inertness**, **declared structure**, and **uniform abstraction**. The specific guarantees differ between contexts; the disciplines do not.

## Keybind infrastructure

MenuKit ships a small utility subsystem for multi-key keybinds — bindings like `Alt+Shift+K` rather than vanilla's single-key model, and the ability for consumer mods to define keybinds in their own config files. This subsystem is adjacent to the component library, not part of it. It does not follow component patterns, does not compose with panels, and does not interact with the element palette.

It exists because keybind handling is a shared consumer need that benefits from being solved once. It lives in the same jar as the component library because splitting it out would add friction without adding clarity. Its inclusion in MenuKit does not expand the component-library thesis — the palette does not include "keybind" as an element, and the contexts document does not include keybind capture as a rendering context.

Treat it as a fellow traveler. It ships with MenuKit because that's where it makes sense to ship; it does not belong to the thesis.

## Summary

MenuKit is a component library for Minecraft UI. It ships small, composable elements that work across inventory menus, HUDs, and standalone screens. It stops at being a library — it does not take ownership of vanilla code paths, does not compete with specialized libraries, and does not grow beyond its target contexts. It ships elements that multiple independent consumers would reach for; it leaves domain widgets to consumers. It holds architectural disciplines uniformly across the contexts it targets.

Every element that ships, every context that's supported, every feature added in the library's lifetime is checkable against this thesis. If a candidate does not survive the check, it belongs with the consumer, not with MenuKit.
