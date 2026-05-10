# MenuKit (the UI library)

Pure client-side Fabric library. Produces `menukit-1.0.0.jar`. Mod ID `menukit`. `environment: "client"`.

## What lives here

HUD panels, widgets (Button, Checkbox, Dropdown, Radio, Slider, TextField, Toggle, ProgressBar, ScrollContainer, Tooltip, etc.), layouts (Row/Column/Grid), modal panels on vanilla menus, region anchoring (`MenuRegion`, `HudRegion`, `StandaloneRegion`), click-through prohibition, recipe-book awareness, standalone screens (`MenuKitScreen`).

## What does NOT live here

Anything slot-aware — slots, `ScreenHandler`s, slot state, slot groups, slot identity, slot storage. Those live in the sibling `menukit-containers` codebase. **If you reach for `Slot`, `Container`, `MenuKitScreenHandler`, `SlotGroup`, `Storage`, or any M1/M7 type, you are in the wrong codebase.**

## §0042 boundary (binding)

This codebase **never** depends on `menukit-containers`. The dependency direction is one-way and gradle-enforced (`menukit-containers` depends on `:menukit` via `api`; this codebase has no project dependencies). A class touching slot concepts is a partition violation — move it to `menukit-containers` instead.

## Architectural canon

Governed by the `@ Trevlar Mods/@ MenuKit/` sub-unit of the Trevlar Mods Silcrow agency. Local canon at `@ Trevlar Mods/@ MenuKit/1 | Canon/accepted/`; design docs (THESIS, CONTEXTS, NORTH_STAR, M1–M9, PALETTE) at `@ Trevlar Mods/@ MenuKit/2 | Working Files/Design Docs/`.
