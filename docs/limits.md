# Limits

What MenuKit and MenuKit: Containers do not do, and the open gaps a consumer can hit. [concepts.md](concepts.md) defines the terms.

## Out of scope

These are not planned. Use vanilla or another library.

- Config screens. Use YACL or Cloth Config.
- Chat and the F3 overlay. Both render outside the `Screen` system `VanillaScreenPanelAdapter` targets.
- Nested panels. A panel holds elements only.
- Themes and skins. `PanelStyle` and `ControlStyle` are the full set.
- Animation beyond HUD notifications.
- A cross-mod event bus.
- Drag and drop between elements.
- In-world rendering.
- Persistence for element state. The consumer stores the value; the element reads it through a supplier.
- Input on the HUD. HUD panels render only. For a clickable control, open a standalone screen from a key binding.
- Testing UI. The library ships no test panels or commands.

## Open gaps

Behavior that is incomplete in the current release.

| Area | Current behavior |
|---|---|
| Shift-click into a created slot | Not routed. Direct click works. The consumer overrides `quickMoveStack` in its own mixin, or accepts the gap. |
| Unplaced panels on a standalone screen | The default stacks later panels below the main one with a 14 pixel gap, assuming every earlier stacked panel is shown. With one hidden, those after it sit 12 pixels lower than the old column did. Declare `region(BOTTOM_CENTER)` with your own `.offset` for exact spacing. |
| Offsets and stacking | `.offset(dx, dy)` moves one panel after placement; siblings stack on its un-nudged size. An offset that pushes a panel over its sibling overlaps it. |
| Created slot in an overlay panel | Not supported. Overlay panels draw after vanilla's slot pass, so a slot they host is placed one frame late and drawn under the panel. Put created slots in flow panels. |
| Server-fired reactions | Client-observed reactions fire. Server-authoritative firing resolves to a no-op. |
| Window scope | Every address resolves in the primary scope. Per-tab and per-sub-window scopes are not active. |
| Panel and element addressing | The window addresses slots. It does not yet address panels or elements. |
| A slot group built with a menu, before any menu | `MKCSlots.onto(menu, player)` registers a group when that menu is built, so `SlotGroups` lists it only after the first one. Declare the group at init with `SlotGroups.declare` to list it from the title screen. |
| Operations on a slot reached with no menu open | An inventory insert (`Inventory.getFreeSlot`) has no menu, so a vanilla slot's category is unknown there. `INVENTORY_INSERT` on such a slot resolves from the slot's own declaration or the key's default, then the vetoes; the group and category rungs need a menu. |
| Operations the server works out for itself, on a server without MenuKit | The client refuses to send a click whose own slot refuses it: plain click, shift-click out, drop, swap, a slot joining a drag. Where a shift-click lands, what double-click collect sweeps, and where a picked-up item goes are decided by the server. Those hold in singleplayer and on a LAN host, where MenuKit runs the server side, and not on a server that does not run it. The same goes for a Q action a mod sends to the connection itself instead of through `LocalPlayer.drop`. |
| Persisting a created slot identity | An `Address` has no codec, and the client-side addressing helper is internal. A consumer that must remember one created slot across sessions encodes the identity itself. Vanilla slot indices are unaffected. |
| Drop rule key | `dropsOnDeath(DropRule)` on a player storage attachment covers death. No window key covers drop rules. |
| Block-entity container resolver | Registering a custom resolver for a block entity is a no-op. |
| Advancements | Created slots use a separate container and do not fire vanilla's inventory-change trigger. The consumer fires it. |
| Item-attached storage | Uses vanilla's container component only. |
| Block-portable content | Metadata travels with a carried shulker. General content travel for other blocks does not. |

## Element gaps

| Element | Current behavior |
|---|---|
| `Row`, `Column` | Build time only. For a row that follows the panel's width at runtime, use `Flow` with `Flow.spacer()`. No grid helper. |
| `Dropdown` | Fixed item list. No type-to-filter. |
| Secondary click and tint | `Button` and `Toggle` only. Other shipped elements take left clicks alone. A custom `PanelElement` handles any button in its own `mouseClicked`. |
| `ScrollContainer` | Vertical only. No keyboard scrolling. The scrollbar stays visible. |
| `Slider` | A 0 to 1 fraction, whole numbers (`ofInts`) or an enum's constants (`ofEnum`). No range handle, no vertical orientation. |
| `Slider`, `TextField` on a panel injected onto a vanilla screen | The claim routes clicks to the panel's elements before vanilla sees them, and these two take their clicks as vanilla widgets, so a click does not reach them there. On a standalone screen (`MKScreen`, `MKCHandledScreen`) they work. |
| `Slider`, `TextField` in a hidden panel | Stay registered with the screen and can keep keyboard focus. Blur them (`screen.setFocused(null)`) before hiding their panel. |
| Keyboard focus on `Button`, `Toggle`, `Checkbox`, `Radio` | Follows the screen's widget order, which is panel order. An element that stops being drawn loses focusability a quarter second later, not the same frame. |
| Disabled `Tabs` | The strip and the body are inert and the body greys; the strip's labels keep their colours. |
| Item outline (`SlotRendering.drawItemOutline`, `ItemDisplay.outline`) | Items whose model reaches outside their 16 by 16 box, which vanilla draws through a separate path, draw without the outline. |
| Auto-sizing elements with supplier content | The build measures width once. Reserve width for the longest expected content. |

## Runtime constraints

- `build()` freezes a panel's element list, and every element is immutable after its own `build()`. Build a new panel to change the list.
- A `ScreenPanelAdapter` with no target renders on every container screen. A `SlotGroupPanelAdapter` or `VanillaScreenPanelAdapter` with no `.on(...)` fails at the first screen open with the panel id in the message.
- Adapters, HUD panels and notifications are declarations: constructing, targeting or unregistering one after the client starts throws. Gate a panel at runtime with `showWhen`.
- A panel with no placement is rejected by adapters and the HUD. Only `MKScreen` and `MKCScreenHandler` place an unplaced panel for you.
- Panel ids are global across mods. Prefix them with the mod id.
- `MKCMenu` handler factories run on both sides and must produce identical storages.
- A server running Containers cannot be joined by a client without it; the client is told what to install before the world loads. A Containers client on a server without it has no created slots there.
- A `SHARED` slot-state channel with no `canWrite` rule cannot be written from a client, only by server code.
- Slot-state writes from one player are limited to a burst of 64, then 32 a second. Past that the server refuses them until the allowance refills.
- A custom menu with no `validWhen` cannot be opened by a client's request, only by the server.
- The slot state a shulker box carries as an item is not sent to clients. A creative player who moves such an item sends the game's copy back without it, so its slot state is lost; survival is unaffected.
- Hoppers and dispensers ask a slot's gate, not the vetoes. A lock that should stop automation needs its own seam until MenuKit names an automation operation.
- The dev runtime assigns a new offline player UUID per launch. Test player-scoped persistence across quit-to-title and re-enter within one launch.
