# Concepts

Definitions of the terms MenuKit coins and the rules that bind consumer code. Every other page links here rather than redefining a term.

## Two artifacts

| Artifact | Environment | Contents |
|---|---|---|
| MenuKit (`menukit`) | Client only | Elements, panels, layout helpers, HUD panels, placement on vanilla screens, standalone screens |
| MenuKit: Containers (`menukit-containers`) | Client and server | Created slots, custom container menus, per-slot state, storage attachments |

Containers depends on MenuKit. MenuKit does not depend on Containers. A client-only mod that depends on MenuKit alone cannot import a Containers type. The build fails.

## Panel

A `Panel` is a bounded rectangle that holds an ordered list of elements. It renders, receives input, and shows or hides as one unit. It has an id, a `PanelStyle`, a padding, and a visibility.

Panels do not nest. A panel holds elements. It does not hold other panels.

Panel ids are global. Prefix every id with the mod id: `"mymod:controls"`.

## Element

A `PanelElement` is one item inside a panel. Its position is relative to the panel's content area. The constructor sets it once. MenuKit ships these elements:

| Kind | Types |
|---|---|
| Render only | `TextLabel`, `Icon`, `Divider`, `ItemDisplay`, `ProgressBar`, `InfoBox` |
| Interactive | `Button`, `Toggle`, `Checkbox`, `Radio` (with `RadioGroup`), `Slider`, `TextField`, `Dropdown`, `DropdownMulti` |
| Composite | `ScrollContainer`, `ConfirmDialog`, `AlertDialog` |
| Slot (Containers) | `SlotElement`, `SlotFlowElement` |

A consumer implements `PanelElement` for a custom element.

Constructor argument order is `(childX, childY, [width, height,] content, [callback])`. Elements that size from their content omit width and height.

## State

A stateful element does not own its state. It reads the value from a `Supplier` each frame and writes through a callback on interaction. Persistence is the consumer's. The exceptions are `Toggle`, `Checkbox`, and `Radio`, which hold a boolean value unless the `linked` factory constructs them.

## Structure does not change after build

`build()` freezes the panel's element list. No method adds an element to a built panel. Visibility, position, and supplier-driven content change at runtime. To change the element list, build a new panel.

A hidden element or panel is inert on every surface. It does not render, receive clicks, show a tooltip, or reserve layout.

## Layout helpers

`Row` and `Column` compute positions at build time and return a `List<PanelElement>`. They do not exist at runtime. An element enters a layout as an `ElementSpec`, produced by the element's static `spec(...)` factory. `.build()` returns positioned elements that go into a panel with `.add(...)`.

## Region

A region names where a panel sits relative to a frame. Three enum types exist, one per frame type:

| Type | Frame | Values |
|---|---|---|
| `MenuRegion` | A container screen's frame | `LEFT_ALIGN_TOP`, `LEFT_ALIGN_BOTTOM`, `RIGHT_ALIGN_TOP`, `RIGHT_ALIGN_BOTTOM`, `TOP_ALIGN_LEFT`, `TOP_ALIGN_RIGHT`, `BOTTOM_ALIGN_LEFT`, `BOTTOM_ALIGN_RIGHT`, `TOP_CENTER`, `BOTTOM_CENTER`, `CENTER` |
| `SlotGroupRegion` | A slot group's bounding box | Same shape as `MenuRegion` |
| `HudRegion` | The game window | `TOP_LEFT`, `TOP_CENTER`, `TOP_RIGHT`, `LEFT_CENTER`, `RIGHT_CENTER`, `BOTTOM_LEFT`, `BOTTOM_CENTER`, `BOTTOM_RIGHT`, `CENTER` |

Panels in the same region stack in priority order. `region.priority(int)` returns a `RegionAnchor` with an explicit priority. Lower values stack first.

A panel wraps its width to the space its region leaves and scrolls its height when taller than its room. `Panel.size(w, h)`, `pinnedWidth(w)`, and `pinnedHeight(h)` override this.

`PanelPosition.pixel(Supplier<ScreenOrigin>)` places a panel at an absolute origin re-evaluated each frame. A pixel-positioned panel does not stack and does not wrap.

## The four contexts

A context is the answer to one question: what is this panel anchored to?

| Context | Anchor | Entry type | Artifact | Input |
|---|---|---|---|---|
| Menu | A container screen's frame | `ScreenPanelAdapter` | MenuKit | Yes |
| Slot group | A named slot group's bounds | `SlotGroupPanelAdapter` | MenuKit | Yes |
| HUD | The game window during play | `MKHudPanel` | MenuKit | No |
| Standalone | A screen the consumer opens | `MKScreen` (subclass) | MenuKit | Yes |

HUD panels do not receive input. For a clickable HUD control, open a standalone screen from a key binding.

An element renders the same in every context. The context owns the machinery around it.

## Targeting

A `ScreenPanelAdapter` with no target renders on every container screen. `.on(Class...)` limits it to those screen classes and their subclasses. `.onAny()` states the default explicitly. `.onPlayerInventory()` limits it to the player inventory screen.

A `SlotGroupPanelAdapter` requires `.on(SlotGroupCategory...)`. It renders once per category that resolves in the open menu. Categories cover every vanilla menu. A mod with its own menu registers a `SlotGroupResolver` for it.

Both adapters register in their constructor. `unregister()` removes them.

## Slot group category

A `SlotGroupCategory` is a name for a group of slots, such as `PLAYER_INVENTORY`, `HOTBAR`, `CHEST_STORAGE`, or `FURNACE_INPUT`. It carries no rendering rule. MenuKit maps categories to slot indices per menu each frame.

## Created slot

A created slot is a real `Slot` that a mod adds to a menu through Containers. It syncs through vanilla's slot protocol. Its contents persist through a `StorageAttachment` on the slot's owner: a player, block entity, entity, or item stack.

`MKCContainerPanel` creates slots on the player's inventory menu and projects them onto every container screen. `MKCScreenHandler` creates slots on a custom menu.

## Address

An `Address` names one slot without holding a reference to it. The same address resolves after a menu reopens and on both client and server. `CreatedSlotAdapter.addressOf(panelId, groupId, index)`, `MKCContainerPanel.address(...)`, and `MKCScreenHandler.address(...)` produce addresses for created slots. `Window.slot(address).set(key, value)` attaches behavior by address.

## Slot state channel

A `SlotStateChannel<T>` stores one typed value per slot, separate from the slot's item. `MKSlotState.register(id, codec, streamCodec, defaultValue, visibility)` creates one at common init. `PRIVATE` stores one value per viewer. `SHARED` stores one value per slot for all viewers. Values persist as NBT on the slot's owner and are readable with `/data get`.

## Storage

A `Storage` is the item container behind a created slot group. `StorageAttachment.playerAttached(namespace, key, size)` builds one that persists on the player. `EphemeralStorage.of(size)` builds one that lasts for the menu session. Containers also provides block-scoped and item-scoped attachments.
