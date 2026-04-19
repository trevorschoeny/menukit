# M8 — Four-Context Model + SlotGroupContext

**Status: advisor round-1 signed off; adjustments applied. Ready to implement per §14 sequencing.**

Phase 12.5's work on V2 (probe validation under vanilla chrome), M7 (chrome-aware regions), and V4 (native-screen + cross-context parity) surfaced a lurking structural issue: what MenuKit calls a "context" conflates two things — (1) an implementation boundary with its own rendering pipeline, and (2) a consumer mental model of what a panel is anchored to. The two maps have never been cleanly aligned. As long as MenuKit had two inventory-context paths (player-inventory decorations vs. container-inventory decorations) and a soft "HUD / inventory / standalone" trichotomy elsewhere, consumers and the library were doing different decompositions and meeting in the middle through convention.

This doc lands the alignment. Contexts become consumer mental models. Four contexts — **MenuContext**, **SlotGroupContext**, **HudContext**, **StandaloneContext** — cover every panel a consumer might want to place. The consumer's single question is *"what am I anchoring this to?"* and the answer names the context. The rendering pipeline underneath may share between MenuContext and SlotGroupContext (both render into the same AbstractContainerScreen render path), but that's an implementation detail below the line the consumer sees.

SlotGroupContext is the new primitive. MenuContext replaces what used to be called InventoryContext. Adapter targeting lands as part of the same change because MenuContext adapters need to declare which screens they apply to — there is no meaningful MenuContext adapter without a target.

---

## 1. Problem — "context" as implementation vs. mental model

### 1.1 What "context" meant before

Looking at Phase 12.5's V4 work and the THESIS around it, "context" was used two ways:

- **Implementation-boundary context.** Three code paths: `ScreenPanelAdapter` (inventory), `MKHudPanel` (HUD), `MenuKitScreen` (standalone). Each has its own `RenderContext` factory, its own origin-resolution path, its own input-dispatch path. V4's Principle 9 made this specific — rendering pipelines are uniform, embedding is context-specific, where "context" here meant the embedding machinery.
- **Mental-model context.** What a consumer says when describing a panel: *"this decorates the inventory screen"*, *"this is an HUD overlay"*, *"this is in a standalone screen"*. The consumer's answer is about where the panel anchors, not about which code path renders it.

The two overlapped enough that the conflation didn't break anything. But the overlap is not perfect:

- **PlayerInventoryContext vs. ContainerContext** (historical informal split — never actually separate contexts in code, but mentioned in design docs and in advisor review).  The motivation was *"these feel different because one anchors to the player inventory's shape, the other to an arbitrary container's shape."* Under a frame-anchored-only model, both are MenuContext panels with different target classes — no context split needed.
- **Decorations for a slot group, regardless of which screen renders it.** A sort button that belongs "to the inventory" should appear above the player-inventory grid whether the player is in a chest, a furnace, an anvil, or the player-inventory screen itself. Under the implementation-boundary model this is "MenuContext panel with multi-screen targeting and chrome-dependent positioning" — which is MenuContext doing something it wasn't designed for. It's actually a separate mental-model context: anchored to the slot group, not to any particular screen's frame.

Once SlotGroupContext is named explicitly, MenuContext becomes unambiguous: frame-anchored panels with declared screen targeting. The ContainerContext / PlayerInventoryContext informal split dissolves because its motivating difference was really about anchoring target, not implementation.

### 1.2 What "context" means after

A context is a consumer mental model for panel placement. Every panel belongs to exactly one context. The consumer's decomposition is the library's decomposition:

| Mental-model question | Context |
|---|---|
| *"I'm decorating the frame of this screen."* | MenuContext |
| *"I'm decorating this slot group wherever it appears."* | SlotGroupContext |
| *"I'm adding an HUD overlay."* | HudContext |
| *"I'm building my own screen."* | StandaloneContext |

The library's contexts map one-to-one with the mental-model decomposition. Implementation factoring that splits one mental-model context into two code-level contexts is rejected; implementation factoring that merges two code-level paths under one consumer mental model is rejected.

This is codified as Principle 10.

---

## 2. Two THESIS principles to codify

### 2.1 Principle 10 — Contexts are consumer mental models, not implementation boundaries

> **Contexts are consumer mental models, not implementation boundaries.** Every panel belongs to exactly one context. A consumer deciding where a panel goes answers one question: *"what is this anchored to?"* Library contexts align with the natural answers to that question — not with the rendering pipeline's internal decomposition. When consumer mental models identify a new natural anchor category, the library expresses it as a context even if the rendering pipeline underneath is shared with an existing context. Conversely, when implementation factoring would split a single mental-model anchor into multiple contexts, the split is rejected — contexts track the consumer's decomposition, not the library's.

**Test sentence (structural).** *Does this context have a distinct answer to "what am I anchoring to?" that isn't available under any existing context? If yes, it's a context. If the anchor can be expressed through an existing context plus targeting, it isn't a new context.*

The structural framing — "is this anchor category expressible under existing contexts + targeting?" — resolves debates that a subjective framing ("do consumers perceive this as distinct?") would admit. Worked example: *should PlayerInventory and Container be separate contexts?* Under the structural test, no — both are `AbstractContainerScreen` frames; the distinction is expressible through MenuContext plus `.on(Class...)` targeting. The difference consumers perceive is a targeting difference, not an anchor difference. SlotGroupContext, by contrast, passes the structural test cleanly: its anchor (a slot group's bounding box, traveling across screens) isn't expressible through MenuContext + targeting because MenuContext's anchor is always the screen frame, not an interior slot rectangle.

The test runs on anchors, not on perceptions. Subjective framings push debates about consumer mental models back to the library's maintainers; the structural framing gives a definite answer that any reader can compute.

### 2.2 Principle 11 — Evidence drives primitive scope; exhaustive coverage available when per-item cost is low and incompleteness cost is high

> **Evidence drives primitive scope; speculation defers to Rule of Three.** New primitives' shape and scope come from the concrete consumers driving them; the library doesn't design for hypothetical consumers. The default for adding entries to a catalog (contexts, categories, screen targets) is Rule of Three — wait for three concrete uses before generalizing.
>
> **Exception: exhaustive coverage when per-item cost is low and incompleteness cost is high.** When each catalog entry is cheap to add and omitted entries force consumer migrations on each discovery, exhaustive coverage at v1 is appropriate. The exception is named, not implicit — invoking it requires naming both the low per-item cost and the high incompleteness cost.

**Test sentence.** When considering a catalog addition not driven by three concrete uses, the library asks two questions: *"what's the marginal cost of including this entry?"* and *"what's the migration cost if a consumer later discovers we omitted it?"* If the first is small and the second is large, exhaustive coverage applies. Otherwise defer.

**v1 SlotGroupContext categories are the current exception invocation** — per-category cost is tiny (one enum entry + one resolver line), migration cost on discovery is high (every consumer that targeted a category discovers their panel doesn't appear on the omitted screen). Exhaustive vanilla 1.21.11 coverage applies at v1. See §6.

### 2.3 Intro sentence

Drop the count — *"The following principles govern every design decision in MenuKit"* — per the same drift problem that prompted the change from "Eight" in V4's commit. Count is not load-bearing; enumeration is.

---

## 3. The four contexts

**MenuContext.** Panels anchored to the frame of an `AbstractContainerScreen`. Consumer declares target screen classes via `.on(ClassA.class, ClassB.class)` or `.onAny()`. Chrome-aware (M7 applies). Replaces the former name "InventoryContext" throughout the library.

**SlotGroupContext.** Panels anchored to a slot group's bounding box, wherever that group renders. Consumer declares target slot-group category via `.on(SlotGroupCategory.PLAYER_INVENTORY)`. Bounds are the rectangle enclosing all slots in the category, recomputed per frame. Chrome is screen-owned, not slot-owned; SlotGroupContext does not participate in chrome.

**HudContext.** Panels in the HUD overlay. No targeting (HUD is a single surface). Unchanged structurally.

**StandaloneContext.** Panels in MenuKit-native screens opened via `MenuKitScreenHandler`. No targeting (the screen is constructed by the consumer). Unchanged structurally.

Every panel placement the consumer can describe in a mental-model sentence maps to exactly one context. If a proposed feature doesn't fit any of the four, it's either a primitive gap (name the missing context, surface for advisor) or a consumer concern (lives outside the library's contexts entirely, composed from adapter primitives).

---

## 4. MenuContext specification

### 4.1 Surface

MenuContext is the library's answer to *"decorate this screen's frame."* Panels anchored to `leftPos`/`topPos`/`imageWidth`/`imageHeight` of an `AbstractContainerScreen`. Region resolution uses `MenuRegion` (formerly `InventoryRegion`). Chrome applies via `MenuChrome` (formerly `InventoryChrome`).

### 4.2 MenuRegion

`MenuRegion` replaces `InventoryRegion`. Same eight values, same semantics — `LEFT_ALIGN_TOP`, `LEFT_ALIGN_BOTTOM`, `RIGHT_ALIGN_TOP`, `RIGHT_ALIGN_BOTTOM`, `TOP_ALIGN_LEFT`, `TOP_ALIGN_RIGHT`, `BOTTOM_ALIGN_LEFT`, `BOTTOM_ALIGN_RIGHT`. Same `isHorizontalFlow()` API. The rename is purely nomenclature.

Rationale for rename: "Inventory" was misleading (applied to every `AbstractContainerScreen`, not just `InventoryScreen` / `CreativeModeInventoryScreen`). "Menu" matches vanilla's `AbstractContainerMenu` terminology and reads plainly in *"this region is on the menu frame."*

### 4.3 Targeting

Adapters must declare which screen classes they apply to. See §7. MenuContext targeting is **class-ancestry** — a consumer targeting `ChestScreen.class` gets `ChestScreen` and all modded subclasses. The library ships no `.onAny()` syntactic shortcut that secretly means "ancestry-walk AbstractContainerScreen" — `.onAny()` is literally "all screens pass the filter" (no class check).

### 4.4 Chrome

MenuContext panels consult `MenuChrome` (formerly `InventoryChrome`) per-frame to resolve chrome-extended bounds before delegating to `RegionMath`. Existing M7 machinery carries forward unchanged except for the rename. Vanilla providers landing today (`CreativeModeInventoryScreen`, `InventoryScreen`, `CraftingScreen`) re-register against the new name.

### 4.5 Adapter type

`ScreenPanelAdapter(panel, MenuRegion)` is the constructor. The `.on(...)` / `.onAny()` method declares targeting and triggers registration with `ScreenPanelRegistry`. See §7, §8 for enforcement.

---

## 5. SlotGroupContext specification

### 5.1 Surface

SlotGroupContext is the library's answer to *"decorate this slot group wherever it appears."* Panels anchored to the bounding rectangle enclosing all slots in a named category. Categories are pure identity tags (see §5.2); the library resolves which slots in a given menu belong to which category via registered resolvers (§5.3).

### 5.2 Category = pure identity tag

A `SlotGroupCategory` is a labeled tag. It says nothing about where to render, how to stack, what to display — only *"this slot group, for targeting purposes, is named PLAYER_INVENTORY"* (or whatever).

```java
public record SlotGroupCategory(String namespace, String path) {
    public static final SlotGroupCategory PLAYER_INVENTORY =
        new SlotGroupCategory("menukit", "player_inventory");
    // ... (full list in §6)
}
```

Categories are constants, resolved by `(namespace, path)` identity. Vanilla categories live in the library; modded consumers register their own via:

```java
SlotGroupCategories.register(MyModMenu.class, menu ->
    Map.of(new SlotGroupCategory("mymod", "custom_output"), List.of(menu.slots.get(0))));
```

Same pattern as `MenuChrome.register(screenClass, provider)` — library-owned catalog, consumer-extension API identical.

### 5.3 Resolver machinery

A resolver maps an `AbstractContainerMenu` instance to `Map<SlotGroupCategory, List<Slot>>`. Library ships resolvers for every vanilla 1.21.11 menu class in §6; modded consumers register resolvers for their own menus.

```java
@FunctionalInterface
public interface SlotGroupResolver {
    Map<SlotGroupCategory, List<Slot>> resolve(AbstractContainerMenu menu);
}

public final class SlotGroupCategories {
    public static <T extends AbstractContainerMenu> void register(
            Class<T> menuClass, SlotGroupResolver resolver) { ... }

    public static Map<SlotGroupCategory, List<Slot>> of(AbstractContainerMenu menu) { ... }
}
```

**Exact-class resolution, not ancestry.** Categories split on semantically-meaningful menu boundaries (e.g., `InventoryMenu` has a 2×2 crafting grid; `CraftingMenu` has a 3×3 crafting grid — both tagged `CRAFTING_INPUT` but the underlying slot lists differ). Modded menu classes register their own resolvers rather than inheriting.

**First-registration-wins** on duplicate, same as `MenuChrome`.

### 5.4 Bounds computation

A category's bounding box in screen space is the rectangle enclosing all registered slots' on-screen positions (`screen.leftPos + slot.x`, `screen.topPos + slot.y`, plus 16×16 slot visuals).

```java
public record SlotGroupBounds(int leftPos, int topPos, int imageWidth, int imageHeight) { }
```

Computed per frame (screen leftPos/topPos shift on resize and recipe-book toggle). The category→slot mapping is cached per-screen-open; only the bounds rectangle re-runs.

**Caching constraint.** If a menu adds slots dynamically mid-session, the cached category map goes stale — the adapter renders against the originally resolved slot set and the new slots won't appear in the bounding box. Re-resolution is not supported in v1; see §12 "Runtime category mutation." Menus with dynamic slot sets (rare in vanilla; potentially present in modded menus) either (a) register a resolver that includes all eventual slots at menu-open time, or (b) live outside SlotGroupContext and decorate via the lambda escape hatch.

### 5.5 Region — SlotGroupRegion

Parallel to `MenuRegion`, eight values, different type. Same semantics applied to the slot-group bounding box rather than the screen frame. A `SlotGroupRegion.TOP_ALIGN_LEFT` panel anchors above the top-left of the slot group, stacks rightward.

Why a separate type (not shared `Region` enum): type-safety at the call site — consumer passes `SlotGroupRegion.TOP_ALIGN_LEFT` to `SlotGroupPanelAdapter`; passes `MenuRegion.TOP_ALIGN_LEFT` to `ScreenPanelAdapter`; compiler catches the mis-match. Matches how `HudRegion` / `StandaloneRegion` / `InventoryRegion` are separate today.

### 5.6 Adapter type

`SlotGroupPanelAdapter(panel, SlotGroupRegion)` is the constructor. The `.on(SlotGroupCategory...)` method declares targeting (no `.onAny()` — SlotGroupContext targeting is always explicit category enumeration; "any category" isn't a meaningful mental model).

### 5.7 Render pipeline position

SlotGroupContext panels render inside the same `AbstractContainerScreen.render` @TAIL hook as MenuContext panels, dispatched by the same `ScreenPanelRegistry`. The distinction is anchor computation (frame vs. slot-group bounds) and targeting (class vs. category). Pipeline-wise: same path, different inputs. Consistent with Principle 9 — uniform pipeline, context-specific embedding (here: context-specific anchor source).

### 5.8 Chrome, visibility, overflow — inherited semantics

- **Chrome.** SlotGroupContext bounding box is always inside the frame — slot groups render in-frame — so M7/MenuChrome doesn't apply. No separate chrome registry for slot groups.
- **Visibility.** Same `panel.isVisible()` gate as every other context.
- **Overflow.** Same `RegionMath.resolveInventory` overflow cutoff applies; the bounds argument is the slot-group bounding box rather than the frame. (`RegionMath` stays agnostic — it takes a bounds rectangle.)
- **Stacking + padding.** Same `RegionRegistry.axialPrefix` path, but registered against `SlotGroupCategory + SlotGroupRegion` rather than `MenuRegion`. Parallel registry state.

---

## 6. Category list (vanilla 1.21.11, source-verified)

Verified against `net/minecraft/world/inventory/*.java` at `loom-cache/minecraftMaven/.../1.21.11-loom...-sources.jar`. Slot indices are authoritative from each menu's addSlot ordering.

Per Principle 11 with its exhaustive-coverage exception: per-category cost is one enum entry + one resolver line; incompleteness cost is consumer-migration-on-discovery. Shipping vanilla-complete at v1.

### 6.1 Player-scoped — all resolved from InventoryMenu + any menu with `addStandardInventorySlots`

| Category | Slots | Source |
|---|---|---|
| `PLAYER_INVENTORY` | 27 main inv slots (indices vary per menu; consistently the main 3×9 grid) | `Inventory.addStandardInventorySlots` |
| `PLAYER_HOTBAR` | 9 hotbar slots in the screen | same, last row |
| `PLAYER_ARMOR` | 4 armor slots (head / chest / legs / feet) | `InventoryMenu` slots 5–8 |
| `PLAYER_OFFHAND` | 1 offhand slot | `InventoryMenu` slot 45 |

Every menu that calls `addStandardInventorySlots` contributes `PLAYER_INVENTORY` and `PLAYER_HOTBAR`. Only `InventoryMenu` (the player-inventory screen and, via `CreativeModeInventoryScreen`, creative's INVENTORY tab) contributes `PLAYER_ARMOR` and `PLAYER_OFFHAND`.

### 6.2 Storage containers

| Category | Slots | Source |
|---|---|---|
| `CHEST_STORAGE` | 27 or 54 (single / double) | `ChestMenu` — 3×9 or 6×9 grid |
| `SHULKER_STORAGE` | 27 | `ShulkerBoxMenu` |
| `DISPENSER_STORAGE` | 9 (3×3) | `DispenserMenu` — dispenser + dropper |
| `HOPPER_STORAGE` | 5 (1×5) | `HopperMenu` |

**Note on lumping vs. splitting.** Advisor's brief proposed a single `GENERAL_STORAGE` category lumping chest/shulker/barrel/dispenser/dropper. This doc splits them because (a) sizes differ substantially (27 vs. 9 vs. 5), which breaks slot-group bounds continuity; (b) semantically they feel distinct — a "decorate the 3×3 dispenser grid" target shouldn't fire for a 54-slot double chest; (c) Rule of Three isn't violated by splitting cheaply. **Open question for advisor — accept split or revert to lumped.** §13 Q1.

(Barrel uses `ChestMenu` with container-size 27 — contributes `CHEST_STORAGE`, not a separate category.)

### 6.3 Crafting family

| Category | Slots | Source |
|---|---|---|
| `CRAFTING_INPUT` | 2×2 or 3×3 grid | `AbstractCraftingMenu` — `InventoryMenu` (2×2), `CraftingMenu` (3×3) |
| `CRAFTING_OUTPUT` | 1 | same, `RESULT_SLOT` |
| `CRAFTER_GRID` | 3×3 (9) | `CrafterMenu` — auto-crafter |
| `CRAFTER_RESULT` | 1 | `CrafterMenu.resultContainer` — **new in doc, missing from advisor brief** |

**Note.** Advisor's brief listed only `CRAFTER_GRID`. `CrafterMenu` has a result container too (exposed via `getResultSlot`). Including for symmetry with other input/output pairs. §13 Q2.

### 6.4 Furnace family — shared across Furnace / Smoker / BlastFurnace via `AbstractFurnaceMenu`

| Category | Slot | Source |
|---|---|---|
| `FURNACE_INPUT` | 1 | `AbstractFurnaceMenu` slot 0 |
| `FURNACE_FUEL` | 1 | same, slot 1 |
| `FURNACE_OUTPUT` | 1 | same, slot 2 |

### 6.5 Utility blocks with slots

| Category | Slots | Source |
|---|---|---|
| `ENCHANTING_INPUT` | 1 | `EnchantmentMenu` slot 0 |
| `ENCHANTING_LAPIS` | 1 | `EnchantmentMenu` slot 1 |
| `ANVIL_INPUT` | 2 | `AnvilMenu` / `ItemCombinerMenu` slots 0–1 |
| `ANVIL_OUTPUT` | 1 | `AnvilMenu` slot 2 |
| `GRINDSTONE_INPUT` | 2 | `GrindstoneMenu` slots 0–1 |
| `GRINDSTONE_OUTPUT` | 1 | `GrindstoneMenu` slot 2 |
| `SMITHING_TEMPLATE` | 1 | `SmithingMenu` slot 0 |
| `SMITHING_BASE` | 1 | `SmithingMenu` slot 1 |
| `SMITHING_ADDITION` | 1 | `SmithingMenu` slot 2 |
| `SMITHING_OUTPUT` | 1 | `SmithingMenu` slot 3 |
| `LOOM_BANNER` | 1 | `LoomMenu` slot 0 |
| `LOOM_DYE` | 1 | `LoomMenu` slot 1 |
| `LOOM_PATTERN` | 1 | `LoomMenu` slot 2 |
| `LOOM_OUTPUT` | 1 | `LoomMenu` slot 3 |
| `STONECUTTER_INPUT` | 1 | `StonecutterMenu` slot 0 |
| `STONECUTTER_OUTPUT` | 1 | `StonecutterMenu` slot 1 |
| `CARTOGRAPHY_MAP` | 1 | `CartographyTableMenu` slot 0 — **new in doc, missing from advisor brief** |
| `CARTOGRAPHY_ADDITIONAL` | 1 | `CartographyTableMenu` slot 1 — **new in doc** |
| `CARTOGRAPHY_OUTPUT` | 1 | `CartographyTableMenu` slot 2 — **new in doc** |

**Note.** Advisor brief missed `CartographyTableMenu`. Including. §13 Q3.

### 6.6 Brewing

| Category | Slots | Source |
|---|---|---|
| `BREWING_POTIONS` | 3 | `BrewingStandMenu` slots 0–2 |
| `BREWING_INGREDIENT` | 1 | slot 3 |
| `BREWING_FUEL` | 1 | slot 4 |

### 6.7 Trading

| Category | Slots | Source |
|---|---|---|
| `MERCHANT_PAYMENT` | 2 | `MerchantMenu` slots 0–1 (PAYMENT1_SLOT, PAYMENT2_SLOT) |
| `MERCHANT_RESULT` | 1 | `MerchantMenu` slot 2 (RESULT_SLOT) |

**Note on naming.** Advisor brief called these `MERCHANT_OFFER` / `MERCHANT_RESULT`. Vanilla field names are `PAYMENT1_SLOT` / `PAYMENT2_SLOT` / `RESULT_SLOT`. Renaming to `MERCHANT_PAYMENT` for source-fidelity. §13 Q4.

### 6.8 Beacon

| Category | Slot | Source |
|---|---|---|
| `BEACON_PAYMENT` | 1 | `BeaconMenu` slot 0 |

### 6.9 Mount (horse / donkey / mule / llama / nautilus) — shared via `AbstractMountInventoryMenu`

| Category | Slot | Source |
|---|---|---|
| `MOUNT_SADDLE` | 1 | `AbstractMountInventoryMenu` slot 0 |
| `MOUNT_BODY_ARMOR` | 1 | same, slot 1 |
| `MOUNT_STORAGE` | 0–15 (conditional on `HorseInventoryMenu` chest capacity) | `HorseInventoryMenu` slots 2+ |

**Note on naming.** Advisor brief used `HORSE_*`. Vanilla 1.21.11 factored the mount inventory into `AbstractMountInventoryMenu` with subclasses `HorseInventoryMenu` and `NautilusInventoryMenu` (Nautilus mob). `BODY_ARMOR` is vanilla's generic term (covers horse-armor, llama-carpet, nautilus-specific body items). Renaming `HORSE_*` → `MOUNT_*` for source-fidelity and correct coverage of Nautilus mounts. §13 Q5.

`MOUNT_STORAGE` applies only to `HorseInventoryMenu` subtypes with `j > 0` (donkey / mule / llama). `NautilusInventoryMenu` doesn't have a storage grid. Resolver returns empty list for those cases.

### 6.10 Deferred — not in v1

- **`LECTERN_BOOK`** — `LecternMenu` has a single book slot, but the lectern screen is a book reader, not a typical inventory UI. Decorating it isn't a natural consumer use. Not shipped; register if demand surfaces.
- **Intermediate abstract classes.** `ItemCombinerMenu`, `AbstractCraftingMenu`, `AbstractFurnaceMenu`, `AbstractMountInventoryMenu`, `RecipeBookMenu` — base classes. Resolvers live on the concrete subclasses. Not separately registered.

### 6.11 Coverage summary

| Menu class | Resolver categories |
|---|---|
| `InventoryMenu` | PLAYER_INVENTORY, PLAYER_HOTBAR, PLAYER_ARMOR, PLAYER_OFFHAND, CRAFTING_INPUT (2×2), CRAFTING_OUTPUT |
| `ChestMenu` | CHEST_STORAGE, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `ShulkerBoxMenu` | SHULKER_STORAGE, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `DispenserMenu` | DISPENSER_STORAGE, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `HopperMenu` | HOPPER_STORAGE, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `CraftingMenu` | CRAFTING_INPUT (3×3), CRAFTING_OUTPUT, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `CrafterMenu` | CRAFTER_GRID, CRAFTER_RESULT, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `FurnaceMenu`, `SmokerMenu`, `BlastFurnaceMenu` | FURNACE_INPUT, FURNACE_FUEL, FURNACE_OUTPUT, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `EnchantmentMenu` | ENCHANTING_INPUT, ENCHANTING_LAPIS, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `AnvilMenu` | ANVIL_INPUT, ANVIL_OUTPUT, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `GrindstoneMenu` | GRINDSTONE_INPUT, GRINDSTONE_OUTPUT, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `SmithingMenu` | SMITHING_TEMPLATE, SMITHING_BASE, SMITHING_ADDITION, SMITHING_OUTPUT, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `LoomMenu` | LOOM_BANNER, LOOM_DYE, LOOM_PATTERN, LOOM_OUTPUT, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `StonecutterMenu` | STONECUTTER_INPUT, STONECUTTER_OUTPUT, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `CartographyTableMenu` | CARTOGRAPHY_MAP, CARTOGRAPHY_ADDITIONAL, CARTOGRAPHY_OUTPUT, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `BrewingStandMenu` | BREWING_POTIONS, BREWING_INGREDIENT, BREWING_FUEL, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `MerchantMenu` | MERCHANT_PAYMENT, MERCHANT_RESULT, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `BeaconMenu` | BEACON_PAYMENT, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `HorseInventoryMenu` | MOUNT_SADDLE, MOUNT_BODY_ARMOR, MOUNT_STORAGE (conditional), PLAYER_INVENTORY, PLAYER_HOTBAR |
| `NautilusInventoryMenu` | MOUNT_SADDLE, MOUNT_BODY_ARMOR, PLAYER_INVENTORY, PLAYER_HOTBAR |
| `LecternMenu` | (deferred — §6.10) |

Total: **20 vanilla menu resolvers**, **36 category constants** (count tentative until §13 questions resolved).

---

## 7. Adapter targeting

### 7.1 MenuContext — class-ancestry

```java
// Covers InventoryScreen + CreativeModeInventoryScreen (which extends InventoryScreen) +
// any modded subclass of either.
new ScreenPanelAdapter(panel, MenuRegion.TOP_ALIGN_RIGHT)
    .on(InventoryScreen.class, CreativeModeInventoryScreen.class);

// Covers every AbstractContainerScreen that opens.
new ScreenPanelAdapter(panel, MenuRegion.TOP_ALIGN_RIGHT)
    .onAny();
```

`.on(Class...)` accepts one or more screen classes. Ancestry resolution: on screen-open, the library checks `screen.getClass()` against each target with `isAssignableFrom` — if any target is an ancestor of the opened screen, the adapter fires.

`.onAny()` bypasses the class check — the adapter fires for every opened `AbstractContainerScreen`. Separate intent from `.on(AbstractContainerScreen.class)` (which would do the same thing via ancestry); `.onAny()` is the named-intent form.

### 7.2 SlotGroupContext — exact-category

```java
// Single category
new SlotGroupPanelAdapter(panel, SlotGroupRegion.TOP_ALIGN_RIGHT)
    .on(SlotGroupCategory.PLAYER_INVENTORY);

// Multiple categories (the panel fires on any screen where any listed category resolves)
new SlotGroupPanelAdapter(panel, SlotGroupRegion.TOP_ALIGN_RIGHT)
    .on(SlotGroupCategory.FURNACE_INPUT, SlotGroupCategory.SMOKER_INPUT);
    // (FURNACE_INPUT covers all three furnace variants — this example is illustrative only)
```

No `.onAny()` for SlotGroupContext. *"Any slot group"* isn't a consumer mental model; naming the category is the whole point.

No inheritance — categories are pure tags (§5.2).

### 7.3 Default = none (required declaration) — applies to region-based adapters only

A *region-based* adapter — constructed via `new ScreenPanelAdapter(panel, MenuRegion)` or `new SlotGroupPanelAdapter(panel, SlotGroupRegion)` — must declare targeting via `.on(...)` or `.onAny()`. Construction without targeting is incomplete and never renders. The library enforces this via a lifecycle checkpoint: on the first `ScreenEvents.AFTER_INIT` firing (any screen opened), `ScreenPanelRegistry` walks all constructed region-based adapters; any without declared targets logs a `LOGGER.error` naming the adapter's panel ID and throws `IllegalStateException` to fail the client boot visibly.

Construction adds the adapter to a tracking weak-set; `.on(...)` / `.onAny()` removes it. If the set is non-empty at the lifecycle checkpoint, the unfinished adapters are flushed via the error path.

**Lambda-based adapters are exempt.** Adapters constructed via `new ScreenPanelAdapter(panel, ScreenOriginFn)` (the lambda / escape-hatch path, see §8.4) don't participate in `ScreenPanelRegistry` and don't require targeting. Lambda adapters rely on the consumer's own mixin to scope which screens they render on; the library doesn't mediate that. The targeting requirement only makes sense for adapters the registry manages, which is the region-based path.

This is "build-time-ish" error in the practical Java sense — fails at client-boot-time before any real render, visible in the log, un-ignorable. Not pure compile-time (Java idioms don't support that for chain-style builders), but functionally equivalent for the consumer experience.

### 7.4 Missing category on screen-open

When a SlotGroupContext adapter targets categories, and an opened screen's resolver produces none of the targeted categories, the adapter silently skips that screen. Not an error — expected: a `PLAYER_INVENTORY` adapter doesn't fire on a `LecternScreen` (which has no player inventory in vanilla's lectern UI).

---

## 8. Library-owned listener pipeline

### 8.1 Current state — consumer writes the listener

Today, a consumer wiring a MenuContext adapter writes:

```java
ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
    if (!(screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen)) return;
    var acs = (AbstractContainerScreen<?>) screen;
    ScreenEvents.afterRender(screen).register((s, g, mx, my, dt) -> ADAPTER.render(g, bounds(acs), mx, my, acs));
    ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> {
        ADAPTER.mouseClicked(bounds(acs), e.x(), e.y(), e.button(), acs);
        return true;
    });
});
```

Every consumer writes this same boilerplate with their own screen-class filter. The filter logic (class check, cast, render/click wiring) is library-shaped, not consumer-shaped.

### 8.2 New state — library owns it

```java
public final class ScreenPanelRegistry {
    /** Called by adapter .on(...) / .onAny() completion. */
    static void register(AdapterRegistration reg) { ... }

    /** Called once from MenuKitClient.onInitializeClient. */
    public static void init() {
        ScreenEvents.AFTER_INIT.register(ScreenPanelRegistry::onScreenInit);
    }

    private static void onScreenInit(Minecraft client, Screen screen, int sw, int sh) {
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
        checkOrphanAdapters();  // lifecycle checkpoint for §7.3

        // MenuContext dispatch — filter adapters by class-ancestry.
        List<MenuAdapterRegistration> menuMatches = registeredMenuAdapters.stream()
            .filter(r -> r.matches(screen.getClass()))
            .toList();

        // SlotGroupContext dispatch — resolve categories for this menu, then filter adapters.
        Map<SlotGroupCategory, List<Slot>> resolved = SlotGroupCategories.of(acs.getMenu());
        List<SlotGroupAdapterRegistration> sgMatches = registeredSlotGroupAdapters.stream()
            .filter(r -> r.targets().stream().anyMatch(resolved::containsKey))
            .toList();

        // Per-screen Fabric hooks (auto-cleaned when screen closes)
        ScreenEvents.afterRender(screen).register((s, g, mx, my, dt) -> {
            for (var r : menuMatches)  r.adapter().render(g, frameBounds(acs), mx, my, acs);
            for (var r : sgMatches)    r.adapter().render(g, slotGroupBounds(acs, r, resolved), mx, my, acs);
        });
        ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> {
            for (var r : menuMatches)  r.adapter().mouseClicked(frameBounds(acs), e.x(), e.y(), e.button(), acs);
            for (var r : sgMatches)    r.adapter().mouseClicked(slotGroupBounds(acs, r, resolved), e.x(), e.y(), e.button(), acs);
            return true;  // defer cancellation decision to consumer mixin (Phase 13a migration)
        });
    }
}
```

Consumer code shrinks to adapter declaration:

```java
new ScreenPanelAdapter(panel, MenuRegion.TOP_ALIGN_RIGHT)
    .on(InventoryScreen.class, CreativeModeInventoryScreen.class);
```

No more `ScreenEvents.AFTER_INIT` boilerplate in consumers. No more class-filter duplication. The library owns the hook.

### 8.3 Cancellation decision

The current per-consumer pattern returns `true` from `allowMouseClick` to let vanilla process clicks that miss the panel. The library-owned pipeline keeps this default. v1 ships the simple `return true` default.

**Named future-extension shape.** When a Phase 13 consumer needs the alternative — "if my panel consumed the click, cancel vanilla" — the intended API is a configuration on the adapter declared at construction time:

```java
new ScreenPanelAdapter(panel, MenuRegion.TOP_ALIGN_RIGHT)
    .on(InventoryScreen.class)
    .cancelsUnhandledClicks(true);  // default: false
```

Naming the future shape now means Phase 13 consumers hitting this case know to request the extension (or stub it as-needed) rather than rolling a parallel mixin workaround. The method is not shipped in v1; the named shape is the placeholder.

Consumers whose panels need richer input behavior than the `cancelsUnhandledClicks` flag can express (e.g., selective cancellation based on hit-test zone) fall back to the lambda-adapter path with a consumer-owned mixin (§8.4).

### 8.4 API visibility — region path vs. lambda path

The registry pipeline dispatches render / input to region-based adapters by calling their `render(...)` / `mouseClicked(...)` methods. These methods stay **public** — not package-private — because lambda-based adapters continue to need them for direct invocation from consumer mixins.

- **Region-based adapter:** `new ScreenPanelAdapter(panel, MenuRegion)` → registered with `ScreenPanelRegistry` via `.on(...)` / `.onAny()` → registry calls `render` / `mouseClicked` per-frame.
- **Lambda-based adapter:** `new ScreenPanelAdapter(panel, ScreenOriginFn)` → not registered with `ScreenPanelRegistry` → consumer's own mixin calls `render` / `mouseClicked` directly.

Both paths use the same public methods; different ownership of the call site. The only "API change" at the method-signature level is that `ScreenOriginFn.compute(bounds, screen)` takes the screen parameter (landed in the M7 commit). No API gets removed.

**`RegionProbes.renderInventoryProbes(...)` transition.** Probes currently render via `ProbeRenderMixin` walking `RegionProbes`' adapter list directly. Post-M8, probes should use `.onAny()` on their region-based adapters and let the registry dispatch them. The `renderInventoryProbes` static method and the two probe mixins (`ProbeRenderMixin`, `ProbeRenderRecipeBookMixin`) become obsolete — their job transfers to the registry. Confirmed during V2 close-out.

---

## 9. M7 interaction and rename (InventoryChrome → MenuChrome)

M7 machinery stays structurally identical. What changes:

- `InventoryChrome` class → `MenuChrome` class.
- `ChromeExtents` record unchanged (same fields, same `NONE` constant).
- `ChromeProvider` functional interface unchanged.
- `InventoryChrome.register(screenClass, provider)` → `MenuChrome.register(screenClass, provider)`.
- `InventoryChrome.of(screen)` → `MenuChrome.of(screen)`.
- Vanilla provider registrations in `MenuKitClient.registerVanillaInventoryChrome` — method renamed to `registerVanillaMenuChrome`, same three provider registrations (CreativeModeInventoryScreen, InventoryScreen, CraftingScreen).

**SlotGroupContext does not have chrome.** Slot groups render inside the screen frame; their bounding box is already inside the chrome-extended region. A panel anchored to a slot group's edge is anchored inside the frame too. Chrome is the screen's concern, not the slot group's.

MenuContext panels continue to benefit from M7 exactly as today. `RegionRegistry.menuOriginFn` (renamed from `inventoryOriginFn`) consults `MenuChrome.of(screen)` per frame, extends bounds, delegates to `RegionMath.resolveMenu` (renamed from `resolveInventory`).

---

## 10. M5 §11 non-goal amendment

Current §11 reads (after M7 landed):

> Vanilla-HUD-element awareness. Regions do not know about vanilla hotbar / XP bar / boss bar / chat. Consumers that need clearance from vanilla HUD use the `.anchor(...)` path with manual offset (see §5.4). **Inventory chrome** (creative tabs, recipe book widget) is handled by M7 — see `Phase 12.5/M7_CHROME_AWARE_REGIONS.md`. Consumers get chrome-aware region placement automatically; modded screens register their own chrome extents via `InventoryChrome.register(...)`.

Replacement text:

> **Chrome awareness is scoped per context.** *MenuContext* chrome (creative tabs, recipe book widget, any AbstractContainerScreen drawing outside its declared frame) is library-owned via M7/MenuChrome — see `Phase 12.5/M7_CHROME_AWARE_REGIONS.md`. Consumers get chrome-aware region placement automatically; modded screens register their own chrome extents via `MenuChrome.register(...)`. *HudContext* chrome (vanilla hotbar, XP bar, boss bar, chat) remains a non-goal — HUD panels needing clearance from vanilla HUD elements solve locally via manual offset. *SlotGroupContext* and *StandaloneContext* do not have chrome concerns — slot-group bounding boxes are always inside the screen frame (chrome is the parent screen's problem), and standalone screens are MenuKit-owned end-to-end.

References to "InventoryContext" elsewhere in M5 update to "MenuContext." §11's other non-goals (grafted-slot backdrop panels, dynamic panel construction, vanilla-menu-element-anchored regions, priority stacking, user override) are unchanged, but text explaining them drops "inventory-context" framing where it appears.

**Grafted-slot backdrop panels (M4 F8/F15 consumers) migrate to SlotGroupContext.** This is the natural home for them — a grafted-slot group tagged with a consumer-registered category, decorated via `SlotGroupPanelAdapter`. The current "fixed-anchor" non-goal language (§11 bullet on grafted-slot backdrops) drops — the mechanism exists now.

---

## 11. Migration list

### 11.1 Core library renames (mechanical)

| From | To |
|---|---|
| `core.InventoryRegion` | `core.MenuRegion` |
| `inject.InventoryChrome` | `inject.MenuChrome` |
| `RegionRegistry.registerInventory` | `RegionRegistry.registerMenu` |
| `RegionRegistry.inventoryOriginFn` | `RegionRegistry.menuOriginFn` |
| `RegionRegistry.axialPrefix(Panel, InventoryRegion)` | `RegionRegistry.axialPrefix(Panel, MenuRegion)` |
| `RegionMath.resolveInventory` | `RegionMath.resolveMenu` |
| `ScreenPanelAdapter(Panel, InventoryRegion)` | `ScreenPanelAdapter(Panel, MenuRegion)` |
| `MenuKitClient.registerVanillaInventoryChrome` | `registerVanillaMenuChrome` |
| private `RegionRegistry.INVENTORY` map | `RegionRegistry.MENU` |
| private `RegionRegistry.INVENTORY_PADDING` map | `RegionRegistry.MENU_PADDING` |
| private `RegionRegistry.WARNED_INVENTORY` map | `RegionRegistry.WARNED_MENU` |

### 11.2 New library code

- `core/SlotGroupRegion.java` — enum, parallel to `MenuRegion`.
- `core/SlotGroupCategory.java` — record, holds vanilla constants (§6).
- `core/SlotGroupResolver.java` — functional interface.
- `inject/SlotGroupCategories.java` — registry, `register` + `of` API.
- `inject/SlotGroupPanelAdapter.java` — adapter, parallel to `ScreenPanelAdapter`.
- `inject/ScreenPanelRegistry.java` — library-owned listener pipeline.
- `MenuKitClient.registerVanillaSlotGroupResolvers` — 20 resolver registrations (§6.11).
- THESIS.md — Principles 10 and 11.

### 11.3 Consumer call-site migrations — Phase 12.5 scope = mechanical renames only

Phase 12.5 is a library-restructure phase, not a consumer-migration phase. Consumer modules (inventory-plus, sandboxes, shulker-palette) are touched only for mechanical renames in file references (`InventoryRegion` → `MenuRegion`, imports updated, etc.). Their adapter shape — lambda-based `ScreenOriginFn` with consumer-owned mixin scoping — stays unchanged. Any decision to migrate these to the new region-based targeting API is Phase 13a's call, per-consumer.

Matches how M5 and M7 bounded their scope: landed library primitives, deferred consumer migrations. M7's §11.3 list called out the same deferral; M8 follows the pattern.

**In-scope for Phase 12.5:**

| Consumer | Change |
|---|---|
| `validator/V4CrossInventoryDecoration` | Full migration: `new ScreenPanelAdapter(PANEL, MenuRegion.RIGHT_ALIGN_TOP).on(InventoryScreen.class, CreativeModeInventoryScreen.class);` — the per-screen `ScreenEvents.AFTER_INIT` + `afterRender` + `allowMouseClick` boilerplate deletes. Validator is part of this restructure's scope; not a Phase 13 consumer. |
| `validator/V2Verification` | Panels `.onAny()` since V2's test setup exercises all screen classes. |
| `menukit/verification/RegionProbes` | Probe adapters use `.onAny()`; `ProbeRenderMixin` and `ProbeRenderRecipeBookMixin` become obsolete (registry dispatches the probes). Noted in §8.4. |

**Mechanical-rename-only in Phase 12.5 (adapter shape unchanged):**

| Consumer | Current shape | Phase 12.5 change |
|---|---|---|
| `inventory-plus/SettingsGearDecoration` | Lambda `ScreenOriginFn` + custom mixin for screen scoping | Zero (lambda path doesn't reference `InventoryRegion`). |
| `sandboxes/SandboxInventoryDecoration` | Three lambda adapters + sandbox mixin | Zero. |
| `shulker-palette/PaletteToggleDecoration` | Lambda `ScreenOriginFn` + mixin | Zero. |
| menukit example mixins (5 files) | Custom mixins with lambda adapters | Zero if no `InventoryRegion` reference; otherwise rename import. |

**Deferred to Phase 13a:** per-consumer decision on whether to migrate to the region-based targeting API. Lambda path stays viable indefinitely — it's the documented escape hatch for consumers whose anchoring doesn't fit any of the four contexts' regions.

### 11.4 Validator migrations

- `V4CrossInventoryDecoration` — canonical example of MenuContext-with-targeting migration.
- `V4CrossHud` — no change (HudContext).
- `V4CrossStandaloneScreen` — no change (StandaloneContext).
- `V2Verification` — verify panels get `.onAny()` or `.on(specific-test-screen-class)` as needed.
- `RegionProbes` (menukit) — migrate probe registration to new targeting API; probes should be `.onAny()` since they're meant to fire on every screen.

### 11.5 THESIS.md

Add Principles 10 and 11 in full (§2.1, §2.2). Update intro sentence per §2.3.

### 11.6 M5, M7 design docs

- M5 §11: amend per §10 above.
- M5 elsewhere: rename "InventoryContext" / "InventoryRegion" references to "MenuContext" / "MenuRegion".
- M7: rename "InventoryChrome" references to "MenuChrome". Update the Status section to note the rename.

### 11.7 Phase 12.5 DESIGN.md

V2 section and elsewhere: rename "InventoryContext" → "MenuContext". Add SlotGroupContext references where V2 probe scope expands (§3 below).

---

## 12. Non-goals

- **"Any category" targeting for SlotGroupContext.** No `.onAny()`. Categories are the point.
- **Category inheritance.** `PLAYER_INVENTORY` is not a "parent" of anything. Tags are flat.
- **Chrome for slot groups.** The slot group's bounding box is inside the screen frame; the screen's chrome is handled by M7 at the screen level.
- **Runtime category mutation.** A menu's resolver returns its category map once per screen-open; re-resolution is not supported. If a menu adds slots dynamically mid-session (rare), the consumer rebuilds the screen.
- **Multiple region systems per context.** Each context has one Region enum (`MenuRegion`, `SlotGroupRegion`, `HudRegion`, `StandaloneRegion`). No sub-regions or nested regions in v1.
- **Back-compat shims for the rename.** `InventoryRegion` is removed, not deprecated. Phase 12.5 is pre-1.0; breaking rename is cheap. See Principle 11's evidence basis.
- **Unregister / removal APIs.** Adapters and resolvers are process-lifetime, same as M7.

---

## 13. Advisor round-1 resolutions

All ten open questions from the round-1 draft confirmed by advisor. Resolutions captured here for audit trail; doc body (§5–§12) updated to reflect them.

**Q1 — Storage category split.** **Accepted.** `CHEST_STORAGE` (chest + barrel), `SHULKER_STORAGE`, `DISPENSER_STORAGE`, `HOPPER_STORAGE`. Size differences and semantic distinctions warrant split; Principle 11's exhaustive-coverage exception applies.

**Q2 — `CRAFTER_RESULT`.** **Added.** Symmetry with `CRAFTING_INPUT`/`CRAFTING_OUTPUT` is correct.

**Q3 — Cartography categories.** **Added.** `CARTOGRAPHY_MAP`, `CARTOGRAPHY_ADDITIONAL`, `CARTOGRAPHY_OUTPUT`. Brief omission caught via source verification.

**Q4 — Merchant naming.** **`MERCHANT_PAYMENT` accepted.** Source-fidelity wins over advisor's `MERCHANT_OFFER` phrasing.

**Q5 — Mount naming.** **`MOUNT_*` accepted.** Covers Nautilus mount correctly; advisor's `HORSE_*` would have mis-named for non-horse mounts.

**Q6 — Lambda adapters and targeting.** **Lambda adapters exempt.** They stay the escape hatch with consumer-owned mixin scoping; only region-based adapters participate in `ScreenPanelRegistry`. See §7.3 (targeting-requirement scoping) and §8.4 (two-path API visibility).

**Q7 — `.onAny()` semantics.** **Confirmed.** `.onAny()` fires for every opened `AbstractContainerScreen` — `CreativeModeInventoryScreen`, modded screens, future screens included. Consumer's explicit declaration; narrower scoping uses `.on(Class...)`.

**Q8 — `.on(Class...)` multi-target semantics.** **OR confirmed.** Any target being an ancestor of the opened screen class fires. (AND would be meaningless under Java's single-inheritance model.)

**Q9 — Build-time-ish enforcement shape.** **Chain-style with boot-checkpoint accepted.** Constructor-required-target loses chain ergonomics without enough benefit; boot-checkpoint fails visibly at client-boot before any real render — functionally equivalent to compile-time for consumer experience.

**Q10 — Phase 13a timing for existing consumers.** **Deferred.** Existing IP/sandboxes/shulker-palette decorations stay on lambda path through Phase 12.5; per-consumer migration decisions happen in Phase 13a. Phase 12.5 touches them only for mechanical renames. §11.3 rewritten to reflect this bounded scope.

---

## 14. Work sequencing

Per brief's sequencing, executed after round-1 sign-off:

1. Doc lands as committed.
2. `MenuRegion` rename (mechanical).
3. Adapter targeting (MenuContext `.on`/`.onAny`).
4. `ScreenPanelRegistry` listener pipeline.
5. SlotGroupContext machinery (`SlotGroupRegion`, `SlotGroupCategory`, `SlotGroupCategories` registry, `SlotGroupPanelAdapter`, 20 vanilla resolvers).
6. THESIS.md Principles 10 + 11.
7. M5 §11 amendment, M7 rename, Phase 12.5 DESIGN updates.
8. V2 resumes against new model: probe mixin guard removal, MenuContext probes, SlotGroupContext probes (PLAYER_INVENTORY + CHEST_STORAGE), chrome-adaptation test on CraftingScreen / FurnaceScreen / SmokerScreen / BlastFurnaceScreen.

Phase 12.5 close-out (task #59) captures the reframe as highest-value output alongside Principles 9, 10, 11 + M7 + adapter targeting + SlotGroupContext.

---

## 15. Principle 9 continuity note

This restructure is the third Principle 9 instance (after ScreenPanelAdapter completeness and M7). Apply the Principle 9 test: *"when a rendering behavior varies between contexts, does the variation have a named reason rooted in the screen's relationship to gameplay, not in the container's implementation?"*

MenuContext's `TOP_ALIGN_LEFT` vs. SlotGroupContext's `TOP_ALIGN_LEFT` produce different visible placements. Is there a gameplay-rooted reason? **Yes.** MenuContext anchors to "the screen's frame" — a gameplay concept (the screen is a UI surface). SlotGroupContext anchors to "the slot group's bounding box" — also a gameplay concept (the slot group is a categorized inventory section). The variation is gameplay-rooted; both contexts are principled.

Contrast: a hypothetical "InventoryContext vs. ContainerContext" split, where `TOP_ALIGN_LEFT` differs because one is InventoryScreen and one is ChestScreen — the variation is implementation-rooted (different screen subclasses). Principle 9 flags it. SlotGroupContext passes the test because "slot group" is a gameplay concept, not an implementation boundary.

---

**End of M8.** Advisor round-1 signed off (§13). Implementation proceeds per §14 sequencing: MenuRegion rename → adapter targeting → ScreenPanelRegistry → SlotGroupContext machinery → THESIS Principles 10+11 → M5/M7/DESIGN updates → V2 resumes.
