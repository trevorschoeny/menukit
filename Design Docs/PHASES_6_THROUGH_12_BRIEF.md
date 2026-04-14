PHASES_6_THROUGH_12_BRIEF.md
Overview
The first MenuKit migration (Phases 1-5) moved the inventory-menu subsystem from god-class architecture to clean architecture. The five canonical guarantees were empirically verified at the end of Phase 5. The library has a coherent architecture for inventory menus.
The second migration (Phases 6-12) is a different kind of work. It's an identity migration — from "library that handles inventory menus well" to "component library for Minecraft UI." The scope broadens to three rendering contexts (inventory menus, HUDs, standalone screens), the element palette grows substantially, and the library's thesis sharpens into something closer to what consumers reach for when they want to build UI in Minecraft.
This is a second migration with its own arc. Seven phases, approximately 30-40 days of focused work, with consumer mods remaining disabled until Phase 11. The scale matches the scope — treating the component-library shift as a feature addition would produce an incoherent library; treating it as a genuine migration produces a coherent one.
Phase 6: Thesis, contexts, and palette
Purpose
Before any implementation, articulate MenuKit's identity as a component library sharply enough to guide the rest of the migration. This phase is deliberately pre-code. The output shapes every subsequent phase.
Scope
Three documents produced, reviewed, and locked before Phase 7 begins:
Thesis document. What MenuKit is as a component library. What distinguishes it from vanilla UI tools. What design principles it holds. What the line is between library elements and consumer elements — the criterion for "ships in MenuKit" vs. "consumers build it themselves." What scope ceilings exist (what MenuKit deliberately doesn't do). The library-not-platform discipline inherited from the first migration, applied to the broader scope.
Contexts document. The three rendering contexts MenuKit targets (inventory menus, HUDs, standalone screens) and for each: what the composition root is (probably Panel in all three), what context-specific machinery exists, what guarantees apply. Scope boundaries — explicitly not config UIs (use standalone screens), not chat/F3/world-selection (out of scope), not other niche contexts.
Palette document. The complete intended element set. For each element: what it does, what use case it serves, which contexts it applies to (some elements work everywhere; SlotGroup only in inventory menus), ship-vs-consumer decision, rough API shape, implementation priority (ship now / ship after X / defer indefinitely).
The palette covers substantially more than the Phase 5 audit surfaced. The audit was reactive — it named what current consumers already need. The palette is proactive — it names what a coherent component library should include over time. Expect elements like Toggle, Checkbox, Radio, Divider, Icon, ProgressBar, Tooltip-as-element alongside the audit items.
Working practice
This phase is primarily writing and design. No code changes. Resistance to "let's just start building" is the discipline — the foundation needs to be solid before subsequent phases build on it.
Each document goes through draft → review → iteration → lock. Drafts surface questions; reviews resolve them; locked documents become north stars for implementation phases.
Scope discipline

Don't design element APIs in detail here. The palette names elements and sketches their shape; detailed API design happens in the element-specific phases.
Don't make implementation decisions that should come from design docs in later phases. Thesis, contexts, and palette are deliberately abstract.
Don't commit to elements that don't fit the thesis. If the palette includes something the thesis can't justify, one of them is wrong.

Estimated effort
3-5 days. Less if the writing flows; more if fundamental questions surface during thesis work.
Phase 7: Context generalization
Purpose
Before new element work, generalize the existing Panel and PanelElement system across the three target contexts. Currently, panels live entirely inside the inventory-menu subsystem; they need to work in HUDs and standalone screens too.
Scope
The question the phase answers: what refactoring is needed to make Panel and PanelElement context-agnostic?
Possible answers, to be determined by design work:

Panel already abstract enough; only context-specific container classes need work
Panel needs a context-neutral base with context-specific subclasses
A higher abstraction like "Container" is needed that Panel implements

Whichever answer, the phase produces a design doc first, review, then implementation. The design work for Phase 8's foundational elements depends on the context abstraction being right.
Specific refactoring targets:

Panel's dependencies on inventory-menu infrastructure (if any) move to context-specific subclasses
HUD rendering integrates with the generalized panel system; MKHudPanel becomes a context-specific panel subclass rather than a separate subsystem
Standalone screen support is added — probably a MenuKitScreen extends Screen class that holds panels via the same abstractions
Element rendering, input dispatch, and visibility work uniformly across contexts where they apply

Working practice
Design doc → review → implement. The design is the load-bearing part; if the abstraction is wrong, subsequent phases inherit the wrongness.
Inventory-menu functionality must not regress. The Phase 5 contract verification must still pass after Phase 7's refactoring. Run the verification suite as part of Phase 7's completion criteria.
Scope discipline

Don't add new elements in this phase. Foundational elements are Phase 8.
Don't add new contexts beyond the three. Config and other contexts stay out of scope.
Don't redesign the inventory-menu machinery. The Phase 1-5 architecture is the baseline; generalization means lifting common abstractions above it, not rewriting it.

Estimated effort
5-7 days. The design work is substantial. The implementation is mostly refactoring with careful regression testing.
Phase 8: Foundational elements
Purpose
Build out the element palette. The elements a coherent component library should ship — the ones not surfaced by Phase 5's consumer audit but identified in Phase 6's palette as foundational.
Scope
Likely elements (specifics determined by Phase 6's palette):

Toggle (the general primitive — Phase 9 will build the specialized state-linked version)
Checkbox
Radio / RadioGroup
Divider
Icon (standalone, also reusable within Button)
ProgressBar
Tooltip-as-element (explicit element rather than a bolted-on property)

Each element gets a design doc before implementation. The design doc covers: what it does, API surface, which contexts it works in (most work in all three; some might be context-specific), how it composes with existing elements, event bus integration, how it fits the builder API.
Working practice
Design doc per element before code for that element. Small elements might have short design docs; that's fine. The discipline is always design before code, not the doc length.
Consistency matters more here than anywhere else in the migration. Users of a component library expect elements to follow shared conventions — if Button has onClick, Toggle should have onToggle (not onPress or handler or something different). Establish conventions early and apply them uniformly.
In-game visual testing as each element lands. Component libraries are visual; bugs are visual.
Scope discipline

Don't build elements that the palette doesn't call for. No speculative additions.
Don't exceed the palette's API sketches substantially. If an element's sketch is "Toggle with boolean state and onToggle callback," don't build a state-machine-driven toggle with custom transitions — that's design drift.
Don't refactor existing elements to "match" new ones unless the existing elements are genuinely inconsistent. Match to new ones; don't churn old ones.

Estimated effort
5-7 days depending on how many foundational elements the palette includes.
Phase 9: Audit-surfaced elements
Purpose
The Phase 5 audit items. Elements and capabilities the four consumer mods specifically need.
Scope

Icon-only button variant (sandboxes, shulker-palette)
Toggle button with supplier-based pressed state (shulker-palette) — specialization of Phase 8's Toggle, linked to consumer state via supplier
Icon swap by state (shulker-palette) — probably part of the specialized toggle, not a separate element
Dynamic tooltip via Supplier<Component> (shulker-palette) — applies to Button and other elements
Dynamic text content via Supplier<Component> for TextLabel (agreeable-allays)

These build on Phase 8's foundations. A specialized state-linked Toggle implies Phase 8's general Toggle exists. Dynamic tooltips and text imply Phase 8's conventions for element content. Icon-only Button implies Phase 8's Icon element.
Working practice
Same as Phase 8 — design doc per element, in-game testing, consistency with established conventions. These elements are simpler because they build on Phase 8's foundations; the design work is mostly "how does this specialize or extend the existing element?"
Scope discipline

Don't expand scope beyond the audit items. If Phase 8 missed an element the audit needs, add it in Phase 8 (reopen if necessary); don't sneak it into Phase 9.
Don't adjust Phase 8 elements to "accommodate" specialized versions. The general primitives should stand alone; specializations extend rather than requiring changes to the base.

Estimated effort
3-5 days.
Phase 10: Injection patterns
Purpose
Document and support consumer-side injection of MenuKit elements into vanilla UI contexts. The library-not-platform discipline means MenuKit doesn't inject into vanilla contexts itself; consumers write the mixins. But the library can provide primitives, helpers, and documented patterns that make consumer-side injection ergonomic.
Scope
Three injection patterns, one per context:

Injecting into vanilla screens (e.g., adding a button to every chest screen)
Injecting into vanilla HUD rendering (e.g., adding a status indicator to the HUD)
Injecting into vanilla standalone screens (e.g., adding a button to the pause menu)

Each pattern has:

A canonical consumer mixin shape (written as a documented example)
Any helper classes MenuKit ships to make the common cases easier (probably small utility classes for positioning, visibility management, event dispatch composition)
Documentation of how the pattern composes with other mods doing similar injection

The scope ceiling: MenuKit provides elements and optional helpers. MenuKit does not ship a general "inject into vanilla" framework. Consumers write their own mixins; MenuKit keeps their job ergonomic without doing it for them.
Working practice
Design doc for each pattern. The doc is the primary deliverable — these patterns will appear in documentation at Phase 12. Get them right.
Test each pattern empirically: write a sample consumer mixin that follows the pattern, verify it works, capture log output as evidence. This validates the pattern is usable, not just plausible.
Scope discipline

Don't generalize the pattern into a framework. The discipline is "consumer writes the mixin, using our primitives." Drifting toward "library injects for them" recreates the platform trap.
Don't require consumer-specific helpers. Every helper MenuKit ships for injection should be usable by any consumer, not tailored to one.

Estimated effort
3-5 days. The design work dominates; the code is usually small.
Phase 11: Consumer mod refactors
Purpose
Validate the completed library by rebuilding the four consumer mods against it. If the library is right, refactors should be clean. If refactors keep hitting gaps, earlier phases reopen.
Scope
Rebuild each mod to use the new architecture:

inventory-plus (biggest, most surface)
shulker-palette
agreeable-allays
sandboxes

Re-enable each in dev/build.gradle as its refactor completes.
Specific captured items that must be preserved:

The IP bug fixes captured in DEFERRED.md during Phase 5 (peek region arg, creative ItemPickerMenu dual-menu fix) must be re-introduced in the refactored IP. Flag these at the start of IP's refactor session.

Working practice
IP first. If IP refactors cleanly, the library's shape is validated. If IP surfaces gaps, the relevant earlier phase reopens briefly before continuing.
For each mod:

Read the current code to understand what it does
Identify the MenuKit APIs needed
Refactor incrementally, file by file or feature by feature
Test in-game after substantive changes
Re-enable in dev/build.gradle
Document anything that felt awkward — running notes during the refactor

At the end of each refactor, review the notes. Decide whether any noted awkwardness is a library gap (reopen earlier phase) or just implementation labor.
Scope discipline

Don't improve the consumer mods beyond refactor. If a mod has pre-existing suboptimal design, note it but don't fix it.
Don't change mod behavior. Refactor is rebuild against new infrastructure, not feature revision.
Don't write public documentation for the consumer mods. That's their own README territory, not Phase 12.

Estimated effort
3-5 days for IP. 1-2 days total for the three small mods. Add time if IP triggers a Phase 8 or 10 reopen.
Phase 12: Documentation
Purpose
Write all documentation together, describing the library as it actually is after the migration completes.
Scope
Three artifacts:
STORY.md rewrite. The internal canonical architectural document. Leads with the component-library framing. Names all three contexts with their guarantees. Foregrounds the element palette. Treats slot groups as one structurally complex element. Reflects everything Phases 6-11 surfaced — refined patterns, confirmed framings, architectural decisions made during implementation.
External docs rewrite. GitHub README, Modrinth page, any other public-facing material. Consistent with STORY.md. Typically shorter and more concrete — leads with "what this library is and how to use it," not architectural narrative.
Cairn rewrite. MenuKit's Architectural Knowledge Management. Captures the full architectural knowledge from both migration arcs:

The five canonical guarantees for inventory menus
Analogous guarantees for HUDs and standalone screens
The three-layer model (structure / visibility / rendering)
SlotGroupLike and the uniform abstraction
The library-not-platform discipline and how it's maintained across contexts
The component-library thesis and the element-palette decisions
Major decision points across both migrations
Deferred concerns and how they were resolved

Working practice
Load all Phase 6-11 reports before writing. The architectural knowledge to capture spans the full arc.
Draft STORY.md first — the internal document anchors the framing. Draft external docs next, using STORY.md as source material. Draft Cairn last, synthesizing everything.
Review for consistency across the three artifacts. The framing in STORY.md should match the framing leading the README, which should match the architectural principles captured in Cairn.
Scope discipline

Don't change code during Phase 12. Documentation-only phase.
Don't add features based on "now that I'm writing docs, I realize we need X." Capture as a future item; don't implement.
Don't rewrite docs that exist and are already accurate. Focus on what needs to change.

Estimated effort
3-5 days across the three artifacts.
Meta-practices
Resume fresh between phases. The arc is long. Rest between phases produces better work than momentum.
Design doc before code in Phases 7 through 10. Implementation without design produces incoherent APIs in a component library. Phase 6 is all design; Phases 7-10 each include design-first working practice.
In-game visual testing throughout. Component libraries are visual; most bugs are visual; reading code rarely catches them.
Honest reporting at phase boundaries. Same format as Phases 1-5: what was done, deviations from plan, completion test results with concrete evidence, surprises, decisions not in plan, outstanding concerns.
Scope discipline as first-class concern. Each phase has specific scope-creep risks called out. Treat them as constraints. Adding features is the scope-creep magnet in this migration (contrast with subtractive work in Phases 1-5).
Reopen prior phases if needed. The arc is sequential but not one-way. If Phase 11 surfaces a Phase 8 gap, reopen Phase 8 briefly. If Phase 10's injection pattern reveals a missing primitive, reopen Phase 8 or 9. The cost of reopening grows with distance — caught at Phase 9, a Phase 8 gap is cheap; caught at Phase 11, it's expensive. Catch early where possible.