# Phase 11 Final Consumers — Report

Combined close-out for sandboxes + agreeable-allays under the Phase 11 / 12 / 13 arc.

---

## Agreeable-Allays

**Finding: zero code changes needed.** All MenuKit imports are living APIs. Compiles as-is. Loads and initializes in the dev client. HUD panel registered successfully (`[MenuKit] Registered HUD panel 'allay_action_hint'`).

**MenuKit surface:** `MKHudPanel.builder()` with `MKHudAnchor.CENTER`, `PanelStyle.NONE`, `.showWhen()`, `.text()` — all current Phase 10 APIs. No dead imports, no dead patterns.

**Verification:**
- Compiles: yes (already on dev classpath)
- Boots: yes
- HUD panel registration: yes (log confirmed)
- Runtime behavior: needs visual verification (shift + look at bonded allay → action hint should render)

**No deferrals. No mechanism candidates. No frictions.**

---

## Sandboxes

**11 compile errors fixed.** All in `CreativeSandbox.initClient()` from dead `MKContext` and `MKPanel.builder()` APIs.

### What shipped

**SandboxInventoryDecoration** — Pattern 3 decoration with three Panels:
1. **sandbox_enter_button** — 11x11 icon button, visible in main world when `showSandboxButton` config is enabled. Click enters most recent sandbox. `showWhen` predicate: server exists + config enabled + not in sandbox.
2. **sandbox_back_button** — 11x11 icon button, visible in sandbox. Click returns to parent world. `showWhen` predicate: server exists + config enabled + in sandbox. Same position as enter button (mutually exclusive).
3. **sandbox_mode_label** — red "SANDBOX MODE" text, visible in sandbox only. Positioned above the buttons.

All positioned to the LEFT of IP's settings gear to avoid collision (gear at `topRight(11, -4, -16)`, sandbox buttons at `topRight - 13px`).

**SandboxInventoryMixin** — on `AbstractContainerScreen`, gated `instanceof InventoryScreen || CreativeModeInventoryScreen` (replaces old `MKContext.PERSONAL`). Render TAIL + mouseClicked HEAD.

**CreativeSandbox.java** — dead API code removed. `initClient()` now registers keybind + tick handler only. Three panel methods (`enterRecentSandbox`, `openSandboxScreen`) promoted from private to public static for decoration access.

**SandboxScreen.java** — unchanged. Settings button uses `MenuKit.family("trevmods").buildConfigScreen()` which is alive API.

**CreativeSandboxClient.java** — unchanged. `MKFamily.configCategory()` registration is alive API.

### Verification

- Compiles: yes
- Boots: yes (all mixins apply, mod initializes)
- Runtime: needs visual verification (sandbox buttons in inventory, enter/exit sandbox flow)
- Config: needs verification ("Sandbox" tab in Trev's Mods config screen)

**No deferrals. No mechanism candidates. No new frictions** (same Pattern 3 + COMMON_FRICTIONS #6 mixin pattern as shulker-palette).

---

## Files changed

### Sandboxes — created
- `ui/SandboxInventoryDecoration.java` — Pattern 3 decoration (three Panels + adapters)
- `mixin/SandboxInventoryMixin.java` — render + click on AbstractContainerScreen

### Sandboxes — modified
- `CreativeSandbox.java` — dead API removed, methods promoted to public
- `sandboxes.mixins.json` — added `SandboxInventoryMixin` to client section
- `dev/build.gradle` — sandboxes enabled on dev classpath

### Agreeable-Allays — no changes

### Documentation
- `Design Docs/Phase 11/final-consumers/AUDIT.md` — combined audit
- `Design Docs/Phase 11/final-consumers/REPORT.md` — this file

---

## Phase 11 consumer-mod arc complete

All four consumer mods are now rebuilt against current MenuKit:

| Mod | Status | Deferrals | Mechanism candidates |
|-----|--------|-----------|---------------------|
| inventory-plus | Layer 1 shipped | F1–F7 | M1 (per-slot state), M3 (MKFamily) |
| shulker-palette | Layer 1 shipped | SP-F1 (peek toggle) | None (per-item state self-contained) |
| sandboxes | Layer 0a + 1 shipped | None | None |
| agreeable-allays | No changes needed | None | None |

All six mods (menukit + 4 consumers + offrail) load together in the dev client without conflict. This validates the full monorepo against current MenuKit.

**Evidence for Phase 12:** M1 (unified per-slot state) has IP-only evidence. Neither shulker-palette, sandboxes, nor agreeable-allays surfaced per-slot state needs. The per-item state negative finding from shulker-palette confirms M1 stays scoped to per-slot concerns.
