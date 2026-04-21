# NORTH STAR

The target product MenuKit is building toward — what "golden state" looks like when the library is complete. This document states the end-state vision: the consumer experience at golden, the architectural state at golden, and the principled non-goals that bound the library.

This is the **what**. THESIS.md is the **how** (principles governing design decisions). CONTEXTS.md is the **where** (rendering-context specifications). PALETTE.md is the **inventory** (current element palette). NORTH_STAR names the product at its target state.

Any proposed work checks against this doc first: does it move the library toward golden, or does it miss?

---

## The one-sentence vision

A Minecraft mod author imports MenuKit, writes a few dozen lines, and gets a vanilla-indistinguishable UI — in any of the four rendering contexts — without thinking about sync protocols, HUD callbacks, screen lifecycles, or silent-inert mixin coverage.

**"It just works."**

---

## Consumer experience at golden state

**One vocabulary, four contexts.** Panel + Elements is the whole mental model. The consumer asks "what am I anchoring to?" — menu frame → MenuContext; slot group → SlotGroupContext; screen edge → HudContext; standalone UI → StandaloneContext — picks the builder, and the shared element palette composes in. A Button written once works in all four.

**"Decorate vanilla" is first-class, not second-tier.** Half of real consumer UIs are decorations — a sort button on every chest, a status panel in the player inventory, a HUD overlay tracking mod state. The library's render pipeline covers every vanilla container screen automatically. Consumer writes `ScreenPanelAdapter(panel, region).on(Class...)` and dispatch flows. Zero hand-rolled silent-inert mixin scaffolding for standard cases.

**Palette complete for realistic needs.** Current 11 elements (Button, TextLabel, Icon, Toggle, Checkbox, Radio, Tooltip, ItemDisplay, ProgressBar, Divider, SlotGroup) plus the V0-surfaced gaps (text input, slider, dropdown, scroll container). Each with a lens-factory where state applies — state stays in consumer stores; the library provides visual handles only.

**Vanilla-indistinguishable output.** Players can't tell MenuKit UI from vanilla UI without reading the mod list.

**Cross-mod composability is free.** Two unrelated mods using MenuKit in the same context coexist without coordination. The library never owns shared code paths.

---

## Architectural state at golden

Four contexts — MenuContext, SlotGroupContext, HudContext, StandaloneContext — exhaustively cover the 43 vanilla menu categories. Library mixins cover every vanilla container screen's render pipeline; consumers never hand-roll that dispatch.

The five disciplines — composability, substitutability, inertness, declared structure, uniform abstraction — hold uniformly across all four contexts, instantiated as context-specific guarantees per-context (documented in CONTEXTS.md).

---

## Scope ceilings

Principled non-goals, not gaps:

- **No config-UI framework.** Cloth Config's space.
- **No theme system.** Consumers style what they need.
- **No animation framework beyond notifications.** Notifications ship slide/fade because animation is load-bearing to their purpose; nothing beyond.
- **No vanilla-code-path ownership.** The library does not mixin-inject its own dispatch into vanilla. Consumers write their own mixins to decorate vanilla contexts.
- **No ecosystem-wide event bus.** Per-screen event listeners only; cross-mod events are consumer work.
- **No general drag-and-drop** beyond the inventory-menu drag protocol.
- **No networking infrastructure** beyond the vanilla inventory-menu sync protocol.

The library stays a library.

---

## What makes this tractable

The operating disciplines Phase 12.5 has proven work — surface gaps rather than workaround, validate the product not just primitives, contexts track consumer mental models, library-not-platform, evidence drives scope. Seven primitive gaps surfaced and mostly closed in one phase. Golden state is reached by continued application of these disciplines on the remaining work, not by architectural leaps.
