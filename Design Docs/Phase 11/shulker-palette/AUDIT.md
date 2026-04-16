# Phase 11 Shulker-Palette — Audit

Archaeological audit of shulker-palette against current MenuKit (post-Phase-10/11). Same framing as IP: rebuild as far as current MenuKit allows; defer features that need primitives that don't exist.

---

## Module overview

Shulker-palette has **two distinct feature surfaces**:

1. **Palette placement** — when a shulker box is marked as a "palette," right-clicking places a random block from its contents instead of the shulker itself. 3D open-lid rendering shows representative items inside.
2. **Palette toggle UI** — a toggle button on the ShulkerBoxScreen (and optionally on IP's peek panel) lets the player mark/unmark a shulker as a palette.

Feature surface 1 is **entirely vanilla/Fabric mixin work** — zero MenuKit dependency. Feature surface 2 is **entirely MenuKit-dependent** and uses APIs that no longer exist.

---

## File inventory (21 Java files)

### Core logic (no MenuKit dependency) — 9 files

| File | Purpose | MenuKit? | Status |
|------|---------|----------|--------|
| `ShulkerPaletteMod.java` | Server entrypoint → `ShulkerPalette.init()` | No | OK |
| `ShulkerPaletteState.java` | Signal fields for Strategy B (client/server overrides, pending roll) | No | OK |
| `ShulkerPaletteRoll.java` | Weighted random selection from shulker contents; `mostCommonItem`; `topNItems` | No | OK |
| `ShulkerPaletteTogglePayload.java` | C2S packet: toggle palette flag (block entity or item CUSTOM_DATA) | No | OK |
| `ShulkerPaletteCompositeRenderer.java` | 3D: renders open shulker base + up to 3 representative items | No | OK |
| `ShulkerPaletteOverlayRenderer.java` | 2D slot overlay rendering | No | **Dead code** — defined but never called; superseded by 3D composite renderer |
| `ShulkerPaletteAccessor.java` | Duck interface on `ShulkerBoxBlockEntity` for `isPalette`/`setPalette` | No | OK |
| `ShulkerPaletteMenuAccessor.java` | Duck interface on `ShulkerBoxMenu` for palette `DataSlot` | No | OK |
| `ShulkerPaletteConfig.java` | Standalone JSON config (`enabled` field) | No | OK |

### Mixins (no MenuKit dependency) — 8 files

| Mixin | Target | Purpose | Status |
|-------|--------|---------|--------|
| `ShulkerPaletteBlockEntityMixin` | `ShulkerBoxBlockEntity` | Adds palette flag; persists via saveAdditional/loadAdditional + collectImplicitComponents/applyImplicitComponents (CUSTOM_DATA) | OK |
| `ShulkerPaletteMenuMixin` | `ShulkerBoxMenu` | Adds synced DataSlot backed by block entity flag | OK |
| `ShulkerPaletteClientMixin` | `MultiPlayerGameMode` | Client HEAD/RETURN: rolls block, sets clientOverride, stores pendingRoll | OK |
| `ShulkerPalettePlayerMixin` | `LivingEntity` | Intercepts `getItemInHand` to return override item during placement | OK |
| `ShulkerPalettePacketMixin` | `ServerGamePacketListenerImpl` | Transfers pendingRoll → server fields at HEAD of `handleUseItemOn` | OK |
| `ShulkerPaletteServerMixin` | `ServerPlayerGameMode` | Server RETURN: clears override, decrements shulker contents | OK |
| `ShulkerPaletteGuiMixin` | `ItemModelResolver` | Swaps ShulkerBoxSpecialRenderer for composite renderer on palettes | OK* |
| Accessor mixins (×3) | `ShulkerBoxSpecialRenderer`, `ItemStackRenderState`, `LayerRenderState` | Field access for GUI mixin | OK* |

*\*These need compilation verification — vanilla rendering internals may have shifted in 1.21.11, but the classes they target exist in that version.*

### MenuKit-dependent (broken) — 3 files

| File | Purpose | Broken imports | Status |
|------|---------|----------------|--------|
| `ShulkerPalette.java` | `initClient()`: registers button attachment via dead APIs | `MKButton`, `MKButtonDef`, `MKContainerType`, `MKGroupChild`, `MenuKit.buttonAttachment()` | **Compile error** |
| `ShulkerPalettePeekCompat.java` | Peek palette toggle button using dead APIs | `MKButton`, `MKButtonDef`, `MKGroupChild` | **Compile error** |
| `ShulkerPaletteClient.java` | Client entrypoint: `MKFamily` config registration | `MKFamily` (still exists), `KeyMapping.Category` | **Compiles** (API unchanged) |

### Resources — 4 files

| File | Purpose | Status |
|------|---------|--------|
| `shulker-palette.mixins.json` | Mixin config (5 common + 5 client) | OK |
| `fabric.mod.json` | Mod metadata; depends on menukit, suggests inventory-plus | OK |
| `en_us.json` | Translation keys (2 entries) | OK |
| `atlases/gui.json` | Sprite atlas for `palette_off.png` / `palette_on.png` | OK |
| Textures (×2) | `palette_off.png`, `palette_on.png` in `gui/sprites/` | OK |

---

## Feature-by-feature analysis

### Feature 1: Palette placement (Strategy B)

**What it does.** When the player right-clicks with a shulker palette in main hand, the mod:
1. Client HEAD: rolls a random block from shulker contents (weighted by stack count), stores it as `clientOverride`
2. `LivingEntity.getItemInHand()` returns the override → vanilla's entire placement pipeline thinks the player is holding the rolled block
3. Packet arrives at server → `pendingRoll` → `serverOverride`
4. Server RETURN: clears override, decrements the actual item inside the shulker (creative mode skips decrement)

**Self-healing design.** Uses `AtomicReference<PendingRoll>` (single-slot) instead of a FIFO queue. If another mod cancels the placement, the stale roll gets overwritten by the next click rather than accumulating. Documented in `ShulkerPaletteState` javadoc.

**MenuKit dependency: none.** This is pure vanilla mixin work.

**Assessment: should work as-is.** No API changes needed. Verify at compilation + runtime.

### Feature 2: Block entity persistence

**What it does.** Palette flag (`trevormod_palette`) persists via two paths:
- **Disk**: `saveAdditional` / `loadAdditional` on `ShulkerBoxBlockEntity` via `ValueOutput` / `ValueInput`
- **Item cycle** (break → item → place): `collectImplicitComponents` / `applyImplicitComponents` storing in `CUSTOM_DATA`

**MenuKit dependency: none.**

**Assessment: should work as-is.** `ValueOutput`/`ValueInput` are the 1.21.11 block entity serialization API (replaces the old CompoundTag-based approach). The mixin uses both correctly.

### Feature 3: Menu DataSlot sync

**What it does.** `ShulkerPaletteMenuMixin` adds a `DataSlot` to `ShulkerBoxMenu`:
- Server: backed by block entity's `isPalette()` flag
- Client: standalone slot receiving synced values via vanilla container data sync

**MenuKit dependency: none.**

**Assessment: should work as-is.** Vanilla DataSlot sync is stable. The toggle button reads this slot to show on/off state.

### Feature 4: 3D composite rendering

**What it does.** `ShulkerPaletteGuiMixin` intercepts `ItemModelResolver.updateForTopItem()` RETURN. For palette shulkers:
1. Creates an open-lid `ShulkerBoxSpecialRenderer` (custom openness value)
2. Resolves top 3 items' render states via recursive `updateForTopItem`
3. Wraps both in `ShulkerPaletteCompositeRenderer` which renders all in one `submit()` call
4. Marks as animated for atlas re-render on content change

Uses 3 accessor mixins to reach renderer internals (`ShulkerBoxSpecialRenderer` fields, `ItemStackRenderState` layers, `LayerRenderState` transform).

**MenuKit dependency: none.**

**Assessment: likely works but needs compile verification.** The `ShulkerBoxSpecialRenderer` constructor and `NoDataSpecialModelRenderer` interface may have shifted between minor versions. The accessor targets (field names) need validation against 1.21.11 mappings.

### Feature 5: Palette toggle button (ShulkerBoxScreen)

**What it does.** A toggle button on the shulker box screen: click to mark/unmark the opened shulker as a palette. Reads state from the synced DataSlot. Sends C2S packet on click.

**Old implementation.** Used `MenuKit.buttonAttachment("palette_toggle")` with the dead `MKButtonDef` / `MKGroupChild.Button` / `MKContainerType.SIMPLE` / `MKButton.ButtonStyle.NONE` APIs.

**Current MenuKit replacement.** The button registration must be rebuilt using:
- `Panel` with a `Button` (or icon-button) element
- `ScreenPanelAdapter` for render/input dispatch
- `ScreenOriginFns.aboveSlotGrid(...)` or similar for positioning
- A mixin on `ShulkerBoxScreen` or `AbstractContainerScreen` (gated `instanceof ShulkerBoxScreen`) wiring render + mouseClicked

**Assessment: rebuild required.** This is the primary work item. The button's *behavior* (read DataSlot, send toggle packet) is unchanged — only the wiring to MenuKit's current API changes. Pattern 2 injection (small panel at slot grid region) is the natural fit.

### Feature 6: Peek palette toggle (IP cross-mod)

**What it does.** When IP's peek shows a shulker, a second palette toggle button appears on the peek panel. Reads palette state from the peeked item's `CUSTOM_DATA`. Sends toggle packet with the peeked slot index.

**Old implementation.** `ShulkerPalettePeekCompat.java` — same dead button APIs as Feature 5, plus imports `ContainerPeekClient.isPeeking()`, `.getPeekedSlot()`, `.getSourceType()` and `PeekS2CPayload.SOURCE_ITEM_CONTAINER`.

**Current state of IP peek.** IP's peek is **Layer 3 — not shipped in Phase 11 IP**. `ContainerPeekClient` exists but all methods return stubs (`isPeeking() → false`, `getPeekedSlot() → -1`, `getSourceType() → 0`). There is no functioning peek panel to attach a button to.

**Assessment: defer.** The peek toggle is doubly blocked:
1. Dead MenuKit button APIs (same as Feature 5)
2. IP's peek is not functional (Layer 3, not yet shipped)

File as a deferred feature. When IP's Layer 3 ships and peek works, rebuild the peek toggle against current APIs.

### Feature 7: Config

**What it does.** `ShulkerPaletteConfig` is standalone JSON (`enabled` field). `ShulkerPaletteClient` registers a YACL config category via `MKFamily.configCategory(modId, name, builder, onSave)`.

**Assessment: compiles as-is.** `MKFamily`'s builder API is unchanged. The `configCategory` 4-arg overload matches exactly.

---

## Per-item state question

The kickoff brief predicted per-shulker state would surface as a new primitive question. Here's the finding:

**Current approach:** palette state lives in `CUSTOM_DATA` on the shulker ItemStack (for items) and as a custom field on `ShulkerBoxBlockEntity` (for placed blocks). The menu `DataSlot` syncs placed-block state to the client.

**For the peek path:** the client reads `CUSTOM_DATA` directly from the peeked ItemStack (via `menu.slots.get(idx).getItem()`). This works because the peeked item is a real slot in the player's `containerMenu`, and `CUSTOM_DATA` syncs as part of the ItemStack.

**Verdict: shulker-palette's per-item state needs are self-contained.** The `CUSTOM_DATA` approach is sufficient for its own use case. It doesn't need M1's per-slot state primitive — M1 is about persistent per-slot metadata that survives menu transitions, which is a different shape than per-item metadata stored on the item itself.

**No new mechanism entry needed** for per-item state. If future mods need a generalized per-item-state API (beyond what `CUSTOM_DATA` provides), that's a Phase 12 design question — but shulker-palette doesn't surface it as a concrete blocker.

---

## Compilation assessment

**Will compile broken.** Two files (`ShulkerPalette.java`, `ShulkerPalettePeekCompat.java`) import dead APIs. Everything else should compile.

**Layer 0a scope (get it compiling, doing nothing new at runtime):**
1. Rewrite `ShulkerPalette.initClient()` to use current MenuKit APIs (Panel + Button + ScreenPanelAdapter + mixin injection)
2. Stub or remove `ShulkerPalettePeekCompat` (peek is not functional)
3. Remove dead `ShulkerPaletteOverlayRenderer`
4. Verify compilation
5. Verify runtime boot (dev client loads without crash)

**Layer 1 scope (features work):**
1. Wire the rebuilt toggle button: renders, click toggles, state reflects DataSlot
2. Verify placement still works: roll → override → place → decrement
3. Verify rendering: open lid, representative items, atlas caching
4. Verify persistence: place palette → break → pick up → place again → still palette
5. Config screen opens and `enabled` toggle works

---

## What's in scope for Phase 11

| Item | Scope | Dependency |
|------|-------|------------|
| Palette placement (Strategy B) | Verify works | None |
| Block entity persistence | Verify works | None |
| Menu DataSlot sync | Verify works | None |
| 3D composite rendering | Verify works | None |
| **Toggle button on ShulkerBoxScreen** | **Rebuild** | Current MenuKit Panel/Button APIs |
| Config registration | Verify works | MKFamily (unchanged) |
| Remove dead code (`ShulkerPaletteOverlayRenderer`) | Cleanup | None |

## What's deferred

| Item | Reason | Phase |
|------|--------|-------|
| Peek palette toggle | IP peek is Layer 3 (stubs); dead MenuKit APIs | Phase 13 (after IP Layer 3 ships) |

---

## Risk flags

1. **Rendering accessor stability.** The 3 accessor mixins target vanilla rendering internals (`ShulkerBoxSpecialRenderer`, `ItemStackRenderState.LayerRenderState`). Field names like `shulkerBoxRenderer`, `orientation`, `material`, `layers`, `activeLayerCount`, `specialRenderer`, `transform` are all mapping-dependent. If 1.21.11 mappings shifted any of these, the accessors will fail at mixin application time (same friction as COMMON_FRICTIONS #4 — compile is fine, crash at boot). Verify at compilation.

2. **`NoDataSpecialModelRenderer` interface.** `ShulkerPaletteCompositeRenderer` implements `NoDataSpecialModelRenderer`. If vanilla renamed or removed this interface, the class won't compile. Check against current mappings.

3. **`InteractionResult.consumesAction()` API.** Used in both `ShulkerPaletteClientMixin` and `ShulkerPaletteServerMixin`. Verify this method still exists in 1.21.11's `InteractionResult`.

---

## Estimated scope

Much smaller than IP. The core placement/rendering/persistence code is pure vanilla mixin work that likely compiles and runs as-is. The only rebuild work is the toggle button (~50 lines replacing ~80 lines of dead API calls). Plus one deferral (peek toggle).

**Layer 0a:** 1–2 hours (remove dead APIs, rewrite button registration, verify boot)
**Layer 1:** 2–4 hours (wire button, end-to-end verification of all features)
**Estimated total:** half a day to one day.
