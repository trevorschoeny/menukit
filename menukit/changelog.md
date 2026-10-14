## 7.1.0

### Added

- The cycle strip. A small hotbar drawn just right of the vanilla one shows a ring of stacks turning through the held slot, and slides one cell with a little spring each time the ring turns. Any mod that rotates stacks into the hand can register a source with `HudCycleStrip`, so its strip shows without needing any other mod installed. A ring can read as a row, as a column rising above the hotbar, or as both in a cross that shares the held cell. [The guide](https://github.com/trevorschoeny/menukit/blob/main/docs/cycle-strip.md) has the details.
- HUD panels beside the hotbar. On the HUD, `HudPanel.builder(...).region(OutsideRegion.X)` places a panel around the vanilla hotbar: `LEFT_ALIGN_BOTTOM` is a column of icons just left of it, rising from its bottom. Panels in one region stack 2 pixels apart in priority order, and a hidden one takes no room, so small icons from several mods line up beside the hotbar without overlapping or leaving gaps. [Recipe](https://github.com/trevorschoeny/menukit/blob/main/docs/recipes.md#put-an-icon-beside-the-hotbar).
- Tooltips of several lines. `tooltip(first, more...)` on every element builder and on `Panel` takes the lines one by one, each in its own style, and `MKTooltip.joinLines(lines)` makes one text of them for a tooltip read every frame.

### Fixed

- A line feed (`\n`) in a tooltip now starts a new line on every tooltip. Before, only a tooltip wide enough to wrap broke its lines, and a short one drew the line feed as a boxed "LF" on a single line. It works in plain text, in a styled part of the text and in a translation, and each line keeps its style.

Nothing was removed. Mods built for 7.0.0 work as before.
