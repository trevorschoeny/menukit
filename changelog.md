## 7.5.0

#### Added

- `SlotDecorations.under(id, decoration)` and `.over(id, decoration)` draw a mark under or over the item in every container slot, with an optional tooltip. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#slot-decorations).
- `ContainerTabs.groups(id, scope, groups, keys)` declares tab groups that come and go at runtime, one per group key, each with its own header and sub-tabs. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#container-tabs).
- Tab groups nest. A group whose key is one of another live group's keys stands inside that group, in place of the sub-tab it would have been, at any depth.
- `ContainerTabs.frameStaysWhen(scope, screens, when)` keeps a container screen's frame at vanilla's place while its tab rows come and go, moving it only when a row wouldn't fit on the window.
- `Flow.Builder.wrapAfter(n)` caps how many children a column or row holds before it wraps, and `keepLast()` keeps the last ones in a vertical Flow's first column. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#layout-helpers).
- `Contribution.Builder.wrapAfter(scope, n)` caps how many contributions a shared panel holds in a row, or in a column for a vertical panel, per game mode, and the rest wrap. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#shared-panels).
- `ItemAnimation.swapped(...)` and `ItemAnimation.AS_IS` let a slot item look draw another stack in the item's place, not only animate the item. [Concepts](https://github.com/trevorschoeny/menukit/blob/main/docs/concepts.md#slot-item-looks).
