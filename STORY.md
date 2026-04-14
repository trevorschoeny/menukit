# The MenuKit Story, Canonical

## The problem we're actually solving

Minecraft menus are deceptively hostile. What looks like a grid of slots to the player is, underneath, a flat `List<Slot>` on a `ScreenHandler` being shuffled between two machines that might be on opposite sides of the planet. Every menu you've ever seen is held together by a fragile protocol of slot-click packets and sync snapshots indexed by position in that flat list. Mod developers who want to build menus have to become fluent in that protocol — sync IDs, cursor state, the reason a shift-click crashes the server when a third mod is installed. The abstraction leaks in every direction, and the leaks are sharp.

MenuKit's thesis is that nobody should have to think about any of this. A consumer declares *what they want* — these slots, here, backed by that storage, behaving like an input — and the library handles every protocol detail underneath. The test of success is that a mod author who's never heard of a sync packet can still build a chest that doesn't desync.

But MenuKit makes a second, quieter promise that matters just as much: it does this *without disrupting the ecosystem around it*. A mod that modifies slot rendering sees MenuKit slots rendered the same way. A mod that adds right-click behavior to every slot in the game sees MenuKit slots respond the same way. MenuKit gives its consumers powerful tools while staying invisible to everyone else.

## The core insight

The flat slot list vanilla demands is the wrong altitude for humans. Humans think in groups: "the player's inventory," "the crafting grid," "the output slot." Vanilla thinks in indices: slot 27, slot 28, slot 29. MenuKit is the lens between those worlds — consumers author in the language of groups while the library quietly maintains the flat indexing vanilla needs.

The architecture is a bidirectional translator. Upward, it presents a clean hierarchy of Panels and SlotGroups. Downward, it emits the `List<Slot>` vanilla requires, with every slot carrying its own coordinates in the hierarchy — `(panelId, groupId, localIndex)` as final fields on a `MenuKitSlot extends Slot` — so the translation never needs a side map that can desync. Given any slot, you know where it lives. Given any coordinate, you know which flat index it maps to, because the `ScreenHandler` subclass owns a precomputed flat-index range on each SlotGroup, built at screen construction.

That subclass — `MenuKitScreenHandler` — is the structural heart of the library. It holds the Panel tree, owns the flat slot list, owns the coordinate mapping in both directions, and handles all custom C2S packets. It's a proper subclass of vanilla `ScreenHandler`, not a wrapper, because vanilla's sync machinery traverses `this.slots` constantly and anything that breaks `instanceof Slot` breaks the world.

## The hierarchy

**Screen** wraps the handler's lifecycle. It exists on both sides, registered through `ScreenHandlerType` the way vanilla expects, with the standard two-constructor pattern. The client-side partner is `MenuKitHandledScreen extends HandledScreen`, which owns layout, rendering, hover detection, keybind dispatch, screen-scoped events, and drag modes.

**Panel** is the fundamental unit of composition — every element in MenuKit lives inside a Panel, because that's the scope at which visibility toggles. A Panel holds an ordered list of SlotGroups, a list of PanelElements, a PanelPosition constraint, a visual style, and a visibility flag.

**SlotGroup** is where behavior lives. A group composes a Storage, an InteractionPolicy, a QuickMoveParticipation, routing metadata, and layout metadata, with delegation methods (`canAccept`, `canRemove`, `maxStackSize`) that forward to the policy. When a slot asks "can this stack go in me?", it delegates up to its group.

**MenuKitSlot** is a thin subclass of vanilla `Slot` that carries its coordinates and delegates behavior to its owning group. Just enough subclass to carry identity; the group is where the logic lives.

Each layer has a single responsibility. Screens own lifecycle, indexing, layout, and rendering. Panels own composition and visibility. SlotGroups own behavior. Slots own identity. The hierarchy is shallow on purpose — deep hierarchies are where the bugs live.

## Vanilla-slot substitutability: the invisibility principle

Here's the commitment that makes MenuKit a library rather than a platform: **a MenuKit slot is a vanilla slot to the outside world.** Not "wraps one," not "adapts one" — it inherits from `Slot` with additive capabilities bolted on, and every behavior method either calls `super` or delegates in a way that preserves vanilla's contract.

The consequence is strong. A mod that mixins `Slot.canInsert` to add a global filter has its filter run on MenuKit slots. A mod that modifies slot rendering renders MenuKit slots the same way. A mod that adds right-click handling to every slot sees MenuKit slots respond identically. None of those mods need to know MenuKit exists. None need compatibility patches. MenuKit inherits every ecosystem-wide slot enhancement for free, and every mod that enhances slots continues to work on MenuKit screens automatically.

This is the Liskov Substitution Principle taken seriously as a compatibility strategy. MenuKit's additions *layer on top of* vanilla behavior, never *replace* it. When MenuKit delegates `canInsert` to its group's policy, the mechanism is still `canInsert`, and another mod's mixin into `canInsert` composes with MenuKit's delegation. The most restrictive answer wins, which is almost always the right semantics.

This is also what resolves the "who's responsible for ambient slot behaviors" question. A consumer who wants custom drag modes on every slot in the game writes a mixin into `AbstractContainerScreen` or `Slot`, and substitutability carries that behavior to MenuKit slots. MenuKit doesn't need to deliver ambient behavior — the ecosystem's existing mechanisms do, because MenuKit slots present themselves as ordinary vanilla slots. A consumer who wants drag modes only on their MenuKit screen registers a handler on MenuKit's event bus, scoped to `MenuKitHandledScreen`. Both paths exist, and they don't conflict.

There are subtleties. A mod doing `slot.getClass() == Slot.class` instead of `instanceof Slot` will miss MenuKit slots — but it'll also miss `CraftingResultSlot` and `TrappedSlot`, so that mod is already broken by vanilla. MenuKit inherits vanilla's existing contract with the ecosystem, including its limitations. The rule holds: if it works on `CraftingResultSlot`, it works on MenuKit slots.

## Storage and interaction as orthogonal axes

The move that makes SlotGroups useful is refusing to conflate two things vanilla conflates: *where items live* and *what you can do with them*.

**Storage** is a narrow interface: `getStack`, `setStack`, `size`, `markDirty`. A subinterface `PersistentStorage` adds `save`/`load` hooks for implementations that need persistence; `EphemeralStorage` and `VirtualStorage` don't implement it, keeping their contracts minimal.

MenuKit ships a small family of implementations. `PlayerStorage` delegates to a `PlayerInventory` — items follow the player. `BlockEntityStorage` holds a block position and resolves the block entity each access (safer than caching across chunk unloads). `ItemStackStorage` reads and writes `ContainerComponent` on an `ItemStack`, which is how shulker boxes and bundles work in component-land. `EphemeralStorage` is an in-memory list that drops its contents on close. `VirtualStorage` is the escape hatch — give it a supplier and a mutator.

Each storage is adapted internally to vanilla's `Inventory` interface, because that's what `Slot` expects as backing. Consumers never see `Inventory`; they see Storage. The adapter is plumbing, owned by the handler.

**InteractionPolicy** is a pure behavioral description — a record of predicates and callbacks. `canAccept`, `canRemove`, `onInsert`, `onTake`, `maxStackSize`. MenuKit ships common policies as named constructors: `input(filter)`, `output(onTake)`, `free()`, `locked()`, `display()`. Composite policies like `craftGrid(inputs, output, recipeResolver)` wire several groups together — the output group's `onTake` consumes from the inputs group's storage and re-resolves the recipe.

Because storage and policy are independent, a full crafting table that outputs into the player inventory is just a declarative arrangement: a 3×3 Input group with EphemeralStorage, a 1-slot Output group with a policy whose `onTake` consumes inputs, and the player's inventory group referenced as the shift-click destination. No bespoke handler. Just composition. The consumer assembled a behavior that vanilla only ships as a monolith.

## The declarative topology

The hardest architectural question was how panels appear and disappear without breaking vanilla's stability assumptions. MenuKit's answer: *they don't*. They're declared upfront at screen construction. The consumer writes a topology — "this screen has a main inventory panel, an upgrades panel, and a filter panel" — and all three are fully allocated at `OPEN_SCREEN` time. Slots created, storage attached, indices assigned.

What changes at runtime is **visibility, not structure**. When the user clicks the upgrades tab, MenuKit flips a visibility flag on that Panel. The slots were always there, always backed by their storage, always participating in sync. They just weren't being drawn.

The architecture is three layers, cleanly divided:

- **Structure** is server-authoritative and frozen — decided once at screen construction, never mutable.
- **Visibility state** is server-authoritative and mutable — panel show/hide flows through vanilla's `clickMenuButton` C2S packet so both sides agree on what's presented.
- **Rendering** is client-local — the layout engine, slot positioning, and element rendering all recompute each frame from the visible structure.

The same pattern that makes React's immutable-state-plus-view-function model work, and the same pattern that makes a compiled query plan plus runtime filters work in a database.

Layout is **constraint-based**. Panels declare a `PanelPosition` — `BODY` stacks vertically in the main column; `RIGHT_OF`, `LEFT_OF`, `ABOVE`, `BELOW` position relative to a named anchor panel. The layout engine computes bounds each frame from these constraints. Consumers think in relationships, not coordinates.

The builder API is declarative:

```
MenuKitScreenHandler.builder(MENU_TYPE)
    .panel("main", p -> p
        .group("container", BlockEntityStorage.of(pos), InteractionPolicy.free())
        .group("player", PlayerStorage.of(player), InteractionPolicy.free()))
    .panel("upgrades", p -> p
        .rightOf("main")
        .toggleKey(GLFW.GLFW_KEY_U)
        .group("slots", EphemeralStorage.of(4), InteractionPolicy.input(isUpgrade))
        .hidden())
    .build(syncId)
```

The builder walks the declaration, allocates slots in flat-index order, populates the coordinate maps, and produces a configured handler ready for vanilla. Declaration order determines flat index order, which determines shift-click priority and tab order naturally from how the code reads.

Topology is static once declared. If a real use case ever surfaces where a screen genuinely can't know its shape until after opening, the path forward would be append-only growth via a custom packet — but this hasn't been needed, and the static constraint is what buys the guarantees.

## Panel elements

Slot groups determine a panel's layout; its size comes from the slot grid they declare. Elements are a decorative and interactive layer on top of that layout, positioned absolutely within the panel's content area via `(childX, childY)` relative to the panel's padded origin.

The core `PanelElement` interface exposes `render`, `mouseClicked`, bounds, and an `isVisible` hook. MenuKit ships two implementations: `Button` (click handler, hover detection, optional disabled predicate, left-click only by default) and `TextLabel` (component text, ARGB color, optional shadow). Consumers implement `PanelElement` directly for icon buttons, progress bars, toggles, or anything more specialized.

Elements participate in the same visibility story as slots: hidden panels skip element rendering and click dispatch entirely. Consumers gate individual elements by overriding `isVisible` without touching the panel's declared topology.

Element clicks dispatch before drag modes and before vanilla's slot handling. A button returning false from `mouseClicked` lets the click fall through — that's how a consumer implementing a draggable element would opt into drag mode. The default Button consumes only left-click; right-click and middle-click pass through, so a button placed over a slot doesn't steal that slot's right-click handler.

## Inertness: what hidden really means

"Hidden" had to mean more than "not rendered," because a third-party mod walking `handler.slots` to consolidate items would otherwise see phantom items — stacks sitting in a hidden panel's slots, invisible to the player but visible to iteration.

So MenuKit introduces **inertness** as a first-class state. A slot in a hidden panel is inert to the outside world entirely: `canInsert` returns false, `canTakeItems` returns false, `getStack` returns `ItemStack.EMPTY`, quick-move routing skips it, `isActive` returns false, and `MenuKitSlot.isInert()` exposes the status for well-behaved third parties.

The key move is `getStack` lying. The real stack sits safely in the backing storage, untouched. MenuKit refuses to admit it exists while the panel is hidden. When the panel becomes visible again, `getStack` starts returning the real stack and `sendContentUpdates` pushes the current state to the client.

This is still consistent with substitutability. An inert MenuKit slot is a valid vanilla slot that happens to be empty — a legal state for any slot. A mixin on `getStack` still runs, composing over MenuKit's inertness check. The outside world sees a consistent, well-defined slot; MenuKit has just chosen to report empty under specific conditions.

Panel elements follow the same rule. A button in a hidden panel isn't rendered and doesn't receive clicks.

Visibility transitions require one `sendContentUpdates` pass over the affected slot range. Both directions are bounded and trivial. Visibility is almost free.

Inertness also clarifies how MenuKit-internal features should work. Any feature that counts or queries items across the screen — "how many diamonds are in this menu?" — should read through Storage, not through slot iteration. Storage is the source of truth; slots are the presentation-layer access point.

## Shift-click as declarative routing

Shift-click is vanilla's worst API. Every modder who's written a custom container has written a buggy `quickMove`. MenuKit turns it from an imperative "override this method and write a routing loop" into a declarative routing graph.

Every SlotGroup declares its `QuickMoveParticipation` — `EXPORTS`, `IMPORTS`, `BOTH`, or `NONE` — and its existing filters are reused for routing. That's the whole configuration surface.

The library's `quickMoveStack` override runs a single algorithm: identify the source group, collect candidate destinations that can accept the stack, order them by priority, try each in turn until the stack is consumed. Priority layers three strategies:

**Source-aware baseline.** If the source is player-backed, prefer container-backed destinations; if container-backed, prefer player-backed. This gets vanilla-feel shift-click automatically on any screen containing both.

**Declared priority.** Each group has a `shiftClickPriority: int`. Defaults ship sensible — PlayerInventory at 0, container groups at 100, crafting inputs at 200, outputs at -1.

**Directional pairing.** Groups declare `.pairsWith(otherGroup)` edges — shift-click from this group prefers that one. This is how vanilla furnaces think: fuel items target the fuel slot, smeltable items target the input slot.

The three layer cleanly. A group with no explicit configuration gets vanilla feel because the baseline does the right thing.

## The dual mode: owned and observed

MenuKit's abstractions become interesting when they describe other people's screens too — vanilla chests, modded backpacks, anything that implements `ScreenHandler`.

This works because vanilla already gave us the grouping key. Every `Slot` carries an `inventory` reference. A vanilla chest handler's 63 slots resolve to exactly two distinct `Inventory` object identities: the chest's for the first 27 slots, the player's for the remaining 36. Walk the slot list, group contiguous runs by inventory reference-identity, synthesize a `VirtualSlotGroup` per run. That gets you chests, shulkers, barrels, ender chests, horse inventories, and most modded containers.

Weird cases — furnace-style handlers where three logical slots share one inventory, brewing stands, creative inventory with its tab system, genuinely bespoke modded handlers — get dedicated recognizers. MenuKit maintains a `HandlerRecognizerRegistry` of matchers, tried in order with the inventory-identity walker as the fallback. Consumers register their own matchers via the public `register(Recognizer)` API.

Both `SlotGroup` and `VirtualSlotGroup` implement the `SlotGroupLike` interface — the type consumer code programs against. Query a slot with `HandlerRecognizerRegistry.findGroup(handler, slot)` and you get back an `Optional<SlotGroupLike>` regardless of whether the screen is MenuKit-native or observed. Whether the group is a real SlotGroup or a synthesized VirtualSlotGroup is transparent. **The uniform-abstraction promise is structural, not aspirational.**

VirtualSlotGroup's storage is read-only. MenuKit reads through it freely — `getStack` reflects the live state of the wrapped vanilla slots — but `setStack` is a no-op. Consumers wanting to mutate a vanilla screen's contents do so through vanilla's own APIs, not through MenuKit. That's the library-not-platform line: MenuKit describes screens it doesn't own, but it doesn't mutate them.

On observed screens, QuickMoveParticipation describes what vanilla's shift-click would do — it predicts the routing, it doesn't control it. Consumers use this to reason about behavior ("if this stack got shift-clicked, where would it land?") without MenuKit modifying vanilla's routing code. On MenuKit-native screens, MenuKit is the routing implementation and participation is authoritative. On observed screens, participation is a best-effort prediction. The split is honest.

## The line MenuKit chooses not to cross

MenuKit stops at being a library. It does not mixin into vanilla's `HandledScreen` or `AbstractContainerScreen`. It does not inject behavior into screens it didn't build. It provides the recognizers, the abstractions, the builder API, and a MenuKit-aware `HandledScreen` base class — for consumers building their own screens. Consumers who want ambient hover-sort across every vanilla chest write that mixin themselves, using MenuKit's recognizers and event bus.

This restraint is what substitutability buys us. Because MenuKit slots present as vanilla slots, consumers who want ambient behaviors already have a delivery mechanism — the normal modding ecosystem — and MenuKit slots participate automatically. MenuKit doesn't need to be a platform because the platform already exists, and MenuKit's contribution is to stay compatible with it.

The mixin layer follows the same restraint. MenuKit uses the most additive, most composable mixin patterns — non-cancelling `@Inject`s, MixinExtras `@WrapOperation` and `@ModifyReturnValue`, duck interfaces via `@Mixin` — and avoids `@Redirect` and `@Overwrite` entirely. Two mods using MenuKit compose cleanly. MenuKit plus any other well-behaved mod composes cleanly. Where MenuKit cannot guarantee composition, it declines.

## The contract

Five guarantees:

- **Composability.** MenuKit plays well with other mods because it never takes ownership of code paths it doesn't need.
- **Vanilla-slot substitutability.** A MenuKit slot is a vanilla slot. Ecosystem mixins into `Slot` affect MenuKit slots identically and automatically. No compatibility patches required.
- **Sync-safety.** Structure is decided once and frozen; visibility syncs via vanilla's C2S mechanism; rendering is client-local. The protocol cannot desync because MenuKit never asks it to do anything structural after open.
- **Uniform abstraction.** `SlotGroupLike` is the type consumers program against, and both native SlotGroups and observed-screen VirtualSlotGroups implement it. Consumers write one code path.
- **Inertness.** Hidden is invisible to the world, not just to rendering. Phantom items don't exist.

Five declarative surfaces a consumer configures:

- **At the group level:** Storage (where items live), InteractionPolicy (what operations are allowed), QuickMoveParticipation (how the group participates in shift-click routing).
- **At the panel level:** Visibility (whether the panel is currently presented), Elements (buttons, labels, and custom widgets positioned within the panel).

Every architectural choice — subclassing `Slot` thinly, separating storage from policy, declaring topology upfront, refusing to mixin into `HandledScreen`, lying about `getStack` when inert, routing shift-click through a priority graph, preserving substitutability at every layer, making the uniform abstraction structural via `SlotGroupLike`, keeping panel elements separate from slot layout — is one of those contracts being honored in a specific context.

The coherence isn't accidental. It's the same idea repeated at every level: describe the structure once, statically; let presentation and behavior vary dynamically on top; treat other mods as peers and the vanilla contract as sacred; never ask the protocol to do something it wasn't designed for.

MenuKit gives consumers structural abstractions and scoped behaviors for their own menus, stays invisible to the rest of the ecosystem, and inherits every slot enhancement the modding community ever writes. That's the library. That's MenuKit.