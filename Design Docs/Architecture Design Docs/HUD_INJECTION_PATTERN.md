# HUD Injection Pattern

**Phase 10 deliverable.** How consumer mods put MenuKit elements onto the in-game HUD with predicate-based visibility — *e.g.*, agreeable-allays's action-hint text, inventory-plus's pockets HUD overlay.

---

## Scope

Phase 10's audit surfaced this as Pattern 5 of the five-pattern decomposition. It covers the case where a consumer wants render-only UI to appear on the game HUD while the player is actively playing, without opening a screen, with visibility gated on runtime state (block in hand, ability armed, controller connected).

Out of scope:

- **Decorating vanilla HUD elements specifically** — *e.g.*, drawing on top of vanilla's hotbar slot, replacing vanilla's experience bar. Consumers needing this write their own mixin into `Gui` or the relevant vanilla HUD class and compose MenuKit elements inside, same library-not-platform discipline as the other injection patterns. MenuKit ships no `Gui` mixins.
- **Interactive HUD elements during gameplay.** HUD context is render-only by THESIS principle. Consumers wanting click-driven interaction during gameplay open a standalone screen with a keybind, *not* a HUD overlay. See `CONTEXTS.md` § HUDs for the rationale.

---

## Principle

Same library-not-platform position as the other injection patterns. The full discussion lives in `INVENTORY_INJECTION_PATTERN.md`.

For HUDs specifically, the principle has an additional consequence: MenuKit registers its own HUD render callback (via Fabric's `HudRenderCallback` or equivalent) for panels declared via `MKHudPanel.builder()`, but does not attempt to mediate render order between consumer mods, does not provide a "HUD layer" abstraction, and does not own the HUD render pipeline. Multiple mods registering HUD callbacks coexist via Fabric's normal callback chain.

---

## The pattern shape

Already shipped. Use `MKHudPanel.builder()` with `.showWhen(Supplier<Boolean>)` to declare a panel whose visibility flips with consumer-owned state. Register at mod init.

```java
public class MyMod implements ClientModInitializer {
    private static volatile boolean hintActive = false;

    @Override
    public void onInitializeClient() {
        MKHudPanel.builder("my_action_hint")
            .anchor(HudAnchor.BOTTOM_CENTER)
            .offset(0, -32)
            .padding(4)
            .style(PanelStyle.DARK)
            .showWhen(() -> hintActive)
            .text(() -> Component.literal("[Right-click] commit"))
            .build();

        // Consumer flips `hintActive` from wherever the trigger comes from —
        // ItemUseCallback, KeyBindingHelper, ClientTickEvents, S2C packet, etc.
    }
}
```

Key properties:

- **Inertness.** When `showWhen` evaluates false, the panel does not tick its suppliers, does not contribute to layout, does not emit render calls. Costs are bounded by the predicate evaluation itself.
- **Supplier-driven dynamic content.** Text strings, item stacks, progress values, icon identities all come from `Supplier<T>` fields evaluated at render time. Consumers update their own state; the panel re-reads each frame.
- **Screen-edge anchoring + offset.** Nine-position anchor enum (top-left through bottom-right) + pixel offset inward from the anchor. Resolved per-frame against the current GUI-scaled screen size — handles resizes correctly with no resize listener required.

The full HUD subsystem API surface (anchors, padding, style, element catalogue, notification subsystem) lives in the existing `MKHudPanel.builder()` documentation. This pattern doc only addresses the injection-specific aspect: predicate-gated visibility for runtime state.

---

## Positioning relative to vanilla HUD elements

Consumers anchoring HUD panels near vanilla HUD elements (hotbar, experience bar, food bar, chat) should pick anchors and offsets that don't overlap. The library does not attempt to lay out HUD panels around vanilla elements automatically — the consumer chooses the anchor and offset, and accepts responsibility for the overlap question.

A few practical anchors observed in the audit:

- `BOTTOM_CENTER` with negative Y offset — sits above the hotbar (where vanilla shows item names and the "press R to open recipe book" hint). Used by agreeable-allays for action hints. Consumers should choose offsets large enough to clear vanilla's hotbar overlay region (~40px above hotbar).
- `TOP_RIGHT` with small offsets — sits in the area vanilla uses for boss bars and overlay messages. Less reliably clear; consumers using this should accept that vanilla content may overlap.
- `MIDDLE_LEFT` / `MIDDLE_RIGHT` — typically clear of vanilla HUD content, good for status indicators.

Consumers wanting precise per-vanilla-element positioning (*e.g.*, "above the experience bar specifically") have to compute coordinates from the vanilla HUD layout themselves — vanilla's layout is not exposed as a public coordinate API, so consumers either hardcode known offsets per Minecraft version or mixin into `Gui` to read live values.

---

## What does NOT ship

Consistent with the inventory-menu and standalone docs:

- **No `Gui` mixins.** MenuKit does not modify vanilla's HUD render pipeline. It registers its own callback alongside vanilla's; it does not wrap, replace, or filter vanilla's draws.
- **No HUD layout manager.** Consumers position panels with anchor + offset; if multiple panels overlap, that's between the consumer mods. MenuKit does not arbitrate.
- **No interactive HUD widgets.** No hover, no click, no drag. THESIS principle: the HUD context is render-only. Consumers wanting interaction open a standalone screen.
- **No defaults.** MenuKit ships zero out-of-box HUD panels.

---

## Failure modes

The injection-specific failure modes documented in `INVENTORY_INJECTION_PATTERN.md` mostly do not apply here — there is no consumer mixin into a vanilla screen, no `@Shadow` of inherited fields, no class-load rule (consumers just call `MKHudPanel.builder()` from `onInitializeClient`).

The one mode that does carry over: **predicate-driven visibility means the supplier runs every frame the panel is potentially visible.** Heavy work in the supplier (item lookups, network calls, complex state checks) compounds across frames. Consumers should keep the predicate cheap; if it depends on expensive state, cache the result on a tick callback and read the cache in the supplier.

---

## Status

Already works as of pre-Phase-10 architecture. `MKHudPanel.builder().showWhen(...)` shipped as part of the HUD subsystem in earlier phases; Phase 10's contribution is documenting it as the canonical answer for the "HUD with runtime visibility" pattern surfaced by the audit. No new library-side primitives required.

Phase 11 consumer refactors will validate by porting agreeable-allays's action hint and inventory-plus's pockets HUD to this pattern.
