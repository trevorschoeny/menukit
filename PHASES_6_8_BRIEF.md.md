PHASES_6_8_BRIEF.md
Overview
The MenuKit migration (Phases 1-5) is complete. Three phases of follow-up work remain, committed to as a sequenced continuation. This brief captures the scope, working practices, and sequencing for Phases 6, 7, and 8.
The phases build on each other. Phase 6 grows the library to meet real consumer needs surfaced by Phase 5's audit. Phase 7 validates the library by rebuilding consumer mods against it. Phase 8 writes the documentation that describes what the library actually became.
Documentation is last, deliberately. The library's final shape isn't known until Phase 7 completes. Writing docs before then captures a fiction.
Phase 6: Library completion
Purpose
Grow the library to support the patterns Phase 5's consumer audit revealed. The audit showed that the dominant consumer use case is decorating vanilla screens rather than building custom screens, and that specific element primitives are needed across multiple consumer mods.
Scope
Elements to ship (each traced to real consumer need):

Icon-only button. Button variant that displays an icon instead of text. Needed by sandboxes and shulker-palette.
Toggle button with supplier-based pressed state. Two-state button whose appearance reflects live state read through a supplier, click flips the state via a handler. Needed by shulker-palette.
Icon swap by state. Extension of icon button where the displayed icon changes with state. Needed by shulker-palette, potentially as part of the toggle button rather than a separate element.
Dynamic tooltip (supplier-based). Button and other elements accept Supplier<Component> for tooltips that change with state. Needed by shulker-palette.
Dynamic text content (supplier-based). TextLabel accepts Supplier<Component> for text that changes at runtime. Needed by agreeable-allays.

Patterns to design:

Vanilla-screen injection pattern. A documented consumer pattern for injecting MenuKit elements into vanilla screens via consumer-written mixins. The library provides primitives and helpers; consumers write the mixins. This is the biggest design piece of Phase 6 and deserves careful thought before implementation. Needed by all four consumer mods.

Architecture question to resolve:

MKHudPanel's relationship to the main architecture. MKHudPanel currently survives in the codebase but its place in the canonical surface is undocumented. Question to answer: is it a first-class part of MenuKit, a separate library that happens to ship in the same jar, or something in between? The answer affects whether HUD work is in MenuKit's scope going forward.

Potential items:

Any gaps surfaced during Phase 5's contract verification that weren't addressed in Phase 5.
The InventoryMenu dedicated recognizer, if any consumer need becomes concrete during Phase 6 or 7. Currently deferred.

Working practice: design doc per item
Phase 6 is different in character from the migration phases. The migration was "move existing functionality to a better home" — the answers were mostly known, the work was refactoring. Phase 6 is "design new functionality the library should have" — the answers aren't known, and rushing into implementation without design work produces incoherent APIs.
For each new element type and for the injection pattern, produce a design document before implementation. The design document should cover:

What the element does and what use case it serves (with specific reference to which consumer mod needs it)
API surface — methods, fields, builder integration
How it composes with existing elements and the panel system
How it integrates with the event bus and other infrastructure
Any new architectural decisions being made (new enums, new interfaces, etc.)
What the element does NOT do (scope boundaries)

Design docs are reviewed before coding starts. Coding happens against an approved design, not against a general intuition.
The vanilla-screen injection pattern deserves the most design attention. It's not just "add a class" — it's a documented convention for how consumers compose MenuKit elements into vanilla screens they're injecting into. The design needs to cover what helper classes or utilities MenuKit provides (if any), what the consumer mixin looks like, how positioning works relative to vanilla screen bounds, how the consumer manages visibility of their injected elements, how input dispatch composes with vanilla's existing input handling.
Sequencing within Phase 6
Approximate order, adjust as designs surface dependencies:

Review all audit findings and the current STORY.md to load context
Design doc: icon-only button
Implement icon-only button (following design)
Design doc: toggle button with supplier-based state
Implement toggle button
Design doc: supplier-based dynamic content for Button tooltips and TextLabel text
Implement supplier-based content
Design doc: vanilla-screen injection pattern — the biggest piece
Implement injection pattern primitives and helpers
Resolve MKHudPanel's architectural relationship (design decision, possibly some code)
Address any remaining gaps from Phase 5 verification

Scope discipline
Phase 6 is the phase most vulnerable to scope creep because it's adding features. Things to avoid:

Building element types that no current consumer needs (no speculative primitives)
Adding API convenience methods beyond what the designs call for
Refactoring existing elements to "match" new ones stylistically
Touching consumer mods (that's Phase 7)
Writing documentation (that's Phase 8)
Generalizing patterns from the audit beyond what the audit specifically revealed

If a use case comes to mind that might want a primitive, and no Phase 5 audit finding supports it, defer. The library grows from real consumer needs, not from speculation.
Estimated effort
5-8 days of focused work. The injection pattern design might be 1-2 days on its own. Implementation for each element is typically half a day to a day including testing.
Phase 7: Consumer mod refactors
Purpose
Rebuild the four consumer mods (inventory-plus, shulker-palette, agreeable-allays, sandboxes) against the completed MenuKit library. This is the validation phase — if the library is right, the refactors should be clean. If consumer refactors keep hitting gaps, that's feedback that Phase 6 missed something.
Scope
Refactor each mod to use:

New architecture primitives (Panel, SlotGroup, MenuKitSlot, PanelElement subclasses)
New builder API (MenuKitScreenHandler.builder for owned screens)
Phase 6's injection pattern (for mods decorating vanilla screens)
Phase 6's new element primitives (icon button, toggle button, supplier-based content)
SlotGroupLike / HandlerRecognizerRegistry for queries against vanilla screens

Re-enable each mod in dev/build.gradle as its refactor completes.
Sequencing
inventory-plus first. It's the biggest, uses the most MenuKit surface, and will most stress-test the library. If IP refactors cleanly, the library boundary is correct. If IP surfaces gaps, Phase 6 reopens briefly before continuing.
Then the three small mods in any order. They're simpler and will likely go quickly if IP validated the library:

shulker-palette
agreeable-allays
sandboxes

Specific captured items
The IP bug fixes captured in DEFERRED.md (peek region arg, creative ItemPickerMenu dual-menu fix) must be preserved during the IP refactor. These were real bug fixes that existed in working-tree only when Phase 5 closed. The refactor must re-introduce the fixes in the new code, not accidentally re-introduce the bugs.
Flag them explicitly at the start of the IP refactor session.
Working practice
For each mod:

Read the mod's current code to understand what it does
Identify the MenuKit APIs it needs (most will be obvious from the audit findings)
Refactor incrementally — usually file by file or feature by feature
Test in-game after each substantive change
Re-enable in dev/build.gradle once the refactor is complete
Document anything that felt awkward or revealed a library gap

The last item matters. If a refactor feels like it's working against the library rather than with it, that's a signal. Capture the specifics in a running notes document; at the end of Phase 7, review the notes to decide whether Phase 6 needs to reopen.
Scope discipline

Don't "improve" the consumer mods beyond the refactor. If a mod has pre-existing suboptimal design, note it but don't fix it — that's a separate project.
Don't change mod behavior. The refactor is a rebuild against new infrastructure, not a feature revision.
Don't write documentation for the consumer mods during refactor. That's the mods' own README territory, not Phase 8.

Estimated effort
3-5 days for inventory-plus. 1-2 days total for the three small mods. If IP surfaces a Phase 6 reopen, add time for that reopen plus the delay it introduces.
Phase 8: Story, docs, and Cairn
Purpose
Write all documentation. Describe the library as it actually is, not as it was planned to be.
Scope
Three artifacts, written together for consistency:
STORY.md rewrite. The internal canonical architectural document. Should lead with the component-library framing, foreground panels and elements, treat slot groups as one structurally complex element type. Should reflect whatever Phase 6 and 7 surfaced — refined patterns, confirmed or revised framings, architectural decisions made during implementation.
External docs rewrite. GitHub README, Modrinth page, and any other public-facing consumer-facing material. Must be consistent with STORY.md. Typically shorter and more concrete than the story — the README leads with "what this library is and how to use it," not with architectural narrative.
Cairn rewrite. MenuKit's Architectural Knowledge Management. Captures the full architectural knowledge from the whole arc:

The five canonical guarantees and what they mean
The three-layer model (structure / visibility / rendering)
SlotGroupLike and the uniform abstraction
The library-not-platform discipline and how it's maintained
The component-library framing and what distinguishes elements from slot groups
Major decision points resolved during migration (MKMenu deletion, event bus deletion, feature extraction to consumers)
Deferred concerns and how they were resolved
Phase 6 and 7's architectural decisions (new elements, injection pattern, MKHudPanel resolution)

The Cairn rewrite is the highest-value meta-work from this whole arc. The migration produced substantial architectural knowledge that currently lives only in conversation history and STORY.md. Capturing it properly means future work on MenuKit (extensions, new elements, potential v2 refactors) has the decision context available, not just the current state.
Why last
Documentation describes the library as it actually is. Phases 6 and 7 may surface refinements:

A Phase 6 design might reshape something described in STORY.md
Phase 7 consumer refactors might reveal a framing that's cleaner than what's currently believed
The injection pattern, once implemented and consumer-validated, might have specifics that a pre-written document couldn't anticipate

Writing docs at the end means they reflect the stable, validated endpoint. Writing them first means they capture intent, which then has to be revised.
Doing the three artifacts together — rather than staggered — means they stay consistent. The framing in STORY.md is the same framing leading the README, which is the same framing captured in Cairn. Writing them separately would mean re-loading the same framing context three times.
Sequencing within Phase 8

Read the current STORY.md (the pre-Phase-8 version — whatever exists at Phase 8's start)
Read Phase 6 and 7 reports to load all decisions made during implementation
Draft STORY.md rewrite
Review and iterate
Draft README rewrite
Draft Modrinth page rewrite (often similar to README but adapted for Modrinth's format)
Review external docs for consistency with STORY.md
Begin Cairn rewrite, using the drafted STORY.md and external docs as source material
Review Cairn, ensure it captures decision history and patterns, not just current state

Scope discipline

Don't change code during Phase 8. Documentation-only phase.
Don't add features based on "now that I'm writing docs, I realize we need X." Capture as a future-work item; don't implement.
Don't rewrite docs that exist and are already accurate. Focus on what needs to change.

Estimated effort
3-5 days. STORY.md is probably a day or two. External docs a day. Cairn a day or two depending on how much architectural history is being captured.
Meta-practices across all three phases
Resume fresh. Each phase is substantial work. Resume with rest between phases, not momentum from the previous phase.
Design doc before code in Phase 6. Implementation without design produces incoherent APIs.
In-game testing as you go in Phases 6 and 7. Both phases touch code that users will see; visual and interactive testing matters more than unit-test-style verification.
Honest reporting at phase boundaries. Each phase ends with a report in the format established during Phases 1-5: what was done, deviations from plan, completion test results with concrete evidence, surprises, decisions not in plan, outstanding concerns.
Scope discipline as a first-class concern. Each phase has specific "things to avoid" sections. Treat them as constraints, not suggestions. Cleanup and feature work are magnets for scope creep.
Reopen prior phases if needed. If Phase 7 surfaces that Phase 6 missed something, reopening Phase 6 briefly is correct. If Phase 8 writing surfaces that something in Phase 6's architecture is wrong, that's a Phase 6 reopening, not a documentation workaround. The phases are sequential but not one-way.