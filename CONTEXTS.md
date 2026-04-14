# CONTEXTS

The three rendering contexts MenuKit targets. This document names each context, specifies its composition root, enumerates the context-specific machinery that lives beneath the shared element abstraction, states the concrete guarantees that hold within it, and identifies the consumer entry points used to build UI in it. It also describes how the three contexts share composition through Panel, and names the contexts MenuKit deliberately does not target.

This document is the reference for which target is which. Subsequent work lifting Panel across contexts, building context-specific containers, or deciding whether a new element belongs in one or all contexts checks against this document.

---

## What a "context" is

A rendering context, in MenuKit's sense, is a situation in which a consumer wants a panel of elements to render, receive input, and update over time — *and* has enough distinct surrounding machinery that the library treats it as a separate integration point.

Three properties distinguish one context from another:

- **How the container is held by the game.** Inventory menus are held by `AbstractContainerMenu` on the server and `AbstractContainerScreen` on the client, with vanilla's sync protocol linking them. HUDs are held by the HUD render callback, fired each frame. Standalone screens are held by the client's current-screen slot, with vanilla's screen lifecycle.
- **What kind of update loop drives the container.** Inventory menus update through sync (visibility changes propagate server→client). HUDs update through per-frame supplier evaluation. Standalone screens update through a screen lifecycle (init, render, tick, removed).
- **What context-specific elements the container requires.** Inventory menus require slot groups. HUDs and standalone screens do not.

Each of MenuKit's three contexts has distinct answers to all three questions. Any candidate context where the answers collapse into an existing context is not a new context — it is a use case *within* an existing context. Anything that cannot answer these three questions at all (chat messages, F3 debug overlay, world-selection list) is not a context MenuKit targets.

## Inventory menus

The context where a consumer opens a screen tied to a container — a chest, furnace, custom machine, custom menu — with slots that sync between client and server.

**Composition root.** Panel. In this context, a panel holds both a list of slot groups and a list of elements. The slot groups determine the panel's size from their grid; the elements are positioned absolutely within the content area after padding.

**Context-specific machinery.**

- **Server-side handler** (`AbstractContainerMenu` subclass) that owns the panel tree, the flat slot list, and the bidirectional coordinate mapping between panel/group/local and flat slot index.
- **Client-side screen** (`AbstractContainerScreen` subclass) that owns per-frame layout computation, panel background rendering, slot positioning, hover detection, and element click dispatch.
- **Slot subclass** (`MenuKitSlot`) that carries group membership and coordinates, and is a proper `net.minecraft.world.inventory.Slot` to the outside world.
- **Storage abstraction** that backs each slot group — block entity, player inventory, ephemeral, item-stack-backed, read-only, virtual. The storage layer is how consumers plug any item container into the slot system without it having to look like a `net.minecraft.world.Container`.
- **Interaction policy and quick-move participation** attached per slot group. Policy controls what can enter and leave; quick-move participation controls whether the group participates in shift-click routing and in which direction.
- **Three-layer shift-click routing**: directional pairings first, then source-aware baseline (player↔container preference), then declared priority.
- **Sync protocol** for visibility: visibility is mutable, changes trigger a `broadcastChanges()` pass, and client-initiated toggles go through vanilla's `clickMenuButton` C2S packet. Structure is frozen at handler construction.
- **Handler recognizer registry** that allows consumers to observe non-MenuKit container menus as `SlotGroupLike` views, so consumer code can reason about vanilla handlers (furnace, brewing stand, generic container) with the same abstraction it uses for MenuKit handlers.
- **Constraint-based panel layout** (`PanelPosition`): panels are body-stacked or relatively positioned against a named anchor panel.

**Guarantees.** Five, all empirically verified.

1. **Composability.** MenuKit inventory menus coexist with other mods that mixin into vanilla screen classes, container menu classes, or slot classes. MenuKit does not take ownership of those types; it subclasses them cleanly.
2. **Vanilla-slot substitutability.** A MenuKit slot is a vanilla slot. Ecosystem mixins into `Slot` affect MenuKit slots identically. Drop-in compatibility is structural, not best-effort.
3. **Sync-safety.** Structure is decided once at handler construction and frozen. Visibility syncs through vanilla's sync protocol via C2S clickMenuButton; rendering is client-local. No construction-time decision can desync client and server mid-session.
4. **Uniform abstraction.** `SlotGroupLike` is the type consumers program against. Native MenuKit slot groups and observed VirtualSlotGroups from the recognizer registry both implement it. Consumer code written against the abstraction works on any container handler the registry understands.
5. **Inertness.** Hidden is invisible to the world. Hidden slots return EMPTY, refuse insertion, skip quick-move, and render off-screen so vanilla's hover pipeline cannot find them. Phantom items do not exist.

**Consumer entry points.**

- `MenuKitScreenHandler.builder(menuType)` — declarative handler construction: panels, groups, elements, pairings, toggle keys.
- `MenuKitHandledScreen` — client-side base class for MenuKit-owned inventory screens.
- `HandlerRecognizerRegistry.register(Recognizer)` — extending the uniform abstraction to new vanilla or third-party container menus.
- Consumer mixins into vanilla container screens, composing MenuKit elements inside — the "decorate vanilla" path.

## HUDs

The context where a consumer wants something to render on top of the game while the player is actively playing, without opening a screen.

**Composition root.** Panel. A HUD panel holds a list of elements only (no slot groups). It carries a screen-edge anchor and offset rather than a constraint against another panel, because HUDs render against the game window rather than a centered container.

**Context-specific machinery.**

- **Per-frame render dispatch** via the vanilla HUD rendering pipeline (`HudRenderCallback` or equivalent). The panel is rendered each frame without ever entering a screen lifecycle.
- **Screen-edge anchoring** via a nine-position enum (top-left, top-center, top-right, center row, bottom row). Offsets adjust inward from the anchor. Resolution happens per-frame against the current GUI-scaled screen size.
- **Auto-sizing from content.** Panels can declare an explicit size or grow to fit their children plus padding.
- **Supplier-driven dynamic content.** All runtime data — text strings, item stacks, progress values, icon identities — comes from `Supplier<T>` fields evaluated at render time. Elements are otherwise stateless definitions.
- **Screen-open visibility policy.** A HUD panel declares whether it stays visible when a screen is open or hides. The default is to hide, matching vanilla HUD behavior.
- **Notification subsystem.** A time-bounded element type with slide-in and fade-out animation, triggered at runtime by key and optional text/item data. The only stateful HUD element; animation state is held centrally rather than on the element itself.
- **No input dispatch.** HUDs are render-only. MenuKit does not route clicks, keys, or any other input to HUD elements. This is a deliberate scope boundary, not a gap: interactive HUD elements during gameplay would require input dispatch machinery that competes with vanilla input handling and breaks the tick-safety discipline. **Consumers who need interactive overlays during gameplay build a standalone screen**, opened by keybind and rendered on top of the game. The HUD is for display; the standalone screen is for interaction.

**Guarantees.** The HUD context's analogues of the five disciplines.

1. **Composability.** HUD panels coexist with vanilla HUD rendering and with other mods' HUD overlays. MenuKit does not replace vanilla's HUD renderer and does not take ownership of the HUD render pipeline beyond registering its own callbacks.
2. **Vanilla-pipeline substitutability.** HUD elements render through the same `GuiGraphics` API and the same HUD render hooks vanilla exposes. Ecosystem observation of those hooks sees MenuKit's output identically to vanilla's.
3. **Tick-safety.** Structure is declared once at registration. Runtime re-evaluation happens through suppliers only; there is no structural mutation per frame. This makes each frame's HUD cost bounded by the declared element count, not by any runtime shape-change.
4. **Uniform abstraction.** The element abstraction is the same one used in inventory menus and standalone screens. A Button on a HUD panel is the same type as a Button on an inventory-menu panel, rendered through the same code path.
5. **Inertness.** Hidden HUD panels do not evaluate their suppliers, do not reserve screen space, do not emit render calls. A panel whose `showWhen` is false costs nothing per frame beyond the predicate evaluation.

**Consumer entry points.**

- HUD panel builder — declarative construction from mod init: anchor, offset, padding, style, elements, visibility predicate.
- Notification builder + runtime trigger — `build()` at init, `notify(key, ...)` at runtime.
- Consumer mixins into vanilla HUD rendering, composing MenuKit elements inside — the "decorate vanilla" path for HUDs.

## Standalone screens

The context where a consumer wants a full-screen, client-local, interactive UI without a container menu — a custom main-menu replacement, a settings-like screen, an in-game UI opened by keybind that is not tied to a block or entity.

**Composition root.** Panel. A standalone-screen panel holds a list of elements only (no slot groups). It uses the same constraint-based `PanelPosition` layout as inventory-menu panels so multi-panel standalone screens compose the same way.

**Context-specific machinery.**

- **Screen subclass** (`net.minecraft.client.gui.screens.Screen`, not `AbstractContainerScreen`) that owns the panel tree, per-frame layout computation, and full input dispatch.
- **Screen lifecycle integration**: `init()` builds layout state against the current screen size; resize re-invokes `init()`; `removed()` clears listeners. The screen lifecycle is vanilla's; MenuKit does not replace it.
- **Full input dispatch**: `mouseClicked`, `mouseReleased`, `mouseDragged`, `keyPressed`, `charTyped`, `mouseScrolled` all route through the panel tree before falling through to vanilla defaults.
- **No slot system.** No sync protocol, no container menu, no server-side counterpart. Standalone screens are entirely client-local unless the consumer opens their own networking.
- **Optional pause-the-game behavior.** Standard vanilla `Screen` decision — consumers override `isPauseScreen()` per their use case.
- **Escape closes the screen.** Standard vanilla behavior; consumers override `shouldCloseOnEsc()` if needed.

**Guarantees.** The standalone-screen context's analogues of the five disciplines.

1. **Composability.** MenuKit standalone screens coexist with other screens, with other mods' screens, and with vanilla's screen management. MenuKit does not take ownership of the current-screen slot or the screen stack.
2. **Vanilla-screen substitutability.** A MenuKit standalone screen *is* a `net.minecraft.client.gui.screens.Screen`. Ecosystem mixins into `Screen` affect MenuKit standalone screens identically. This is the direct analogue of vanilla-slot substitutability — same discipline, different vanilla type.
3. **Lifecycle-safety.** Structure is declared at screen construction and frozen. `init()` and resize recompute layout only; they do not rebuild the element tree. `removed()` does not leave listeners attached. The screen can be opened, resized, and closed any number of times without structural drift.
4. **Uniform abstraction.** Panel and PanelElement, the same types used in the other two contexts.
5. **Inertness.** Hidden elements do not render, do not receive input, do not contribute to layout. Closed screens (removed from the screen stack) have no residual presence.

**Consumer entry points.**

- `MenuKitScreen` base class — subclass with panels declared in construction, optional title and pause-behavior overrides.
- Consumer mixins into vanilla standalone screens (pause menu, title screen, options screens), composing MenuKit elements inside — the "decorate vanilla" path for standalone screens.

## Cross-context composition

Panel is the composition root in all three contexts. PanelElement is the element interface in all three contexts. A Button written once works in any context it is placed in. This is the center of MenuKit's identity as a component library — the composition unit and the element vocabulary are shared, and the context-specific machinery lives beneath the shared abstraction.

Three practical consequences of shared composition follow.

**What panels hold differs by context.** An inventory-menu panel holds slot groups *and* elements. A HUD panel holds elements only. A standalone-screen panel holds elements only. Slot groups are the only first-class container-held thing that exists in one context and not the others. Elements are universal.

**What panels know differs by context.** A panel does not know which context it is in. Position metadata (body-stacked vs. relative-to-anchor vs. screen-anchored) is carried on the panel, but the panel does not reach for context-specific machinery to interpret it. The container holding the panel — the handler, the HUD dispatch, the standalone screen — interprets position metadata according to its own layout rules.

**What containers provide differs by context.** The inventory-menu handler provides slot sync, shift-click routing, cursor handling, and full input dispatch. The HUD dispatch provides per-frame iteration and auto-sizing, and nothing else — **HUDs are render-only**. The standalone screen provides screen lifecycle and full input dispatch. Each container is the context-specific part of the system; the panel is the context-neutral part.

A corollary of this asymmetry: interactive elements have their interactive behavior only in contexts with input dispatch. Consumers who want click-driven behavior during gameplay build a standalone screen, not a HUD. The palette document specifies per element whether it is meaningful on HUDs at all.

The target state of the library is that a consumer writing an element (Button, Toggle, ProgressBar, Icon, Tooltip) can do so without caring which context it will end up in. Context-specific elements — SlotGroup is the canonical case — declare their scope explicitly in the palette and do not transplant.

## What is not a context

Several rendering situations in Minecraft are not MenuKit contexts. Each is ruled out for reasons that match the scope ceilings in the thesis.

**Config UIs** are not a MenuKit context. Config UIs have a specific domain (typed fields, defaults, apply-on-save, file binding) that is handled well by Cloth Config. MenuKit's standalone-screen context is the appropriate home for any consumer who wants to build a config-like screen from primitives, but MenuKit does not ship config-as-a-context with field widgets and save pipelines.

**Chat** is not a MenuKit context. Chat has heavy vanilla ownership (message pipeline, command suggestions, auto-complete), narrow cross-consumer demand, and no compositional value from sharing vocabulary with the other contexts. Consumers who want to decorate the chat HUD write their own mixins; MenuKit does not recognize chat as a target.

**F3 debug overlay** is not a MenuKit context. The F3 overlay is a vanilla debugging feature with narrow ownership. Consumers who want custom debug text use a HUD panel; consumers who want to modify F3 itself do so outside MenuKit.

**World-selection, server-selection, realms** are not MenuKit contexts. These are narrow vanilla screens with specific workflows. Consumers who want to decorate them write their own mixins and use MenuKit elements inside — the same pattern as any vanilla standalone-screen decoration.

**The main menu and pause menu**, specifically, are not MenuKit contexts in the sense that MenuKit does not target them as a category. They are instances of vanilla standalone screens; consumers who want to add MenuKit elements to them use the standalone-screen consumer-mixin pattern.

**Game-world rendering** — rendering things in the 3D world rather than on the 2D screen — is not a MenuKit context and is not a rendering context in any meaningful sense here. MenuKit's scope is the 2D GUI layer.

The three contexts — inventory menus, HUDs, standalone screens — are exhaustive for MenuKit. A candidate context outside that set is either a use case within an existing context or out of scope.

## Summary

MenuKit targets three contexts. Each has its own container and its own context-specific machinery. All three share Panel as the composition root, PanelElement as the element interface, and the five disciplines from the thesis as guarantees (instantiated as five context-specific guarantees per context). Elements written against the shared abstraction work uniformly across the three; context-specific elements declare their scope explicitly.

Any future work introducing a new element, generalizing an abstraction across contexts, or deciding whether a use case belongs to MenuKit at all checks against this document first.
