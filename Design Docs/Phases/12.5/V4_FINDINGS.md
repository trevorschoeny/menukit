# V4 Findings — primitive gaps surfaced during V4 scenario implementation

Phase 12.5 V4 — native screen lifecycle + cross-context reuse — built out three of four contexts cleanly (V4.1 lifecycle, V4.2 HUD, V4.2 standalone). The fourth, **V4.2 inventory-context via `ScreenPanelAdapter`**, surfaced four primitive gaps that could not be resolved in consumer code without violating the "library, not platform" discipline (CLAUDE.md § Architectural Discipline First, Functionality Second).

Per Phase 12.5 § 7 (bug-handling discipline) + the broader principle: these are **primitive-scope-shaped** gaps, to be surfaced for advisor review rather than papered over in consumer code. V4.2 inventory is **deferred**; the other three V4 contexts shipped.

This document names the gaps so the advisor can decide between (a) extending `ScreenPanelAdapter` in-phase as additive primitives, or (b) filing as a mechanism for a future phase.

---

## Gap 1 — `ScreenPanelAdapter` does not render panel backgrounds

**Observed.** Constructing `new ScreenPanelAdapter(Panel, InventoryRegion)` with a panel whose `PanelStyle` is `RAISED` (or `INSET`/`DARK`) renders the panel's elements with **no background behind them**. The adapter's javadoc confirms this is intentional: "The adapter renders elements only."

**Why it's a gap.** The other two rendering contexts handle background rendering automatically:

- `MKHudPanel` passes `PanelStyle` through to `MenuKitHudRenderer`, which paints the background.
- `MenuKitScreen` paints the panel background inline in `render()`.

`ScreenPanelAdapter` delegates this to the consumer. That forces every injection consumer to either:

1. Call `PanelRendering.renderPanel(graphics, originX, originY, width, height, style)` themselves, **and** re-derive the origin via `RegionMath.resolveInventory(...)` (duplicating math the adapter does internally, leaking library internals).
2. Restrict themselves to `PanelStyle.NONE` and rely on elements to self-style.

Option 1 is a consumer-side rediscovery of the adapter's own origin function. Option 2 loses context parity — a panel injected into an inventory looks visually different from the same panel in a HUD or standalone screen.

**Suggested primitive.** An additive `ScreenPanelAdapter.renderBackground(graphics, screenBounds)` helper, or an internal background pass inside the existing `render(...)`, gated on `panel.getStyle() != NONE`.

**Design-axis question for advisor.** Is the scope-refusal ("adapter renders elements only") a deliberate narrow-scope decision that consumers should accept, or is it an oversight from Phase 10 when the injection pattern first landed? If deliberate, what's the intended idiom for consumers wanting a styled background — re-derive origin, or skip the style?

---

## Gap 2 — `ScreenPanelAdapter` has no content-padding support

**Observed.** Elements declared at `childX = 0` render flush with the adapter's origin — which, after the origin function resolves, is the panel's logical top-left. There is no equivalent of `MKHudPanel.padding(int)` or `MenuKitScreen`'s baked-in `PANEL_PADDING = 7`.

**Why it's a gap.** The three rendering contexts disagree on where `childX = 0` is relative to the panel's visible edge:

| Context | Content origin offset from panel edge |
|---|---|
| `MenuKitScreen` (standalone) | 7 (baked-in `PANEL_PADDING`) |
| `MKHudPanel` (HUD) | `builder.padding(N)` — consumer choice, rendered with background |
| `ScreenPanelAdapter` (inventory) | 0 — elements flush against the styled background edge |

This is the kind of unevenness THESIS § 5 (context-agnostic elements, context-specific containers) is supposed to prevent: the same element instance should render identically in each context. With this gap, it does not — an identical element at `childX = 0` looks centered in HUD/standalone but crammed against the background edge in adapter-injected inventory.

**Suggested primitive.** `ScreenPanelAdapter.padding(int)` builder option, consumed by the adapter when computing the render context's content origin. Analogous to `MKHudPanel.padding`.

**Design-axis question for advisor.** Is the current zero-padding the library's considered position (elements supply their own padding via `childX` / `childY`), or is it an omission that contradicts the HUD + standalone defaults? If the former, the `MKHudPanel.padding()` API is inconsistent and one of the two should change.

---

## Gap 3 — `RegionMath.resolveInventory` silently returns `OUT_OF_REGION`

**Observed.** V4.2's cross-context panel is 180 px wide (progress bar 160 + childX 20). `InventoryRegion.TOP_ALIGN_RIGHT` flows horizontally with overflow gated on the inventory's `imageWidth = 176`. `180 > 176` → resolver returns `Optional.empty()` → adapter converts to `ScreenOrigin.OUT_OF_REGION` → `render` short-circuits. **No log line, no warning, no visible output.** From the consumer's point of view, the panel simply doesn't render; there is no signal why.

**Why it's a gap.** Silent failure in a diagnostic-unfriendly API is expensive. A consumer hitting this with no log spends meaningful time re-checking their visibility supplier, their `showWhen` predicate, their Fabric `ScreenEvents` registration, their mixin wiring — everything except the one actual cause.

**Suggested primitive.** A one-time `LOGGER.warn` in `RegionMath.resolveInventory` / `resolveHud` (or in `ScreenPanelAdapter.render`) on the first `OUT_OF_REGION` per panel + region pair. Message names the panel, the region, the panel's extent, and the region's capacity.

**Design-axis question for advisor.** `OUT_OF_REGION` is a valid outcome (region's axial budget exceeded). The question is only whether it should be silent. A one-shot warn per panel costs ~negligible runtime and would close a sharp consumer-experience gap.

---

## Gap 4 — Panel-origin math is not exposed for consumer reuse

**Observed.** If a consumer wants to paint something alongside the adapter's element area — a background, a hover highlight, a tooltip anchor — they need to know where the adapter will paint. The adapter's origin is computed inside `ScreenPanelAdapter.render(...)` from its private `originFn`. The consumer can call `RegionMath.resolveInventory(...)` themselves to recompute, but this duplicates the computation and leaks the library's region-math internals into the consumer's module.

**Why it's a gap.** This is an indirect consequence of Gap 1 (consumer needs to render background → consumer needs to know origin → consumer re-derives it). If Gap 1 is closed by `ScreenPanelAdapter.renderBackground`, this gap partly closes too. But there are other consumer use cases (tooltip positioning, hover overlay, sibling decoration) where origin access is independently useful.

**Suggested primitive.** `ScreenPanelAdapter.getOrigin(screenBounds)` returning `Optional<ScreenOrigin>`. Read-only accessor that invokes the internal origin function. Pure and additive.

**Design-axis question for advisor.** Is the current hiding of origin deliberate — "consumers should not care where the adapter paints, only that it does"? If yes, does that position survive Gap 1's resolution (the moment `renderBackground` exists, so does an implicit public position)?

---

## Proposed dispositions

Per Phase 12.5 § 7, primitive-scope-shaped gaps resolve via advisor + design-doc amendment + implementation **in-phase**, OR get filed as a mechanism candidate for Phase 13+. The four gaps above cluster tightly around `ScreenPanelAdapter` completeness:

- **Disposition A (in-phase extension).** Add `.padding(int)`, `.renderBackground(graphics, screenBounds)`, `.getOrigin(screenBounds)` to `ScreenPanelAdapter`. Add a one-shot warn log to `RegionMath.resolve*`. Advisor review → M5 design-doc amendment documenting the new adapter surface → implementation → re-run V4.2 inventory in V4 follow-up commit.
- **Disposition B (defer as mechanism).** File as `M7 — ScreenPanelAdapter completeness` or similar, with these four gaps as its scope. Phase 12.5 closes with V4.2 inventory scenario marked deferred (7 of 8 sub-scenarios shipped). Mechanism lands in Phase 13 alongside the consumer migrations that need it.

Disposition A is cheaper if done now because the gaps are concrete, narrow, and purely additive (lower risk profile than M3 scope-down). Disposition B is cleaner phase-boundary hygiene.

Advisor's call.

---

## What V4 shipped in the interim

- **V4.1 lifecycle** (`/mkverify v4`) — five panels exercising every `PanelPosition.Mode`. 2/2 automated checks pass (constraint coverage + V4.2a element-factory parity). Visual inspection: 5 panels render at expected positions (center off-center per a separate finding about `imageWidth/imageHeight` counting BODY panels only — noted in Phase 12.5 palette-gap roll-up).
- **V4.2 HUD** (`/mkverify v4 cross hud`) — `MKHudPanel` with `.autoSize()`, region `TOP_RIGHT`, visibility tied to `V4State.hudVisible`. Counter ticks via `ClientTickEvents.END_CLIENT_TICK` (monotonic 20 Hz).
- **V4.2 standalone** (`/mkverify v4 cross standalone`) — `MenuKitScreen` subclass opened via S2C marker packet. Fixed a latent library bug along the way: `MenuKitScreen.render` called `renderBackground` explicitly AND via `super.render`, tripping the `Can only blur once per frame` check in 1.21.x. Fix landed in the same commit as V4 (first consumer of `MenuKitScreen` surfaced it).
- **V4.2 inventory** — DEFERRED, this document.

Visual parity goal: HUD + standalone render the same `V4State.counter` value via supplier-driven suppliers. Confirmed in-game. The inventory context's share of the parity demo is deferred until the primitives above land.
