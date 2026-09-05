# Limits

What MenuKit and MenuKit: Containers do not do, and the open gaps a consumer can hit. [concepts.md](concepts.md) defines the terms.

## Out of scope

These are not planned. Use vanilla or another library.

- Config screens. Use YACL or Cloth Config.
- Chat, the F3 overlay, world and server select, the title screen, and the pause menu.
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
| Server-fired reactions | Client-observed reactions fire. Server-authoritative firing resolves to a no-op. |
| Window scope | Every address resolves in the primary scope. Per-tab and per-sub-window scopes are not active. |
| Panel and element addressing | The window addresses slots. It does not yet address panels or elements. |
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
| `ScrollContainer` | Vertical only. No keyboard scrolling. The scrollbar stays visible. |
| `Slider` | Normalized 0 to 1 value. No steps, no range handle, no vertical orientation. |
| Auto-sizing elements with supplier content | The build measures width once. Reserve width for the longest expected content. |

## Runtime constraints

- `build()` freezes a panel's element list. Build a new panel to change it.
- A `ScreenPanelAdapter` with no target renders on every container screen. A `SlotGroupPanelAdapter` with no `.on(...)` fails at client boot with the panel id in the message.
- Panel ids are global across mods. Prefix them with the mod id.
- `MKCMenu` handler factories run on both sides and must produce identical storages.
- The dev runtime assigns a new offline player UUID per launch. Test player-scoped persistence across quit-to-title and re-enter within one launch.
