# M5 — Context-Scoped Region System

**Phase 12 mechanism — layout-shaped** (per `Phase 11/POST_PHASE_11.md`).

**Status: Resolved — ready for implementation, pending F9 UI-structure clarification for §7.**

**Enables:** Phase 13a migrations (settings gear, sandboxes buttons, pocket-panel for F9 once UI clarified), and future cross-mod panel placement without coordinate-level coordination. **Grafted-slot backdrops (F8 equipment, F15 peek, and any grafted-slot visual layer) do not use regions** — see §4A and §11.

**Companion doc:** `M5_REGION_SPECS.md` — authoritative specification of the 25 region names, anchors, and flow directions. This doc is the HOW; the specs doc is the WHAT.

---

## 1. Purpose

Consumers today position decoration panels by hand-crafted screen-relative coordinates. Two mods wanting adjacent space in the same inventory corner (e.g., IP's settings gear and sandboxes' sandbox-button) coordinate via magic offsets ("13px left of IP's gear"). As more consumers want inventory-screen real estate — and as F8/F9 add equipment + pockets panels that also need placement — this does not scale.

M5 introduces **named regions**. A consumer declares a panel's region; the library resolves each panel's concrete position based on the region's anchor, flow direction, stacking order with other panels in that region, and the current anchor frame (menu bounds, screen bounds, or main panel bounds depending on context). Consumers never hardcode inter-mod coordinate offsets.

M5 is **additive**. Manual `ScreenOriginFn` lambdas, direct `MKHudAnchor` anchoring, and fixed-coordinate positioning still work. Consumers opting out of regions opt out of the arbitration — intentional, for panels tied to vanilla screen geometry (e.g., the shulker palette toggle above the shulker grid, which is not an outside-the-frame position any region models).

---

## 2. Scope — locked by the specs doc

- **Inventory context** — 8 regions, anchored to a vanilla menu's container frame. Naming convention: `SIDE_ALIGN_END`.
- **HUD context** — 9 regions, anchored to screen edges and the crosshair. Naming convention: positional names.
- **Standalone screen context** — 8 regions, anchored to a MenuKit-native screen's main panel. Same 8 names as inventory.

25 total regions across 3 contexts. V1 scope: registration-order stacking, 2px default gap, cutoff overflow. No priority, no user override.

The specs doc describes anchors and flow directions per region; this doc describes the implementation that realizes them.

---

## 3. Design decisions

Each decision below is **resolved per advisor review** and locked for v1 implementation. Divergence during implementation requires a follow-up review.

### 3.1 Enum shape — three per-context enums

```java
public enum InventoryRegion {
    LEFT_ALIGN_TOP, LEFT_ALIGN_BOTTOM,
    RIGHT_ALIGN_TOP, RIGHT_ALIGN_BOTTOM,
    TOP_ALIGN_LEFT, TOP_ALIGN_RIGHT,
    BOTTOM_ALIGN_LEFT, BOTTOM_ALIGN_RIGHT
}

public enum HudRegion {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    LEFT_CENTER, RIGHT_CENTER,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
    CENTER   // below crosshair, centered, flows down
}

public enum StandaloneRegion {
    LEFT_ALIGN_TOP, LEFT_ALIGN_BOTTOM,
    RIGHT_ALIGN_TOP, RIGHT_ALIGN_BOTTOM,
    TOP_ALIGN_LEFT, TOP_ALIGN_RIGHT,
    BOTTOM_ALIGN_LEFT, BOTTOM_ALIGN_RIGHT
}
```

**Rationale (matches specs doc lean):** Per-context enums give type safety — the compiler rejects passing a HudRegion to an inventory adapter. The naming conventions differ (SIDE_ALIGN_END vs positional), so a single combined enum would be awkward.

**Trade-off:** InventoryRegion and StandaloneRegion have identical 8 names. The duplication is intentional — the types are distinct because the adapter they're passed to expects one specific type. A consumer cannot accidentally register a standalone-screen panel into an inventory adapter.

**Alternative considered and rejected:** Collapse InventoryRegion + StandaloneRegion into a single `FrameRegion` enum with 8 values, routing through context based on which API receives it. Smaller type surface, but loses the compile-time context distinction — a consumer could accidentally pass a standalone-screen region to an inventory adapter with no compile-time warning. The ~20 lines of enum duplication is a fair trade for that safety. See §10 decision 1.

### 3.2 Registration + stacking — construction-order, auto-registered

Consumers register a panel into a region by constructing an adapter/builder that names the region. Registration is a side effect of construction; no separate `register()` call is needed.

```java
// Inventory context
private static final ScreenPanelAdapter ADAPTER = new ScreenPanelAdapter(
        PANEL, InventoryRegion.RIGHT_ALIGN_TOP);

// HUD context
MKHudPanel.builder("pocket_hud")
        .region(HudRegion.BOTTOM_CENTER)
        .style(PanelStyle.NONE)
        .build();

// Standalone context (MenuKitScreenHandler builder)
builder.panel("settings", p -> p
        .region(StandaloneRegion.TOP_ALIGN_RIGHT)
        .icon(...)
        ...);
```

**Registration ordering.** Stacking order = registration order. To make this deterministic across sessions, consumers are expected to touch their decoration classes from mod init in a stable order. Library docs will call this out: "If two mods register panels into the same region, order depends on which mod-init runs first — use `fabric.mod.json` `depends` relationships to enforce ordering, or accept arbitrary order if it doesn't matter."

**Live registry.** A `RegionRegistry` (internal to MenuKit) holds:
- `Map<InventoryRegion, List<Panel>>`
- `Map<HudRegion, List<Panel>>`
- `Map<StandaloneRegion, List<Panel>>` (deferred impl — see §3.6)

Adapters register their panel on construction. No deregistration — adapters live for the process lifetime.

### 3.3 Panel size for stacking — auto-size default, `.size(w, h)` opt-in pin

Stacking requires each panel's width and height to compute the next panel's offset. Panel doesn't store dimensions today — they're implicit in the elements.

**Auto-size by default.** Add `Panel.getWidth()` / `getHeight()` that compute the panel's bounding box from its visible elements plus any panel-background extent:

```java
public int getWidth() {
    int elementExtent = elements.stream()
            .filter(PanelElement::isVisible)
            .mapToInt(e -> e.getChildX() + e.getWidth())
            .max().orElse(0);
    // Panel background (when style != NONE) renders to a frame slightly
    // larger than the element extent. Include any declared background
    // padding so the stacking math reflects the visible footprint, not
    // just the element footprint.
    return elementExtent + backgroundPadding();
}
public int getHeight() { /* analogous, using childY + height */ }
```

The `backgroundPadding()` contribution is zero for `PanelStyle.NONE` and matches the style's frame inset otherwise (typically 2–4px depending on style). Exact values during implementation.

**Stacking jitter on dynamic elements.** A Panel whose only visible elements are themselves `showWhen`-gated may report `(0, 0)` for a frame between state transitions. Subsequent panels would shift inward, then shift back once the element appears. For panels with this shape, consumers **pin dimensions explicitly** via a new `.size(int w, int h)` method on the Panel constructor or a post-construction setter:

```java
Panel p = new Panel("pocket_overlay", elements).size(54, 20);
// getWidth() / getHeight() now return 54/20 regardless of element visibility.
```

Pinned size overrides auto-size. This is the explicit escape hatch for panels that can't tolerate stacking jitter.

**Dynamic supplier-driven sizes are not supported in v1.** A panel that needs to grow/shrink per-frame based on state crosses into layout territory M5 does not model. Consumers with this need either stabilize via `.size(w, h)` or opt out of regions entirely.

**MKHudPanel already auto-sizes** via its own `.autoSize()` builder flag; that path continues to work and supersedes the Panel-level computation for HUD panels.

### 3.4 Coordinate computation (inventory context)

Each inventory region is an anchor + flow direction + per-panel gap rule, parameterized by the current `ScreenBounds` and the stacking index of the panel within the region.

**Gap semantics (from specs doc):**
- 2px gap between menu frame edge and first panel.
- 2px gap between stacked panels.
- No gap before first panel along the flow axis (panels align flush with anchor).

**Per-region formulas** (where `bounds` is `ScreenBounds(leftPos, topPos, imageWidth, imageHeight)`, `pw`/`ph` are the current panel's width/height, and `prefix` is the total axial extent of all visible panels registered before this one in the same region, plus one 2px gap per preceding panel):

```
RIGHT_ALIGN_TOP:
    x = leftPos + imageWidth + 2
    y = topPos + prefix
    flow axis: y, direction: down
    prefix contribution per panel: ph + 2

RIGHT_ALIGN_BOTTOM:
    x = leftPos + imageWidth + 2
    y = topPos + imageHeight - ph - prefix
    flow axis: y, direction: up
    prefix contribution per panel: ph + 2

LEFT_ALIGN_TOP:
    x = leftPos - pw - 2
    y = topPos + prefix
    flow axis: y, direction: down
    prefix contribution: ph + 2

LEFT_ALIGN_BOTTOM:
    x = leftPos - pw - 2
    y = topPos + imageHeight - ph - prefix
    flow axis: y, direction: up
    prefix contribution: ph + 2

TOP_ALIGN_LEFT:
    x = leftPos + prefix
    y = topPos - ph - 2
    flow axis: x, direction: right
    prefix contribution: pw + 2

TOP_ALIGN_RIGHT:
    x = leftPos + imageWidth - pw - prefix
    y = topPos - ph - 2
    flow axis: x, direction: left
    prefix contribution: pw + 2

BOTTOM_ALIGN_LEFT:
    x = leftPos + prefix
    y = topPos + imageHeight + 2
    flow axis: x, direction: right
    prefix contribution: pw + 2

BOTTOM_ALIGN_RIGHT:
    x = leftPos + imageWidth - pw - prefix
    y = topPos + imageHeight + 2
    flow axis: x, direction: left
    prefix contribution: pw + 2
```

**Prefix computation** — for the current panel at stacking index `N` with preceding panels `p0..p(N-1)`:

```
prefix = sum over preceding visible panels p_i of:
           (panel_i's axial extent along the flow axis) + 2
```

Hidden panels (isVisible() == false) contribute zero — the stack collapses. When the hidden panel becomes visible again, subsequent panels shift to accommodate.

**Overflow (cutoff).** If `prefix + panel_axial_extent` exceeds the available axial space for the region (menu height for side regions, menu width for top/bottom regions), the panel does not render. Returns an "out of region" sentinel that the adapter checks before calling its render method.

**Frame tracking.** All computations read `bounds` per-frame. When the recipe book opens and the menu shifts right, all panels in all regions shift with it automatically.

### 3.5 Coordinate computation (HUD context)

HUD regions use the screen dimensions instead of a menu frame. Screen dimensions come from `Minecraft.getInstance().getWindow().getGuiScaledWidth()` / `getGuiScaledHeight()`.

**Corner/edge inset:** 4px hardcoded (matches vanilla's F3 debug-overlay convention). Not configurable in v1 — Rule of Three reconsiders if a second consumer needs a different inset. See §10 decision 2.

**Per-region formulas** (where `sw`/`sh` are screen dimensions, `pw`/`ph` are panel dimensions, `prefix` is the per-context preceding-panel offset):

```
TOP_LEFT:
    x = 4, y = 4 + prefix
    flow: down, prefix contribution: ph + 2

TOP_CENTER:
    x = (sw - pw) / 2, y = 4 + prefix
    flow: down, prefix contribution: ph + 2

TOP_RIGHT:
    x = sw - pw - 4, y = 4 + prefix
    flow: down, prefix contribution: ph + 2

LEFT_CENTER:
    x = 4, y = sh/2 + prefix
    flow: down (stack grows downward from center)
    prefix contribution: ph + 2

RIGHT_CENTER:
    x = sw - pw - 4, y = sh/2 + prefix
    flow: down
    prefix contribution: ph + 2

BOTTOM_LEFT:
    x = 4, y = sh - ph - 4 - prefix
    flow: up, prefix contribution: ph + 2

BOTTOM_CENTER:
    x = (sw - pw) / 2, y = sh - ph - 4 - prefix
    flow: up, prefix contribution: ph + 2

BOTTOM_RIGHT:
    x = sw - pw - 4, y = sh - ph - 4 - prefix
    flow: up, prefix contribution: ph + 2

CENTER:
    x = (sw - pw) / 2, y = sh/2 + CENTER_CROSSHAIR_CLEARANCE + prefix
    flow: down, prefix contribution: ph + 2
```

**`CENTER_CROSSHAIR_CLEARANCE` derivation.** Vanilla renders the crosshair as a 15px sprite centered on screen center (half-extent 8px from the middle, rounded). The clearance value is `8 (half crosshair) + 8 (breathing gap) = 16px` as a GUI-scaled constant. Defined as `HudRegion.CENTER_CROSSHAIR_CLEARANCE = 16` with a comment citing the derivation. Implementation must spot-check visual placement at GUI scales 2, 3, and 4 — the value is in scaled pixel units, so it scales with the GUI, which matches the crosshair sprite's scaling. If a scale produces an undesirable overlap, revisit before shipping.

**Relationship to MKHudAnchor.** MKHudAnchor is retained. New HUD panels use `.region(HudRegion.X)`; legacy consumers that still want raw anchor + offset keep `.anchor(MKHudAnchor.X, dx, dy)`. The two are not composable on the same panel — declaring both is a **build-time error** (throw at `.build()`). Migration is a single commit per consumer (delete `.anchor()`, add `.region()`); there is no intentional transitional state where both are set.

Internally, HudRegion resolution computes its own coords (via the formulas above) rather than delegating to MKHudAnchor. The two systems diverge in behavior: MKHudAnchor is pure anchor + consumer-supplied offset (no stacking, no region membership). HudRegion handles stacking and region membership automatically. Keeping them separate avoids surprise interactions.

### 3.6 Coordinate computation (standalone context) — **deferred implementation**

Standalone regions anchor to the main panel (the screen's primary content panel in a `MenuKitScreenHandler` screen). Coordinate formulas mirror inventory regions, substituting the main panel's bounds for `ScreenBounds`.

**Implementation deferral.** Phase 13a does not migrate any standalone-screen consumer. The standalone region specs, enum, and API are reserved in M5 but not implemented. When a consumer materializes (or when MenuKit ships a standalone demo), the implementation is straightforward given the inventory-context scaffolding.

**Reserved API:**

```java
// PanelPosition extension — new mode
public enum Mode { BODY, RIGHT_OF, LEFT_OF, ABOVE, BELOW, IN_REGION }

// Factory
PanelPosition pos = PanelPosition.inRegion(StandaloneRegion.LEFT_ALIGN_TOP);

// Builder wiring (within MenuKitScreenHandler.PanelBuilder)
public PanelBuilder region(StandaloneRegion region) {
    this.position = PanelPosition.inRegion(region);
    return this;
}
```

**Why defer.** Standalone layout today runs inside the handler's own coordinate system. The existing BODY / RIGHT_OF / LEFT_OF / ABOVE / BELOW modes have solver code; IN_REGION adds a new solver path that stacks across a region. Building it without a real consumer risks over-specification. Phase 12 ships the enum + API so Phase 13 can migrate incrementally when needed.

---

## 4A. Architectural distinction — by-value vs by-reference panel composition

Two composition models coexist in MenuKit, and regions only work with one of them. Naming this distinction is load-bearing for several decisions below.

**By-value composition (stackable).** Decoration panels that can move without breaking anything. The settings gear, sandboxes button, pocket-preview HUD — these panels render in isolation; their coordinates have no downstream consumers. Stacking reorganizes them per-frame based on other panels' visibility. Panels flow and re-layout, like CSS flexbox. **Regions model this.**

**By-reference composition (fixed-anchor).** Panels whose coordinates are referenced by some other system. Grafted-slot backdrops are the canonical case: M4 grafted slots get fixed `(x, y)` at `addSlot()` time during `<init>` RETURN and cannot move. A backdrop Panel drawn behind those slots must render at the matching coordinates — the Panel's position is a reference to a foreign key (the slot's `x`/`y`), not a value in a flow. If the backdrop participated in region stacking, it would shift when other panels in the region toggle visibility, while the slots stay put — items render inside Slot bounds (vanilla-owned), backdrop renders at Panel-resolved position (M5-owned), and they drift out of alignment.

**Rule:** anything whose position is referenced by a foreign system (vanilla Slot coords, vanilla HUD element positions, grafted block-entity UI coordinates) is by-reference and does not use regions. Regions are for by-value panels whose coordinates only matter to themselves.

**Consequence for Phase 13:** the F8/F15 visual layers are by-reference (their backdrops trace the slot grid positions declared at M4 graft time). The F9 pocket panel *may* be by-reference or by-value depending on how its UI is structured (see §7 note). The settings gear, sandboxes buttons, pocket HUD are by-value and migrate to regions cleanly.

This framing also explains why dynamic panel size (§3.3 jitter) interacts badly with stacking: stacking assumes the panel's size is a stable value other panels flow around. Dynamic size treats the panel as a moving reference that other panels track. Same seam, different axis.

---

## 4. Integration with existing infrastructure

### 4.1 ScreenPanelAdapter (inventory context)

Add a constructor overload that takes an `InventoryRegion`. The overload internally:
1. Registers `(panel, region)` with `RegionRegistry`.
2. Constructs a region-aware `ScreenOriginFn` that computes the panel's origin each frame based on current bounds + stacking index.

```java
// New overload
public ScreenPanelAdapter(Panel panel, InventoryRegion region) {
    this.panel = panel;
    this.originFn = RegionRegistry.inventoryOriginFn(panel, region);
    RegionRegistry.registerInventory(panel, region);
}
```

Existing `(Panel, ScreenOriginFn)` constructor is untouched. Consumers keep it for non-region cases.

**Rendering contract unchanged.** The adapter's `render(g, bounds, mx, my)` call signature is identical. Consumers that migrate to regions change only the construction — all existing render/mouseClicked plumbing works as-is.

### 4.2 ScreenOriginFns (inventory context)

No changes. Regions are expressed through the adapter constructor, not as a new `ScreenOriginFns.inRegion(...)` factory. Rationale: factory methods that take a Panel feel awkward (the panel's state isn't pure — it has visibility), and adapter-level is the natural registration point anyway.

### 4.3 MKHudPanel.Builder (HUD context)

Add a `.region(HudRegion)` method. Mutually exclusive with `.anchor(...)`.

The current `Builder.anchor` default is `MKHudAnchor.TOP_LEFT` (not null), so "anchor is null" can't distinguish "consumer called `.anchor()`" from "default left in place." Use separate `anchorSet` / `regionSet` booleans to track explicit calls:

```java
// Builder fields
private MKHudAnchor anchor = MKHudAnchor.TOP_LEFT;   // default retained
private int offsetX = 0, offsetY = 0;
private HudRegion region;                             // null unless .region() called
private boolean anchorSet = false;
private boolean regionSet = false;

public Builder anchor(MKHudAnchor anchor, int offsetX, int offsetY) {
    if (regionSet) {
        throw new IllegalStateException(
            "Cannot combine .anchor() with .region(). Pick one.");
    }
    this.anchor = anchor;
    this.offsetX = offsetX;
    this.offsetY = offsetY;
    this.anchorSet = true;
    return this;
}

public Builder region(HudRegion region) {
    if (anchorSet) {
        throw new IllegalStateException(
            "Cannot combine .region() with .anchor(). Pick one.");
    }
    this.region = region;
    this.regionSet = true;
    return this;
}
```

On `.build()`: if `regionSet`, use region-based positioning (registers with `RegionRegistry`, resolves per-frame via `RegionMath.resolveHud`). Else fall through to anchor-based positioning (including the existing TOP_LEFT/(0,0) default for consumers that call neither — preserving backwards compat for any existing or external consumer that relies on the default).

**Alternative considered (stricter):** `PositioningMode { UNSET, ANCHOR, REGION }` starting UNSET; build() fails if still UNSET. Rejected for v1 — changing "no call" from default-TOP_LEFT to build-error is a backwards-incompatible change for any consumer relying on the default. The two-boolean approach preserves current behavior while adding region support.

### 4.4 PanelPosition (standalone context)

Extend per §3.6. Implementation deferred; enum + factory shipped.

### 4.5 MKHudAnchor

Retained. Legacy consumers (PocketHud) continue to work unchanged. Migration to HudRegion is opt-in.

---

## 5. Consumer API — before / after

### 5.1 IP settings gear (inventory)

**Before:**
```java
private static final ScreenPanelAdapter ADAPTER = new ScreenPanelAdapter(
        PANEL,
        ScreenOriginFns.fromScreenTopRight(11, -4, -16));
```

**After:**
```java
private static final ScreenPanelAdapter ADAPTER = new ScreenPanelAdapter(
        PANEL, InventoryRegion.TOP_ALIGN_RIGHT);
```

**Visual change — screenshot-before-commit workflow.** Gear moves from inside-the-frame-top-right (current: -4 inset, -16 above top) to outside-above-the-frame, right-aligned. This is a real visual move. Migration workflow: apply the change, ship the build, show Trevor a screenshot, iterate if needed, then commit. The long-term position is outside-above (that's where other mods will compete for space, which is the whole reason regions exist), but the exact placement is worth eyeballing in-game before locking in.

### 5.2 Sandboxes button (inventory)

**Before:**
```java
private static final ScreenPanelAdapter MAIN_ADAPTER = new ScreenPanelAdapter(
        MAIN_WORLD_PANEL,
        bounds -> new ScreenOrigin(
                bounds.leftPos() + bounds.imageWidth() - 28,
                bounds.topPos() - 16));
```

**After:**
```java
private static final ScreenPanelAdapter MAIN_ADAPTER = new ScreenPanelAdapter(
        MAIN_WORLD_PANEL, InventoryRegion.TOP_ALIGN_RIGHT);
```

**Implicit ordering.** If both IP and sandboxes register in TOP_ALIGN_RIGHT, the second registration stacks leftward from the first (flow: left). Registration order (IP first, sandboxes second) produces the current visual layout (gear on the right, sandbox button to its left).

**Explicit dependency required.** Current ordering works by accident — Fabric sorts mods alphabetically within a dependency tier, and "inventory-plus" < "sandboxes" places IP's init before sandboxes'. This would flip if either mod gets renamed. Sandboxes must add an explicit dependency in its `fabric.mod.json`:

```json
"depends": {
    "inventory-plus": "*"
}
```

(Or a version range — `">=1.0.0"` once there's a meaningful lower bound.) This pins sandboxes' init after IP's regardless of future renames. Migrating the sandboxes panel to a region without this dependency declaration is a latent ordering bug.

### 5.3 Shulker-palette toggle (inventory)

**Before:**
```java
private static final ScreenPanelAdapter ADAPTER = new ScreenPanelAdapter(
        PANEL,
        bounds -> new ScreenOrigin(
                bounds.leftPos() + 159,
                bounds.topPos() + 5));
```

**After (design choice):** The current position is "above the shulker grid" — not an outside-the-frame region. No M5 region maps to this. Two options:
- Stay on the raw `ScreenOriginFn` lambda. Regions are opt-in; this consumer opts out.
- Migrate to `TOP_ALIGN_RIGHT` (accepts the visual shift upward and outside the frame).

**Recommendation:** stay on the lambda. Phase 13a doesn't force-migrate consumers whose placement needs don't fit regions.

### 5.4 IP pocket HUD — stays on `.anchor(...)` (resolved)

**No migration in v1.** The pocket HUD's current offset `(0, -60)` clears the vanilla hotbar + XP bar + food/health column. `HudRegion.BOTTOM_CENTER` would place the panel flush against the bottom edge, overlapping vanilla HUD. Regions do not know about vanilla HUD elements (non-goal §11).

Adding a per-panel region offset would dilute the region-as-layout-contract. Adding a `BOTTOM_CENTER_ABOVE_HOTBAR` region pre-builds an escape hatch for one consumer. Rule of Three: wait for a second and third consumer to hit the "above vanilla HUD" wall before adding a dedicated region.

Pocket HUD keeps its current `.anchor(MKHudAnchor.BOTTOM_CENTER, 0, -60)` declaration unchanged.

### 5.5 IP lock overlay

Lock overlay renders lock icons on individual slot positions — it's not a panel in the region-system sense. No migration; stays as a per-slot render call.

### 5.6 Grafted-slot visual layers (F8, F15, any future M4 consumer)

**By-reference composition — lambda path only.** The backdrop Panel for grafted slots traces the fixed `(x, y)` positions declared at `addSlot()` time during `<init>` RETURN. A region-stacked backdrop would drift out of alignment when other panels in the region toggle visibility (see §4A).

Pattern:

```java
// Consumer's shared-constants file (one per feature)
public final class EquipmentLayout {
    public static final int ELYTRA_X = -22;
    public static final int ELYTRA_Y = 8;
    public static final int TOTEM_X = -22;
    public static final int TOTEM_Y = 26;
    public static final int PANEL_WIDTH = 18;
    public static final int PANEL_HEIGHT = 36;
}

// M4 mixin uses the constants when grafting slots
this.addSlot(new Slot(adapter, 0, EquipmentLayout.ELYTRA_X, EquipmentLayout.ELYTRA_Y) { ... });
this.addSlot(new Slot(adapter, 1, EquipmentLayout.TOTEM_X, EquipmentLayout.TOTEM_Y) { ... });

// Visual-layer Panel uses the same constants, via lambda ScreenOriginFn
private static final ScreenPanelAdapter ADAPTER = new ScreenPanelAdapter(
        EQUIPMENT_BACKDROP_PANEL,
        bounds -> new ScreenOrigin(
                bounds.leftPos() + EquipmentLayout.ELYTRA_X,
                bounds.topPos() + EquipmentLayout.ELYTRA_Y)
);
```

The same X/Y source of truth drives both the handler-layer graft and the visual-layer origin. If the coordinates change, the consumer edits one place.

**No region API for this case.** If a future consumer hand-rolls the same pattern a third time (Rule of Three), M5 v2 may introduce a "fixed-anchor, non-stacking" region variant. V1 does not pre-build that.

---

## 6. Stacking mechanics (detail)

### 6.1 Registration — singletons only, process-lifetime

At adapter construction / builder build time:

```java
// Pseudocode, internal
void registerInventory(Panel panel, InventoryRegion region) {
    inventoryPanels.computeIfAbsent(region, r -> new ArrayList<>()).add(panel);
}
```

**Library contract — stable singletons only.** Consumers construct exactly one `ScreenPanelAdapter` (or `MKHudPanel` with `.region()`) per logical panel at mod init, typically as a `static final` field. The resulting Panel is held for the process lifetime.

**Dynamic adapter construction is not supported.** If a consumer constructs adapters in a method that runs repeatedly (per-screen-open, per-frame), each call adds another Panel to the registry — duplicates the stacking slot and leaks memory. There is no `unregister()` API and no `WeakReference` fallback. This is a precondition documented in the API javadoc, not a guarded runtime check.

Within Trevor's monorepo, all current consumers follow the static-final pattern. If a future consumer needs per-session dynamic UI, it uses raw `ScreenOriginFn` lambdas (opt out of regions) or the library revisits this contract with evidence.

### 6.1a Registration ordering — class-load as implicit input

Stacking order is registration order. Registration happens during `static` field initialization when adapter classes are first touched. The order in which the JVM loads adapter classes becomes an implicit input to the final layout.

Within a single mod: consumers control this by touching decoration classes from mod init in a known sequence (e.g., `ClassLoader.forName("...SettingsGearDecoration")` or referencing the class in the mod's client-init method). Deterministic within the mod.

Across mods: order depends on Fabric's mod load order, which respects `fabric.mod.json` `depends` relationships. Mods wanting deterministic ordering relative to another mod declare the dependency. Otherwise, order is technically arbitrary but stable-per-launch (Fabric sorts mods alphabetically within the same dependency tier, subject to version constraints).

**No priority knob in v1.** Consumers resolve ordering via `depends` or accept the default. Revisit if ecosystem-external consumers produce collision reports.

### 6.2 Stacking index resolution (per-frame)

For each adapter's render call, the library computes the panel's stacking index:

```java
// Pseudocode
int stackingIndex(Panel self, InventoryRegion region) {
    List<Panel> panels = inventoryPanels.get(region);
    int index = 0;
    for (Panel p : panels) {
        if (p == self) return index;
        if (p.isVisible()) index++;
    }
    throw new IllegalStateException("panel not registered in region");
}
```

Hidden panels contribute 0 to the index. When a panel flips from hidden to visible, subsequent panels' indices increment.

### 6.3 Prefix computation (per-frame)

```java
// Pseudocode
int axialPrefix(Panel self, InventoryRegion region, Panel.AxialAxis axis) {
    List<Panel> panels = inventoryPanels.get(region);
    int prefix = 0;
    for (Panel p : panels) {
        if (p == self) return prefix;
        if (!p.isVisible()) continue;
        prefix += (axis == HORIZONTAL ? p.getWidth() : p.getHeight()) + GAP;
    }
    throw new IllegalStateException("panel not registered in region");
}
```

`GAP = 2` constant. Flow-axis determined per region.

### 6.4 Origin computation (per-frame)

```java
// Pseudocode
ScreenOrigin origin(Panel self, InventoryRegion region, ScreenBounds bounds) {
    int prefix = axialPrefix(self, region, region.axis());
    int pw = self.getWidth(), ph = self.getHeight();
    return region.resolve(bounds, pw, ph, prefix);
}
```

Each region's `resolve(bounds, pw, ph, prefix)` applies the formulas from §3.4.

### 6.5 Overflow cutoff — sentinel public, Optional internal

**Public interface: sentinel.** `ScreenOriginFn.compute()` returns a `ScreenOrigin`. Preserving that signature is non-negotiable — every existing consumer lambda returns a `ScreenOrigin` directly, and breaking them for an internal edge case is not acceptable. Overflow is signaled via a dedicated `ScreenOrigin.OUT_OF_REGION` sentinel value.

**Internal implementation: Optional.** Private region-math functions in `RegionMath` return `Optional<ScreenOrigin>` — cleaner for composition and clearer about intent. The region → `ScreenOriginFn` adapter maps the Optional to sentinel at the boundary:

```java
// RegionMath — internal, idiomatic
static Optional<ScreenOrigin> resolveInventory(InventoryRegion r, ScreenBounds bounds,
                                                int pw, int ph, int prefix) {
    int available = r.availableSpace(bounds);
    int selfExtent = r.isHorizontalFlow() ? pw : ph;
    if (prefix + selfExtent > available) return Optional.empty();
    return Optional.of(computeOrigin(r, bounds, pw, ph, prefix));
}

// Adapter layer — maps to the public sentinel at the boundary
ScreenOriginFn originFn = bounds -> RegionMath
        .resolveInventory(region, bounds, panel.getWidth(), panel.getHeight(),
                axialPrefix(panel, region))
        .orElse(ScreenOrigin.OUT_OF_REGION);
```

`ScreenPanelAdapter.render` checks the sentinel before rendering:

```java
public void render(GuiGraphics graphics, ScreenBounds bounds, int mx, int my) {
    if (!panel.isVisible()) return;
    ScreenOrigin origin = originFn.compute(bounds);
    if (origin == ScreenOrigin.OUT_OF_REGION) return;
    // ... render elements ...
}
```

Public-API callers (consumer lambdas) never see the sentinel because they never exceed their regions — they return explicit coordinates. Sentinel is internal-to-regions; it flows through the `ScreenOriginFn` interface only as an implementation artifact of the region → origin pipeline.

---

## 7. Migration plan — Phase 13a

**Scope:** migrate by-value decoration panels whose placement fits regions. By-reference panels (grafted-slot backdrops, anything with foreign coordinate consumers) stay on the lambda path per §4A and §5.6.

**Recommended migration order:**

1. **IP settings gear** → `InventoryRegion.TOP_ALIGN_RIGHT`. First migration — simplest case, single panel. Validates the region→coord pipeline.
2. **Sandboxes buttons** → `InventoryRegion.TOP_ALIGN_RIGHT`. Validates two-panel stacking (sandbox button registered after IP gear stacks to its left per the region's leftward flow).

**Consumers that stay on lambdas / anchors in v1:**

- **Pocket HUD** — stays on `.anchor(MKHudAnchor.BOTTOM_CENTER, 0, -60)`. Regions don't know about vanilla hotbar clearance (§5.4).
- **Shulker-palette toggle** — stays on the "above the grid" lambda. No region models menu-internal placement (§5.3).
- **IP lock overlay** — per-slot rendering, not a panel in the region sense (§5.5).
- **F8 equipment backdrop** — by-reference (backdrop traces M4 slot coords). Shared-constants lambda pattern per §5.6.
- **F15 peek backdrop** — by-reference (backdrop traces M4 peek slot coords). Shared-constants lambda pattern per §5.6. Temporary-panel stacking is not a v1 M5 use case.

**F9 pockets — pending UI-structure clarification.** The pocket-panel-versus-pocket-buttons UI decomposition is a Phase 13b decision, not a Phase 13a one. The advisor's working reading is: nine `Toggle.linked` buttons hand-positioned above each hotbar slot (lambda path, hotbar-anchored, by-reference to vanilla hotbar positions) + one conditional 3-slot pocket panel shown when `openPocketIndex != -1` (region-placeable if by-value). If that reading lands: the conditional panel is the only region user; the nine buttons stay on lambdas. Migration plan finalizes when F9's UI design is committed.

**Consumers opting out intentionally.** The library contract is clear: regions are opt-in. A consumer not in the list above can still use `.region(...)` later if a region fits; a consumer in the list can still migrate later if its placement needs change. Migration is not irreversible.

---

## 8. Library surface

**New files:**
- `core/InventoryRegion.java` — enum + per-region `resolve()` method (may pull shared helpers into a package-private `RegionMath`)
- `core/HudRegion.java` — enum + per-region `resolve()` method
- `core/StandaloneRegion.java` — enum only (resolve() deferred with implementation)
- `inject/RegionRegistry.java` — internal registry for inventory + HUD panel lists per region
- `core/RegionMath.java` (optional) — shared coordinate math helpers

**Modified files:**
- `inject/ScreenPanelAdapter.java` — new `(Panel, InventoryRegion)` constructor
- `hud/MKHudPanel.java` — new `.region(HudRegion)` builder method, build-time validation
- `core/PanelPosition.java` — add `IN_REGION` mode, `inRegion(StandaloneRegion)` factory
- `core/Panel.java` — add `getWidth()` / `getHeight()` auto-size methods

**Unchanged:**
- `ScreenOriginFns` — no new factory methods
- `ScreenOriginFn` / `ScreenBounds` — no changes
- `MKHudAnchor` — retained; legacy path

---

## 9. Verification plan

### 9.1 Math — runs via `/mkverify all`

Region math is pure given explicit inputs (`bounds, pw, ph, prefix`). `/mkverify all` invokes `RegionMath.resolveInventory` / `resolveHud` with synthetic bounds + synthetic panel sizes and asserts the returned origin against expected values. Matches the library's established verification philosophy — runtime assertions via chat command, not headless harness.

Cases the `/mkverify` pass covers:
- Each of 8 inventory regions + 9 HUD regions at `prefix = 0` produces the first-panel origin matching the specs doc.
- Each region at `prefix = 20` (a non-zero stacking offset) advances the origin along its flow axis by 20px.
- `Panel.getWidth()` / `getHeight()` on a synthetic panel with known elements matches the computed bounding box.
- Overflow: `resolveInventory(..., prefix=largeValue)` returns `Optional.empty()` (→ sentinel at the adapter boundary).

These assertions run at every phase boundary (per-session `/mkverify all`) so regressions surface immediately.

### 9.2 Visual verification — dev-scaffolding mod

Math correctness doesn't prove visual correctness. A dev-only scaffolding mod (or a temporary feature flag in an existing consumer) registers probe Panels in each region to confirm the pixels land as expected.

**Single-panel visual check per region.** Register one 18×18 probe panel in each of the 25 regions. Verify placement matches specs doc.

**Multi-panel stacking.**
- Three panels in `InventoryRegion.TOP_ALIGN_RIGHT`. Verify they stack leftward from the right edge with 2px gaps.
- Toggle middle panel's visibility. Verify stack collapses and re-expands.

**Frame-responsive behavior.**
- Open the inventory screen. Open the recipe book. Verify regions track the menu shift.
- Open a chest, then a shulker box. Verify regions position correctly for each menu's `ScreenBounds`.

**Overflow.**
- Stack enough panels in `LEFT_ALIGN_TOP` to exceed menu height. Verify the last one does not render; others render normally.

**HUD stacking at multiple GUI scales.**
- Register two HUD panels in `HudRegion.TOP_LEFT`. Verify vertical stacking.
- Repeat at GUI scales 2, 3, 4. Verify `CENTER_CROSSHAIR_CLEARANCE = 16` produces acceptable crosshair clearance at each scale.

### 9.3 Migration validation (Phase 13a)

Each migrated consumer: visual placement before/after matches the specs-doc intent. Visual diff is acceptable and expected for panels where the specs-doc region differs from the pre-M5 coordinate choice (e.g., settings gear moving from inside-top-right to outside-top-right-aligned).

---

## 10. Resolved design decisions

The eight questions that were open during the draft pass are now resolved per advisor review. Divergence during implementation requires a follow-up review; otherwise implement as below.

1. **Enum shape — three separate enums.** `InventoryRegion`, `HudRegion`, `StandaloneRegion` remain distinct types. The 8-name duplication between inventory and standalone is ~20 lines of enum declaration; compile-time type safety at API boundaries is permanent value. Shared math lives in package-private `RegionMath`, not in a unified enum.
2. **HUD corner/edge inset — 4px hardcoded.** Matches vanilla's F3 debug-overlay convention. Configurable when a second consumer needs a different inset — Rule of Three, not pre-built.
3. **Pocket HUD migration path — stays on `.anchor(...)`.** No per-panel region offset. Regions encode a layout contract; escape hatches dilute it. If three consumers hit the "above vanilla HUD" wall, add a dedicated `BOTTOM_CENTER_ABOVE_HOTBAR` region (or similar) at that point. Until then, the anchor path is the answer.
4. **Panel auto-size — default on; `.size(w, h)` opt-in override.** Backwards-compatible with existing Panels that don't declare dimensions. Explicit pin available for consumers with dynamic elements that need stacking jitter suppression (see §3.3).
5. **Cross-mod registration ordering — `fabric.mod.json depends`.** No priority knob in v1. Within-mod ordering is the consumer's responsibility (force-touch classes in mod init if order matters). Revisit if ecosystem-external consumers surface collision reports.
6. **Standalone regions — deferred implementation, enum shipped.** Phase 13a has no standalone consumer. Enum + `PanelPosition.IN_REGION` mode + factory are reserved API; the solver is deferred until a concrete consumer materializes. This matches the library-not-platform discipline: no speculative code for imagined consumers.
7. **`OUT_OF_REGION` — sentinel public, `Optional` internal.** `ScreenOriginFn.compute()` signature is stable — existing consumer lambdas return `ScreenOrigin` directly, and breaking them for an internal edge case is unacceptable. `RegionMath` returns `Optional<ScreenOrigin>` internally; the region → adapter pipeline maps to sentinel at the boundary (see §6.5).
8. **Build-time validation — strict.** `MKHudPanel.Builder.build()` throws if both `.region()` and `.anchor()` are set. Migration is a single commit per consumer (delete `.anchor()`, add `.region()`); no transitional state justifies the laxer validation. Fail fast catches typos at dev time.

---

## 11. Non-goals / out of scope

- **Grafted-slot backdrop panels (F8, F15, any future M4 consumer) do not use regions in v1.** Their visual-layer positioning derives from fixed slot coordinates via shared constants in the consumer's codebase. A by-reference backdrop stacked in a by-value region would drift out of alignment with the handler-layer slots it visually represents (§4A, §5.6). If a third consumer hand-rolls the same pattern, Rule of Three reconsiders adding a "fixed-anchor, non-stacking" region variant.
- **Dynamic panel construction is unsupported.** The library contract assumes consumers construct exactly one adapter per logical panel at mod init, as a `static final` field. There is no `unregister()` API and no `WeakReference` fallback. Consumers needing per-session UI use raw `ScreenOriginFn` lambdas (opt out of regions).
- **Vanilla-HUD-element awareness.** Regions do not know about vanilla hotbar / XP bar / boss bar / chat. Consumers that need clearance from vanilla HUD use the `.anchor(...)` path with manual offset (see §5.4).
- **Vanilla-menu-element-anchored regions.** No "above crafting grid" region — consumers manually offset via `ScreenOriginFn` if they need menu-internal placement. The shulker palette toggle is the canonical example.
- **Priority stacking.** Registration order only. No consumer-supplied priority knob.
- **User override.** No runtime API for the player to re-stack or relocate regions.
- **Graceful overflow.** Panels beyond region capacity are hidden, not scaled or compressed.
- **Edge-center regions in inventory context** (e.g., "left edge, vertically centered"). Ambiguous flow axis; specs doc explicitly excludes.
- **Dynamic panel sizes.** Supplier-driven width/height. V1 is static-only. Consumers whose panels can change size pin with `.size(w, h)` to avoid stacking jitter (§3.3).
- **Post-construction panel size changes.** Panels register with their current dimensions; size mutation mid-session is not supported.
- **Runtime region deregistration.** Panels are registered at mod init and remain registered for the process lifetime. No `unregister()`, no reassign-to-different-region.

---

## 12. Summary

M5 introduces named regions that consumers declare at panel construction. Three per-context enums (`InventoryRegion`, `HudRegion`, `StandaloneRegion`) cover 25 regions total. The library holds an internal registry mapping region → registered panels; per-frame origin computation uses the panel's stacking index, its width/height, and the current anchor frame to produce concrete coordinates.

Integration is additive: `ScreenPanelAdapter` gets a new constructor overload, `MKHudPanel.Builder` gets a `.region()` method, `PanelPosition` gets a new `IN_REGION` mode (standalone implementation deferred). Legacy manual positioning remains fully supported.

**By-value only.** Regions model by-value composition — panels that can shift per-frame as other panels in the region toggle visibility. By-reference panels (grafted-slot backdrops whose position is referenced by M4 slot coordinates; anything with foreign coordinate consumers) stay on the `ScreenOriginFn` lambda path with shared-constant coordinates. Phase 13a migrates the settings gear and sandboxes buttons; Phase 13b's F9 UI structure is pending clarification before its pocket panel (if by-value) joins the migration list.

**Status: resolved, ready for implementation.** §10 decisions are locked. Outstanding pre-implementation dependency: F9 UI structure for §7 migration plan finalization. Implementation can proceed on the library scaffolding (enums, `RegionMath`, `RegionRegistry`, adapter overload, MKHudPanel builder, PanelPosition extension) in parallel with that clarification — F9's answer affects which consumer lines into which region, not the library primitive itself.

**Implementation watch-list** (carry-over from advisor review):
- Rule of Three applies to every escape hatch. If implementation tempts priority knobs, per-panel region offsets, vanilla-HUD awareness, or runtime deregistration — resist. The lambda path is the escape for anything that doesn't fit. Accumulate evidence; don't pre-build.
- Keep `RegionMath` pure. Resolve functions take `(bounds, pw, ph, prefix)` and return coordinates. Registry state stays out of the math. This makes region semantics referentially transparent given explicit inputs — and testable without registry setup, which is what lets `/mkverify all` run the math without spinning up a screen.
- Registration-as-side-effect-of-construction makes class-load order an implicit input to stacking. Document it as a known property; don't chase it as a bug during debugging.
- Panel registration contract: stable singletons only. If the code starts wanting `unregister()`, that's a sign of dynamic Panel construction, which isn't the intended usage.

---

## §12.5a — Addendum: ScreenPanelAdapter completeness (Phase 12.5)

Phase 12.5 V4 validation surfaced four gaps in `ScreenPanelAdapter` that broke the component-library promise of "identical elements render identically across contexts":

1. `ScreenPanelAdapter` rendered elements only — no panel-background rendering despite `Panel.getStyle()` being RAISED/INSET/DARK. Consumers had to manually call `PanelRendering.renderPanel` and re-derive origin via `RegionMath` to align.
2. No content padding — unlike `MenuKitScreen`'s baked-in `PANEL_PADDING = 7` and `MKHudPanel.padding()`, the adapter rendered elements flush with its origin. `childX = 0` meant three different things in three contexts.
3. `RegionMath.resolveInventory` / `resolveHud` returned `Optional.empty()` silently on overflow. Panels wider/taller than a region's axial capacity rendered nothing with no log signal. Consumer debug time spent hunting wrong causes.
4. The adapter's origin was private. Consumers wanting to paint sibling decorations (tooltips, hover overlays, related info) re-derived origin math.

Disposition: **in-phase additive extension of `ScreenPanelAdapter` + `RegionRegistry`.** Advisor-reviewed. Scope is purely additive: no existing API signatures change; no behavioral coupling removed. Visual output of pre-12.5 consumers shifts — Phase 13a visual-diff review catches placement drift.

### Closed-gap surface

- **`ScreenPanelAdapter.DEFAULT_PADDING = 7`** — public constant, matches `MenuKitScreen.PANEL_PADDING`. Content origin is panel origin + padding; element `childX` / `childY` are relative to the content area.
- **New constructor overloads**: `new ScreenPanelAdapter(Panel, ScreenOriginFn, int padding)` and `new ScreenPanelAdapter(Panel, InventoryRegion, int padding)`. Existing two-arg constructors default padding to `DEFAULT_PADDING`.
- **`ScreenPanelAdapter.render` auto-renders background** when `panel.getStyle() != PanelStyle.NONE`. Paints a padding-inclusive rectangle at the panel origin before dispatching element renders. Consumers who want flush rendering without a background declare `PanelStyle.NONE` on their `Panel`.
- **`ScreenPanelAdapter.getOrigin(screenBounds)` → `Optional<ScreenOrigin>`** — public accessor returning the panel's screen-space top-left. Returns empty when invisible or out-of-region. Content area begins at `origin + getPadding()`.
- **`RegionRegistry.registerInventory(Panel, InventoryRegion, int padding)`** — stores padding per (panel, region) registration. Axial-prefix stacking math and `RegionMath.resolveInventory` overflow checks both use padding-inclusive extents.
- **`RegionRegistry.inventoryOriginFn`** and the HUD render loop in `MenuKit.java` — log a one-shot `LOGGER.warn` the first time each (panel identity, region) pair overflows. Keyed on panel identity (not class) via `WeakHashMap`; deduplication is per-session. Message names the panel, the region, the axial extent, the region capacity, and the prefix from preceding panels.
- **`RegionRegistry.warnHudOverflowOnce(...)`** — parallel helper for `MKHudPanelDef` + `HudRegion`.

### Consumer impact (for Phase 13a review)

Four current consumers of `ScreenPanelAdapter`: IP `SettingsGearDecoration`, IP `LockOverlayDecoration`, sandboxes buttons, shulker-palette toggle. Most constructed Panels without explicitly declaring `PanelStyle` — `new Panel(id, elements)` defaults to `RAISED`. Pre-12.5, the adapter ignored this; post-12.5, it renders a RAISED background.

Visual shifts those consumers will see on 13a relaunch:
- A RAISED panel background appears behind what was previously a transparent-panel decoration. For `Button.icon`-only decorations (IP settings gear), this is raised-on-raised — a visible ghosted rectangle around the button.
- Elements shift by 7 pixels inward (content padding). `ScreenOriginFn` calibration points drift by 7.

Migration path per decoration:
- **If the decoration doesn't want a panel background** (IP settings gear, IP lock overlay — likely the majority): declare `PanelStyle.NONE` on the `Panel` AND pass `padding = 0` to the adapter to preserve pre-12.5 placement exactly.
- **If the decoration does want a panel background**: accept the visual shift, re-tune `ScreenOriginFn` offsets to account for padding.

The Phase 13a review passes each decoration through this checklist as a structured migration step, not an ad-hoc visual fix.

### Design-axis decisions locked

- **Name.** `ScreenPanelAdapter` keeps its name. The class now renders panels fully (style + padding + elements), contradicting the "adapter" framing, but rename cost exceeds benefit given four current consumers. Class javadoc documents the historical name explicitly.
- **Padding default = 7.** Matches `MenuKitScreen.PANEL_PADDING`. Preserves the "just works" promise over preserving accidentally-inconsistent pre-12.5 placement. Current consumers who want flush-edge behavior pass `padding = 0` explicitly during 13a migration.
- **One-shot warn keyed on Panel identity.** `WeakHashMap<Panel, Set<InventoryRegion>>` — one entry per distinct Panel instance. A second adapter wrapping a new Panel instance logs independently. Resize-triggered re-warn on hidden→visible transition is **deferred** to a follow-up: V4 doesn't exhibit the dynamic-size case, and adding the transition detector before a consumer exercises it would cut against the "ship what we need" discipline. Filed for when evidence accumulates.
- **Panel stays pure.** Padding lives on `ScreenPanelAdapter` (and implicitly on `MenuKitScreen`'s `PANEL_PADDING` and `MKHudPanel.padding()`), never on `Panel`. This preserves THESIS §5 — Panel is context-neutral; padding is context-specific machinery.
- **RegionMath stays pure.** The warn log lives at callsites (`RegionRegistry.inventoryOriginFn` lambda, `MenuKit.renderHud` loop), not inside the math functions. Preserves the "registry state stays out of the math" invariant from §6.

### THESIS Principle 9 (landed alongside these changes)

The four gaps shared a root: `ScreenPanelAdapter` was shipped in Phase 10 as "rendering-lite," dispatching element renders but not the panel's background-paint or content-padding. The rendering pipeline that `MenuKitScreen` and `MKHudPanel` shared wasn't uniform; the adapter was outside it. Phase 11 consumers worked around the gap because they didn't push full-pipeline content through the adapter. Phase 12.5 V4 did, and the gap surfaced.

Closing the four gaps additively was the tactical move. Naming the invariant that those gaps violated is the strategic one. THESIS Principle 9 — *"Rendering pipelines are uniform across contexts; embedding is context-specific"* — lands alongside this addendum. It splits the container's responsibilities cleanly: **embedding** (where the panel's bounding box sits, how the screen surrounds it with blur/darkening/world-integration) stays context-specific per Principle 5; **rendering pipeline** (what happens inside the bounding box — background, padding, element layout, element dispatch) is uniform across contexts.

Every future rendering context MenuKit adds — tooltip overlay, boss-bar overlay, waystone UI, whatever Phase N ships — has a named checklist to clear before it's considered complete. Principle 9 makes that checklist explicit; its absence is what let Phase 10's adapter ship rendering-lite.

### Status

Library additions landed. V4.2 inventory decoration ships against the new primitives — consumer code is scenario-wiring-only, no origin math, no background painting, no padding hacks. 13a migration list filed. `/mkverify v4 cross inventory` validates render parity with HUD + standalone per the V4.2 test.
