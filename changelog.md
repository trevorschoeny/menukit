## 7.4.0

This release is mostly additions: the container region, tabs on container screens, grids of slots that scroll, background panels, Panel-look panels that follow resource packs, a placement per game mode, panels several mods share, and panels players can move. Nothing was removed, and mods built for 7.3.0 work as before except in the two cases [Upgrading](https://github.com/trevorschoeny/menukit/blob/main/docs/upgrading-7.md#two-things-to-check) names. The forms with no game mode are deprecated. A resource pack that themed MenuKit's buttons through `menukit:panel_raised` themes them through `menukit:control_raised` now.

#### Added

- `ContainerRegion.section(id, scope)` stacks a chest's own grid and every box opened below it in one scrolling column above your inventory. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#container-region), [recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#stack-containers-in-one-scrolling-column-above-the-inventory).
- The container region lays itself out again when a section opens or closes, keeping the cursor item, the scroll, and the open sections. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#container-region).
- A section shows only while its content does, and is titled by its container's name when a resource pack blanks the usual title. [Recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#stack-containers-in-one-scrolling-column-above-the-inventory).
- `Panel.Builder.background()` puts a panel behind everything else on the screen, under every widget and slot. [Recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#a-panel-behind-everything-on-a-container-screen).
- Panel-look panels take their frame, fill, and slot frames from the active resource pack's chest art. [Resource packs](https://github.com/trevorschoeny/menukit/blob/main/docs/resource-packs.md).
- Panel-look controls (buttons, toggles, dropdowns, checkboxes) follow the pack's restyled vanilla widgets, or a pack can theme them directly. [Resource packs](https://github.com/trevorschoeny/menukit/blob/main/docs/resource-packs.md#sprites-a-pack-can-override).
- Text on a Panel-look panel picks a color that reads against any pack's fill. [Resource packs](https://github.com/trevorschoeny/menukit/blob/main/docs/resource-packs.md#text-that-adapts-to-the-fill).
- `ContainerRegion.Builder.source(...)` names the container a section shows, for its art and its title. [Resource packs](https://github.com/trevorschoeny/menukit/blob/main/docs/resource-packs.md#container-titles-and-names).
- With OptiGUI installed, its per-container art rules apply to each container region section on its own. [Resource packs](https://github.com/trevorschoeny/menukit/blob/main/docs/resource-packs.md#per-section-art-through-optigui).
- `ContainerRegion.Builder.texture(...)`, `GAP`, and `CONTENT_WIDTH` round out the container region's layout API. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#container-region).
- `ModeScope` and `Placement` let a panel sit somewhere else per game mode. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#mode-scope), [recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#put-a-panel-somewhere-else-in-creative).
- `OutsideRegion` now has a centered region on every side, not just top and bottom ([Upgrading](https://github.com/trevorschoeny/menukit/blob/main/docs/upgrading-7.md#two-things-to-check)).
- `SlotClip.clipOf(slot)` gives the rectangle a slot is shown within, so a library can tell a slot cut by a scroll edge from one fully in view.
- `PanelElement.renderCulled(ctx)` runs for a shown element that's only out of view, so a slot library can park its slot instead of losing it. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#element).
- `SharedPanels.join(id, contribution)` lets mods that work together put their elements in one panel under an agreed id. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#shared-panels), [recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#share-one-panel-with-another-mod).
- A "Move panels" key lets the player drag panels to their own position, saved per panel in `config/menukit/panel-positions.json`. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#moving-panels).
- `ContainerTabs.tab(id, scope)` puts tabs like creative's on a container screen's edge, following the resource pack. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#container-tabs).
- `ContainerTabs.keyed(...)`, headers, and pinned tabs round out container tabs for a per-box tab row.
- `ContainerRegion.sections(kind, scope, keys)` declares one section per key a mod has open.
- `ContainerRegion.fitted(scope, screens)` sizes the container list and inventory panel to the shown sections, for showing one container at a time. [Recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#show-one-container-at-a-time-under-tabs).
- A hidden section's slots stop taking clicks, and `onHiddenLanding(...)` catches a shift-click that lands there anyway.
- `SlotScroll.builder()` makes a grid of slots that scrolls inside its panel when it doesn't fit. [Recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#scroll-a-slot-grid-when-it-doesnt-fit).
- `ScreenFrames.scrollsWhenShort(scope, screens)` scrolls a vanilla screen's own grid inside its frame on a window too short for it.
- `ScrollContainer.Builder.fitRoom(step)` and `laneOutside()` round out scroll area sizing.
- `SlotItemLooks.declare(id, look)` changes what a slot's item draws as, with an `ItemAnimation` such as opening a box's lid. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#slot-item-looks).
- `ScreenFrames.stretch(screen, seam, by)` makes a mod's own container screen taller or shorter at a row of its layout.

#### Changed

- Vanilla's shulker box, chest, barrel, and ender chest screens are all 166 pixels tall now, like the inventory, so the frame stays put when one opens in place of another.
- A container screen opened in place of another animates to its new size and place instead of jumping.
- The container region is never placed on the player's own inventory, in survival or creative, whatever a section's targeting says.
- A container region section wraps its content to the section's width (`ContainerRegion.CONTENT_WIDTH`) instead of running on in one row.
- `SlotClip.panelShowing(slot)` is forgotten when another container screen opens, so the player's inventory doesn't carry one screen's panel to the next.
- A click on a container screen's widget is never eaten by a slot panel's gap or a scrolled-off slot's hidden edge.
- An out-of-view element is skipped at every depth, not only inside a `ScrollContainer` ([Upgrading](https://github.com/trevorschoeny/menukit/blob/main/docs/upgrading-7.md#two-things-to-check)).
- A slot two panels show in one frame belongs to the panel on top, in everything: draw order, hover, and clip. MenuKit logs the clash.
- Raised controls in the Panel look draw the new `menukit:control_raised` sprites instead of `menukit:panel_raised`, so a pack themes panels and controls separately.
- The pressed look on a held button is drawn relative to the button's own art, so it reads on a pack's buttons too.
- `SlotRendering.drawSlotBackground` draws a slot from the pack's own container art on a Panel-look panel.

#### Deprecated

These keep working, mean `ModeScope.BOTH`, and are removed at 8.0.0. [Upgrading](https://github.com/trevorschoeny/menukit/blob/main/docs/upgrading-7.md#to-740).

- `Panel.Builder.position(position)` is replaced by `in(ModeScope.BOTH, Placement.screen(position))`.
- `AnchorPanelAdapter.on(anchors)` is replaced by the anchor in the placement, `Placement.around(anchor, position)`.
- `HudPanel.builder(id)` is replaced by `HudPanel.builder(id, scope)`.
- `OwnHost.add(panel, position, padding)` is replaced by `OwnHost.add(panel, padding)`, which reads the panel's placements.
