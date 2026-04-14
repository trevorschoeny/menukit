# ItemDisplay — design doc

**Element purpose.** Renders an `ItemStack` at a fixed position with optional count overlay and durability bar. No interaction. The "show an item" primitive, distinct from a slot (no sync, no storage binding, no mutation).

**Generalization from existing code.** MKHudItem exists as a HUD-specific draft of this element. Phase 8 promotes it to core/ and deletes the HUD-specific class. The HUD builder's `.item(...)` method retargets to produce core ItemDisplay.

**Works in all three contexts.** Render-only.

---

## Conventions pressure-test

ItemDisplay is the first element that existed in draft form before its conventions were established. This doc tests whether Phase 8's conventions hold against pre-existing code, or whether the code forces convention adjustments.

### Convention 1 — Constructor shape `(childX, childY, [width, height,] content, [callback])`

**Partial fit, one adjustment.** Item icons are always square — width and height are always equal. ItemDisplay takes a single `size` parameter rather than separate width and height.

Refinement to Convention 1 (narrowing, not expanding): *"dimensions"* means width and height for rectangular elements, a single size parameter for square-only elements. The reading order (where, how big, what) holds. ItemDisplay and any future square-only element (possibly a future specialized Icon-square variant) follow this.

```java
new ItemDisplay(x, y, stack);              // default size (16×16), default overlays
new ItemDisplay(x, y, size, stack, ...);   // explicit size, explicit overlays
```

### Convention 2 — Supplier variants for variable content (data-vs-configuration)

**Applies, both forms ship.** Per the review refinement, ItemDisplay ships both fixed and supplier forms despite the "static case is unusual" argument:

```java
new ItemDisplay(x, y, MY_STACK);             // fixed
new ItemDisplay(x, y, this::currentItem);    // supplier
```

Java's overload resolution distinguishes `ItemStack` from `Supplier<ItemStack>` unambiguously. Internal representation wraps the fixed form in a `() -> stack` closure so the render path is uniform (same pattern Icon uses).

`showCount` and `showDurability` are configuration (how the element looks), not data (the element's current state). Fixed at construction. Confirms the data-vs-configuration distinction is doing work — an element with both a data supplier and configuration flags correctly splits them.

### Convention 3 — Render-only inherits defaults

Applies. Overrides only position accessors, `getWidth`, `getHeight`, and `render`. Everything else inherits.

### Convention 4 — One builder method per element

Applies. `.itemDisplay(...)` added to PanelBuilder with four overloads (2 short × 2 content forms + 2 full × 2 content forms, minus overlap = 4 total):

```java
panelBuilder.itemDisplay(x, y, ItemStack);
panelBuilder.itemDisplay(x, y, Supplier<ItemStack>);
panelBuilder.itemDisplay(x, y, size, ItemStack, showCount, showDurability);
panelBuilder.itemDisplay(x, y, size, Supplier<ItemStack>, showCount, showDurability);
```

Four builder methods. After ItemDisplay: 3 + 2 (Icon) + 4 (Divider) + 4 (ItemDisplay) = 13 methods. Still comfortable.

HUD builder's `.item(x, y, Supplier<ItemStack>)` keeps its existing signature but produces a core `ItemDisplay` internally. The sub-builder `ItemBuilder` (on MKHudPanel.Builder) similarly retargets.

### Convention 5 — No factory methods except direction

Applies. ItemDisplay has no direction discriminator, no factory methods.

### Convention 6 — Vanilla textures for MenuKit defaults

**N/A for this element.** ItemDisplay's visuals come entirely from vanilla's `GuiGraphics.renderItem()` and `renderItemDecorations()` — no MenuKit-owned texture choice. The convention doesn't apply because MenuKit ships no default visual of its own for ItemDisplay; the rendering is pure delegation to vanilla.

Good edge case to confirm — Convention 6 is specifically about *MenuKit's own default visuals*, not about every element that uses textures. Pure delegation to vanilla rendering is outside the convention's scope.

---

## Existing MKHudItem features audit — keep vs. drop

MKHudItem has several features. ItemDisplay's design decides what transfers.

| Feature | Decision | Reasoning |
|---|---|---|
| `Supplier<ItemStack>` content | Keep (both supplier and fixed forms) | Per Convention 2 |
| `size` (single int) | Keep | Items are square; matches existing API |
| `showCount` flag | Keep | Configuration; useful for icon-only contexts |
| `showDurability` flag | Keep | Configuration; useful for icon-only contexts |
| `onRender` callback | **Drop** | Per-render hook with no verified consumer need; library-minimalism discipline |
| JOML matrix scale for size≠16 | Keep (internal) | Implementation detail; preserves visual correctness |

The `onRender` drop is the only substantive feature removal. Reasoning: MKHudItem's `onRender` is a testing/debug hook that no current consumer uses (agreeable-allays and inventory-plus-PocketHud don't touch `.item()` at all). Per the palette's library-minimalism discipline, constructor fields must earn their keep. Consumers who want per-render callbacks implement `PanelElement` directly or wrap ItemDisplay.

If this turns out wrong (a consumer surfaces a genuine need during Phase 11), it's a one-constructor-field addition to reopen.

---

## Default overlays — on or off?

MKHudItem's existing `.item()` builder defaulted both `showCount` and `showDurability` to **false**. That's inconsistent with vanilla behavior (vanilla shows both by default) and with MKHudItem's own `ItemBuilder` sub-builder (which defaults both to true).

ItemDisplay standardizes: **both default to true, matching vanilla item rendering.** Consumers who want icon-only display pass `showCount=false, showDurability=false` explicitly via the full-control constructor.

No consumer currently calls `.item()` in the HUD builder (confirmed by earlier audit). Changing the default is safe.

---

## API

```java
public class ItemDisplay implements PanelElement {

    /** Native item render size — vanilla renders items at this size. */
    public static final int DEFAULT_SIZE = 16;

    // ── Constructors: fixed stack ────────────────────────────────────
    public ItemDisplay(int childX, int childY, ItemStack stack);
    public ItemDisplay(int childX, int childY, int size, ItemStack stack,
                       boolean showCount, boolean showDurability);

    // ── Constructors: supplier-driven stack ──────────────────────────
    public ItemDisplay(int childX, int childY, Supplier<ItemStack> stack);
    public ItemDisplay(int childX, int childY, int size, Supplier<ItemStack> stack,
                       boolean showCount, boolean showDurability);

    // ── PanelElement ────────────────────────────────────────────────
    public int getChildX();
    public int getChildY();
    public int getWidth();   // returns size
    public int getHeight();  // returns size
    public void render(RenderContext ctx);

    // ── Queries ──────────────────────────────────────────────────────
    public ItemStack getCurrentStack();  // resolves supplier
    public int getSize();
    public boolean showsCount();
    public boolean showsDurability();
}
```

Internal fields: `childX`, `childY`, `size`, `Supplier<ItemStack> stackSupplier`, `showCount`, `showDurability`.

Render:
```java
@Override
public void render(RenderContext ctx) {
    ItemStack stack = stackSupplier.get();
    if (stack == null || stack.isEmpty()) return;

    var mc = Minecraft.getInstance();
    var graphics = ctx.graphics();
    int drawX = ctx.originX() + childX;
    int drawY = ctx.originY() + childY;

    if (size != DEFAULT_SIZE) {
        float scale = size / (float) DEFAULT_SIZE;
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) drawX, (float) drawY);
        graphics.pose().scale(scale, scale);
        graphics.renderItem(stack, 0, 0);
        if (showCount || showDurability) {
            graphics.renderItemDecorations(mc.font, stack, 0, 0);
        }
        graphics.pose().popMatrix();
    } else {
        graphics.renderItem(stack, drawX, drawY);
        if (showCount || showDurability) {
            graphics.renderItemDecorations(mc.font, stack, drawX, drawY);
        }
    }
}
```

Effectively identical to MKHudItem's current render body, with `RenderContext` replacing the flat parameters.

---

## HUD builder migration

`MKHudPanel.Builder.item(int x, int y, Supplier<ItemStack> item)` and the `ItemBuilder` sub-builder retarget to produce `ItemDisplay` instead of `MKHudItem`. Method signatures stay identical — consumer-facing API unchanged.

After this lands, `MKHudItem.java` is deleted. One less HUD-specific class; the HUD subsystem becomes a little thinner.

---

## Scope boundary — what ItemDisplay does not do

- **No click handling.** ItemDisplay is render-only. Consumers who want clickable items use a proper `MenuKitSlot` in an inventory-menu panel (which has click handling, sync, storage).
- **No tooltip.** Phase 8's Tooltip element handles hover tooltips for items. ItemDisplay doesn't own tooltip rendering itself.
- **No animation.** Scale is fixed at construction. Consumers who want animated items drive via the `Supplier<ItemStack>` for the stack and wrap the ItemDisplay if they need animated scale or position.
- **No drag/drop.** Not a slot; doesn't participate in item movement.

---

## Phase 9 / 10 notes

No specialization planned for ItemDisplay in Phase 9. The element is complete as shipped in Phase 8.

After ItemDisplay (and ProgressBar, and the Icon/TextLabel subsumption of MKHudIcon/MKHudText) lands, **MKHudSlot is the only remaining HUD-specific element-like class** in the codebase. A HUD-styled item display with slot-background rendering is close to being ItemDisplay with a sprite-background property. If Phase 11's consumer refactors reveal shulker-palette or another mod wants this shape, it becomes a Phase 9-reopen or Phase 10 audit item. Not Phase 8 scope.

---

## Implementation flag

`graphics.pose().pushMatrix()` / `translate()` / `scale()` / `popMatrix()` — confirm this pattern still compiles under current 1.21.11 mappings during implementation. MKHudItem uses this pattern today and the codebase builds, so the API is valid. Sanity-check during build.

---

## Summary

Three convention tests:
- Convention 1 narrows to accept single-int "size" for square-only elements
- Convention 2 applies cleanly; data-vs-configuration distinction does work (splits `stack` from `showCount`/`showDurability`)
- Convention 6 is N/A (pure vanilla delegation, no MenuKit default visual)

One feature drop: `onRender` callback (no verified consumer need). One default change: overlays default to true (was false in `.item()` builder). No current consumer affected.

File: `menukit/src/main/java/com/trevorschoeny/menukit/core/ItemDisplay.java`. ~100 lines. Plus deletion of `menukit/src/main/java/com/trevorschoeny/menukit/hud/MKHudItem.java` and retargeting of the HUD builder.

**Approved 2026-04-14.** Implementation proceeds.
