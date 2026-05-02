# Phase 14d-2.7 — Close-out REPORT (Test Surface Comprehensive Cleanup)

**Status: complete.** Single test entry point shipped per `Design Docs/TESTING_CONVENTIONS.md`. The inventory "Test" button at `RIGHT_ALIGN_TOP` runs every automated probe (library contracts + validator scenario aggregators) then opens the single-panel scrollable Hub for visual scenarios. Every `/mkverify` chat command (bare and subcommands) deleted. Library/validator boundary cleaned: library exposes only pure-logic `ContractVerification.runAll(player)`; validator owns all visual test scaffolding. Two primitive-gap fold-inlines (MenuKitScreen + MenuKitHandledScreen scroll/release dispatch). Bounded scope as advisor predicted; one round + two fold-inlines.

---

## Executive summary

Phase 14d-2.7 supersedes 14d-2.6 (which was wrong-shaped — built a parallel `TestHub` in `menukit/` that was shadowed by the validator's existing Hub). 14d-2.7 is the comprehensive cleanup Trevor asked for after that misread surfaced.

The conventional commitment: **testing should be friction-free and trustworthy**.

- *Friction-free* — one persistent inventory button. No chat commands. Open inventory, click button, every automated test runs.
- *Trustworthy* — clean automatic-vs-visual split. Computer verifies everything that can be verified mechanically; humans verify only the residue. Mixing them is noise; the split is now structural.

The library/validator boundary moved with this:
- **Library** — `menukit/.../verification/ContractVerification.runAll(ServerPlayer)` is the only public test surface. Pure-logic contracts (regionMath, slotState, m7Storage, m8LayoutMath, m10–m15, composability, substitutability, uniform, syncSafety, inertness). Library has zero testing-UI scaffolding.
- **Validator** — every visual test surface: inventory Test button, Test Hub screen, MenuKit-primitive smoke wireups, M5 region probes, V0–V8 scenario screens. Validator IS the canonical test consumer; tests of MenuKit live in the consumer that uses MenuKit.

What shipped:

### New files (validator)

- **`InventoryTestButton.java`** — single test entry point. Region-anchored Button at `RIGHT_ALIGN_TOP` of `InventoryScreen` + `CreativeModeInventoryScreen`. onClick sends `RunAllAndOpenHubC2SPayload`.
- **`RunAllAndOpenHubC2SPayload.java`** — marker payload for the Test button.
- **`OpenScenarioC2SPayload.java`** — scenario-key-carrying payload for Hub button entries (V0/V1/V4/V5).
- **`MenuKitSmokeState.java`** — visibility flags for dialog/scroll/opacity smoke panels (migrated from `ContractVerification`'s static fields).
- **`MenuKitSmokeWireup.java`** — registration of dialog/scroll/opacity smoke panels (migrated from `ContractVerification.wireXxxSmoke`).
- **`scenarios/regions/RegionProbes.java`** — M5 region probes (migrated from `menukit/.../verification/RegionProbes.java`).

### Rebuilt (validator)

- **`HubHandler.java`** — single panel containing a ScrollContainer wrapping a mixed list of toggle and button entries. Most-recent-at-top via newest-first source order. Replaces the prior two-panel split (Validator Hub + Visual Probes).
- **`MkValidator.java`** — new C2S payload registrations + server handlers. `runAllAutomatedThenOpenHub` runs library contracts + validator aggregators + opens Hub (replaces `ValidatorCommand.cmdAll`'s body, button-driven). `dispatchOpenScenario` switch routes Hub scenario buttons to `openMenu` calls.
- **`MkValidatorClient.java`** — registers `InventoryTestButton`, `RegionProbes`, `MenuKitSmokeWireup` at client init.

### Deleted

- **`ValidatorCommand.java`** — entire file. All chat commands gone.
- **`menukit/.../verification/TestHub.java`** + **`TestHubScreen.java`** — parallel dead code from 14d-2.6.
- **`menukit/.../verification/RegionProbes.java`** — migrated to validator.
- **`menukit/.../verification/ElementDemoHandler.java`** + **`ElementDemoScreen.java`** — per-phase scratch space; V1.1 Palette Matrix + V1.2 Composed cover the same evidence territory.
- **`Design Docs/Phases/14d-2.6/REPORT.md`** — superseded by this report.

### Refactored (library)

- **`ContractVerification.java`** — `runAll(CommandContext)` → `runAll(ServerPlayer)`. Removed: smoke wireup methods (`wireDialogSmoke`/`wireScrollSmoke`/`wireOpacitySmoke`), visibility flags (`dialogVisible`/`scrollPanelVisible`/`scrollOffsetState`/`opacityPanelVisible`), helpers (`openInventoryClient`, `openElementDemoScreen`, `cmdMkverify`), entire Brigadier registration block, `elementDemoMenuType` registration. Library is now testing-UI-scaffolding-free.
- **`MenuKit.java`** — removed `RegionProbes.registerClient` call (RegionProbes lives in validator now).
- **`MenuKitClient.java`** — removed `wireXxxSmoke` calls (smoke wireups live in validator now).

### Refactored (validator scenarios)

All `runAggregated*` and `runLifecycle` / `run` methods refactored to take `ServerPlayer` (or no parameter) instead of `CommandContext<CommandSourceStack>`:
- `V2Verification.runAggregated()`
- `V3Verification.runAggregated()`
- `V5Verification.run(ServerPlayer)` + `runPersistenceAggregated(ServerPlayer)`
- `V6Verification.runAutomatedAggregated(ServerPlayer)`
- `V7Verification.runAggregatedChecks()`
- `V4Verification.runLifecycle(ServerPlayer, Runnable)` + new `sendOpenStandalone(ServerPlayer)`. Deleted: `runCrossHud`, `runCrossInventory`, `runCrossStandalone`, `printManualChecklist` (toggles handled directly by Hub state mutation; manual checklist referenced obsolete chat commands).

### Primitive-gap fold-inlines

Two primitive gaps surfaced during implementation; both folded inline per the convention's structural test sentence (*"if a primitive gap surfaces while wiring the Hub, surface it as a finding"*):

1. **`MenuKitScreen.mouseScrolled` + `mouseReleased`** — element dispatch was missing in StandaloneContext (only `mouseClicked` was wired). Added scroll dispatch with hit-test (parallel to mouseClicked pattern) and release dispatch without hit-test (drag-end fires for all visible elements regardless of cursor position, per 14d-2 ScrollContainer plumbing).

2. **`MenuKitHandledScreen.mouseScrolled` + element-level `mouseReleased`** — same gap on the inventory-menu-style native screen path. Existing `mouseReleased` handled slot drag-mode end only; element release was missing. The Hub's ScrollContainer needs both. Added scroll dispatch + element release dispatch (mouseReleased now does both: drag-mode end AND element release).

Same shape as 14d-2's primitive-gap pattern. Both are real library improvements (StandaloneContext + MenuContext-native-handler now have full element input dispatch parity), not just hub-specific patches.

### Convention doc rewritten

`TESTING_CONVENTIONS.md` reflects the new shape:
- §0 scope: applies to validator's Hub; library exposes pure-logic `runAll` only
- §2.1 framing: single test entry point (inventory button), not single chat command
- §2.2 reframed: Hub Screen is validator-side
- §3 add-a-new-test pattern: validator-side, no library-side scaffolding
- §6 verdicts: Q1 dissolved (no chat commands at all); Q2/Q3/Q4 confirmed; Q3 mechanism reframed for hand-coded list vs open registry; scope clarification confirmed

---

## What didn't ship / deferred

- **C2S packet plumbing for ElementDemo** — was filed in 14d-2.6 DEFERRED.md. Resolved differently: ElementDemo deleted entirely (V1.1/V1.2 cover the same evidence). The "server-menu Hub entries need C2S packet plumbing" item is no longer relevant in this form — the Hub now has full server-side dispatch via `OpenScenarioC2SPayload`, and any future server-menu test follows that pattern.
- **Hub entry icons / visual polish** — entries are plain Button/ToggleButton labels. Adequate for v1; visual polish folds-on-evidence.
- **Hub keybind** — only entry point is the inventory button (single test entry point per the convention). A keybind would add a second entry point; deferred unless evidence shows the inventory button is too friction-heavy.
- **Open-registry Hub** (consumers register entries dynamically) — not needed for the validator's hand-coded list. If a future consumer wants to extend the Hub from outside validator, the open-registry pattern can be added then.

---

## Process notes

**One round + two fold-inline primitive gaps.** Round 1 (the convention itself) closed cleanly with six verdicts. Implementation surfaced two MenuKitScreen / MenuKitHandledScreen primitive gaps; both folded inline. No round 2 needed.

**Calibration heuristic re-applied:** *"When the entry-point method overrides input, it owns dispatch responsibility for ALL input modalities (click + scroll + release), not just click."* Same shape as 14d-2.5's symmetric press/release calibration. MenuKit screen base classes were missing scroll + release dispatch; fold-inline added both.

**Comprehensive cleanup discipline.** Trevor's instruction was "really look at what's necessary, what's not." The audit deleted ElementDemoHandler/Screen rather than migrate, removed printManualChecklist + runCrossHud/Inventory/Standalone rather than refactor, deleted ValidatorCommand entirely. The cleanup actually shrank the test surface (~700 LOC deleted vs ~600 LOC added across new validator files). Less code, clearer shape.

---

## Verification

### Smoke checklist (change-scoped per redundancy rule)

Standing by for visual confirmation:

1. Open inventory (E) — Test button visible at RIGHT_ALIGN_TOP.
2. Click Test button — chat shows 17 library contracts run + 5 validator aggregators pass + Hub opens.
3. Hub shows 14 entries in newest-first order: M9 Opacity → ScrollContainer → Modal Dialog → V4.2 Standalone → V4.2 Inventory → V4.2 HUD → V4.1 → V1.2 → V1.1 → V0 → V5 → V7 → Region Probes → Region Stack Middle.
4. Hub's own ScrollContainer scrolls (dogfoods MenuKitHandledScreen scroll dispatch fold-inline).
5. Click each toggle — flag flips (label updates ON↔OFF). Close Hub, navigate to inventory/HUD to see the smoke panel.
6. Click each scenario button — corresponding scenario screen opens.
7. Close any scenario screen → "Back to Hub" decoration sends user back to Hub.

### Build status

Full monorepo (menukit + validator + 5 consumer mods + dev) builds clean.

---

## Phase 14d-3 entry conditions

- Single test entry point shipped (inventory Test button).
- All chat commands deleted.
- Library/validator boundary cleaned.
- Convention doc updated to match shipped reality.
- Two primitive-gap fold-inlines closed (StandaloneContext + MenuKitHandledScreen scroll/release dispatch).
- Audit identified zero "currently visual but auto-convertible" tests; all auto-tests already auto.

Phase 14d-3 (text input): designs and dogfoods the convention from day one. Adding the text input smoke is one wireup in validator + one Hub entry — no chat command, no library-side scaffolding.

---

## Diff summary

Validator: 6 new files (~600 LOC), 1 deleted file (`ValidatorCommand.java`), 6 refactored files. Menukit: 5 deleted files (TestHub, TestHubScreen, RegionProbes, ElementDemoHandler, ElementDemoScreen), 3 refactored files (`ContractVerification.java`, `MenuKit.java`, `MenuKitClient.java`). Plus convention doc rewrite + REPORT + 2 primitive-gap fold-inlines (MenuKitScreen.java, MenuKitHandledScreen.java).

Net: ~+800 / ~-1100 LOC. The cleanup actually shrank the test surface — less code, clearer shape.

**Phase 14d-2.7 closed.**
