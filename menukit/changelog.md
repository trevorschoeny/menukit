## 7.3.0

This release is additions, plus one change to when slots shown in a panel are drawn. Nothing was removed, and mods built for 7.2.0 work as before.

### Added

- Lists that change while the screen is open. `ListView` shows one row per item of a list your mod owns and follows it every frame: a new item gets its row at once, a removed one loses it, and every other row stays as it is, so a rename in progress keeps its text and the keyboard while the list refreshes. Rows are kept by a key you give, in your list's order. An optional selection, held by your mod, is set by clicking a row and moved with the arrow keys, and a row your code selects scrolls into view. [Recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#a-list-that-changes-while-the-screen-is-open).
- A button with a menu of actions. `MenuButton` opens a small menu whose rows run actions, for a row's Duplicate and Delete. An action that destroys something is drawn red. The menu is never cut off by a scroll area around it.
- A list that scrolls under a fixed header. `ScrollContainer.Builder.fillHeight()` takes the height it is given: the rest of a screen's main panel, of a vertical `Flow` under a title and buttons, or of a `Tabs` page. A `Tabs` page whose body fills hands the page to it instead of scrolling the whole body, so the header stays put and only the list moves, and the page looks the same as before.
- Text fields that can be cancelled and started from code. Escape on a focused `TextField` puts back the text from before the edit, lets go of the keyboard and runs `onCancel`, without closing the screen. `focus()` gives a field the keyboard, for a rename field that appears with its button.
- `Dropdown` and `DropdownMulti` can take their options from a supplier, for options that change while the screen is open.
- `ScreenMatcher.where(test)` puts a panel on the container screens a test accepts, asked as each screen opens, for a panel that belongs where a slot group's slots are. [Recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#show-a-panel-on-the-screens-that-hold-your-slots).
- `Attachment`, the one way a container attaches a child added after it was built, for mods that write their own containers.

### Changed

- A slot a panel shows draws right after that panel, on top of it. Before, a vanilla slot shown in a panel was drawn before the panel, so the panel covered its item. A panel above another now covers the slots shown in the one below it, and a slot in a dialog draws on the dialog in the same frame. Slots no panel shows draw as before. Mods that mark slots by hooking vanilla's `extractSlot` still see every slot. A mod that hooks the whole slot pass (`extractSlots`) sees the slots shown in panels drawn after it.
- A panel placed by an adapter whose elements are all hidden or empty is no longer drawn as an empty frame, and no longer blocks clicks. A panel with a pinned size keeps its room as before.

### Fixed

- An element removed from a screen lets go of the keyboard if it had it, and an open dropdown in it closes.
