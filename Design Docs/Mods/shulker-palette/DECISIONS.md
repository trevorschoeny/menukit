# Shulker Palette — Design Decisions

Design decisions specific to shulker-palette that live outside MenuKit's library docs.

## Feature inventory

For Phase 11 state, see [Phases/11/shulker-palette/REPORT.md](../../Phases/11/shulker-palette/REPORT.md).

**SP-F1 (peek toggle)** blocked on inventory-plus F15 peek panel — see [Phases/11/inventory-plus/POST_REPORT.md](../../Phases/11/inventory-plus/POST_REPORT.md). Migration occurs in Phase 15d per [PHASES.md](../../PHASES.md).

## Architectural decisions

**MKFamily migration (Phase 14a).** Previously joined the `trevmods` family for shared identity (no keybinds, no surviving queries — pure no-op post-Phase-12.5 scope-down). With MKFamily removed from MenuKit in Phase 14a, shulker-palette's family block was deleted entirely. No replacement needed; SP contributes no section to vanilla's Controls screen.

## Known issues

*None flagged as of Phase 13 doc reorganization.*

## Ongoing questions

*Populated as design questions surface.*
