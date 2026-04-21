# Agreeable Allays — Design Decisions

Design decisions specific to agreeable-allays that live outside MenuKit's library docs.

## Feature inventory

For Phase 11 state, see [Phases/11/final-consumers/REPORT.md](../../Phases/11/final-consumers/REPORT.md).

## Architectural decisions

Previously a pure MKFamily Layer A consumer (`family.getKeybindCategory()` + `modId(...)`). With MKFamily removal (Phase 13 carry-forward), agreeable-allays needs to declare its own `KeyMapping` category directly.

## Known issues

*None flagged as of Phase 13 doc reorganization.*

## Ongoing questions

*Populated as design questions surface.*
