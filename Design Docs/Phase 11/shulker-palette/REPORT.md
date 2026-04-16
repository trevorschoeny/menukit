# Phase 11 Shulker-Palette — Report

Close-out for shulker-palette under the Phase 11 / 12 / 13 arc. Same framing as IP: rebuild as far as current MenuKit allows; defer features that need primitives or sequencing dependencies.

---

## Arc context

- **Phase 11 (this report)** — rebuild shulker-palette against current MenuKit. Defer features blocked on unshipped IP work.
- **Phase 12** — design and ship MenuKit primitives using multi-consumer evidence.
- **Phase 13** — complete deferred consumer-mod features.

---

## Shipped in Phase 11

### Layer 0a — compile-clean baseline

- Removed dead MenuKit imports: `MKButtonDef`, `MKGroupChild.Button`, `MKButton.ButtonStyle`, `MKContainerType`, `MenuKit.buttonAttachment()`.
- Deleted `ShulkerPalettePeekCompat.java` — deferred to SP-F1 (sequencing-blocked on IP Layer 3).
- Deleted `ShulkerPaletteOverlayRenderer.java` — dead code, never called, superseded by 3D composite renderer.
- Enabled shulker-palette in the dev module (`dev/build.gradle`).
- Result: compiles clean, dev client boots, all mixins apply.

### Layer 1 — toggle button rebuild + full verification

**PaletteToggleDecoration** — Pattern 2 decoration using current MenuKit APIs:
- `Panel` with a single `Button.icon` element (11x11, sprite-supplier variant for on/off icon swap).
- `ScreenPanelAdapter` positioned above the 9x3 shulker slot grid, right-aligned at `(leftPos + 159, topPos + 5)`.
- Reads palette state from synced DataSlot via `ShulkerPaletteMenuAccessor`.
- Sends C2S toggle packet on click (block-entity path, `menuSlotIndex = -1`).
- Tooltip: dynamic "Palette: On" / "Palette: Off".

**ShulkerPaletteScreenMixin** — mixin injection on `AbstractContainerScreen`:
- `render` TAIL: draws the toggle button (gated `instanceof ShulkerBoxScreen`).
- `mouseClicked` HEAD: dispatches clicks to the toggle; cancels vanilla handling if consumed.
- Targets `AbstractContainerScreen` (not `ShulkerBoxScreen`) because Mixin can't resolve inherited methods with explicit descriptors on subclasses that don't override them (COMMON_FRICTIONS #6).

**Verified at runtime (all pass):**
1. Toggle button renders above the shulker grid.
2. Click toggles state; icon swaps; tooltip updates.
3. Palette placement works — right-click places random block from contents.
4. 3D open-lid rendering with representative items works.
5. Persistence survives place/break/pickup/place cycle.
6. Config screen shows "Shulker Palette" tab in the trevmods family config; enabled toggle works.

### Config (unchanged, verified)

`ShulkerPaletteClient` registers a YACL config category via `MKFamily.configCategory(modId, name, builder, onSave)`. API unchanged from pre-Phase-11 — compiles and works. This is the second consumer exercising MKFamily's config surface (after IP's SettingsGearDecoration), confirming the API is stable.

---

## Deferred features (see `POST_PHASE_11.md`)

| Entry | Title | Category |
|-------|-------|----------|
| SP-F1 | Peek palette toggle | sequencing-blocked (IP Layer 3 not shipped) |

SP-F1 is **not** primitive-blocked. When IP's Layer 3 ships, the peek toggle is straightforward Pattern 2 injection — same approach as the ShulkerBoxScreen toggle. No Phase 12 mechanism needed.

---

## Per-item state finding

The kickoff predicted per-shulker state would surface a new mechanism candidate. **Negative finding: no new mechanism needed.**

Palette state is self-contained via CUSTOM_DATA on the ItemStack (items) and a custom field on ShulkerBoxBlockEntity (placed blocks). The DataSlot syncs placed-block state to the client. For peeked items, the client reads CUSTOM_DATA directly from the ItemStack in the menu slot.

M1 (unified per-slot state) stays scoped to per-slot concerns. Per-item state on the item itself doesn't need a library abstraction.

---

## Frictions surfaced (see `COMMON_FRICTIONS.md`)

6. **`@Inject` with explicit descriptor can't target inherited methods on a subclass.** Mixin applicator crashes if `@Mixin(ShulkerBoxScreen.class)` specifies `method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"` — the method lives on `AbstractContainerScreen`, not on `ShulkerBoxScreen`. Fix: target the parent class and gate with `instanceof`.

---

## Files changed

### Created
- `PaletteToggleDecoration.java` — Pattern 2 decoration (Panel + Button.icon + ScreenPanelAdapter)
- `mixin/ShulkerPaletteScreenMixin.java` — render + click injection on AbstractContainerScreen

### Modified
- `ShulkerPalette.java` — removed dead imports, `initClient()`, `screenPaletteButton()`, ICON constants
- `ShulkerPaletteClient.java` — removed `initClient()` call; config registration unchanged
- `shulker-palette.mixins.json` — added `ShulkerPaletteScreenMixin` to client section
- `dev/build.gradle` — enabled shulker-palette on dev classpath

### Deleted
- `ShulkerPalettePeekCompat.java` — deferred (SP-F1)
- `ShulkerPaletteOverlayRenderer.java` — dead code

### Documentation
- `menukit/Design Docs/Phase 11/shulker-palette/AUDIT.md` — archaeological audit
- `menukit/Design Docs/Phase 11/shulker-palette/REPORT.md` — this file
- `menukit/Design Docs/Phase 11/POST_PHASE_11.md` — created with IP + SP entries
- `menukit/Design Docs/Phase 11/COMMON_FRICTIONS.md` — added friction #6

---

## Scope assessment

Shulker-palette's Phase 11 is complete. One feature deferred (peek toggle, sequencing-blocked). All in-scope features verified at runtime. The key deliverable beyond the code is the negative finding: per-item state doesn't need a new primitive, narrowing M1's scope to per-slot concerns only.

Estimated half day; delivered in one pass. No surprises beyond the mixin-on-subclass friction (captured).

---

## What's next

Per the three-phase arc:
- **Next consumer mod:** sandboxes or agreeable-allays under the same framing.
- **Phase 12:** design MenuKit primitives (M1 primarily) using evidence from IP + shulker-palette + remaining consumers.
- **Phase 13:** complete IP's F1–F7 and SP-F1 against Phase 12's primitives.
