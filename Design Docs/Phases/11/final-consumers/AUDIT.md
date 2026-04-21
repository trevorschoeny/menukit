# Phase 11 Final Consumers — Audit

Combined audit for sandboxes + agreeable-allays. Both are small mods with minimal MenuKit surface.

---

## Agreeable-Allays

**20 Java files.** Companion allay behavior mod — sit/follow commands, teleport rescue, delivery behavior, collision tweaks, particle effects.

**MenuKit surface:** One file (`AgreeableAllaysClient.java`) uses:
- `MKFamily` — family registration (alive)
- `MKHudPanel.builder()` — HUD panel for "Shift + Right-click: Stay/Follow" tooltip (alive)
- `MKHudAnchor.CENTER` — HUD positioning (alive)
- `PanelStyle.NONE` — transparent background (alive)
- `MenuKit` — family factory (alive)

**Compilation: passes.** All imports are living APIs. Zero code changes needed.

**Cross-mod dependency: none.** No IP imports.

**Phase 11 scope: runtime verification only.** Verify the HUD action-hint renders when looking at a bonded allay while shifting.

**Per-slot/per-entity state: none needed.** Allay sitting state uses vanilla's synched entity data (`SITTING` flag on the entity via `AllaySynchedDataMixin`). No per-slot state, no CUSTOM_DATA, no mechanism candidates.

---

## Sandboxes

**13 Java files.** Creative sandbox world copies — create, sync, rename, delete sandbox copies of survival worlds.

**MenuKit surface:** Three files import MenuKit:

| File | Imports | Status |
|------|---------|--------|
| `CreativeSandbox.java` | `MKContext` (dead), `MKPanel` (dead) | **11 compile errors** |
| `CreativeSandboxClient.java` | `MKFamily` (alive), `MenuKit` (alive) | OK |
| `SandboxScreen.java` | `MenuKit` (alive) — `buildConfigScreen` call | OK |

**What's broken:** `CreativeSandbox.initClient()` registers three inventory-context panels via the old `MKPanel.builder()` declarative API:

1. **sandbox_button** — 11x11 icon, visible in main world only, click enters recent sandbox
2. **sandbox_back_button** — 11x11 icon, visible in sandbox only, click returns to parent world
3. **sandbox_mode_label** — red "SANDBOX MODE" text, visible in sandbox only

All use `.showIn(MKContext.PERSONAL)` (inventory screen context) and `.posAboveRight()`.

**What's alive:**
- `CreativeSandboxClient` — `MKFamily.configCategory()` registration (same pattern as SP, verified working)
- `SandboxScreen` — `MenuKit.family("trevmods").buildConfigScreen(this, modId)` settings button (alive API)
- The keybind (no MenuKit dependency) works independently

**Cross-mod dependency: none.** No IP imports.

**Rebuild scope:** Replace three `MKPanel.builder()` calls with Pattern 3 injection:
- `SandboxInventoryDecoration` — two Panels (main-world button + sandbox button/label), positioned to the LEFT of IP's settings gear to avoid collision
- `SandboxInventoryMixin` on `AbstractContainerScreen` gated `instanceof InventoryScreen || CreativeModeInventoryScreen`
- Strip dead APIs from `CreativeSandbox.initClient()`

**Per-slot/per-entity state: none.** Sandbox state is file-system-level (world folders, metadata JSON).

---

## Combined findings

- **No new mechanism candidates.** Neither mod surfaces per-slot state, per-entity state, or any other primitive need. M1's evidence base stays IP-only.
- **No cross-mod dependencies.** Neither mod imports IP.
- **No deferrals expected.** Both mods' MenuKit usage can be fully rebuilt with current APIs.
- **COMMON_FRICTIONS: no new entries expected.** Sandboxes' rebuild follows the same Pattern 3 injection established by IP's settings gear and SP's toggle button.
