# Phase 12.5 — Close-out REPORT

**Status: complete.** All planned scenarios shipped (V0–V8); seven primitive gaps surfaced, six fixed in-phase, one filed for Phase 13.

---

## Executive summary

Phase 12.5 built a synthetic consumer mod (`mkvalidator`) that exercises every MenuKit primitive through realistic usage patterns. The hypothesis: a consumer-mod-shaped validator catches library incompleteness that isolated primitive tests miss, because real consumer composition stresses primitives in combinations isolated tests don't reach.

**The hypothesis held.** Seven primitive gaps surfaced over the phase, none of which had failed during isolated unit tests of the same primitives. Each had to be discovered through realistic-consumer composition — V4's cross-context decoration, V2's tooltip layering, M7's chrome boundary, V5.1's builder-method exposure, V5.6's recipe-book render coverage, β's category-extension API, V5.7's M1 storage-layer wiring.

Six gaps closed in-phase via additive library primitives (~20–150 lines each). The seventh, gap #7 (M1 storage-layer end-to-end wiring), filed and deferred to Phase 13 — it's primitive-completion work, not a Principle-11-cheap additive fix, and its broader framing pending Trevor + advisor's architectural design pass on M1's scoped-storage primitive shape.

The phase also surfaced two distinct sub-shapes of "validator-consumer caught" that are themselves novel — the validator pattern catches incompleteness not only during scenario *execution* (gaps 1–5) but during pre-check *reading* (gap #6) and pre-scaffold *survey* (gap #7).

---

## Scenario-completion table

| Scenario | Status | Type | Commit | Notes |
|---|---|---|---|---|
| V0 — Consumer mini-app | ✅ green | mixed | `d42b2ee` | THESIS Principle 8 introduced |
| V1 — Element palette × PanelStyle matrix | ✅ green | mixed | `a544da2` | V1.1 isolated + V1.2 composed |
| V2 — Regions × element palette | ✅ green | automated + visual | `8c57e9c` | Surfaced gaps #2 + #3 (M7); RegionProbes scaffolding for ongoing visual checks |
| V3 — Visibility lifecycle + inertness | ✅ green | automated | `c5c3fdd` | V3.7 slot-data inertness validates the 12a-checkpoint regression class |
| V4 — Native screen lifecycle + cross-context reuse | ✅ green | mixed | `fc43cc3` | Surfaced gap #1 (V4 ScreenPanelAdapter completeness — 4 sub-gaps) + THESIS Principle 9 |
| V5.1 — Shift-click routing primitives | ✅ green | automated | `3fa5bce` | Surfaced gap #4 (`PanelBuilder.pairsWith` exposure) — fix in `ac0210e` |
| V5.3 — Group-level right-click handler | ✅ green | manual | `b41192c` | Pre-check PASS, scaffold inline into V5Handler |
| V5.5 — Creative/survival shared-Container M1 invariant | ✅ green | automated | `a327714` | Validates M1 §4.1 finding empirically |
| V5.6 — Grafted-slot visual-layer scaffold (M5 §5.6) | ✅ green | manual | `b5081e6` | Surfaced gap #5 (recipe-book render-pipeline coverage) — fix folded into same commit |
| V5.7 — M4 × M1 × M8 seam | ✅ green (narrowed) | mixed | `5c29940` | Gates B+D narrowed to within-session per gap #7 finding; gate C uses β `extend` (gap #6 fix) |
| V6 — M1 persistence | ✅ green | automated | `31a3d2d` | Round-trip + reconciliation (V6.1a) |
| V7 — HUD behavior | ✅ green | mixed | `c791523` | Supplier content + hideInScreen pair + HUD-side stacking |
| V8 — MKFamily Layer A (post-M3 scope-down) | ✅ green | automated | `1402cf0` | Identity + displayName + modId roster + keybind-category sharing |

---

## Primitive-gap catalog

| # | Gap | Surface mode | Disposition | Fix commit |
|---|---|---|---|---|
| 1 | `ScreenPanelAdapter` completeness — background render, padding, overflow diagnostics, origin accessor | scenario execution (V4.2 inventory injection) | fixed-in-phase | `fc43cc3` (folded with V4) |
| 2 | Tooltip layering — `MenuKitPanelRenderMixin` injection point for panels-above-slots-below-tooltips | scenario execution (V2 visual probes) | fixed-in-phase | `d57b07e` |
| 3 | Chrome awareness (M7) — region anchoring outside frame for creative tabs + recipe-book widget | scenario execution (V2 visual probes) | fixed-in-phase | `85f9bed` |
| 4 | `PanelBuilder.pairsWith` exposure — internal API hidden from builder surface | scenario execution (V5.1 scaffold) | fixed-in-phase | `ac0210e` |
| 5 | Recipe-book render pipeline coverage — `MenuKitRecipeBookPanelRenderMixin` for the 5 recipe-book-hosted screens silent-inert under `MenuKitPanelRenderMixin` alone | scenario execution (V5.6 crafting-table backdrop) | fixed-in-phase | `b5081e6` |
| 6 | `SlotGroupCategories.extend` (β) — consumer can't add a category to a vanilla menu's library-registered resolver under first-wins semantics | **pre-check reading** (V5.7 pre-check 2 + M8 §10.560 design-intent trace) | fixed-in-phase | `fa89cf6` |
| 7 | M1 storage-layer wiring — `BlockEntityStorage` stub since Phase 3; `PlayerStorage` save/load not invoked by handler lifecycle; consumers hand-roll Fabric attachments | **scaffolding survey** (V5.7 pre-scaffold pass on M1 storage) | **filed-and-deferred to Phase 13** | finding only: `5c29940` |

---

## Validator-consumer pattern story

The validator mod is a synthetic consumer with no production users — its sole job is to compose MenuKit primitives realistically and surface incompleteness. Across seven gaps, it accomplished that in three distinct ways:

### Surfacing during scenario execution (gaps 1–5)

Five of seven gaps appeared the way the original hypothesis predicted — a scenario was scaffolded against existing primitives, run in the dev client, and broke or rendered nothing. The break was the signal. V4's panel rendering with no background, V2's tooltips covered by panels, V2's regions overlapping creative tabs, V5.1's missing builder method, V5.6's silent-inert render hook on recipe-book screens — all surfaced as concrete behavior gaps the moment a real consumer composition needed the primitive.

This is the canonical validator-consumer pattern: realistic usage stresses primitives in combinations isolated tests don't reach.

### Surfacing during pre-check reading (gap #6)

Gap #6 surfaced *before* any V5.7 code was written. The pre-check question was "does `SlotGroupCategories` resolver visibility cover M4-grafted slots?" Tracing the M8 design doc's "natural home" claim at §10.560 down to `SlotGroupCategories.register`'s first-wins semantics revealed the gap: design intent existed (consumer-declared categories for grafted slots) but the public API only supported it for consumer-OWNED menu classes, not consumer-grafted-into-vanilla menu classes.

No scaffolding, no runtime evidence — just design-claim-to-source traceability. The validator-consumer pattern catches not only what breaks at runtime, but what the documentation *promises* and the API doesn't deliver. Pre-check discipline pays out as a separate evidence channel.

### Surfacing during scaffolding survey (gap #7)

Gap #7 surfaced one layer deeper than gap #6. Pre-check 1 ("does `SlotIdentity` produce stable keys for grafted slots?") had passed cleanly within its scoped question. But during V5.7 scaffolding, surveying the M1 storage classes for which to use revealed that `BlockEntityStorage` is a Phase 3 TODO stub and `PlayerStorage`'s save/load is unwired — IP's `PlayerAttachedStorage` sidesteps both with consumer-private hand-rolled attachments.

This is a *different* validator-pattern signal than gap #6: not a runtime break, not a design-claim trace, but a "the primitives I'd need don't exist as advertised" discovery during pre-implementation survey. Pre-checks have a scoped boundary; downstream-layer stubs surface during scaffolding, not pre-check reading. Naming this distinction explicitly preserves accurate framing for future phases — don't self-critique a correct pre-check answer when a downstream layer-gap surfaces later.

### Net outcome

| Surface mode | Count | Implication |
|---|---|---|
| Scenario execution | 5 | Predicted by hypothesis |
| Pre-check reading | 1 | Novel — design-claim traceability |
| Scaffolding survey | 1 | Novel — primitive-survey discovery |

The validator-consumer pattern is broader than its initial framing. It catches incompleteness through any path a realistic consumer would take — implementing, reading, surveying. All three modes are productive signals.

---

## Phase 13 candidates (carrying forward)

Items surfaced during Phase 12.5 that are out of scope for this phase but worth design attention in Phase 13:

| Item | Origin | Shape |
|---|---|---|
| **M1 scoped-storage primitive design pass** | gap #7 | Trevor + advisor scoping. Comprehensive primitive that ties identity (PersistentContainerKey variants) + lifecycle (within-session / per-player attachment / BE-anchored) + dispatch declaratively. Not a "complete BE wiring" patch — that risks calcifying a partial design. See `V5_7_FINDING_M1_STORAGE_WIRING.md` § 3 for option sketches. |
| **Sandboxes settings-button click failure** | M3 smoke | `SandboxScreen.settingsButton` doesn't open YACL screen on click; press-release propagation across screen transition hypothesized as cause. ModMenu entry works. Defer to Phase 13 sandboxes refactor. POST_PHASE_11.md M3 entry has full record. |
| **Keybind-category-sharing public-release review** | M3 smoke + DEFERRED.md | §11 scope-down preserved `getKeybindCategory()` as Layer A grouping primitive. Reconsider for non-Trevor authors (whose mods may not be a "family") before public release. If answer is "leaves too," MKFamily Layer A shrinks further to identity + mod-id roster only. |
| **Architectural reorg of Phase 13 work** | Concurrent — Trevor + advisor in flight | NORTH_STAR.md draft + STORY.md → Archived/ in working tree at REPORT-write time. Phase 13 begins under the new structure once the reorg lands. |

---

## Cross-references — design docs landed this phase

- **Architectural designs:** `Phase 12.5/DESIGN.md` (phase scope), `M7_CHROME_AWARE_REGIONS.md` (M7), `M8_FOUR_CONTEXT_MODEL.md` (M8), `V5_7_EXTEND_RESOLVER_FIX.md` (β)
- **Primitive-gap findings:** `V4_FINDINGS.md` (gap #1), `V5_FINDING_PAIRSWITH_BUILDER_GAP.md` (gap #4), `V5_6_FINDING_RECIPEBOOK_RENDER_OVERRIDE.md` (gap #5), `V5_7_FINDING_M1_STORAGE_WIRING.md` (gap #7)
- **Pre-check + interim records:** `V5_7_PRECHECK.md` (V5.7 pre-checks 1+2 outcomes), `M8_V2_REPORT.md` (M8 + V2 interim REPORT before this close-out)
- **THESIS principles introduced:** Principle 7 (Phase 12.5 design doc), Principle 8 (V0), Principle 9 (V4), Principle 10 + 11 (M8 step 5)

---

**Phase 12.5 closed.** Next: Phase 13 under the reorg structure currently in progress.
