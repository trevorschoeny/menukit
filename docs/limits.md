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
| Created slot in an overlay panel | Not supported. Overlay panels draw after vanilla's slot pass, so a slot they host is placed one frame late and drawn under the panel. Put created slots in flow panels. |
| Server-fired reactions | Client-observed reactions fire. Server-authoritative firing resolves to a no-op. |
| Window scope | Every address resolves in the primary scope. Per-tab and per-sub-window scopes are not active. |
| Panel and element addressing | The window addresses slots. It does not yet address panels or elements. |
| A slot group built with a menu, before any menu | `MKCSlots.onto(menu, player)` registers a group when that menu is built, so `SlotGroups` lists it only after the first one. Declare the group at init with `SlotGroups.declare` to list it from the title screen. |
| Operations on a slot reached with no menu open | World pickup (`Inventory.getFreeSlot`) has no menu, so a vanilla slot's category is unknown there and MenuKit alone cannot mint its address. `WORLD_PICKUP` on such a slot resolves from the key's default and then the vetoes. With Containers present the slot has an address and a per-slot declaration reaches it. |
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
| `TextLabel`, dialog bodies | Single line. Multi-line text is a `Column` of labels. |
| `Row`, `Column` | No `FILL` cross-alignment. No grid helper. |
| `Dropdown` | Fixed item list. No type-to-filter. |
| Secondary click and tint | `Button` and `Toggle` only. Other shipped elements take left clicks alone. A custom `PanelElement` handles any button in its own `mouseClicked`. |
| `ScrollContainer` | Vertical only. No keyboard scrolling. The scrollbar stays visible. |
| `Slider` | Normalized 0 to 1 value. No steps, no range handle, no vertical orientation. |
| Auto-sizing elements with supplier content | The build measures width once. Reserve width for the longest expected content. |

## Runtime constraints

- `build()` freezes a panel's element list. Build a new panel to change it.
- A `ScreenPanelAdapter` with no target renders on every container screen. A `SlotGroupPanelAdapter` with no `.on(...)` fails at client boot with the panel id in the message.
- Panel ids are global across mods. Prefix them with the mod id.
- `MKCMenu` handler factories run on both sides and must produce identical storages.
- The dev runtime assigns a new offline player UUID per launch. Test player-scoped persistence across quit-to-title and re-enter within one launch.
