# Upgrading to MenuKit 7.0.0

This page moves a mod from MenuKit 6.x to MenuKit 7.0.0. MenuKit 7.0.0 holds no slot code. Everything about slots moved to SlotKit, a separate mod built on MenuKit, and what 6.x deprecated is gone.

Saved data needs no migration. MenuKit 7.0.0 saves nothing of its own that changed, and SlotKit reads every slot value 6.x saved.

## Find your path

Your mod's imports decide which steps apply.

| Your mod uses | You need | Follow |
|---|---|---|
| Panels, elements, HUD panels and screens, and nothing from `api.slot` or `api.window` other than `Declarations` | MenuKit 7 | This page |
| Also slot groups, categories, operations, vetoes, addresses, the window, or `SlotGroupPanelAdapter` | MenuKit 7 and SlotKit 1 | This page, then [SlotKit's upgrading page](https://github.com/trevorschoeny/slotkit/blob/main/docs/upgrading.md) |
| Also MenuKit: Containers (created slots, custom menus, storage attachments, per-slot state) | MenuKit 7 and SlotKit 1 | This page, then [SlotKit's upgrading page](https://github.com/trevorschoeny/slotkit/blob/main/docs/upgrading.md), including its step for declaring sides |

To find out which row you are in, run this from your mod's root:

```sh
grep -rnE 'com\.trevlar\.menukit\.(api\.slot|api\.window|containers)|SlotGroupPanelAdapter' src \
    | grep -v 'menukit\.api\.window\.Declarations'
```

No output means the first row. The second `grep` drops lines naming `Declarations`, which stays in MenuKit.

## Checklist

1. [Change the dependency lines.](#1-change-the-dependency-lines)
2. [Fix what does not compile.](#2-fix-what-does-not-compile)
3. If your mod uses slots, continue at [SlotKit's upgrading page](https://github.com/trevorschoeny/slotkit/blob/main/docs/upgrading.md).
4. Build, and start a client.

Most mods stop after step 1.

## 1. Change the dependency lines

`build.gradle`:

```diff
 dependencies {
-    implementation 'maven.modrinth:menukit:6.2.0+26.3'
+    implementation 'maven.modrinth:menukit:7.0.0+26.3'
 }
```

`fabric.mod.json`:

```diff
-"depends": { "menukit": ">=6.2.0 <7.0.0" }
+"depends": { "menukit": ">=7.0.0 <8.0.0" }
```

Keep both bounds, separated by a space. A comma makes the range unreadable and the mod does not load. [Versioning](versioning.md) has the full contract.

## 2. Fix what does not compile

Each row is one break, the reason for it, and the replacement.

| Was in 6.x | Why it is gone | Do this |
|---|---|---|
| `ControlStyle` and its helpers | It did nothing since 6.1.0. The screen's kind picks the look. | Delete the reference. For a control that must look different, give it a sprite of your own with `Button.builder().sprite(...)`. |
| `style(ControlStyle)` on `Button.Builder`, `Dropdown.Builder`, `DropdownMulti.Builder` | Same. | Delete the call. |
| `Tabs.SIDEBAR_SCROLLBAR_WIDTH` | Deprecated in 6.1.0. | Use `ScrollContainer.TRACK_WIDTH` in the Panel look, and `6` in the Default look. |
| `MKTooltip.MIN_WRAP_WIDTH` | Nothing read it. | Delete the reference. |
| `com.trevlar.menukit.api.window.Declarations` | The window left MenuKit. The declaration freeze stays. | Import `com.trevlar.menukit.api.Declarations`. |
| `Window.panel(id)`, `Window.element(...)`, `PanelHandle`, `ElementHandle` | A panel's own settings do the same job. | Use `visibleWhen` on the panel and `Panel.opaque(boolean)`. |
| `BehaviorKeys.VISIBILITY`, `BehaviorKeys.OPACITY`, `VisibilityRule`, `SlotHandle.visibility` | Only the panel host read them. Set on a slot, they did nothing. | Use the panel's `visibleWhen` and `opaque`. |
| Anything else in `api.slot` or `api.window`, and `api.panel.SlotGroupPanelAdapter` | Moved to SlotKit. | Add SlotKit and continue at [SlotKit's upgrading page](https://github.com/trevorschoeny/slotkit/blob/main/docs/upgrading.md). |

A style call:

```diff
 Button.builder()
         .label(Component.literal("Sort"))
-        .style(ControlStyle.VANILLA)
         .onClick(this::sort)
         .build();
```

The sidebar scrollbar width, on a container screen:

```diff
-int track = Tabs.SIDEBAR_SCROLLBAR_WIDTH;
+int track = ScrollContainer.TRACK_WIDTH;
```

A panel hidden through the window moves onto the panel itself:

```diff
-Window.panel("mymod:toolbar").visibility(config.toolbar);
-Window.panel("mymod:toolbar").opacity(TriBool.FALSE);
+Panel toolbar = Panel.builder("mymod:toolbar")
+        .elements(toolbarButtons)
+        .position(PanelPosition.region(OutsideRegion.TOP_ALIGN_RIGHT))
+        .visibleWhen(() -> config.toolbar)
+        .build()
+        .opaque(false);
```

`visibleWhen` reads its supplier every frame, so the panel follows `config.toolbar` with no further call. A mod that used the window only for this needs no SlotKit.

`Declarations`:

```diff
-import com.trevlar.menukit.api.window.Declarations;
+import com.trevlar.menukit.api.Declarations;
```

## New in 7.0.0

Nothing here is needed to upgrade.

- `Anchor` and `AnchorPanelAdapter` in `api.panel` place a panel around a rectangle your mod names and supplies on any screen, such as the pause menu's buttons. SlotKit's `SlotGroupPanelAdapter` is built on them. [Recipes](recipes.md#anchor-a-panel-to-a-rectangle-you-supply) shows the use.
- `api.host` is for a library that ships its own screens. `OwnHost` lays out a screen's own panels and joins them to MenuKit's claims and draw order. `SlotScreenHook` routes hover and clicks to slots drawn in panels.
- `Declarations.requireOpen` is public, so a library built on MenuKit freezes its own declarations with MenuKit's.

## To 7.4.0

Nothing was removed, and a mod built for 7.3.0 compiles and works as before with no code change, except in the two cases under [Two things to check](#two-things-to-check). 7.4.0 adds the game mode a declaration is for ([Mode scope](concepts.md#mode-scope)), and deprecates the forms that don't say it. They keep working, mean `ModeScope.BOTH`, and are removed at 8.0.0.

### The dependency

Raise the floor only when the mod calls something 7.4.0 added, a scoped form included. A mod that does must say so, or it loads on 7.3.0 and fails the first time it calls the new method ([Versioning](versioning.md#what-to-declare)).

```diff
-    implementation 'maven.modrinth:menukit:7.3.0+26.3'
+    implementation 'maven.modrinth:menukit:7.4.0+26.3'
```

```diff
-"depends": { "menukit": ">=7.3.0 <8.0.0" }
+"depends": { "menukit": ">=7.4.0 <8.0.0" }
```

### Deprecations

| Deprecated (means `BOTH`) | Use |
|---|---|
| `Panel.Builder.position(position)` | `in(ModeScope.BOTH, Placement.screen(position))` |
| `new AnchorPanelAdapter(panel).on(anchor)` with `position(position)` | `in(ModeScope.BOTH, Placement.around(anchor, position))` and `new AnchorPanelAdapter(panel)` |
| `HudPanel.builder(id)` | `HudPanel.builder(id, ModeScope.BOTH)` |
| `OwnHost.add(panel, position, padding)` | the panel's own placements, and `OwnHost.add(panel, padding)` |

A panel on an anchor:

```diff
 Panel row = Panel.builder("mymod:pause_row")
         .elements(rowButtons)
-        .position(PanelPosition.region(OutsideRegion.BOTTOM_CENTER))
+        .in(ModeScope.BOTH, Placement.around(PAUSE_BUTTONS, PanelPosition.region(OutsideRegion.BOTTOM_CENTER)))
         .build();
-new AnchorPanelAdapter(row).on(PAUSE_BUTTONS);
+new AnchorPanelAdapter(row);
```

Where a panel used to check `instanceof CreativeModeInventoryScreen`, or build a second panel for creative, give it one placement per mode instead: `in(ModeScope.SURVIVAL, ...)` and `in(ModeScope.CREATIVE, ...)` ([recipe](recipes.md#put-a-panel-somewhere-else-in-creative)). `in` and `position` don't mix on one panel, and each scope is declared once; either mistake throws at build. A screen keeps one `main()` panel in each mode ([Placement](concepts.md#placement)).

### Two things to check

- **A custom element that draws outside its own box.** `ChildDispatch.render` now skips a child whose box is wholly outside the current scissor, at any depth (inside a scroll area, a `Tabs` body, any clipping container), so a slot row scrolled away is not drawn over what lies past the edge. A custom element that draws past its own box (a glow, a badge over its edge) is now not drawn at all while its box is out of the clip, even where that overhang would have reached into view. Make its box cover what it draws. An element with an open popover is still drawn.
- **A `switch` over `OutsideRegion`.** `OutsideRegion` gained `LEFT_CENTER` and `RIGHT_CENTER`. A `switch` expression over `PanelPosition.region()`, or any `OutsideRegion`, with no `default` that compiled against 7.3.0 doesn't compile against 7.4.0: add cases for the two, or a `default`. Built against 7.3.0 and not rebuilt, the same switch throws `MatchException` if it meets one of them.

### What players see

- **Panels they can move.** Every panel a mod places can be moved by the player, once they bind MenuKit's Move panels key (unbound by default). A mod has nothing to do. Mark a panel `fixed()` where its place is part of what it means, such as one lined up against vanilla's art ([Moving panels](concepts.md#moving-panels)). Namespace panel ids (`mymod:sort`), since a stored position is kept by id across every mod installed.
- **Resource packs.** Panel-look panels and controls follow the active resource pack, as vanilla's screens do. With no pack, nothing looks different. A pack that themed MenuKit's buttons through `menukit:panel_raised` themes them through `menukit:control_raised` now ([Resource packs](resource-packs.md)).

Neither changes what a mod's code does: a moved panel still shows only where its declaration would place it, and a pack changes only how a panel draws.

## To 7.5.0

Nothing was removed and nothing that compiled against 7.4.0 changes behavior. 7.5.0 only adds:

- `SlotDecorations` (draw under or over a slot's item).
- `ContainerTabs.groups` (a keyed list of groups, for tabs whose number changes at runtime). A group whose key is one of another group's keys nests inside it.
- `ContainerTabs.frameStaysWhen` (keep a frame at vanilla's place while its tab rows come and go).
- `Flow.Builder.wrapAfter`/`keepLast` (cap how many children a column or row holds), and `Contribution.Builder.wrapAfter` (the same cap for a shared panel's contributions).
- `ItemAnimation.swapped` and `ItemAnimation.AS_IS` (a slot item look that draws another stack).

Nothing here is needed to upgrade. A mod that calls any of them needs 7.5.0 at run time: on 7.4.0 the call fails with `NoSuchMethodError` or `NoClassDefFoundError`.

Raise the floor when the mod calls one of these ([Versioning](versioning.md#what-to-declare)):

```diff
-    implementation 'maven.modrinth:menukit:7.4.0+26.3'
+    implementation 'maven.modrinth:menukit:7.5.0+26.3'
```

```diff
-"depends": { "menukit": ">=7.4.0 <8.0.0" }
+"depends": { "menukit": ">=7.5.0 <8.0.0" }
```

[Recipes](recipes.md) has one example each.

## Errors you may see

| Error | Cause | Fix |
|---|---|---|
| Fabric refuses to start and lists a `menukit` range below `7.0.0` | A mod in the folder was built for MenuKit 6. | Update that mod, or remove it. |
| A mod does not load, with a malformed version in its `menukit` range | The two bounds are joined with a comma. | Separate them with a space. |
| `cannot find symbol` on `ControlStyle`, `SIDEBAR_SCROLLBAR_WIDTH` or `MIN_WRAP_WIDTH` | Removed in 7.0.0. | See the table in step 2. |
| `package com.trevlar.menukit.api.slot does not exist`, or the same for `api.window` | The class moved to SlotKit, or it is `Declarations`. | Import `com.trevlar.menukit.api.Declarations`, or add SlotKit and follow its upgrading page. |
| A deprecation warning on `position`, `on`, `HudPanel.builder(id)` or `OwnHost.add(panel, position, padding)` (7.4.0) | The form says no game mode. It still works. | See [To 7.4.0](#to-740). |
| `in(...) and position(...) don't mix` (7.4.0) | A panel calls both. | Use `in` alone: `position(p)` is `in(ModeScope.BOTH, Placement.screen(p))`. |
| `already has a placement for` a scope (7.4.0) | `in` is called twice with one scope. | Declare each scope once. |
| `are both main() in SURVIVAL` (or `CREATIVE`) (7.4.0) | In that mode both panels show a `main()` placement: each its placement for the mode, else its `BOTH` one. | Give one of them a placement of its own for that mode that isn't a main. |
| `AnchorPanelAdapter ... has a Placement.screen(...) for` a scope (7.4.0) | An anchor adapter's panel has a screen placement and the adapter has no `on(...)`, so that mode had nothing to place against. | Use `Placement.around(anchor, position)` or `Placement.at(anchor, dx, dy)`. |
| `SharedPanels.join(...) after the shared panels were built` (7.4.0) | A join after client start. | Join from the client initializer. |
