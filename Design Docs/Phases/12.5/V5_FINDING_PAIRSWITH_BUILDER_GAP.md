# V5 finding — `PanelBuilder.pairsWith` missing

**Surfaced during:** V5.1 scaffold (Stage B pre-build read).
**Status:** **RESOLVED in-phase.** Advisor disposition: fix in-phase per M7 precedent — routing machinery already shipped, deferring would contaminate Phase 13 consumers. Fix committed alongside this finding update; V5.1 scaffold proceeds as originally designed.

---

## Observation

`MenuKitScreenHandler` ships the full machinery for directional slot-group pairing:

- `SlotGroup.pairsWith(SlotGroup target)` — live mutation method on the group (line ~222 of `SlotGroup.java`).
- `SlotGroup.getPairedWith()` — accessor consumed by `quickMoveStack` at line 269.
- `GroupConfig.pairingTargets : List<String>` — config-layer field in the PanelBuilder's intermediate record (line 711).
- Build-time resolution loop — lines 393–402 of `MenuKitScreenHandler.java` — iterates `pairingTargets` and calls `source.pairsWith(target)` for each.
- `quickMoveStack` Layer 1 sort — paired targets sort ahead of all other candidates regardless of priority.

**Missing:** There is no `PanelBuilder` method that populates `GroupConfig.pairingTargets`. The only constructor path through `PanelBuilder.group(...)` uses the GroupConfig convenience constructor at line 714, which passes `List.of()` for `pairingTargets`. Consumers have no public API path to declare pairings through the builder.

## What this blocks

V5.1's primary automated check — DESIGN.md §V5 spec "Native MenuKit handler with two slot groups — pickup / place / shift-click between groups routes per `shiftClickPriority` + `pairedWith`" — can't exercise the `pairedWith` half of the routing contract through the builder API. A consumer wanting pair-based directional routing today would have to reach past the builder into `SlotGroup.pairsWith(target)` on the live handler post-build — a shape the library's otherwise-declarative builder surface doesn't invite.

The routing algorithm works — it's been there since Phase 12's handler primitives. The exposure is the gap.

## Suggested primitive-level fix

Purely additive `PanelBuilder.pairsWith(String targetPanelId, String targetGroupId)` method, modeled on the existing `rightClick(handler)` helper (lines 491–505). One method, ~8 lines:

```java
/**
 * Declares a directional pairing from the last-added group to a target group
 * identified by "{panelId}.{groupId}". Target groups sort first in shift-click
 * routing (Layer 1 in quickMoveStack), overriding priority-based ordering.
 */
public PanelBuilder pairsWith(String targetPanelId, String targetGroupId) {
    if (!groups.isEmpty()) {
        GroupConfig last = groups.remove(groups.size() - 1);
        List<String> newTargets = new ArrayList<>(last.pairingTargets);
        newTargets.add(targetPanelId + "." + targetGroupId);
        groups.add(new GroupConfig(last.id, last.storage, last.policy,
                last.qmp, last.priority, last.columns, last.rowGapAfter,
                last.rowGapSize, newTargets, last.rightClickHandler));
    }
    return this;
}
```

Mechanical exposure of an already-implemented primitive. No new semantics, no new architectural decisions. Rule-of-one: the behavior is designed, tested in routing, and missing only its public entry-point.

Scope-shape classification: **primitive-scope-shaped** (library-surface exposure gap), not consumer-policy-shaped. Phase 13 consumers wanting pair-based routing would hit this gap.

## Advisor question

M7 precedent applies here — a gap discovered during V4.2 scaffold, fixed in-phase with its own design doc + implementation pass. Two paths:

1. **Fix in-phase.** ~8 lines added to `PanelBuilder`; one commit; V5.1 scaffold proceeds as originally designed. No separate design doc (machinery already designed and shipped; this is exposure only). V5.1a pair-routing check runs as spec'd.
2. **Defer.** Mark V5.1a pair-routing portion DEFERRED; V5.1 scaffold runs with three groups testing `shiftClickPriority` priority ordering + `InteractionPolicy.input` predicate gating + `QuickMoveParticipation` direction gating (the other three primitives named in DESIGN.md §V5). Pair-routing rescoped into a future Phase 13 primitive-exposure pass.

My read favors path 1: fix is trivial and mechanical, no design trade-off to adjudicate, routing already-correct just needs exposure. Same shape as M7 where the finding's resolution was "add the missing primitive" not "redesign."

## Downstream scope

If path 1: after the `PanelBuilder.pairsWith` commit, V5.1 proceeds as originally designed. V5.3 (right-click) and V5.6/V5.7 are not affected; their primitives (`rightClick`, shared-constants pattern, M8 context machinery) all have public entry points confirmed during Stage B pre-checks.

If path 2: V5.1's test plan rescopes to 3 sub-checks (priority, policy filter, Qmp) instead of 4; V5.1 otherwise proceeds.

---

## Resolution

**Shipped.** `PanelBuilder.pairsWith(String targetPanelId, String targetGroupId)` added immediately after the existing `rightClick(handler)` method in `MenuKitScreenHandler.java`. Implementation matches the pattern verbatim — replaces the last group config with one that appends to its pairing-targets list; build-time resolution loop picks the entries up and wires the live `SlotGroup.pairsWith()` relationships before the handler is constructed. Purely additive; no existing consumer behavior affected.

No design-doc amendment. Pairing semantics were already documented in `quickMoveStack`'s javadoc (Layer 1 directional pairing) and `SlotGroup.pairsWith(SlotGroup)`; this fix exposes the existing semantics through the declarative builder surface, nothing more.

## Pattern note

Fourth primitive gap caught during Phase 12.5 (preceded by V4's `ScreenPanelAdapter` completeness, V2's tooltip layering, and M7's chrome awareness). All four share a shape: **the validator-consumer pattern exercises primitives through realistic usage and surfaces library incompleteness that isolated primitive tests miss.** Worth naming in the Phase 12.5 close-out REPORT as the phase's highest-leverage finding.
