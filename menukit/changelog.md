## 6.2.0

Tooltips dock at the bottom or top of the screen instead of choosing a side of the cursor.

### Changed

- `MKTooltip.avoidCoverWhen(BooleanSupplier)` now docks a tooltip. While it holds, every tooltip vanilla would place beside the cursor (vanilla's items, MenuKit elements, other mods) sits centred at the bottom of the screen, `MKTooltip.EDGE` (4 pixels) above the edge. When the cursor is within `MKTooltip.DOCK_MARGIN` (20 pixels, a bit more than a slot) of where that tooltip would sit, not only directly over it, it goes to the top centre instead. Nothing wraps or changes side any more, and what is on the screen no longer matters. `clampWhen`, `wrapWhen`, `scrollBy` and `hideWhen` work on a docked tooltip as before, with the title still first, and a tooltip taller than the screen behaves as it does under `clampWhen`. A tooltip vanilla places somewhere fixed, such as one anchored to a widget for the keyboard, stays there. While no registered condition holds, tooltips are placed exactly as vanilla places them. The method keeps its name and signature, so a mod that registers it needs no change.

### Deprecated

- `MKTooltip.MIN_WRAP_WIDTH`. Nothing reads it now that `avoidCoverWhen` no longer wraps. It goes in 7.0.0.
