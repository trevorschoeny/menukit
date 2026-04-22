# Agreeable Allays — Design Decisions

Design decisions specific to agreeable-allays that live outside MenuKit's library docs.

## Feature inventory

For Phase 11 state, see [Phases/11/final-consumers/REPORT.md](../../Phases/11/final-consumers/REPORT.md).

## Architectural decisions

**MKFamily migration (Phase 14a).** Previously a pure MKFamily Layer A consumer that retrieved `family.getKeybindCategory()` + called `modId(...)`. With MKFamily removed from MenuKit in Phase 14a, agreeable-allays' family block was deleted entirely — the retrieved `category` variable was never read (AA registers no keybinds; only the HUD action-hint panel survives). No replacement needed; AA contributes no section to vanilla's Controls screen.

## Known issues

*None flagged as of Phase 13 doc reorganization.*

## Ongoing questions

*Populated as design questions surface.*
