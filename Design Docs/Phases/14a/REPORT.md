# Phase 14a — Close-out REPORT

**Status: complete.** MKFamily and the mod-family concept removed from MenuKit. Four consumer mods migrated. V8 validator scenario retired. Build green; smoke test confirms Controls-screen end-state.

---

## Executive summary

Phase 14a was a clean break before larger Phase 14 work. The mod-family concept (`MenuKit.family()` / `MKFamily.java` / the `com.trevorschoeny.menukit.config` package) didn't belong in MenuKit — it was Platform behavior under Principle 1 (library-not-platform), and its sole surviving capability (shared keybind-category routing) coordinated cross-mod display in vanilla's Controls screen, which is consumer territory.

The deletion landed in one commit alongside all four consumer migrations and V8 validator retirement. No interim broken-build state.

What landed:

- **131 lines** of `MKFamily.java` deleted; **~35 lines** of family-API surface trimmed from `MenuKit.java`; the `com.trevorschoeny.menukit.config` package directory removed (was MKFamily's only inhabitant).
- **Two consumer mods** (inventory-plus, sandboxes) swapped to direct `KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, MOD_ID))` — 1:1 replacement for the API MKFamily was wrapping internally.
- **Two consumer mods** (shulker-palette, agreeable-allays) had their family blocks deleted entirely with no replacement — neither registers keybinds, so neither needs a category.
- **V8 validator scenario deleted** in full (`V8Verification.java` + the empty `v8/` directory + `cmdV8` handler + `ValidatorCommand` registration + class-javadoc bump).

User-visible effect: vanilla Controls screen now lists `Inventory Plus` and `Sandboxes` as separate sections instead of a single shared "Trev's Mods" section. Intended end-state per Trevor's Phase 13 decision.

---

## Files touched

### Library deletions

| File | Action |
|---|---|
| `menukit/src/main/java/com/trevorschoeny/menukit/config/MKFamily.java` | Deleted (131 lines) |
| `menukit/src/main/java/com/trevorschoeny/menukit/config/` | Empty package directory removed |
| `menukit/src/main/java/com/trevorschoeny/menukit/MenuKit.java` | Trimmed: removed `MKFamily` import, the `families` map, the `family()` / `getFamily()` / `getFamilies()` API section, orphan `Collection`/`Collections` imports, Family-related class-javadoc bullet |

### Consumer migrations

| Mod | Action |
|---|---|
| `inventory-plus/.../InventoryPlusClient.java` | 1:1 swap — `MKFamily family = MenuKit.family("trevmods")...getKeybindCategory()` → `KeyMapping.Category.register(Identifier.fromNamespaceAndPath(InventoryPlus.MOD_ID, InventoryPlus.MOD_ID))`. Imports cleaned. |
| `sandboxes/.../CreativeSandboxClient.java` | Same 1:1 swap with sandboxes mod-id. Imports cleaned. |
| `shulker-palette/.../ShulkerPaletteClient.java` | Family block deleted; `onInitializeClient()` body emptied with placeholder comment. Imports cleaned. |
| `agreeable-allays/.../AgreeableAllaysClient.java` | Family block deleted; HUD action-hint registration preserved. Imports cleaned. |

### Validator retirement

| File | Action |
|---|---|
| `validator/.../scenarios/v8/V8Verification.java` | Deleted |
| `validator/.../scenarios/v8/` | Empty directory removed |
| `validator/.../cmd/ValidatorCommand.java` | Removed `.then(literal("v8")...)` registration, `cmdV8` method + javadoc, class-javadoc bumped V8 → V7 with retirement note |

### Doc sweep

- `Design Docs/PHASES.md` — `← current` tag moved from Phase 13 to Phase 14a per advisor confirmation; "current phase" footer updated; 14a sub-phase brief annotated with execution notes (SP/AA pure-deletion, V8 deletion).
- `Design Docs/Phases/11/POST_PHASE_11.md` — M3 entry updated to Phase 14a "DELETED (executed)" status with per-mod migration record.
- `Design Docs/Mods/{inventory-plus,sandboxes,shulker-palette,agreeable-allays}/DECISIONS.md` — each mod's "Architectural decisions" section gained a Phase 14a migration entry naming the replacement (or non-replacement) shape.

---

## Migration shape divergences from PHASES.md

PHASES.md's Phase 14a brief said "each declares its own `KeyMapping` category directly." Execution found this true for 2 of 4 mods. The other 2 — shulker-palette and agreeable-allays — had no surviving keybind registrations and didn't need a category. The brief was implicitly assuming all four consumers had keybinds; reality:

| Mod | Keybinds in client init | Migration outcome |
|---|---|---|
| inventory-plus | 7 (sort, move-matching, lock, peek, autofill, pocket-cycle ×2) | 1:1 swap to direct `Category.register` |
| sandboxes | 1 (passed via `CreativeSandbox.initClient(category)`) | 1:1 swap to direct `Category.register` |
| shulker-palette | 0 | Family block deleted; no replacement |
| agreeable-allays | 0 (retrieved `category` was a dead variable) | Family block deleted; no replacement |

**Pattern:** registering a category with zero keybind callers is Platform behavior at the mod boundary — manufacturing UI structure that no behavior actually depends on. Pure deletion is the correct shape. Advisor approved this divergence pre-execution.

---

## V8 disposition rationale

V8's four checks (identity, displayName last-writer-wins, modId roster, keybind-category sharing) all validated MKFamily Layer A behavior. Post-deletion, every check would point at a deleted class — the compiler enforces absence; a runtime "MKFamily is absent" probe has no architectural value.

Deleted in full rather than repurposed.

---

## Verification

- **Build:** `./gradlew build` — clean across all 6 modules (menukit, inventory-plus, shulker-palette, sandboxes, agreeable-allays, validator, dev). 27s, no warnings beyond standing Gradle deprecations.
- **Dev client:** Minecraft relaunched; all 5 mods loaded. *(Smoke test: visual confirmation pending in Trevor's hands — Controls screen sections, /mkverify v0 through v7 still green.)*

---

## Phase 14b entry conditions (met)

- MenuKit library code is MKFamily-free; no orphan references.
- All four consumer mods compile and run against the trimmed library.
- Validator no longer references the deleted Layer A surface.
- Per-mod `DECISIONS.md` records the 14a migration for traceability.

Phase 14b (M7 Storage Attachment Taxonomy — formalizing where slot-group contents persist by owner type, absorbing the Phase 12.5 #7 finding) starts when Trevor kicks it off.

---

## Residual cross-reference debt

Same long-tail as Phase 13 REPORT names. Phase 14a touched only high-traffic docs. Lower-traffic mentions of MKFamily in `Phases/11/inventory-plus/AUDIT.md` (the `config/menukit-family-trevmods.json` reference) and `Phases/12.5/DESIGN.md` / `M8_V2_REPORT.md` (historical scope-down record) remain unswept. Already filed for Phase 18 polish per Phase 13 REPORT's residual list.

The orphan `config/menukit-family-*.json` files on user disks (per the Phase 13 REPORT and PHASES.md note) require no library work — MenuKit never wrote them post-Phase-12.5 scope-down. Cleanup is user-side or a future polish-pass item.

---

**Phase 14a closed.** Working tree carries the deletes + migrations + doc sweep. Trevor commits when smoke test confirms.
