# MenuKit (the UI library)

Pure client-side Fabric library. Produces `menukit-1.0.0.jar`. Mod ID `menukit`. `environment: "client"`.

## What lives here

HUD panels, widgets (Button, Checkbox, Dropdown, Radio, Slider, TextField, Toggle, ProgressBar, ScrollContainer, Tooltip, etc.), layouts (Row/Column/Grid), modal panels on vanilla menus, region anchoring (`MenuRegion`, `HudRegion`, `StandaloneRegion`, `SlotGroupRegion`), click-through prohibition, recipe-book awareness, standalone screens (`MenuKitScreen`).

**Observation idioms (per §0044, pending):** pure-client read-only observation of vanilla slots/menus also lives here — `HandlerRecognizerRegistry`, `VirtualSlotGroup`, `SlotGroupCategory`, `SlotGroupResolver`, `VanillaSlotGroupResolvers`, slot-group region machinery (`SlotGroupRegionRegistry`, `SlotGroupPanelAdapter`, `SlotGroupPanelRegistry`, `SlotGroupRegionMath`, `SlotGroupBounds`), `SlotGroupPanelRenderMixin`, and signature-pure shared contracts (`Storage` interface, `InteractionPolicy`, `QuickMoveParticipation`, `VirtualStorage`, `ReadOnlyStorage`, `SlotGroupLike`, `SlotIdentity`).

## What does NOT live here

**Ownership** of slots — slot creation, owned `ScreenHandler`s, slot state, owned storage. Those live in the sibling `menukit-containers` codebase: `MenuKitSlot`, `MenuKitScreenHandler`, `MenuKitHandledScreen`, `SlotGroup` (owned-collection), `SlotState*` machinery, owned `Storage` implementations (`EphemeralStorage`, `StorageContainerAdapter`, `StorageAttachment`), M7 attachment cohort, server-coupled mixins, network payloads. **If you reach for `MenuKitSlot`, `MenuKitScreenHandler`, `MenuKitHandledScreen`, `SlotGroup`, M1/M7 owned-storage types, or M1 state machinery, you are in the wrong codebase.**

## §0042 / §0043 / §0044 boundary (binding)

This codebase **never** depends on `menukit-containers`. The dependency direction is one-way and gradle-enforced (`menukit-containers` depends on `:menukit` via `api`; this codebase has no project dependencies).

Per §0043 (Complete-on-Side Feature Ownership, accepted): every feature in this codebase is complete on this side. No callback-back-edges where MKC "completes" an MK feature. If a feature needs MKC-side capability to function, it doesn't belong here — surface a parallel feature on MKC's side instead.

Per §0044 (Slot Observation Refinement, pending): observation idioms live in MK; ownership stays MKC. The seam is OBSERVE (read-only) vs CREATE / OWN / hold STATE.

## Architectural canon

Governed by the `@ Trevlar Mods/@ MenuKit/` sub-unit of the Trevlar Mods Silcrow agency. Local canon at `@ Trevlar Mods/@ MenuKit/1 | Canon/accepted/`; design docs (THESIS, CONTEXTS, NORTH_STAR, M1–M9, PALETTE) at `@ Trevlar Mods/@ MenuKit/2 | Working Files/Design Docs/`.
