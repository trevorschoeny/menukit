# V5.7 pre-check outcomes

Pre-checks run before scaffolding V5.7 (M4 × M1 × M8 seam). Results below; scaffolding paused pending advisor decision on pre-check 2.

---

## Pre-check 1 — `SlotIdentity` stability on M4-grafted slots

**Outcome: PASS.** Identity is stable.

**Mechanism** (verified in [SlotIdentity.java:92-94](menukit/src/main/java/com/trevorschoeny/menukit/core/SlotIdentity.java)):

```java
public static SlotIdentity of(Slot slot) {
    return new SlotIdentity(slot.container, slot.getContainerSlot());
}
```

Identity = `(container reference, slot.getContainerSlot())`. Neither component depends on position in `menu.slots`.

**For V5.6's grafted slot in CraftingMenu:** `(V5GraftedStorage.CONTAINER, 0)` — stable for the JVM session regardless of how many slots sit before it in `menu.slots`. Another mod grafting a slot earlier in the list would shift menu-slot-indices (46 → 47, etc.) but would not change `(container, containerSlot)`.

**Session-scope caveat** ([SlotIdentity.java:44-48](menukit/src/main/java/com/trevorschoeny/menukit/core/SlotIdentity.java)): container references die with the JVM. Cross-session persistence uses `PersistentContainerKey` on a natural per-world owner (player UUID, block position + dimension), not `SlotIdentity` directly. V5.7's Gate B (item survives disconnect/reconnect) needs the grafted-slot storage backed by a `PersistentContainerKey` — most naturally `BlockEntityKey(furnacePos, dimension)` since FurnaceMenu is BE-anchored.

**Implication for scaffolding:** SlotIdentity is sufficient for within-session identity. No primitive gap at this layer.

---

## Pre-check 2 — `SlotGroupCategories` resolver visibility on M4-grafted slots

**Outcome: PRIMITIVE GAP. Design intent is option (b) — consumer-declared category — but the mechanism is incomplete for vanilla menus.**

### Design intent (per M8 doc §5.3 + §10)

[M8_FOUR_CONTEXT_MODEL.md:560](menukit/Design Docs/Phase 12.5/M8_FOUR_CONTEXT_MODEL.md:560):

> **Grafted-slot backdrop panels (M4 F8/F15 consumers) migrate to SlotGroupContext.** This is the natural home for them — a grafted-slot group tagged with a consumer-registered category, decorated via `SlotGroupPanelAdapter`.

Advisor's option (b) confirmed: library resolvers intentionally exclude grafted slots; consumers declare their own category for their grafted slot group.

Current library resolvers exclude grafted slots explicitly. [VanillaSlotGroupResolvers.java:384](menukit/src/main/java/com/trevorschoeny/menukit/inject/VanillaSlotGroupResolvers.java:384):

> Index 46 and beyond may include mod-grafted slots and the destroy slot — not named categories.

### The gap — consumers can't declare categories on vanilla menu classes

Public API is [SlotGroupCategories.register(Class, SlotGroupResolver)](menukit/src/main/java/com/trevorschoeny/menukit/inject/SlotGroupCategories.java:60), first-registration-wins. Vanilla menu classes (FurnaceMenu, CraftingMenu, InventoryMenu, etc.) all have library-registered resolvers already. A consumer calling `SlotGroupCategories.register(FurnaceMenu.class, ...)` hits first-wins — library resolver stays, consumer registration is a no-op with a warning log.

There is no extension API. No `extend(Class, SlotGroupResolver)`, no `registerGraftedSlot(Class, Category, Supplier)`, no merge-mode on `register`. The design intent ("consumer-registered category") is expressible *only* for consumer-owned menu classes — which V5.7 is not, since it grafts into vanilla's FurnaceMenu.

**V5.6 sidestepped this.** V5.6 used the lambda-path `ScreenPanelAdapter(Panel, ScreenOriginFn, padding)` — MenuContext, not SlotGroupContext. Works because MenuContext positions via `ScreenOriginFn` reading frame bounds directly; no category resolver involved. V5.6 explicitly notes this at [V5GraftedDecoration.java:32-35](validator/src/main/java/com/trevorschoeny/mkvalidator/scenarios/v5/V5GraftedDecoration.java:32):

> Region-based adapters couldn't express this pattern: the backdrop's coordinates are anchored to a foreign system (vanilla's Slot coords), not composable as a region-stacked decoration. §5.6 explicitly names lambda-based adapters as the only supported shape for grafted-slot backdrops.

But V5.7 Gate C (SlotGroupContext decoration fires on the grafted slot) inherently needs the category-resolver path. There's no lambda-path shortcut for SlotGroupContext — `SlotGroupPanelAdapter` only takes `(Panel, SlotGroupRegion)` and `.on(SlotGroupCategory...)`.

### Four plausible resolutions

| Option | Shape | Cost | Fit |
|---|---|---|---|
| α | Change `register` to merge-resolvers (first-reg-wins → additive, both resolvers' outputs merged per call) | Behavior change to existing API; may surprise consumers | Matches MenuChrome-parity break |
| β | Add `SlotGroupCategories.extend(class, resolver)` — explicit extension, runs after the registered resolver | Additive API; clean semantics | Clear intent at call site |
| γ | Add `SlotGroupCategories.registerGraftedSlot(class, category, slotSupplier)` — dedicated M4-specific helper | Additive; narrower than β; case-specific | Couples M4 and M8 explicitly |
| δ | Scope V5.7 down — skip Gate C, use lambda-path MenuContext decoration like V5.6; file SlotGroupContext-for-grafted-slots as deferred primitive | Zero library change now | Keeps V5.7 shippable; loses seam signal for M4 × M8 |

**My pull:** option β. Explicit `extend(...)` reads as "consumer adds to library-shipped categories" at the call site, which is exactly the V5.6/V5.7 pattern. Avoids breaking existing first-wins semantics of `register`. Narrower than α (no ambiguity on merge semantics for ALL consumers), broader than γ (future non-M4 extension cases covered). Phase 11 "Defer, don't work around" argues against δ — the primitive-gap fix is ~20 lines and high-confidence.

But this is an architectural call, not tactical. Advisor should pick.

### Principle-11 exhaustive-coverage angle

V5.6 + V5.7 are the two M4 scenarios in Phase 12.5. V5.6 proved the lambda-path works for MenuContext-anchored backdrops. V5.7's purpose is specifically to test the SlotGroupContext path, which the M8 design doc explicitly names as "the natural home" for grafted-slot decorations. Punting V5.7 to δ means Phase 12.5 never empirically validates the M8 claim — the migration guidance at §10 line 560 is asserted but unverified.

---

## What unblocks V5.7 scaffolding

Advisor decision on pre-check 2 — α, β, γ, or δ. Options α/β/γ require a library-side commit first; the scenario scaffolds cleanly after. Option δ lets V5.7 scaffold immediately but leaves the M8 claim unverified and files a deferred primitive.

Pre-check 1 is clean either way.

Gate D (decoration reflects persistent state during first-frame pre-sync) is observable during scaffolding — no pre-check needed. Gate A and Gate B inherit V5.6's pattern. All three are green-or-fail-fast during implementation.

---

## Close-out REPORT note — gap #6 of Phase 12.5, novel shape

The prior five primitive gaps (V4 `ScreenPanelAdapter` completeness, V2 tooltip layering, M7 chrome awareness, V5.1 `PanelBuilder.pairsWith` exposure, V5.6 recipe-book render-pipeline coverage) all surfaced during **scenario execution** — the validator mod ran a scenario, something broke or rendered silent-nothing, and the gap became visible.

This one (β extension-resolver API) surfaced during **pre-check reading** — before any V5.7 code was written, the M8 design doc's "natural home" claim (§10.560) was traced to `SlotGroupCategories.register`'s first-wins semantics and found expressible only for consumer-owned menu classes. No scaffolding, no runtime evidence — just design-doc-to-source verification.

Worth a separate bullet in the Phase 12.5 close-out REPORT: **pre-check discipline surfaces primitive gaps before scaffolding, not just during execution**. Principle 7 evidence in a new form — the validator-consumer pattern catches incompleteness not only through realistic usage but through realistic *reading* of the design claims the library invites consumers to act on.
