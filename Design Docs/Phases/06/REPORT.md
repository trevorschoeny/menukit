# Phase 6 Report — Thesis, Contexts, and Palette

Phase 6 is complete. The three canonical documents are drafted, reviewed, and locked. No code changes were made; this was a design-only phase, as specified.

---

## What was done

Three canonical documents produced in order with advisor review between each. Each document went through draft → review → refine → lock before the next began.

- `menukit/THESIS.md` — what MenuKit is as a component library; five design principles with operational tests; the three-test ship criterion; scope ceilings; cross-context disciplines.
- `menukit/CONTEXTS.md` — the three-property definition of a rendering context; inventory menus, HUDs, and standalone screens each with composition root, machinery, five guarantees, and consumer entry points; cross-context composition; what is not a context.
- `menukit/PALETTE.md` — the complete intended element set organized across six tiers: shipping, next, audit, HUD-specific, deferred, consumer-owned. Each entry specifies behavior, use case, contexts, ship decision, rough API sketch, and priority.

The documents are canonical rather than phase-marked. They describe permanent aspects of the library and carry forward into Phase 12's documentation rewrite.

---

## Deviations from the plan

None substantive. The brief's working practice — draft each document before the next, review between each, no code changes — was followed exactly. Three review-and-refine cycles, one per document, each producing small targeted edits before the next draft began.

One proactive scope decision was raised before drafting: document naming (canonical vs. phase-marked). The advisor affirmed canonical. This was not a deviation from the brief but a clarification the brief did not specify.

---

## Surprises

**Panel is not context-neutral today.** The current `Panel` class holds `List<SlotGroup>` as a first-class field, not a context-specific extension. That means Phase 7's generalization work includes structural refactoring of Panel itself, not just its containing subsystems. The Contexts document was written against the target state (an inventory-menu panel holds slot groups and elements; HUD and standalone-screen panels hold elements only) rather than the current state, per advisor guidance that canonical documents describe what MenuKit is rather than intermediate migration states.

**MKHudElement and PanelElement are deeper parallel universes than the brief suggested.** The two interfaces have different render signatures (`GuiGraphics + DeltaTracker` vs. `GuiGraphics + mouseX + mouseY`), different visibility semantics (MKHudElement has no visibility hook), and different positioning models (absolute screen space vs. panel-content-relative). Phase 7's unification work is more than subsuming one under the other — it requires signature reconciliation. Noted as a Phase 7 handoff item below.

**The input-dispatch asymmetry between contexts is a first-class design commitment, not an incidental detail.** HUDs are render-only; interactive overlays require a standalone screen. The Contexts document codified this as a deliberate scope boundary with tick-safety rationale. The Palette document used it to explain which elements have interactive behavior in which contexts. This commitment was implicit in the current HUD subsystem (MKHudElement has no click handling) but had never been stated as doctrine. It is doctrine now.

---

## Decisions not in the brief

The brief specified deliverables and scope, not specific architectural positions. The following positions were established during Phase 6 and are worth naming because they will shape Phases 7-12.

### From the thesis

- **Principle precedence is explicit.** Library-not-platform beats context-agnosticism when they trade off. Earlier principles override later ones when they conflict.
- **Ship-vs-consumer is a three-test conjunction**, not a disjunction. All three tests (independent consumers, compositional primitive, context-agnostic or load-bearing-to-its-context) must pass. Borderline cases default to "don't ship."
- **The declared-structure discipline generalizes across all three contexts**, not just inventory menus. Its origin is sync-safety; its rationale in non-sync contexts is that consistent construction-time declaration is what allows elements to compose identically across contexts.
- **Five disciplines carry forward across contexts**: composability, substitutability, inertness, declared structure, uniform abstraction. Specific guarantees differ between contexts; the disciplines do not.

### From contexts

- **"Rendering context" has a three-property definition**: how the container is held, what update loop drives it, what context-specific elements it requires. A candidate without distinct answers to all three is not a new context.
- **Two new canonical terms coined**: *tick-safety* (the HUD analogue of sync-safety) and *lifecycle-safety* (the standalone-screen analogue). These are load-bearing terms for Phase 7's design work.
- **HUDs are render-only as doctrine.** No click dispatch, no key capture, no input of any kind. Consumers needing interactive overlays during gameplay build a standalone screen.
- **`MenuKitScreen` is committed as the standalone-screen consumer entry point.** The class does not exist yet; Phase 7 or 8 builds it.

### From palette

- **The library is committed to roughly fifteen named elements** across shipping, next, audit, and HUD-specific tiers. Past roughly twenty-five is a prompt to revisit whether recent additions were held to the criterion or rationalized past it.
- **MKHudList and MKHudGroup are deferred, not graduated.** Their current HUD-subsystem implementations do not automatically port to the generalized palette. List conflicts with the declared-structure discipline; Group conflicts with Panel-as-ceiling-of-composition. Consumer use cases will be examined individually during Phase 7.
- **ItemDisplay and ProgressBar are generalized across contexts**, subsuming the current HUD-specific `MKHudItem` and `MKHudBar`.
- **Tooltip ships in two forms**: a builder property on interactive elements (hover-triggered) and a standalone element (persistent info box).
- **Icon-only Button and state-linked Toggle ship as factory-method variants**, not distinct elements. Composition is preferred over element proliferation.
- **Tabs deferred** as composable from Button + panel visibility. Reconsideration trigger: multiple consumers independently building similar tab patterns during Phase 11 refactors.
- **Consumer-owned category includes pattern rationale**, not just examples. Anything that embodies domain logic, implies a specific application, or requires mixing into non-target contexts is consumer-owned.

---

## Handoff notes for Phase 7

These items are not outstanding concerns to resolve before Phase 7 begins. They are items Phase 7's design work must address early — naming them now saves Phase 7 from rediscovering them.

**1. MKHudElement ↔ PanelElement signature reconciliation.** Render signatures differ today. Phase 7's design doc decides: (a) which signature wins (panel-relative with mouse coordinates, or absolute screen-space with delta tracker), (b) how the other context's needs are met under the winning signature (deltaTracker can move to the container; mouseX/mouseY are meaningless on HUDs but not harmful to pass), (c) how visibility hooks unify. This is a non-trivial design call, not a mechanical rename.

**2. Panel's slot-groups-as-first-class-field.** Today `Panel` holds `List<SlotGroup>` directly. Three broad design options for Phase 7: (a) SlotGroup becomes a PanelElement and the slot-groups field is eliminated, (b) SlotGroup remains a parallel list but only populated in inventory-menu panels, (c) Panel has context-specific subclasses where inventory-menu panels add the slot-groups field. The choice is Phase 7's to make; naming the options now so the design doc covers them.

**3. MKHudList and MKHudGroup use-case audit.** The palette defers these. Current HUD consumer usages (if any — today's HUD subsystem is used by the library's own scaffolding, not yet by audited consumer mods) must be examined individually during Phase 7. Three paths per usage: express with fixed-template + hide/show under declared-structure, rebuild as consumer-side code in a decorate-vanilla mixin, or flag the use case as a palette-reconsideration trigger.

**4. `MenuKitScreen` base class.** Committed in the Contexts document but not yet built. Phase 7's design doc should decide whether Phase 7 or Phase 8 builds it. The decision affects when the standalone-screen injection pattern (Phase 10) can be validated against a real base class.

**5. `MKHudItem` and `MKHudBar` migration path.** The Palette graduates these to core-context ItemDisplay and ProgressBar. Phase 7 or 8's implementation needs a migration story for existing HUD-builder callers — either a compatibility shim during the transition or a direct rewrite with documented mapping.

**6. Phase 5 contract verification must pass after Phase 7.** The five guarantees for inventory menus were empirically verified at the end of Phase 5. Phase 7's refactoring must not regress them. The verification procedures are the regression gate; Phase 7's completion criteria include running them.

---

## Outstanding concerns

The handoff items above cover the substantive pending work. No unresolved architectural questions block Phase 7 from beginning. The three documents are internally consistent and externally aligned with the five contracts verified at the end of Phase 5.

One meta-concern worth naming: the three canonical documents represent the longest sustained design work MenuKit has received in a single pre-code phase. Phases 7 through 12 build on them for weeks. They should be referenced continually rather than re-litigated. When Phase 7's design doc faces a decision that seems to conflict with a document's commitment, the default is to honor the document; revisions to the documents themselves should be rare and explicit.

---

## What comes next

Phase 7 begins with design work: Panel and PanelElement generalization across contexts. The design doc is the load-bearing part; implementation is mostly careful refactoring guided by the design. The Contexts document names the target semantics; the handoff notes above name the specific design decisions Phase 7 must make.

The three documents are locked.
