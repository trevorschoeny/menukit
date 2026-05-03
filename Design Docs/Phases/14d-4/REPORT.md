# Phase 14d-4 — Slider — REPORT

**Status: closed. Round 1 + smoke green. Single commit.**

Continuous-value slider control. Wraps vanilla `AbstractSliderButton` via composition — same precedent as 14d-3 TextField/EditBox. No new primitives (PanelElement lifecycle hooks shipped 14d-3 carry the load). Aim of 1 advisor round + inline achieved.

---

## What shipped

**Library (`menukit/`):**

| File | Change | LOC |
|---|---|---|
| `core/Slider.java` (new) | PanelElement; builder; wraps internal `MenuKitSlider` (private subclass of `AbstractSliderButton`); per-frame supplier-pull with applyValue-bypass to avoid spurious onChange fires | ~280 |
| `verification/ContractVerification.java` | M17 Slider builder validation probe (9 cases — required fields, null guards, builder fluency); `runAll` invocation | +130 |

**Validator (`validator/`):**

| File | Change | LOC |
|---|---|---|
| `scenarios/smoke/SliderSmokeScreen.java` (new) | Standalone `MenuKitScreen` subclass — header + Slider with `.label()` + supplier-driven Consumer-state TextLabel + Reset Button + Back-to-Hub Button | ~80 |
| `scenarios/smoke/MenuKitSmokeState.java` | `sliderValue` field (volatile double, default 0.5) | +9 |
| `scenarios/hub/HubHandler.java` | "Slider" entry added at TOP of Hub list (newest-first per convention) | +8 |

**Total:** ~+510 LOC across 5 files. Lighter than TextField (~+450 LOC, 12 files) on a per-file-touched basis because no primitive infrastructure work — lifecycle hooks already in place.

---

## Smoke results

`SliderSmokeScreen` (opened from Hub → Slider entry, post Test-button click that ran M17):

| Check | Outcome |
|---|---|
| In-track label updates live during drag (`Value: NN%`) | ✓ |
| Supplier-driven `Consumer state: X.YYY` TextLabel below slider updates live during drag | ✓ |
| Tab focus + Enter to enable keyboard mode (slider highlights blue) + arrow-key step | ✓ (vanilla keyboard inherited via wrap) |
| Narration auto-derived from in-track label | inherited from vanilla; no separate verification |
| Reset button → slider snaps to 0.5 visually (consumer-state-write + supplier-pull, no imperative `setValue`) | ✓ — proves the Supplier+Consumer lens shape works as designed |
| Back-to-Hub navigates correctly | ✓ |
| Value persists across screen close/reopen | ✓ (static state field) |

Trevor's smoke verdict: *"Wow that works perfectly!"*

---

## Round-1 verdict outcomes

**8 advisor sign-offs, 0 pushbacks, 2 principled divergences validated:**

| Q | Topic | Outcome |
|---|---|---|
| Q1 | Lens shape — Supplier+Consumer (matches ScrollContainer; vanilla `setValue` change-guard makes supplier-pull idempotent) | Sign off |
| Q2 | `.label(DoubleFunction<Component>)` — in-track label via vanilla pattern | **Divergence sign-off** (advisor's brief was wrong-shaped; vanilla bakes label into slider) |
| Q3 | Keep vanilla's keyboard support (Enter to enable + arrows step + narration) | **Divergence sign-off** (cutting vanilla features the wrap inherits for free is anti-pattern) |
| Q4 | Range — normalized 0-1; defer `.range(min, max)` to evidence | Sign off |
| Q5 | Continuous v1; defer `.steps(int)` — vanilla agrees | Sign off |
| Q6 | M9 + click dispatch verify in smoke | Sign off; passed without intervention (same as TextField) |
| Q7 | Visibility-driven attach/detach deferred (TextField precedent) | Sign off |
| Q8 | Single-element phase (Slider only; Dropdown = 14d-5) | Sign off |

---

## Calibration meta

**New standing rule generalized from Q2/Q3 divergences:**

*Follow vanilla when wrapping — don't cut features vanilla provides for free; don't add API surface vanilla doesn't have.*

The advisor confirmed this generalizes the existing *find the vanilla flag/primitive* heuristic into a stronger rule: not just "find vanilla precedent" but "follow vanilla precedent — both in what to ship AND what to cut." Saved to memory ([Follow Vanilla When Wrapping](../../../../.claude/projects/-Users-trevorschoeny-Code-Trevs-Mods/memory/feedback_follow_vanilla_when_wrapping.md)) as a standing principle for every wrap-vanilla phase. Applied immediately — audited the round-1 SLIDER.md draft and cut a `.narrationLabel(Component)` override I'd added that vanilla doesn't expose.

**Heuristics now in the calibration set (numbered for cross-phase reference):**

1. *Compounding mixins → wrong layer* (14d-1)
2. *Find vanilla's existing primitive before inventing one* (14d-2 / 14d-3 — discovery)
3. *Pre-empted dispatch owns responsibility* (14d-2.5)
4. *Audit existing surface before parallel one* (14d-2.7)
5. *Render order matters when wrapping vanilla widgets — manual in custom pipeline* (14d-3)
6. *Follow vanilla when wrapping — don't cut features inherited for free; don't add API vanilla doesn't have* (14d-4 — composition)

**Process meta:** zero rounds beyond round 1; zero implementation findings beyond what was anticipated in the design doc. Bounded scope + clean precedent (TextField) + comprehensive vanilla investigation upfront delivered the cleanest 14d phase to date.

---

## Carried forward (deferred)

Same shape as TextField — these aren't 14d-4-specific gaps; they're standing palette items waiting for evidence:

- **`.range(min, max)` builder convenience** — fold inline if 2+ consumers ask. v1 path: consumer maps externally with one-line math.
- **`.steps(int)` discrete-snap** — fold inline if evidence emerges. v1 path: consumer-side snap via `Math.round` in onChange.
- **Modals containing sliders** — same M9 keyboard-mixin trap as TextField (modal eats arrow keys including step-while-focused). Mouse drag still works. Defer fold-on-evidence.
- **Visibility-driven attach/detach** — same TextField gotcha; same recommended consumer pattern (blur via `screen.setFocused(null)` before hiding).

---

## Next phase

**14d-5 = Dropdown.** Per advisor's 14d-4 entry brief: "Dropdown has more design surface (popover + option list + click-outside-closes + opacity composition + z-order); separate phase." Composes with ScrollContainer (already shipped) for option lists.

After 14d-5: Phase 14 close → Phase 15 consumer migrations.
