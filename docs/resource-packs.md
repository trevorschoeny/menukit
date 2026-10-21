# Resource packs

Since 7.4.0, a Panel-look panel follows the active resource pack the way vanilla's own screens do. Its frame, fill, slots and text come from the pack's container art, and its controls from the pack's restyled vanilla widgets. With no pack, or a pack that touches none of this, everything draws exactly as before.

Mod developers have nothing to do for any of this. There is no switch and no API call, and a mod's panels, dialogs and container region sections follow the pack on their own. This page is for knowing what to expect, and for pack authors who want to theme MenuKit. [concepts.md](concepts.md) defines the terms, and [limits.md](limits.md#resource-packs) lists what doesn't follow.

The Default look already draws vanilla's own sprites (see [Looks](concepts.md#looks)), so it has always followed packs and nothing here changes it.

## What follows the pack

- **Panels.** A `RAISED` panel in the Panel look, on a container screen, on a mod's own Panel-look screen or as a dialog, is cut from the pack's chest art (`minecraft:textures/gui/container/generic_54.png`), since that's the container art players see most. The container region goes further: each section's part of the container list is cut from that section's own container texture (its source block's: the shulker box's for a box, the chest's for a chest or barrel, else the texture the section names), and the inventory panel from the inventory half of the screen's own texture, so a shulker box in a chest's list looks the way it does on its own screen ([Container region](concepts.md#container-region)). HUD panels keep MenuKit's art.
- **Slots.** A slot frame on one of those panels is the slot cut from the same art, so it matches the slots the pack drew into vanilla's screens. Where the art's slot can't be cut, it's vanilla's `container/slot` if a pack restyled that, else MenuKit's.
- **Controls.** Buttons, toggles, dropdowns and menu buttons with their lists, a color field's swatch and the item dialog's small controls draw vanilla's button sprites where a pack restyled them, and checkboxes draw vanilla's checkbox. Text on them takes vanilla's colors for them, white on a button.
- **Text.** Heading, body and muted text on a panel adapt to the pack's fill (below).

Sliders, text fields, the top strip of `Tabs` and the scrollbar were already vanilla's widgets and sprites in both looks, so they follow a pack as they always did. Elements with no vanilla counterpart keep MenuKit's art: radio buttons, progress bars, cards (`Flow.builder().boxed()`), the `INSET` panel style, and the `Tabs` sidebar on a container screen. The `DARK` style is vanilla's status effect background, so a pack that restyles that sprite restyles it too.

## How a look is chosen

Each element's look is decided per sprite, in this order:

1. A pack overrides MenuKit's own sprite for it. Then that's what draws, so a pack can theme MenuKit directly.
2. A pack restyled the element's vanilla equivalent (the chest's container art for a panel, `widget/button` for a button). Then that's what draws, so MenuKit matches the screens around it.
3. Otherwise MenuKit's own art, so an unmodded game looks unchanged.

"A pack" here means any resource pack, or another mod's resources, that supplies the file in place of its owner (MenuKit for `menukit:` sprites, vanilla for `minecraft:` ones). The choice is made again on every resource reload.

Every state of a control comes from the same source as its normal state, so a control never switches look between hovered, pressed and disabled. Where the chosen set has a sprite for a state, that sprite draws. Where it doesn't, the state is derived from the normal sprite:

- **Hovered**: the normal sprite with a white wash inside its 1 pixel outline.
- **Disabled**: the normal sprite darkened to 30% of its brightness.
- **Pressed**: the face under the cursor with MenuKit's pressed transform over it, an inverted bevel and a wash that darkens a light face and lightens a dark one. Vanilla has no pressed button sprite, so a control drawn from vanilla's set is always pressed this way. MenuKit's press on vanilla's own buttons is the same transform.

A toggle that is on draws pressed, and a disabled toggle that is on draws its disabled face pressed, so on and off stay apart at every state.

## Sprites a pack can override

These are MenuKit's own sprites, under `assets/menukit/textures/gui/sprites/`. The panel and control sprites are nine-sliced, so give an override its own `.png.mcmeta` with `gui.scaling`, as MenuKit's do.

| Sprite | What it draws | What it follows when a pack doesn't override it |
|---|---|---|
| `menukit:panel_raised` | A Panel-look `RAISED` panel: panels on container screens and Panel-look screens, dialogs, the container region's two panels, HUD panels | The pack's container art: the chest's for a plain panel, each section's own texture in the container list, the inventory half of the screen's texture for the inventory panel. HUD panels follow nothing but this sprite. |
| `menukit:control_raised` | A raised control at rest: `Button`, `Toggle`, `Dropdown`, `DropdownMulti` and `MenuButton` (trigger and list), `ColorField`'s swatch, the item dialog's controls. Also `InfoBox` | `minecraft:widget/button`. `InfoBox` follows only this sprite. |
| `menukit:control_raised_highlighted` | The same control hovered or focused | `minecraft:widget/button_highlighted` |
| `menukit:control_raised_pressed` | The same control held down, or a toggle that is on | Nothing. Vanilla's set has no pressed sprite, so its press is derived. |
| `menukit:control_raised_disabled` | The same control disabled | `minecraft:widget/button_disabled` |
| `menukit:icon/move_up`, `icon/move_down`, `icon/toolbar_move_up`, `icon/toolbar_move_down`, `icon/remove`, `icon/sort_ascending`, `icon/sort_descending` | The icons on a `ListView`'s buttons | Nothing |

Override the normal sprite of a set first. A state sprite overridden without it draws as given, beside MenuKit's own normal sprite.

These elements have no MenuKit sprite to override, and follow vanilla's sprites when a pack restyles them:

| Element | Vanilla sprites |
|---|---|
| `Checkbox` | `widget/checkbox`, `widget/checkbox_highlighted`, `widget/checkbox_selected`, `widget/checkbox_selected_highlighted`. Without them MenuKit draws its own box. |
| A slot frame | The slot cut from the panel's container art, else `container/slot` |

## Text that adapts to the fill

Heading and body text on a Panel-look panel keep vanilla's label gray wherever it stands out from the pack's fill by 4.5:1, so it stays put on MenuKit's own gray and on any light fill. Where a fill is too dark or too colored for it, the text takes a color made from the fill, taken toward white on a dark fill or black on a light or colored one until it stands out by 4.5:1, so it keeps the fill's hue. Muted text keeps the contrast it has on MenuKit's own fill in the same way. Disabled text is unchanged.

Some dark-mode packs repaint vanilla's label gray with a text shader. On such a pack, MenuKit leaves its gray text on a dark fill alone so the shader repaints it as it repaints vanilla's, once.

A custom element gets this through `AbstractPanelElement.textColor(ctx, role)`, the same as MenuKit's own.

## Container titles and names

Packs whose art covers the title strip blank vanilla's container titles, and a section titled with vanilla's translation key goes blank with them. With one container on the screen that matches vanilla. With two or more in the container list, every section whose title comes out blank shows a name instead, so the player can still tell the containers apart, and sections that would show the same name are numbered. Each name is the container's own, the name the player gave it or its block's ("Lime Shulker Box"), in the player's language. [Titles and names](concepts.md#container-region) says where each comes from.

## Per-section art through OptiGUI

Many packs give each dyed shulker box (or a chest named "Ores") its own art through OptiGUI rules. OptiGUI picks one texture for a whole screen, from the block that opened it. The container region looks up each section's own, so a lime box in a chest's list has the lime art and the chest keeps its own.

This only happens while OptiGUI 2.x is installed. Without it, or with another major version, nothing changes. MenuKit reads the pack's rules itself and evaluates them per section against that section's container (its `source`, see [Container region](concepts.md#container-region)), following OptiGUI 2.3's own loading and order:

- OptiFine `.properties` under `optifine/gui/container/`, with `container=`, `texture=`, `texture.<path>=`, `colors=`, `name=` (plain, `pattern:`, `ipattern:`, `regex:`, `iregex:`), `biomes=`, `heights=`, and for chests `large=`, `trapped=`, `ender=` and `christmas=`, and `variants=` for the dispenser and dropper. The containers are the chest, trapped chest, ender chest, shulker boxes, furnace, hopper, brewing stand, dispenser, dropper, anvils, crafting table and enchanting table.
- OptiGUI `.ini` under `optigui:gui/`, a section per container id, with `replacement=`, `load.priority=`, the load selectors `if` and `if.mods`, and these selectors: `name`, `name.wildcard`, `name.wildcard.ignore_case`, `name.regex`, `name.regex.ignore_case`, `biomes`, `heights`, `chest.large`, `date`, `interaction.texture`, `interaction.hand`, and the `player.` forms of the name, biome and height selectors.

A section opened from an item takes the player's biome and height, and one whose source has no block (an item that isn't a block) is matched by name and place only. A selector that can't be built (a bad biome id, a malformed range) is dropped from its rule, as OptiGUI drops it, and an unknown key is ignored. MenuKit's own section art is drawn outside OptiGUI's whole-screen swap, so a lime box's screen doesn't turn every section lime. The inventory panel takes what OptiGUI picked for the screen's own container, as it would on vanilla's screen.

## What falls back

A look is cut only from art that has a clean strip to cut. A container texture whose fill isn't one flat color (wood grain, a tree drawn over the inventory) or whose edges have no plain stretch to tile keeps MenuKit's look, and so does one that can't be read. A pack that draws the inventory in pieces, rows hung under the container's box and the hotbar boxed apart, has no inventory frame to cut, so the inventory panel takes the pack's plain panel, the chest's.

Frames are tiled from their strips, never stretched, and cut in the texture's own 256-unit coordinates, so a 32x pack or any other resolution needs nothing extra.
