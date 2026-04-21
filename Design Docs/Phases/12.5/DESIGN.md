# Phase 12.5 — Validation Interstitial

**Status: Resolved — ready for implementation, pending creative-adapt repro from Trevor for V2 sub-check scoping (see §13).**

**Post-M8 reframe (in-flight during Phase 12.5).** The four-context model (`MenuContext` / `SlotGroupContext` / `HudContext` / `StandaloneContext`) landed mid-phase — see `M8_FOUR_CONTEXT_MODEL.md`. This doc's references to "inventory-context" refer to what's now `MenuContext`; references to the three-context trichotomy (inventory / HUD / standalone) predate SlotGroupContext. V2's remaining scope updates accordingly: probe coverage expands to include SlotGroupContext probes (at least PLAYER_INVENTORY + one storage category) alongside the MenuContext probes, and the chrome-adaptation test resumes against the M7/MenuChrome path.

**M3 scope-down is a prerequisite library-side work item (not a Phase 12.5 scenario). Can interleave with V1–V3 work; must sequence before V8.**

A validation phase that sits between Phase 12 (primitives shipped) and Phase 13 (consumer features). Its output is not new library code — it's a **synthetic consumer that pressure-tests every primitive through realistic usage patterns, decoupled from real-consumer complexity**. Bugs that surface at the synthetic level are known to be library issues, not consumer-integration confounds.

---

## 1. Purpose

Phase 11 validated library-fitness by rebuilding four real consumer mods against the library. That validation worked, but every surfaced issue had to be triaged ("library bug? IP bug? integration bug?"). Phase 12 shipped three new primitives (M1, M4, M5), plus the `MenuKitSlot.getItem` inertness-regression catch and the M5 §11 vanilla-element-overlap non-goal — surface area that Phase 13's consumer migrations will lean on simultaneously.

Phase 12.5 inverts Phase 11's pressure test: **the consumer is known-trivial, so anything surfacing is a library issue**. Comprehensive scenario-based integration testing through a synthetic consumer that uses every primitive via the same API surface a real consumer would reach for. Bugs found here are fixed here; Phase 13 starts against a validated library rather than layering features on top of unknowns.

This is not unit testing (the canonical contracts in `/mkverify all` already cover contract-level guarantees). This is not consumer-mod rebuild (Phase 11 already did that). This is a deliberate third thing: **dev-only synthetic integration testing with the library acting as a black box from the test harness's perspective**.

### 1.1 Phase 12.5 as a milestone

Phase 12.5 is the first phase in MenuKit's history to explicitly validate the library's **product identity**, not just its implementation. Every prior validation pass — `/mkverify all`'s contracts, Phase 11's consumer rebuilds, per-primitive design-doc reviews — has been implementation-focused. V0 asks the next-level question: *does the library deliver on being a component library when a consumer uses it as one?*

The concurrent codification of THESIS principle 7 ("Validate the product, not just the primitives") alongside this doc is the strategic companion to V0's tactical addition. The principle governs every future validation phase — Phase N.5 under 1.22, Phase 20.5 after a third migration, whatever — ensuring validation never regresses to primitive-coverage alone. Phase 12.5 is the first pass under this principle and the template for all subsequent ones.

Worth naming: this is a bigger claim than "a bridge between Phase 12 primitives and Phase 13 features." It's the point at which MenuKit starts measuring itself against its own product positioning, not just its contract guarantees.

---

## 2. Why it's its own phase

- **Distinct deliverable.** The synthetic-consumer module is a new artifact, not a tweak to existing work. Gradle submodule, entrypoints, mixins, scenarios.
- **Distinct discipline.** Bug-fixing during Phase 12.5 stays in Phase 12.5 (§7 below). Features found missing get filed as Phase 13 work, not absorbed mid-phase.
- **Distinct close-out.** Per-scenario REPORT, phase SESSION_BRIEF for handoff, design-doc corrections for any primitive drift surfaced.
- **Distinct blast radius for failure.** A primitive bug surfacing inside Phase 13 contaminates that phase's feature work. A primitive bug surfacing in Phase 12.5 is insulated — fix it, move on, Phase 13 starts clean.

The phase is bounded: when all seven scenarios report resolved (automated PASS or manual checklist confirmed), Phase 12.5 closes. Phase 13 opens with primitives validated.

---

## 3. Phase naming

**Phase 12.5**, not "Phase V."

Rationale: every other phase in the library's history is numeric (1 through 13). Introducing a letter break is a naming debt future readers pay for. "Phase 12.5" signals interstitial placement between 12 (library primitives) and 13 (consumer features), keeps the numeric ordering, and clearly identifies itself as library-side validation rather than feature scope.

Artifacts live under `menukit/Design Docs/Phase 12.5/`.

---

## 4. Validator module

### 4.1 Architecture — separate Gradle submodule

A new submodule named **`validator/`** (or `mkvalidator/` — final naming decision §13 open question). Separate from the library, separate from real consumer mods, never published.

Structure:

```
validator/
├── build.gradle                             ← depends on :menukit
├── src/main/resources/
│   ├── fabric.mod.json                      ← registers main + client entrypoints
│   └── mkvalidator.mixins.json              ← scenario-specific mixins
└── src/main/java/com/trevorschoeny/mkvalidator/
    ├── MkValidator.java                     ← ModInitializer
    ├── MkValidatorClient.java               ← ClientModInitializer
    ├── scenarios/
    │   ├── V1_ElementPalette.java
    │   ├── V2_RegionsPalette.java
    │   ├── V3_Inertness.java
    │   ├── V4_NativeScreen.java
    │   ├── V5_SlotInteractions.java
    │   ├── V6_M1Persistence.java
    │   ├── V7_Hud.java
    │   └── V8_MKFamily.java
    ├── mixin/                               ← supplementary mixins for scenarios
    │   └── (scenario-specific)
    └── cmd/
        └── MkValidatorCommand.java          ← /mkverify subcommand extensions
```

Loaded only in the dev client (via `dev/build.gradle`'s classpath). Not included in any published consumer jar.

### 4.2 Why a separate module

- **Authentic integration surface.** A consumer mod depends on MenuKit's published API. The validator does the same thing, from a distinct Gradle module. No privileged access, no internal imports. If the validator's fake mod can't express a usage pattern through the public API, neither can a real consumer.
- **Namespace separation.** `mkverify` is the chat-command namespace (contract probes + scenario launchers). `validator/` is the Gradle-module namespace (the fake consumer mod itself). Keeping these distinct avoids conflation — the chat command can invoke scenarios the validator registers, but the module is not named after the command.
- **Clean decoupling for removal.** When Phase 12.5 closes, the validator module stays in the tree as a regression-test asset; `/mkverify v<n>` scenarios run at every future phase boundary. It's never shipped to users.

### 4.3 What the validator module is NOT

- Not a place to stash dev utilities the library wasn't willing to ship. If the library needs a thing, it ships in the library.
- Not a second `/mkverify all`. The seven canonical contracts in `/mkverify all` stay as library-side assertions; Phase 12.5 scenarios are consumer-level integration checks that exercise the contracts through use.
- Not a benchmark or stress suite. Scenarios validate correctness, not performance.

---

## 5. Eight scenarios, ordered by blast radius and product discipline

Ordering reflects two concerns: **product validation first** (V0), **primitive coverage by blast radius** (V1–V8). Every consumer composes elements, so an element-palette bug (V1) contaminates every subsequent primitive scenario; MKFamily (V8) is orthogonal to element/region/slot/state primitives.

V0 runs first because it validates the library's product positioning (THESIS principle 7). If V0 reveals compositional seams, the primitive scenarios (V1–V8) run against a library with acknowledged integration issues — useful context. V0 is structured around a consumer workflow; V1–V8 are structured around primitives.

### V0 — Consumer mini-application [mixed]

**Purpose.** Validate the library as a component library, not as a collection of primitives (THESIS §7). Build a fake "IP-settings-lite" feature inside the validator module — a MenuKit-native screen with 5–8 interactive elements composing into one coherent workflow. Exercise state flow between elements, cross-element dependencies, realistic keybind integration, consumer-owned persistence via the lens pattern (THESIS §8). If a consumer couldn't reasonably build this in a reasonable amount of code, that's a library-level finding, not a per-primitive bug.

**Scenario shape.** A `MenuKitScreenHandler`-backed settings screen. Layout:

- Header `TextLabel` with the feature title.
- A `Toggle` for "enable feature X."
- A second `Toggle` for "enable feature Y" — gated by the first toggle's state (supplier-driven `isVisible` or `isEnabled`). Tests element-to-element state flow.
- A `Checkbox` for a related preference.
- A `ProgressBar` whose value is computed from the toggles' states (e.g., "how many features enabled / total"). Supplier-driven, updates per-frame.
- A `Button` labeled "Save" that commits the dirty session buffer to a consumer-owned disk-persisted config. Persists across close/reopen/disconnect.
- A `Button` labeled "Cancel" that discards unsaved state.
- Each interactive element has a `Tooltip` explaining what it does.

**Persistence model — consumer-owned (THESIS §8).** Feature flags ("enable X," "enable Y," "preference") are not slot-shaped state and therefore not M1 candidates. V0 owns its own disk-persisted config ({@code V0Config}, JSON file in the Fabric config dir) — the canonical real-consumer pattern. Toggle/Checkbox elements wire to a session-buffer dirty-edit layer via {@code Toggle.linked(supplier, callback)}; Save commits the dirty buffer to {@code V0Config} and writes to disk; Cancel closes the screen without committing (next open rehydrates the session buffer from the authoritative config).

Slot-shaped state (lock flags, slot-anchored annotations) belongs to M1 and is exercised in V5 / V6, not V0. V0 deliberately demonstrates the lens-over-consumer-store pattern for non-slot state — the canonical usage the library wants consumers to reach for.

**Automated checks.**
- On open, session buffer loads from the consumer config (reads config defaults if never saved).
- Toggle A's supplier-driven gating of Toggle B fires correctly — toggling A updates B's isVisible / isEnabled without manual refresh.
- Progress bar value updates when toggles change (supplier reads per-frame).
- Save button click commits the session buffer to the config and writes to disk. Reopening reads the persisted state.
- Cancel button click closes the screen without committing. Reopening reads the last-saved state, not the discarded edits.
- Config file survives Minecraft restart — reopening after a fresh launch reads the previously-saved state.

**Manual checklist.**
- Visual layout matches expected positions.
- Tooltips render on hover, dismiss off-hover.
- Overall feels "easy to build" — rough line-count measurement captured in V0's output: "V0 consumer-side code: N lines."
- **Palette gap documentation.** V0's mini-application attempts to use the standard UI shapes real consumers expect. If any of the following aren't available in the MenuKit palette, V0's output lists them as palette gaps for close-out REPORT:
  - Text input / edit field (threshold value, search box)
  - Slider / numeric input (continuous value)
  - Dropdown / select (enum choice from a list)
  - Scroll container (overflow handling for long settings lists)
- **Lens-factory audit (THESIS §8).** V0 exercises which stateful elements ship a `linked(supplier, callback)` factory and which force consumers to work around element-owned state. Every gap surfaced — Checkbox without `linked`, Radio without `linked`, Toggle.linked without a `disabledWhen` overload — goes to the close-out REPORT. These are library palette gaps, not V0 bugs.
- **Post-build size pinning.** `MenuKitScreenHandler.PanelBuilder` has no `.size(w, h)` method; V0 must retrieve the Panel post-build and call `Panel.size(...)` directly to get symmetric margins. Trivially additive palette gap filed for close-out.

**Why first.** V0 is the scenario that validates the product. Everything downstream validates the primitives that support the product. Without V0, Phase 12.5 tests a primitive library, not a component library. With V0, we know whether the library delivers on its positioning before we spend time validating its parts.

If V0 reveals compositional seams ("element A can't talk to element B without ugly boilerplate," "the palette is missing things every settings screen needs," "there's no clean way to flow state"), those are library findings — recorded for follow-up phases per §7 bug-handling discipline. V0 passing doesn't mean V1–V8 are unnecessary; V0 failing doesn't mean V1–V8 should skip.

### V1 — Element palette sweep [automated + manual]

**Purpose.** Every element type renders correctly and dispatches input correctly, in every PanelStyle, across supplier-driven and static variants, through visibility transitions. Extends to multi-element composition — V1 is not purely isolated-element testing, per THESIS §7.

**Element types exercised.** `Button`, `TextLabel`, `Icon`, `Toggle`, `Checkbox`, `Radio`, `Tooltip`, `ItemDisplay`, `ProgressBar`, `Divider`. Plus `Slot` (deferred to V5 — needs a handler context).

**Variants per element.** Static value vs supplier-driven value; visible vs hidden; with tooltip vs without; each of the four `PanelStyle`s (NONE, RAISED, INSET, DARK) where applicable.

**V1.1 — Isolated-element matrix.** One panel per element type × PanelStyle. Render all, verify each renders at its declared position with correct bounds. For interactive elements, programmatically trigger a click via `mouseClicked` and verify the consumer-side state-change handler fires.

**V1.2 — Composed-element panel.** At least one multi-element panel where elements interact. Composition shape:

- Header `TextLabel` (panel title).
- A `Toggle` that controls panel-internal state.
- An `Icon` whose sprite varies based on the Toggle's state (supplier-driven).
- A `ProgressBar` whose value is computed from the Toggle's state (supplier-driven).
- A `Tooltip` on one element that renders on hover.

This tests supplier-driving-between-elements (the interesting bug shape — an element's display deriving from another element's state) and nested layout math (multiple elements in one panel laid out correctly). Isolation testing doesn't catch bugs here; composition testing does.

**Automated checks (V1.1 + V1.2).**
- Element bounds (`getWidth`, `getHeight`, `getChildX`, `getChildY`) match declared values.
- `isVisible()` returns as configured (static + supplier variants).
- Interactive elements' `mouseClicked` returns `true` for clicks within bounds, `false` outside.
- Hidden elements' `mouseClicked` returns `false` (inertness — V3 tests this more thoroughly).
- V1.2: toggling the Toggle updates the Icon's sprite and the ProgressBar's value within one frame. No stale-read bugs.

**Manual checklist.**
- All rendered correctly at visual positions matching specs.
- PanelStyle backgrounds render per design (RAISED looks raised, INSET looks inset, etc.).
- Tooltips appear on hover, disappear off hover.
- V1.2 composed panel: toggle behavior visibly drives the icon swap and progress-bar update with no frame lag.

**Why after V0.** V1 tests that primitives work as primitives. V0 tests that the primitives compose into a product. Running V1 after V0 means a V1 failure is unambiguously a per-element issue — V0 already proved the library composes. Running V1 without V0 first risks missing the product-level failure mode.

### V2 — Regions × element palette [mixed]

**Purpose.** V1's element palette × M5's 25 regions matrix. Validates that every element renders correctly at every region's anchor point, in every context (inventory, HUD, standalone).

**Scope.** Extends the current `verification/RegionProbes` scaffolding. Each region gets a probe panel containing a representative subset of element types (1 Button + 1 TextLabel + 1 Icon), not just a `filledRect`. Probe-panel sizes vary to verify stacking math under non-uniform dimensions.

**Includes "probes don't adapt in creative" repro.** Needs narrowing from Trevor (§13 open question) — tab switch? window resize? specific tab? Once narrowed, becomes a named sub-check inside V2.

**Automated checks.**
- `RegionMath.resolveMenu` / `resolveHud` results match previously-validated values from `/mkverify all` contract 6 (ensures contract-level and scenario-level agree).
- Axial prefix math correct for multi-panel stacking (validated in current probe — extends to more regions).
- Overflow cutoff triggers when total probe extent exceeds region capacity.

**Manual checklist.**
- All 17 current probe positions still correct (regression check on current scaffolding).
- 25 regions × varied element types — each element renders cleanly at region anchor.
- Creative tab-switch sub-check (per narrowed repro).
- Recipe-book toggle shifts inventory probes left/right with menu.
- Window-resize test for HUD probes.
- Chest vs shulker vs hopper vs furnace — probes track each menu's ScreenBounds.

**Dependencies.** V1 must pass first (elements render correctly standalone) before this scenario's manual check is diagnosable.

**Creative-screen probe behavior (post-M8 scoping).** MenuContext probes fire on `CreativeModeInventoryScreen` — the M7 chrome provider accounts for creative's tab rows (25/26 px visible extents), and the registry dispatches via class-ancestry targeting. SlotGroupContext probes track the creative tab state dynamically per M8 §5.4's per-frame resolution: pink `PLAYER_INVENTORY` probe appears on the INVENTORY tab (where the player inventory layout is actually rendered) and disappears on every other tab (where creative items occupy those slot positions instead). `PLAYER_HOTBAR` — if a consumer registers a probe for it — would appear on every tab because the hotbar is always visually present in creative. The `ItemPickerMenu` resolver uses slot-count discrimination: `size == 54` is non-INVENTORY, `size != 54` is INVENTORY. See M8 §5.4 for why a specific INVENTORY-tab slot count can't be assumed (mods grafting slots onto `InventoryMenu` shift the count).

### V3 — Visibility lifecycle and inertness [automated]

**Purpose.** The inertness contract (THESIS principle 3 + `/mkverify all` contract 5) holds under realistic consumer patterns. Hidden means invisible to the world — no click capture, no render tick, no sync leak, no data leak.

**Element-level scenarios (V3.1–V3.6).**
- Rapid show/hide toggle (100 iterations) with assertion that `isVisible()` matches the supplier's current value every frame.
- Hidden element's `mouseClicked` always returns `false`, regardless of click coordinates.
- Hidden element's `render()` is never called (verified via instrumented PanelElement).
- Hidden Panel's getWidth/getHeight collapse to 0 when all elements hidden (+ background padding).
- `Panel.size(w, h)` pin suppresses the 0-collapse per §3.3 M5 escape hatch — automated: `getWidth()` returns the pinned value during a visibility transition that would otherwise collapse it to zero. (§13 Q6 resolved: automated proves the mechanism; consumer-visible jitter follows deterministically.)
- Panel visibility supplier driving from a changing consumer state — no stale-read bugs.

**Slot-level data inertness (V3.7).** This is the scenario that catches the `MenuKitSlot.getItem` regression (commit `03b2a1a`). Element-level visibility is behavioral; slot-level inertness is observational — a hidden slot group must look empty to the outside world, not just skip rendering.

Construct a native `MenuKitScreenHandler` with two slot groups. Populate both with distinct items. Hide one via `showWhen` supplier. Then assert:

- Iterating `menu.slots` and calling `slot.getItem()` on hidden-group slots returns `ItemStack.EMPTY`.
- Calling `broadcastChanges()` on the handler and capturing the sync payload shows no items from the hidden group (verified via instrumented sync-listener or a mock vanilla sync observer).
- A foreign mixin on `Slot.getItem` (we can use the existing `VerifyGetItemMixin` arm-pattern) observing a hidden-group slot returns `EMPTY` from the call-site's perspective.
- The underlying `Storage` still holds the items — inertness is observational, not destructive. When the panel is re-shown, items reappear and `slot.getItem()` returns them.

V3.7 is what would have caught `03b2a1a` pre-ship. Without it, V3 only tests element visibility and doesn't cover the regression it cites as motivation.

**Automated PASS/FAIL.** All V3 checks assertion-based; no visual confirmation needed.

**Why third.** Inertness bugs are the subtle ones that silently break. Phase 12.5's purpose includes catching this class of bug before Phase 13 builds features on the assumption that inertness holds.

### V4 — MenuKit-native screen lifecycle + cross-context reuse [mixed]

**Purpose.** MenuKit-native screens (built via `MenuKitScreenHandler`) open, render, dispatch input, and close correctly. Plus: validate the component-library promise that the same element instance works unchanged across inventory, HUD, and standalone contexts.

**V4.1 — Native screen lifecycle.**
- `openMenu` with a `MenuKitScreenHandler` → client receives open packet → client screen constructs.
- Panel layout computed correctly (BODY / RIGHT_OF / LEFT_OF / ABOVE / BELOW).
- Multiple panels with nested constraints.
- Slot groups render correctly (bridges into V5).
- Click dispatch to interactive elements routes through the adapter.
- Close via Escape or menu-close-button triggers handler cleanup.
- Reopen after close works correctly (no stale state).

**V4.2 — Cross-context element reuse.** The library's component-library promise (THESIS §5: context-agnostic elements, context-specific containers) made concrete. **V4.2 is the first explicit validation of THESIS §5 in MenuKit's history.** Six phases (6–12) have shipped under the context-agnostic-elements principle, but nothing has ever explicitly tested "the same element instance renders identically in three contexts." V4.2 either confirms six phases of architectural assumption or surfaces context-specific leakage that's been there the whole time — either outcome is real project learning.

Take a `Button`, `TextLabel`, `ProgressBar`, and `Icon` — construct each as a single element instance — and place it into three panels: one MenuContext (via `ScreenPanelAdapter`), one HudContext (via `MKHudPanel.Builder.element()`), one StandaloneContext (via `MenuKitScreenHandler.panel(...).element()`). Assert:

- Each element instance renders identically in every context (same bounds, same content, same hover behavior where applicable).
- `mouseClicked` dispatches correctly in inventory and standalone contexts (both dispatch input); returns `false` / is never called in HUD (per `RenderContext.hasMouseInput()` convention).
- Supplier-driven content updates reflect in every context simultaneously (shared supplier, shared state; HUD + inventory + standalone read from the same reference per-frame).
- No context-specific code path alters the element's behavior — the three panels invoke the element's `render(ctx)` identically; only the container's per-frame `RenderContext` differs.

If V4.2 fails for any element, that element has context-specific behavior leaking — a THESIS §5 violation. The fix is in the element, not in the container.

**Automated checks (V4.1 + V4.2).** Panel-position solver produces correct coordinates for each Mode. Handler close hook fires. V4.2: same-instance element renders match across contexts (bounds, supplier outputs).

**Manual checklist.** Visual layout matches expected positions. Click/interaction actually does the thing. Open → close → reopen cycle clean. V4.2: side-by-side visual inspection of the same element in three contexts shows identical rendering.

### V5 — Slot interactions [mixed]

**Purpose.** M4's slot-grafting path, plus canonical slot interactions on MenuKit-native handlers. Every input pattern: click, shift-click, drag, right-click, tooltip-on-hover, creative canonical-slot routing.

**Scenarios.**
- Native MenuKit handler with two slot groups — pickup / place / shift-click between groups routes per `shiftClickPriority` + `pairedWith`.
- Drag protocol (click-hold-sweep across slots) distributes items correctly.
- Right-click custom handler fires when declared on a SlotGroup.
- Tooltips render when hovering a slot with an item.
- **V5.5 — Creative/survival state persistence across menu switches.** Per M1 design doc §4.1 "Creative mode — source finding," `ItemPickerMenu` and `InventoryMenu` both construct their player-inventory slots with the same `player.getInventory()` instance. No canonical-slot mapping mechanism exists or is needed — `PersistentContainerKey.PlayerInventory(uuid)` resolves identically from both menus. V5.5's scope: set per-slot state on a player-inventory slot in survival, switch to creative, open creative inventory, verify state reads through. Then set state in creative, switch to survival, verify. Validates that the shared-`Container`-instance assumption holds empirically. (If V5.5 fails, the M1 design doc's finding needs revisiting.)
- Grafted-slot visual layer via shared constants (M5 §5.6 pattern) — verify the backdrop Panel tracks the grafted Slot coords.

**Automated checks.** Shift-click routing returns the expected target. `InteractionPolicy.input` predicate gates insertion. `QuickMoveParticipation` controls direction.

**Manual checklist.** Visual + interactive verification through the dev client.

**Dependencies.** V4 must work (screen lifecycle) for this scenario's UI to open.

### V6 — M1 persistence scenarios [automated + manual]

**Purpose.** M1's full round-trip. `/mkverify all` contract 7 tests server-side write/read only. V6 exercises the wire protocol, the snapshot paths, the cache coherency, the persistence across session boundaries.

**Scenarios.**
- **V6.1 — Happy-path round-trip.** Client write → server persist → broadcast → client cache coherent.
- **V6.1a — Optimistic reconciliation is idempotent.** M1 design doc §6.4 notes that the client updates its cache pre-ACK; the server's subsequent broadcast overwrites. "Since v1 has no rejection scenarios, this is effectively always a no-op reconciliation." That claim is an assumption until tested. V6.1a proves it: client writes X, cache sets X, server broadcasts X, cache receives broadcast — assert cache is still X (no visible blip, no double-set, no listener-side-effect flash). Matters because real network latency or future rejection logic could expose race conditions this path hides.
- Write → disconnect → reconnect → join snapshot restores state.
- Write to BE-anchored slot → chunk unload → chunk reload → BE attachment rehydrates → state survives.
- Cross-menu `SlotIdentity` stability: set a value on player-inventory slot via survival inventory, verify it reads on the same slot from inside a chest screen.
- Respawn-after-death → `PlayerListRespawnMixin` fires → inventory snapshot restores.
- Two players in multiplayer — per-player-private storage; Player A's state invisible to Player B on shared BE.
- Unregistered-channel read returns channel default.
- `/mkverify` namespace cleanup sweeps probe state at start of each run.

**Automated checks.** Most of the above are programmable. Assertion outputs on a `/mkverify v6` run.

**Manual checklist.** Disconnect/reconnect and chunk unload/reload inherently require dev-client interaction — those are checklist items.

**Why late in ordering.** M1 depends on M2 SlotIdentity stability (validated in earlier scenarios by usage). V6's failures are harder to diagnose without V1–V3 proving the primitives it builds on.

### V7 — HUD behavior [manual + some automated]

**Purpose.** HUD panel registration, positioning, supplier-driven elements, hide-in-screen behavior, window resize tracking.

**Scenarios.**
- 9 HUD probes in 9 regions (already validated in current scaffolding; regression-check).
- Supplier-driven element content updates every frame (animated text, dynamic ItemStack).
- Multiple panels per region stack correctly.
- `hideInScreen(true)` panel hides when any screen opens; `hideInScreen(false)` stays visible.
- Window resize → all HUD panels reposition relative to new screen bounds.
- GUI scale changes — crosshair clearance in `HudRegion.CENTER` stays visually clean at scales 2, 3, 4.

**Automated checks.** Panel position math matches `RegionMath.resolveHud` outputs. Registration count, visibility flags.

**Manual checklist.** Visual confirmation at multiple GUI scales. Window resize reflow. Screen-open hide behavior.

**Why after V6.** Bodyguard-order: HUD behavior is mostly already-covered by current probes. V7 is the least load-bearing of the primary validation tracks.

### V8 — MKFamily cross-mod [mixed]

**Purpose.** Cross-mod integration of the grouping primitive. Validator module registers a `"mkvalidator"` family; a second synthetic mod-id (inside the validator module, via a second entrypoint) joins the same family; assert identity + keybind-category sharing works correctly.

**M3 resolved as scope-down** (see §11). `MKFamily` retains identity-grouping + keybind-category sharing (Layer A); YACL dependency, ModMenu integration, and config-screen aggregation (Layer B) move out to individual consumer mods. V8's scope reduces accordingly — no aggregated-config assertions, no cross-mod ModMenu entry check.

**Scenarios (post-scope-down).**
- Two mod-ids calling `MenuKit.family("mkvalidator")` — same canonical `MKFamily` instance returned both times (identity).
- Display-name registration follows first-writer-wins; second registration with a different name is silently coerced or warning-logged (behavior declared in the Layer A API).
- Keybind-category sharing: both mod-ids register distinct keybinds under the same family; vanilla's Controls screen shows them under a single shared section.
- Family-level metadata (description, authors) accumulates additively where the API allows it.

**Prerequisite: M3 scope-down library work must land before V8 runs.** Phase 12.5's overall sequencing puts M3 scope-down as a library-side work item interleavable with V1–V3 scaffolding; V8 can't run meaningfully until the Layer B code is gone and consumer mods have been migrated.

**If migration work exceeds Phase 12.5's budget:** V8 is marked "pending M3 scope-down landing" and Phase 12.5 closes with 7 of 8 scenarios complete; V8 lands as a follow-on once migration is done.

---

## 6. Scenario-type discipline

Each scenario declares its type explicitly in its implementation and its `/mkverify v<n>` output:

- **[automated]** — PASS/FAIL assertions. No visual confirmation required. Output ends with a `VERDICT — N/N cases pass (PASS|FAIL)` line parallel to current `/mkverify all` contracts.
- **[manual]** — visual checklist printed to chat; the player confirms each item in-game. No programmatic PASS/FAIL.
- **[mixed]** — both. Automated assertions run + manual checklist prints. The scenario is marked complete when both halves confirm.

The scenario header in each `V<n>_*.java` file states its type in javadoc. The `/mkverify v<n>` output announces its type in the first line. No ambiguity about what "ran" means — someone (Trevor, advisor, future agent) reading the log can tell whether a green VERDICT includes visual confirmation or only automated assertions.

Example output shapes:

```
[mixed] [Verify.V2.Regions] BEGIN
[Verify.V2.Regions] Automated checks:
  - RegionMath.resolveMenu agrees with /mkverify all contract 6 — OK
  - Axial prefix math matches across 3-panel stack — OK
  ...
[Verify.V2.Regions] Automated VERDICT — 12/12 cases pass (PASS)
[Verify.V2.Regions] Manual checklist:
  1. Open a chest. Verify 8 probes render at frame edges per specs doc.
  2. Open recipe book on inventory screen. Verify probes shift with menu.
  3. Switch creative tabs (hotbar, search, etc.). Verify probes reposition.
  ...
[Verify.V2.Regions] END — automated green; manual checklist above, confirm in-game.
```

---

## 7. Bug-handling discipline

**Bugs surfaced during Phase 12.5 are fixed during Phase 12.5. Not deferred. Not rolled into Phase 13.**

The phase's purpose is to surface issues at the diagnostic level. Deferring them defeats the phase. If the library has a bug and we ship Phase 12.5 without fixing it, Phase 13's feature work will re-surface the bug under consumer-conflated conditions — the exact scenario Phase 12.5 is designed to prevent.

**Exception handling.** If a "bug" turns out to be a design-level gap rather than implementation bug (e.g., "there's no way to express X in the current API"), evaluate case-by-case:

- **Primitive scope-shaped.** The API really is missing something. Either extend the primitive during Phase 12.5 (advisor review + design-doc amendment + implementation) or defer as a Phase 13+ mechanism candidate (same pattern as Phase 11 filed M1/M4/M5/M6).
- **Consumer-policy-shaped.** The "gap" is something a consumer would address in their own code — not the library's job. Document as a non-goal finding, move on.

Case-by-case is the rule; the advisor adjudicates when unclear.

---

## 8. Shipping cadence — incremental, comprehensive target

**Each scenario lands as its own commit.** V0 commit → V1 commit → V2 commit → ... → V8 commit → Phase 12.5 close-out commit. Partial completion is a valid state — stopping after V4 leaves Phase 12.5 in a "partial validation, documented gap" condition, better than an MVP with unknown coverage.

**Target: comprehensive** (all eight scenarios: V0 + V1–V8).

**V0 runs first.** Its findings contextualize every subsequent scenario. If V0 passes cleanly, primitive scenarios run against a product-validated library. If V0 reveals compositional seams or palette gaps, primitive scenarios run with those findings as known context — and the findings feed back into future-phase planning. Do not skip V0 even in constrained runs.

**Acceptable stopping point if resources constrain:** **V0 + V1 + V3 + V6** — product validation plus the three highest-leverage primitive scenarios. That set catches the product-level failures and the ~80% of likely primitive issues with ~50% of the work.

**Within the V0 + V1 + V3 + V6 fallback quartet, ordering still matters:** V0 first (product-level — if this fails, subsequent primitive data is diagnostically useful but secondary), V1 second (highest breadth — element primitives underpin everything else), V3 before V6 (inertness bugs can mimic persistence bugs and vice versa — hidden slot showing wrong state could look like a sync issue until V3 clears the inertness confound). Run V0 → V1 → V3 → V6 if stopping partial.

---

## 9. Close-out artifacts

At Phase 12.5 completion:

- **REPORT.md** — findings, bugs fixed (with commit refs), design-doc corrections if any, primitives-validated manifest. **Includes a dedicated "Palette gaps" section documenting any missing element types V0 surfaced** (text input, slider, dropdown, scroll container, or anything else a realistic settings panel needs that the MenuKit palette doesn't provide). Palette gaps are library-evidence for future phases — Phase 13+ can evaluate whether each gap is primitive-shaped (library adds it) or consumer-policy-shaped (consumer builds their own). V0 surfaces; Phase 12.5 doesn't resolve.
  - **Resolution pathway for palette gaps is design-doc-first.** Each candidate gap gets a mini-design-doc at the Phase 13+ entry point, following Phase 12's primitive-design cadence (design doc → advisor review → implementation). This hedges against the scope-creep pattern of "let's just add text input real quick" — exactly the anti-discipline Phase 11's "architectural discipline first, functionality second" principle was built to prevent. A candidate gap that can't earn a design doc probably isn't a library-shaped primitive; that's useful filtering in itself.
- **SESSION_BRIEF.md for Phase 13** — updated read order, working principles, first action. Supersedes `Phase 12/SESSION_BRIEF.md` at the Phase 13 entry point.
- **Per-scenario V<n>.md** (optional) — one-page summary of what each scenario covered + its final status + any lingering notes. Only if a scenario has nuance beyond PASS/FAIL.
- **Design-doc corrections** — if M1/M4/M5 design docs drifted from shipped reality during Phase 12.5 bug-fix cycles, correct at close-out (same discipline as Phase 12's close-out pass).
- **THESIS.md reference** — the palette-gaps inventory is the evidence THESIS principle 7 ("Validate the product, not just the primitives") is designed to surface. Close-out REPORT cites the principle + its test (*does the validation pass include a consumer-shaped scenario?*) as satisfied.

---

## 10. Library-vs-validator boundary

**Validator module ships:**
- Scenario implementations (V1–V8).
- Any supplementary mixins needed for scenarios (e.g., if V5 needs a specific `mouseClicked` hook).
- `/mkverify v<n>` command registrations.
- A minimal `fabric.mod.json` declaring its existence as a mod.

**Validator module does NOT ship:**
- Helper utilities that should live in the library. If a scenario needs `X`, and `X` would be useful to real consumers, `X` belongs in the library — not in the validator's package.
- Workarounds for library bugs. If the validator has to hack around a library issue, that's a bug the library should fix. Workaround is temporary.
- Test data that consumer mods would want to reuse. The validator is opinionated about its own tests; sharing isn't its job.

The boundary enforces library integrity — the validator can't become a back-channel for library bloat.

---

## 11. M3 disposition — resolved: scope down (Disposition 2)

**Status: shipped in-phase.** Layer B removed from MenuKit; IP / shulker-palette / sandboxes each own a standalone YACL config screen + ModMenu entry; agreeable-allays unchanged (pure Layer A consumer). Full resolution record — including advisor rulings on storage (Reading C for orphan IP mixin reads, hardcode `SHOW_ITEM_TIPS`, disposition (a) for in-UI config buttons) — folded into `Phase 11/POST_PHASE_11.md`'s M3 entry.

Advisor round-1 resolved M3 as **scope down to grouping only**. The mechanism has two layers tangled together:

> **Layer A — shared identity + keybind-category sharing.** Library-shaped. Consumers register under a family ID; MenuKit returns the canonical instance; keybind categories merge in-game. This is the same pattern as M5's registration-order stacking — a library-owned coordination primitive for cross-mod claims on a shared resource (keybind categories in this case, inventory regions in M5's).
>
> **Layer B — aggregated config screen via YACL + ModMenu.** Platform-shaped. Library takes ownership of rendering a multi-mod config screen, pulls in YACL as a dependency, mediates ModMenu integration. A second mod author wanting to build their own family config UI has to mirror this aggregation machinery — exactly the "library-not-platform" smell we've been avoiding.
>
> Disposition 1 (keep as-is) is comfortable but Layer B is the piece a public ecosystem would immediately complain about ("why does MenuKit force YACL on me?"). Disposition 3 (remove entirely) over-rotates — keybind-category sharing is genuine coordination that consumers shouldn't have to re-solve; Layer A is earned. Disposition 2 keeps the earned piece and drops the platform-y piece.

**Concrete scope-down action items (prerequisite library-side work before V8):**

- `MenuKit.family("id")` stays. Returns canonical `MKFamily` instance. Consumers contribute display name, description, keybind-category descriptor.
- Keybind-category sharing via the family stays — pure identity routing, no rendering.
- YACL dependency leaves MenuKit.
- ModMenu integration leaves MenuKit.
- Config screen rendering leaves MenuKit — consumers build their own ModMenu entries using their own config solutions.

**Consumer migration cost (Phase 13):** each of the four consumer mods (IP, shulker-palette, sandboxes, agreeable-allays) rebuilds its ModMenu entry and config screen. Roughly half a day per mod; bounded, one-time, before any public release.

**Sequencing.** M3 scope-down is a library-side work item separate from Phase 12.5 scenarios. It can interleave with V1–V3 scaffolding (neither depends on the other). It must land before V8 runs meaningfully. If it slips, V8 defers with partial Phase 12.5 close-out (7/8 scenarios).

**Documentation artifact.** Once scope-down lands, update `POST_PHASE_11.md`'s M3 entry from "decision pending" to "resolved: scope-down; see `Phase 12.5/DESIGN.md` §11."

---

## 12. Non-goals

- **Performance benchmarking / stress testing.** Phase 12.5 validates correctness, not performance. Mod authors concerned about FPS under heavy HUD registration handle that themselves.
- **Cross-version testing.** Target is 1.21.11, the current supported Minecraft version. When MenuKit migrates to a later MC version, a new validation pass (maybe "Phase N.5" under that phase) runs.
- **Replacement for `/mkverify all`.** The seven canonical contracts stay. Phase 12.5 adds scenario-level integration checks alongside them, not in place of them.
- **Test against real consumer mods.** Phase 11 did that; it surfaced the primitives we built in Phase 12. Phase 12.5 doesn't re-test Phase 11's output — it pressure-tests the Phase 12 primitives in isolation.
- **Headless unit tests.** Everything runs through the dev client's `/mkverify` harness. Headless JUnit-style tests are explicitly out of scope — the library's test philosophy is runtime assertions via chat command, not build-time test suites.
- **Automated visual regression.** No screenshot diffing, no pixel-perfect checks. Visual confirmations are manual checklist items, per §6 scenario-type discipline.

---

## 13. Resolved decisions + one remaining pre-V2 ask

Round-1 resolved six of the seven open questions. One remains open — the creative-adapt repro narrowing — and it gates V2 start, not overall Phase 12.5 start. V1 proceeds in parallel.

**Resolved:**

1. **Module naming — `validator/`.** Shorter, clearer; doesn't duplicate the "mk" prefix (module lives inside the menukit repo). `mkvalidator/` only useful if the module might ever leave this tree, which it won't.
2. **V1 and V2 stay separate.** V1 establishes element primitives work; V2 layers regions on top. Separate VERDICTs preserve diagnostic signal — a V2 failure tells you whether the issue is element-rendering (V1 regressed) or region-positioning (V2-specific). Merging loses that.
3. **V8 if M3 resolves as "remove entirely" → drop V8 entirely.** A "validate the absence" scenario just tests that ModMenu works as documented, which is ModMenu's contract, not MenuKit's. Not in scope for MenuKit validation. (M3 actually resolved as scope-down per §11, so V8 has a real — though reduced — scope.)
4. **`/mkverify all` stays contracts-only.** Scenarios run under `/mkverify v<n>` (individual) or `/mkverify scenarios` (convenience wrapper for sequential run). Two separate namespaces, two separate purposes: contracts are fast always-on probes of library semantics; scenarios are deliberate integration sweeps you run when you intend to. Bloating `/mkverify all` with scenario-level checks (including manual checklists interleaved with contract output) would make it harder to use as the phase-boundary regression gate it currently is.
5. **Creative-tab-adaptation repro — still open.** See below.
6. **V3 `Panel.size(w, h)` pin — automated.** Assert `getWidth()` returns the pinned value during a visibility transition that would otherwise collapse it to zero. That proves the mechanism; consumer-visible jitter follows deterministically. Manual visual is nice-to-have, not required.
7. **Case-by-case bug-fix discipline, no session cap.** Time isn't the axis; shape-of-fix is. A one-session API-reshaping fix is a deferral candidate; a three-session pure-implementation fix stays in-phase. Trust advisor adjudication when ambiguous.

**Still open — narrow before V2 starts:**

**Creative-tab-adaptation repro (from Trevor's probe walkthrough observation).** Trevor reported "the probes don't adapt in creative" during the current probe validation. Before V2's sub-check for this is scoped, need specifics:

- **Trigger.** Tab switch (e.g., inventory tab → combat tab → search tab)? Window resize while creative is open? Opening a creative tab that has a different screen height (e.g., the hotbar-saves tab)? Reproducible on demand, or intermittent?
- **Symptom.** Probes render at the wrong position? Don't render at all? Capture clicks in the wrong location? Flash / jitter?
- **Which probes affected.** All 8 inventory probes? Only TOP/BOTTOM-anchored ones (where tab row might interact)? Stacking probes specifically?

Advisor's working hypothesis (noted for context): creative's tabs reconstruct the screen but may not trigger a region-registry recompute, leaving probes with stale bounds. If that's the shape, the fix is likely in the region pipeline — bounds are already computed per-frame, so something's cached that shouldn't be. Narrowing the repro confirms or rejects this hypothesis before V2 design finalizes.

**Once narrowed,** the repro becomes a named V2 sub-check with its own PASS/FAIL or visual assertion. Until then, V2 is draft-scoped.

---

## 14. Summary

Phase 12.5 is a validation interstitial: a new Gradle submodule (`validator/`) that acts as a synthetic consumer, exercising every Phase 12 primitive through realistic usage patterns across eight scenarios, surfacing bugs where they're diagnosable (primitive level, not consumer-integration level) and fixing them in-phase.

**V0 validates the product.** Consumer mini-application — a settings-screen feature built end-to-end inside the validator using 5–8 interacting elements, supplier-driven state flow, consumer-owned persistence via the lens pattern (THESIS §8). Surfaces palette gaps (text input, slider, dropdown, scroll container; `Checkbox.linked` / `Radio.linked` absence; `Toggle.linked` without `disabledWhen`; `PanelBuilder.size`) as library-evidence for future phases. Validates THESIS §7 (validate the product, not just the primitives) and exercises THESIS §8 (elements are lenses, not stores). Runs first.

**V1–V8 validate the primitives by blast radius.** **V1** element palette (V1.1 isolated + V1.2 composed) → **V2** regions × palette → **V3** visibility + inertness (including V3.7 slot-level data inertness — the specific regression class that `MenuKitSlot.getItem`'s 12a-checkpoint regression hit) → **V4** native screen lifecycle (V4.1) + cross-context element reuse (V4.2 — same element instance in inventory + HUD + standalone, THESIS §5 made concrete) → **V5** slot interactions (V5.5 rewritten for the shared-`Container`-instance finding from M1 §4.1) → **V6** M1 persistence (V6.1a added for optimistic-reconciliation idempotency) → **V7** HUD → **V8** MKFamily (scope reduced per M3 scope-down resolution).

Each scenario declares type (automated / manual / mixed) explicitly; output leaves no ambiguity about what "passed" means. Ship incrementally, comprehensive target, partial stops valid with documented gaps. If partial: V0 → V1 → V3 → V6 ordering within the fallback quartet. All bugs fixed in-phase; design-level gaps case-by-case.

**M3 resolved as scope-down** (§11): `MenuKit.family("id")` keeps identity + keybind-category sharing (Layer A); YACL + ModMenu + config-aggregation (Layer B) leaves MenuKit. Prerequisite library-side work; interleavable with V0–V3 scaffolding; must land before V8 runs meaningfully.

**THESIS principles 7 and 8** landed alongside this doc. Principle 7 ("Validate the product, not just the primitives") codifies the V0 discipline: every validation phase includes at least one consumer-shaped scenario. Principle 8 ("Elements are lenses, not stores") codifies the state-ownership line for interactive elements: MenuKit provides `linked(supplier, callback)` factories over consumer-owned state; the library does not persist non-slot element state. M1 remains the one library-owned persistence primitive, scoped to slot-shaped state only. Phase 12.5 is the first pass under both principles — V0 exercises the lens pattern as its canonical persistence story, and the palette-gap audit (what ships a `linked` factory, what doesn't) becomes load-bearing close-out evidence.

Close-out: REPORT.md (including palette-gap inventory from V0) + SESSION_BRIEF.md for Phase 13 handoff + design-doc corrections if any drift surfaces + `POST_PHASE_11.md` M3 entry updated to reflect scope-down landing. Phase 13 opens with library-validated primitives, a trimmer library surface, and documented evidence of what consumer needs are met + what palette gaps remain.

**Status: resolved, ready for implementation, pending creative-adapt repro from Trevor for V2 sub-check scoping.** §13 six of seven decisions locked; remaining item gates V2 start only (V0, V1 proceed in parallel).
