# Testing Conventions

**Status: round 1 closed (advisor sign-off, six verdicts).** Convention binding from Phase 14d-2.7 forward.

Living convention document. Captures the standing rules for how MenuKit's testing is structured — single entry point, single panel, automatic vs visual split, library/validator boundary. Every future phase's smoke wireup checks against this doc.

---

## 0. Scope

Convention applies to **the canonical test surface for MenuKit** — the validator's Test Hub. The testing infrastructure is split between two modules per the library/validator boundary:

- **`menukit/`** (the library) — exposes pure-logic contracts via `ContractVerification.runAll(ServerPlayer)`. Library has zero testing-UI scaffolding. Any consumer (validator, future test surfaces, CI runners) can call `runAll(...)` to exercise the library contracts.

- **`validator/`** (the test consumer) — owns the entire visual test surface: the inventory "Test" button, the Test Hub screen, all visual smoke wireups for MenuKit primitives (modal dialog, ScrollContainer, M9 panel opacity), the M5 region probes, all V0–V8 scenario screens.

This split matches the dogfooding thesis literally: validator IS the canonical test consumer of MenuKit. Tests of MenuKit live in the consumer that uses MenuKit, not in MenuKit itself.

Other consumer mods (inventory-plus, shulker-palette, sandboxes, agreeable-allays) aren't bound by this convention. Each can adopt the pattern voluntarily for their own testing if it helps them; they aren't required to.

---

## 1. Intent

Testing should be **friction-free** and **trustworthy**.

- **Friction-free** — one click runs everything testable. No commands to remember. No decisions about what to run when. The path of least resistance is the right path.
- **Trustworthy** — automatic tests are the regression backstop (computer-verified, repeatable). Visual tests are the residue (only humans can verify). Mixing them is noise; the split must be clean.

The five conventions below are the structural commitments that deliver this intent.

---

## 2. The conventions (binding from now on)

### 2.1 Single test entry point — the inventory "Test" button

A single persistent button anchored at `MenuRegion.RIGHT_ALIGN_TOP` of every `InventoryScreen` and `CreativeModeInventoryScreen`. Always reachable: open inventory, click button, everything testable runs.

Click semantics:
1. Server-side: run all automatic tests (library contracts via `ContractVerification.runAll(player)` + every `runAggregated*` validator scenario probe). Each emits its own VERDICT log line; the button click prints a chat summary.
2. Server-side: open the Test Hub screen for the visual scenarios that can't run automatically.

**No chat commands.** Phase 14d-2.7 removed every test-related chat command (bare and subcommands, library and validator both). Single command was a transitional rule; the binding rule is single test ENTRY POINT, which the inventory button delivers more directly.

### 2.2 Single Test Hub panel — scrollable, mixed entries

One panel. Scrollable (dogfooding 14d-2 ScrollContainer). Each entry is one of:

- **Toggle** — flips a visibility flag for a smoke panel that decorates inventory or HUD. Player navigates to the target context to see the result.
- **Button** — opens a full scenario screen via C2S payload → server-side `player.openMenu`.

Both shapes coexist in the same scrollable list; no separate panels for toggles vs buttons.

The Hub is a `MenuKitScreenHandler`-backed screen (Q2 verdict — Hub Screen, not inventory decoration). Standalone context. Bonus dogfooding: the Hub itself exercises MenuKitHandledScreen + ScrollContainer + Button + ToggleButton + M8 Column primitives. If any of those don't compose cleanly, that's a primitive gap to surface.

### 2.3 Most-recent at the top

Newest test surfaces first. In the validator's Hub, this is achieved by hand-coding entries newest-first in source order — no separate registration-order-reversal step needed for a localized hand-coded list. (For a future open-registry shape, registration-order reversal would be the mechanism.)

When a new phase ships a test, its entry goes at the top of the entry list in `HubHandler.create`.

### 2.4 Entry shape: toggles for visibility flags, buttons for scenarios

- **Toggle entries** — dialog/scroll/opacity smokes, V4.2 HUD/Inventory decoration, V7 HUD probes, region probes (master + stack-middle). Toggle flips a static visibility flag; the corresponding smoke panel uses `showWhen(() -> flag)` to render conditionally.
- **Button entries** — open a full screen on click. Two underlying mechanisms:
  - **Server-menu open** via `OpenScenarioC2SPayload` + `dispatchOpenScenario` switch case (V0/V1.1/V1.2/V4.1/V5 — `MenuKitScreenHandler`-backed scenarios).
  - **Client-side standalone open** via `Minecraft.setScreen(new XxxScreen())` (TextField — `MenuKitScreen` subclass; no slots/no menu, doesn't need server roundtrip).
  - **S2C trigger** (V4.2 Standalone) — server sends marker payload; client receives and opens screen via `setScreen`.

Use the shape that matches the test's nature:
- "Is a smoke panel visible right now?" → toggle (decorates a vanilla screen the player navigates to).
- "Open a workflow / dedicated UI" → button. Pick server-menu vs client-standalone based on whether the screen needs slots + sync (server-menu) or is pure client-side composition (client-standalone).

### 2.5 Redundancy minimum — only test what could have changed

When suggesting tests for a change, default to **change-scoped**: only the probes/scenarios the change could plausibly affect. Don't reflexively propose a regression sweep on every iteration.

The inventory "Test" button is the regression backstop (run before commit per the close-out workflow). Per-change tests are the iterative loop; they're scoped.

When unsure, ask Trevor: *"This touches X — re-test Y; do you also want me to re-run W?"* — rather than enumerate everything.

---

## 3. Adding a new smoke test

The mechanical pattern post-14d-2.7. Pick ONE of the smoke surface shapes (per §4 — surface choice is per-test, driven by the primitive's nature):

### 3.1 Inventory-decoration smoke (toggle entry)

Best for primitives that exercise inside an existing screen context (modal dialog over inventory, ScrollContainer alongside slots, M9 opacity over slot grid).

1. **State.** Add visibility flag to `MenuKitSmokeState` (`static volatile boolean xxxPanelVisible = false`).
2. **Wireup.** Add a `wireXxxSmoke()` method in `MenuKitSmokeWireup` — build the smoke `Panel` with a `showWhen(() -> MenuKitSmokeState.xxxPanelVisible)` supplier; register via `ScreenPanelAdapter` at an appropriate `MenuRegion`. Add the call to `wireAll()`.
3. **Hub entry.** In `HubHandler.create`, add a `addToggle(...)` block at the TOP of the entry list. The toggle flips the visibility flag.
4. **Auto-check probes** (optional). Add to `ContractVerification.runAll` (library-pure logic) or to `MkValidator.runAllAutomatedThenOpenHub` (validator scenario aggregator).

### 3.2 Standalone-screen smoke (button entry)

Best for primitives that need their own UI real estate or compose a workflow (TextField with companion controls, sliders + value display, future palette demos).

1. **Screen.** Create a `MenuKitScreen` subclass in `validator/.../scenarios/smoke/` containing the smoke panel(s). Header + the primitive + companion buttons (Clear, Back-to-Hub).
2. **State** (optional). Add fields to `MenuKitSmokeState` for any value lensing or persistence-across-reopen the smoke needs (TextField pattern: `xxxValue` per-keystroke + `xxxLastSubmitted` Enter-only).
3. **Hub entry.** In `HubHandler.create`, add an `addButton(...)` block at the TOP. The button onClick calls `Minecraft.getInstance().setScreen(new XxxSmokeScreen())` directly client-side.
4. **Back-to-Hub button** in the screen — `HubBackDecoration` only reaches `AbstractContainerScreen` subclasses; standalone Screen subclasses bake the back button into their own panel.
5. **Auto-check probes** as in 3.1.

### 3.3 Done

No new chat command. No new screen factory boilerplate beyond the smoke screen itself. No library-side test scaffolding (library exposes only `ContractVerification.runAll` for pure-logic contracts).

The convention's structural test sentence:

> *Can a new smoke test be added by writing one wireup method or smoke screen (in validator), registering one Hub entry (in validator), and optionally adding one auto-check probe — with no new chat command and no library-side test scaffolding?*

If yes, convention is intact. If no — if a new test forces a chat command, library-side scaffolding, or a parallel registration surface — the test design needs reshaping (or the convention needs revisiting).

---

## 4. What the conventions don't decide

Each of these stays on the per-test wireup:

- **Smoke surface shape** — inventory-decoration vs standalone-screen (per §3.1 vs §3.2). Choose based on the primitive's nature.
- **Smoke panel placement** (decoration case) — region-based vs lambda; which `MenuRegion`; auto-size vs pinned; etc.
- **Smoke panel visibility model** (decoration case) — supplier-driven via `showWhen(() -> flag)` is the canonical pattern.
- **Smoke target screens** (decoration case) — `.on(...)` vs `.onAny()` per test.
- **Smoke screen layout** (standalone case) — single panel vs multiple, `MenuKitScreen` vs `MenuKitHandledScreen` (the former for no-slots client-only smokes; the latter only if the smoke genuinely needs slots).
- **Phase REPORT smoke verification text** — per-phase REPORTs describe what was verified; the convention covers HOW tests are run, not WHAT they verify.

---

## 5. Redundancy discipline — example application

**Scenario:** Phase 14d-4 ships a slider. The agent just finished implementation and is about to propose smoke tests.

**Wrong-shaped proposal:**

> "Click the inventory Test button. Also click TextField to make sure it didn't regress. Also click M9 Opacity. Also ScrollContainer. Also Modal Dialog. Also Region probes. Also V0..."

**Right-shaped proposal:**

> "Click the inventory Test button — runs the full contract sweep + validator aggregators (regression backstop). Open the Hub, click the new 'Slider' entry — verifies drag, value lensing, snap behavior. Other smokes don't intersect this change so don't need re-smoking."

The right-shaped proposal trusts the inventory Test button as the regression backstop and scopes visual smoke to what's actually new. **The Test button click already runs every automated contract** — when Trevor smoke-tests by clicking Test + navigating into a Hub entry, the contracts already ran. Don't ask for a separate "now run the contracts" step; that step doesn't exist as a distinct affordance.

The judgment call: "could this change have plausibly affected X?" If yes, propose. If no, don't. When unsure, ask — don't enumerate.

---

## 6. Round-1 verdicts (closed)

Six advisor sign-offs from the original convention round + one inline scope-clarification nit.

**Q1 — Single command shape: dissolved.** The original Q1 verdict (a single bare chat command opens Hub + runs contracts) ran for ~half a phase before Trevor surfaced the deeper convention: no chat commands at all. The inventory "Test" button is the entry point; chat commands are gone. Q1 is moot post-14d-2.7.

**Q2 — Hub render surface: (α) Hub Screen.** Confirmed and reframed: Hub is a `MenuKitScreenHandler` factory in validator (`HubHandler.create`), opened via `player.openMenu`. Validator-side, not library-side.

**Q3 — Most-recent-at-top mechanism: registration-order = display-order for hand-coded list.** The original "registration-order reversed" verdict assumed an open-registry pattern (each test calls `TestHub.add(...)`). For the validator's hand-coded `HubHandler.create`, the simpler shape is to write entries newest-first directly. Same outcome; less indirection.

**Q4 — Compound entry shape: (α) one button/toggle with compound `onClick`.** Confirmed.

**Q5 — Phase 14d-2.7 sequencing: before 14d-3.** Strongly confirmed. 14d-3 (text input) dogfoods the convention from day one.

**Q6 — Doc location: top-level Design Docs/.** Confirmed.

**Inline scope clarification (folded):** convention applies to validator's Hub, not "library-side only." Library exposes pure-logic `runAll` for any consumer; validator owns the visual test surface. The scope statement above (§0) reflects this.
