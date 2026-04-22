# Sandboxes — Design Decisions

Design decisions specific to sandboxes that live outside MenuKit's library docs.

## Feature inventory

For Phase 11 state, see [Phases/11/final-consumers/REPORT.md](../../Phases/11/final-consumers/REPORT.md).

## Architectural decisions

**MKFamily migration (Phase 14a).** Replaced `MenuKit.family("trevmods").getKeybindCategory()` with direct `KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sandboxes", "sandboxes"))`. Sandboxes' keybind now registers under its own Controls-screen section instead of a shared "Trev's Mods" section.

## Known issues

- **In-UI Settings button click failure.** `SandboxScreen.settingsButton` doesn't open its YACL config screen on click. Press-release propagation hypothesis — vanilla `Button.onPress` fires on mouse-press, synchronously calls `setScreen(YACL)`, and the subsequent mouse-release propagates to the newly-active YACL screen at the same top-right coordinate where some clickable YACL element may consume + close. Full record at [Phases/11/POST_PHASE_11.md](../../Phases/11/POST_PHASE_11.md). Deferred to Phase 15e sandboxes refactor.

## Ongoing questions

*Populated as design questions surface.*
