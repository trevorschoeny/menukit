# Inventory Plus — Design Decisions

Design decisions specific to inventory-plus that live outside MenuKit's library docs. Ongoing design canon for this mod.

## Feature inventory

For the Phase 11 baseline feature list (F1–F15), see [Phases/11/inventory-plus/POST_REPORT.md](../../Phases/11/inventory-plus/POST_REPORT.md).

Migration to MenuKit's completed library occurs in Phase 15a–15c per [PHASES.md](../../PHASES.md).

## Architectural decisions

**MKFamily migration (Phase 14a).** Replaced `MenuKit.family("trevmods").getKeybindCategory()` with direct `KeyMapping.Category.register(Identifier.fromNamespaceAndPath("inventory-plus", "inventory-plus"))`. All seven IP keybinds (sort, move-matching, lock, peek, autofill, pocket-cycle left/right) now register under inventory-plus's own Controls-screen section instead of a shared "Trev's Mods" section.

## Known issues

- **Phase 15a carry-forward:** settings gear + related buttons migrate to MenuKit's M4 region system (renumbered from M5 in Phase 13).
- **Phase 15b carry-forward:** F8 equipment panel + F9 pockets panels visual layer (F9 needs UI-structure clarification before implementation).
- **Phase 15c carry-forward:** F15 peek panel UI via M3 option (a) dynamic pre-allocation (M3 renumbered from M4 in Phase 13).

## Ongoing questions

*Populated as design questions surface.*
