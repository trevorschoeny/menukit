# M6 — Client-Side Slot Primitive for Decoration Panels

**Phase 12 mechanism — rendering-shaped** (per `Phase 11/POST_PHASE_11.md`).

**Status: DISSOLVED.**

> **Phase 12 verification finding (2026-04-16).** M6 dissolved during POC verification. Testing showed that peek slots must participate in vanilla's full slot protocol (drag, bidirectional shift-click, native cursor management). A client-side primitive with custom click dispatch doesn't produce native behavior — when something looks like a slot, users expect vanilla slot behavior. ClientSlot occupies a middle ground between ItemDisplay (render-only) and real vanilla Slot instances (full interaction protocol) with no consumer evidence outside peek. Peek is an M4 use case.
>
> **What carries forward to M4:**
> - `SlotRendering` utility design (drawSlotBackground, drawHoverHighlight, drawItem, drawGhostIcon) — factored during M4 implementation
> - The M6/M4 shared-rendering boundary analysis (this doc's rendering section)
> - Verification finding: peek needs real vanilla Slot instances in `containerMenu.slots`
>
> **Sequence update:** Phase 12 becomes M4 → M1 → M5. M6 is removed.

*The remainder of this document is preserved as the historical design record and as input for M4's design doc.*

**Enables:** F15 (peek panel UI); transitively SP-F1 (shulker-palette peek toggle) in Phase 13.
**Evidence:** IP's peek panel (F15). Prior Layer 3 hand-rolled `graphics.fill()` attempt reverted per the Phase 11 process finding.

---

## Purpose

MenuKit consumers need interactive slot-like elements inside decoration panels (Pattern 2/3 injection panels rendered over vanilla screens) whose storage and mutation semantics are entirely client-driven. These slots:

- Render with vanilla-conforming visuals (slot background, hover highlight, item + count + durability overlays, tooltips)
- Detect clicks and dispatch to consumer-provided handlers
- Are not in any `containerMenu.slots` list and do not participate in vanilla's slot-click protocol
- Are backed by a client-side stack supplier (consumer-owned state, not a vanilla `Container`)

M6 provides the primitive. Consumers compose ClientSlots into a Panel, bind each to their own `Supplier<ItemStack>`, and wire click dispatch to their own C2S packet protocol.

---

## Consumer evidence from Phase 11

### IP's peek panel (F15)

Peek sessions hold a `NonNullList<ItemStack>` populated from server `PeekSyncS2CPayload` packets. The visible peek panel needs:

- Grid of interactive slots bound to `ContainerPeekClient.getSession().items()`
- Click-to-pickup and shift-click-to-move-to-inventory dispatching through `PeekMoveC2SPayload` (server-authoritative round-trip; no client-side mutation prediction)
- Hover tooltips for slot items (vanilla item tooltips)
- Variable slot count (bundles have variable capacity — slots beyond `session.activeSlots()` should render as disabled)

A prior Layer 3 pass attempted to hand-roll this with raw `graphics.fill()` + custom hit-testing. The architectural mismatch was called out; the attempt reverted to a stub. M6 is the correct primitive; F15 is the feature that consumes it.

### Transitive consumer — shulker-palette SP-F1

When IP's peek ships in Phase 13 (gated on M6), shulker-palette adds a palette-toggle button on the peek panel. That button is a regular `Button`, not a ClientSlot — but it lives on the same peek-panel surface that M6 enables, so SP-F1 is transitively unblocked.

---

## Shape — PanelElement subclass, Button's orchestration pattern

`ClientSlot implements PanelElement`. Render orchestration is `final`, matching Button's pattern. The render pass runs this sequence (screen-space coords `sx`, `sy`):

1. Update hover state (`hovered = isHovered(ctx)`)
2. Draw slot background via `SlotRendering.drawSlotBackground` — delegates to `PanelRendering.renderSlotBackground` for vanilla-accurate visuals at default 18×18 size, `PanelStyle.INSET` fallback for non-default sizes, `PanelStyle.DARK` when disabled
3. If empty and a ghost-icon supplier is configured → `SlotRendering.drawGhostIcon`
4. If non-empty → `SlotRendering.drawItem` (dimmed to 40% alpha when disabled)
5. If hovered && !disabled → `SlotRendering.drawHoverHighlight`
6. If hovered && (tooltip configured || itemTooltip enabled) → dispatch via `ctx.graphics().setTooltipForNextFrame(...)`

Extension via protected hooks (`renderBackground`, `renderContent`) if a consumer subclass needs custom visuals. Default behavior covers peek's needs.

Double-click detection is per-instance (`lastClickTimestampMs`) and fires only when two clicks land on the same ClientSlot within `DOUBLE_CLICK_WINDOW_MS` (250 ms).

---

## API

Full declaration at `menukit/src/main/java/com/trevorschoeny/menukit/core/ClientSlot.java`:

```java
package com.trevorschoeny.menukit.core;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * An interactive slot rendered inside a {@link Panel}, backed by a
 * client-side item-stack supplier. Click dispatch routes to a consumer-provided
 * {@link ClickHandler} — clicks never round-trip through vanilla's menu
 * slot-click protocol.
 *
 * <p>ClientSlot is the rendering + dispatch primitive for "looks like a slot,
 * acts like a slot, but isn't in any vanilla menu." Primary use case:
 * inventory-plus's peek panel, where peek slots display a client-side snapshot
 * of a container's contents (shulker, bundle, ender chest) and dispatch
 * mutations as C2S packets that round-trip through a server-authoritative
 * sync protocol.
 *
 * <h3>What ClientSlot is NOT</h3>
 *
 * <ul>
 *   <li><b>Not a vanilla menu slot.</b> If you need a real slot that
 *       participates in vanilla's {@code slotClicked} dispatch, shift-click
 *       routing, and {@code mayPlace} enforcement, use MenuKit's SlotGroup
 *       (for MenuKit-native handlers) or the M4 slot-injection primitive
 *       (for grafting into vanilla handlers).</li>
 *   <li><b>Not persistent.</b> Storage is supplier-driven and client-only.
 *       Per-slot state that survives sessions is the M1 primitive's concern,
 *       used separately.</li>
 *   <li><b>Not a full Slot reimplementation.</b> ClientSlot handles rendering,
 *       hit-detection, and click dispatch. Mutation semantics (what a click
 *       actually does) are the consumer's responsibility — typically a C2S
 *       packet send.</li>
 * </ul>
 *
 * <h3>Click-consumption semantics — different from Button</h3>
 *
 * Unlike {@link Button} (which returns {@code false} from {@code mouseClicked}
 * when disabled or when its click criteria don't match, allowing clicks to
 * fall through to other elements or vanilla), ClientSlot always consumes any
 * click that lands within its bounds. Slots are interactive inventory
 * surfaces, not decorative elements — a click inside a slot should never
 * fall through to vanilla or to elements underneath. The {@link ClickHandler}'s
 * return value is not used for consumption decisions; it is reserved for
 * future internal uses (e.g., sound or animation feedback based on whether
 * the click resulted in a meaningful mutation).
 *
 * @see PanelElement   The interface this implements
 * @see Button         The closest analog for orchestration pattern
 * @see ItemDisplay    The render-only item primitive ClientSlot draws items with
 * @see SlotRendering  Shared slot-rendering utility (also used by M4)
 */
public class ClientSlot implements PanelElement {

    /** Vanilla slot size — 16px item area + 1px padding each side. */
    public static final int DEFAULT_SIZE = 18;

    /** Double-click detection window (ms). Matches IP's BulkMove feel. */
    public static final long DOUBLE_CLICK_WINDOW_MS = 250;

    // ── Constructors (Convention 1 shape) ──────────────────────────────

    /** Always-enabled ClientSlot at default 18×18 size. */
    public ClientSlot(int childX, int childY,
                      Supplier<ItemStack> stack,
                      ClickHandler onClick);

    /** ClientSlot with optional disabled predicate, default size. */
    public ClientSlot(int childX, int childY,
                      Supplier<ItemStack> stack,
                      ClickHandler onClick,
                      @Nullable BooleanSupplier disabledWhen);

    /** Full-control ClientSlot — explicit size, optional disabled predicate. */
    public ClientSlot(int childX, int childY, int size,
                      Supplier<ItemStack> stack,
                      ClickHandler onClick,
                      @Nullable BooleanSupplier disabledWhen);

    // ── Orthogonal features (chainable setters, Tooltip-doc pattern) ───

    /**
     * Attaches a hover-triggered tooltip with supplier-driven text. Supplier
     * invoked each frame while hovered. Tooltip renders even when the slot
     * is disabled (accessibility — users hovering over a disabled slot
     * should still learn what would go there).
     */
    public ClientSlot tooltip(Supplier<Component> supplier);

    /**
     * Convenience: vanilla multi-line item tooltip for the current stack
     * (name, description, enchantments, etc.). No-op on empty slots.
     * This is what peek needs by default.
     *
     * <p>If both {@link #tooltip(Supplier)} and {@code itemTooltip()} are
     * set, explicit tooltip wins (opt-in over convenience default).
     */
    public ClientSlot itemTooltip();

    /**
     * Attaches a ghost-icon overlay shown when the slot is empty. The sprite
     * renders dimmed at 40% alpha, centered in the item area.
     *
     * <p>Forward-compat with M4 — M4's equipment slots use the same pattern
     * (F8 elytra placeholder, totem placeholder). M4's design doc can reuse
     * ClientSlot's ghost-icon rendering via {@link SlotRendering#drawGhostIcon}.
     * Peek doesn't currently use ghost icons, but the capability is cheap to
     * include and prevents a "wait, we need ghost icons too" discovery during
     * M4 design.
     */
    public ClientSlot ghostIcon(Supplier<Identifier> sprite);

    // ── Queries ────────────────────────────────────────────────────────

    public boolean isDisabled();
    public boolean isHovered();
    public ItemStack getCurrentStack();
    public int getSize();

    // ── PanelElement implementation ────────────────────────────────────

    @Override public int getChildX();
    @Override public int getChildY();
    @Override public int getWidth();   // returns size
    @Override public int getHeight();  // returns size

    /**
     * Orchestrates the render pass. Final by design — the extension surface
     * for consumer subclasses is the two protected hooks, not this method.
     */
    @Override public final void render(RenderContext ctx);

    /**
     * Dispatches the click to the {@link ClickHandler} (when enabled) and
     * always returns true (consume). See class javadoc for the always-consume
     * rationale.
     */
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button);

    // ── Extension hooks for subclasses (stable) ────────────────────────

    protected void renderBackground(RenderContext ctx, int sx, int sy);
    protected void renderContent(RenderContext ctx, int sx, int sy);

    // ── Click dispatch types ───────────────────────────────────────────

    /**
     * Dispatched when a click lands inside this ClientSlot's bounds and the
     * slot is not disabled. The return value is currently unused for
     * consumption decisions — ClientSlot always consumes in-bounds clicks.
     * See the class javadoc for rationale.
     */
    @FunctionalInterface
    public interface ClickHandler {
        boolean onClick(ClickContext ctx);
    }

    /**
     * Per-click context.
     *
     * @param button      mouse button (0=left, 1=right, 2=middle)
     * @param shiftHeld   whether shift is held at click time
     *                    (polled via {@code InputConstants.isKeyDown})
     * @param doubleClick whether this is the second click of a double-click
     *                    (within {@link #DOUBLE_CLICK_WINDOW_MS} of the
     *                    previous click on this same slot)
     * @param slotItem    snapshot of the stack supplier's value at click time
     */
    public record ClickContext(
            int button,
            boolean shiftHeld,
            boolean doubleClick,
            ItemStack slotItem
    ) {}
}
```

---

## Storage binding — `Supplier<ItemStack>`

Matches ItemDisplay's model. Consumer passes a `Supplier<ItemStack>`; ClientSlot invokes it each frame during render and at click time (for `ClickContext.slotItem`).

### Rejected alternatives

- **`ClientSlotStorage` interface** wrapping `(int size, ItemStack get(int))`. Centralizes grid composition. **Deferred until a second consumer asks.** Peek is the only current consumer and per-slot Suppliers compose cleanly enough for a 3×9 grid. Adding an interface now is speculative scaffolding M6 can ship without. Consistent with MenuKit's library-minimalism discipline (see ItemDisplay's `onRender` drop per `ITEM_DISPLAY_DESIGN_DOC.md`).
- **Bind to MenuKit's `Storage` interface.** `Storage` is designed for inventory-menu slot groups (server-authoritative mutable storage). ClientSlot is client-only and read-only from its own perspective (mutations happen out-of-band via consumer packets); the Storage contract doesn't fit.

### Consumer composition example

Peek panel construction (abbreviated — full form in the POC):

```java
List<PanelElement> peekElements = new ArrayList<>();
for (int row = 0; row < 3; row++) {
    for (int col = 0; col < 9; col++) {
        final int slotIdx = row * 9 + col;
        peekElements.add(new ClientSlot(
            col * 18, row * 18,
            // Stack supplier — reads the session's current items list.
            // Returns EMPTY when not peeking; safe across session transitions.
            () -> {
                var session = ContainerPeekClient.getSession();
                return session != null ? session.items().get(slotIdx) : ItemStack.EMPTY;
            },
            // Click handler — dispatches PeekMoveC2SPayload.
            ctx -> dispatchPeekClick(slotIdx, ctx),
            // Disabled predicate — for bundles, slots beyond active capacity.
            () -> {
                var session = ContainerPeekClient.getSession();
                return session == null || slotIdx >= session.activeSlots();
            }
        ).itemTooltip());
    }
}
Panel peekPanel = new Panel("ip:peek", peekElements, /*visible*/ true)
    .showWhen(ContainerPeekClient::isPeeking);
```

The consumer-side composition is explicit about closures and predicates. The library doesn't hide what's happening.

---

## Click dispatch — ClickHandler + ClickContext

### Contract

1. Click lands in ClientSlot's bounds → `ScreenPanelAdapter` hit-test passes → `ClientSlot.mouseClicked` is called.
2. If `isDisabled()`: consume (return true), do not dispatch.
3. Otherwise: build `ClickContext`, dispatch to handler, consume regardless of handler return.

### Shift-state polling

`PanelElement.mouseClicked` and `ScreenPanelAdapter.mouseClicked` don't carry modifier state (the signatures predate 1.21.11's `MouseButtonEvent` records). ClientSlot polls shift via the COMMON_FRICTIONS #5 pattern:

```java
var window = Minecraft.getInstance().getWindow().getWindow();
boolean shiftHeld = InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
                 || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
```

Same approach as IP's `BulkMove.isShiftHeld()`. The alternative (extending `PanelElement.mouseClicked` or `ScreenPanelAdapter.mouseClicked` to carry modifiers) would touch every PanelElement implementation and every ScreenPanelAdapter consumer — out of scope for M6.

### Double-click detection

Per-instance `lastClickTimestampMs` field. On click:

```java
long now = System.currentTimeMillis();
boolean doubleClick = (now - lastClickTimestampMs) <= DOUBLE_CLICK_WINDOW_MS;
lastClickTimestampMs = now;
```

Per-instance (not static) because double-click semantically means "two clicks on the same slot." Moving the cursor between slots cancels the detection.

### Cursor state — not in ClickContext

ClientSlot does not carry cursor-item state in `ClickContext`. Different consumers have different cursor semantics — peek uses its own client-side cursor (separate from vanilla's carried stack) because peek mutations round-trip through packets, not vanilla's menu-click protocol; other consumers may reuse vanilla's carried stack; yet others may have no cursor concept at all.

If a handler needs cursor state at click time, it reads what it needs directly — `Minecraft.getInstance().player.containerMenu.getCarried()` for vanilla's carried stack, or consumer-managed cursor state already in the handler's closure scope. See the Scope boundary section for the library-not-platform rationale.

### Always-consume rationale

Slots are semantic targets. A click in-bounds should never fall through to vanilla's screen or to other Panel elements underneath. Documented in the class javadoc with explicit contrast to Button, so future contributors reading Button's pass-through pattern don't wonder why ClientSlot diverges.

The `ClickHandler` return value stays on the interface (not dropped) because a future minor release can start using it — click sounds, flash animations, feedback based on "did this click result in a meaningful action" — without breaking consumers who already return a boolean.

---

## Tooltip integration

Two modes, both hover-triggered, both via `setTooltipForNextFrame` for z-correct rendering above items:

- **Explicit text** — `.tooltip(Supplier<Component>)`. Supplier invoked each frame while hovered. Matches Button's pattern.
- **Item tooltip** — `.itemTooltip()`. Renders vanilla's multi-line item tooltip for the current stack. No-op on empty slots. What peek needs by default.

**Precedence:** explicit tooltip wins over `itemTooltip` if both are set (opt-in over convenience).

**Disabled slots still show tooltips** (accessibility — users hovering over a disabled slot should still understand the UI).

---

## Rendering — decomposed into `SlotRendering` utility

ClientSlot renders through a new static utility `com.trevorschoeny.menukit.core.SlotRendering`, parallel to the existing `PanelRendering`:

```java
package com.trevorschoeny.menukit.core;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Shared slot-rendering utility. Used by {@link ClientSlot} (M6) and the
 * forthcoming M4 vanilla-menu slot-injection primitive. Parallel to
 * {@link PanelRendering}, which handles panel-level backgrounds.
 *
 * <p>Both M6 and M4 render slot-shaped surfaces that need the same visuals:
 * slot background, hover highlight, item + decorations, optional ghost icon.
 * Factoring them here avoids duplication and means consistent visual
 * language across client-side decoration slots and vanilla-menu-injected
 * slots.
 */
public final class SlotRendering {
    private SlotRendering() {}

    public static final int DEFAULT_SIZE  = 18;
    public static final int ITEM_INSET    = 1;       // 1px padding around 16×16 item
    public static final int HOVER_COLOR   = 0x30FFFFFF;
    public static final float DISABLED_ALPHA = 0.4f;

    /**
     * Slot background. For enabled 18×18 slots, delegates to
     * {@link PanelRendering#renderSlotBackground} for vanilla-accurate slot
     * visuals (dark top-left edge, light bottom-right edge, medium gray fill,
     * no outer border). For non-default sizes, falls back to {@link PanelStyle#INSET}.
     * For disabled slots of any size, uses {@link PanelStyle#DARK}.
     */
    public static void drawSlotBackground(GuiGraphics g, int sx, int sy,
                                          int size, boolean disabled);

    /** Translucent hover overlay inside the 1px INSET border. */
    public static void drawHoverHighlight(GuiGraphics g, int sx, int sy, int size);

    /**
     * Draws the item centered in the slot, with count + durability
     * decorations. {@code dimmed=true} renders at {@link #DISABLED_ALPHA}
     * (used for disabled-slot items).
     */
    public static void drawItem(GuiGraphics g, ItemStack stack, int sx, int sy,
                                int size, boolean dimmed);

    /**
     * Ghost-icon overlay — dimmed sprite shown when the slot is empty and a
     * ghost-icon supplier is configured. Centered in the item area.
     */
    public static void drawGhostIcon(GuiGraphics g, Identifier sprite,
                                     int sx, int sy, int size);
}
```

### ClientSlot rendering sequence (inside the final `render` method)

```
1. hovered = ctx.isHovered(childX, childY, size, size)
2. disabled = disabledWhen != null && disabledWhen.getAsBoolean()
3. sx = ctx.originX() + childX;  sy = ctx.originY() + childY
4. SlotRendering.drawSlotBackground(g, sx, sy, size, disabled)
5. stack = stackSupplier.get()
6. if stack.isEmpty() && ghostSupplier != null:
       SlotRendering.drawGhostIcon(g, ghostSupplier.get(), sx, sy, size)
   else if !stack.isEmpty():
       SlotRendering.drawItem(g, stack, sx, sy, size, disabled)
7. if hovered && !disabled:
       SlotRendering.drawHoverHighlight(g, sx, sy, size)
8. if hovered && ctx.hasMouseInput():
       dispatch tooltip (explicit or itemTooltip)
```

---

## M6 / M4 shared-rendering boundary

Four rendering concerns both mechanisms share:

| Concern | M6 uses | M4 will use |
|---------|---------|-------------|
| Slot background (enabled / disabled) | Yes — `drawSlotBackground` | Yes — injected slots need standalone background outside container textures |
| Hover highlight | Yes — `drawHoverHighlight` | Yes — same visual, consistent vanilla feel |
| Item + count + durability | Yes — `drawItem` | Yes — both wrap vanilla's `renderItem` / `renderItemDecorations` |
| Ghost icon | Yes — forward-compat, `drawGhostIcon` | Yes — F8 equipment slots (elytra / totem placeholders) |

**`SlotRendering` is the shared layer.** M4's design doc can reuse these methods verbatim. Factoring cost is ~30 lines of internal code shuffling during M6 implementation; naming it now is cheap and prevents M4-era refactoring of a shipped primitive.

### What `SlotRendering` does NOT share

- **Click dispatch** — different integration contexts. M6 dispatches to consumer handlers directly; M4 dispatches through `menu.clicked` and participates in vanilla's slot-click protocol.
- **Storage binding** — M6 uses `Supplier<ItemStack>`; M4 uses vanilla `Container` (potentially adapted through `PlayerAttachedStorage` or similar).
- **Slot-click policy** — `mayPlace`, quick-move routing, sort-lock observation are all M4 concerns tied to vanilla's `Slot` subclass behavior, invisible to M6.

The boundary is clean: `SlotRendering` handles **visual** concerns; each primitive handles its own **semantic** concerns.

---

## Verification plan — minimum-viable peek integration

**Goal:** validate M6 against its real use case (peek), not a simplified abstract demo. Per advisor Q&A #1 decision — peek *is* the evidence for M6, so validating against a simplified stand-in risks subtle mismatches that would resurface in Phase 13.

### What ships in the POC

**1. MenuKit side (M6 itself):**
- `ClientSlot.java` in `core/` (~200 lines)
- `SlotRendering.java` in `core/` (~80 lines)

**2. IP side (peek integration, minimum-viable):**
- `PeekDecoration.java` populated:
  - Static `Panel` constructed at class init with 27 ClientSlots (3×9) bound to `ContainerPeekClient.getSession().items()` via per-slot Suppliers
  - Click handlers build `PeekMoveC2SPayload` with action code derived from `ClickContext`:
    - Left-click, not shift, not double-click → `ACTION_PICKUP`
    - Shift-click (any button, but conventionally left) → `ACTION_SHIFT_CLICK`
    - Right-click or double-click → no-op for POC (F15 territory)
  - `render(screen, g, mouseX, mouseY)` → `ScreenPanelAdapter.render(...)` against the static Panel
  - `mouseClicked(screen, x, y, button, shiftHeld)` → `ScreenPanelAdapter.mouseClicked(...)`
  - Panel visibility gated on `ContainerPeekClient.isPeeking()` via `Panel.showWhen(ContainerPeekClient::isPeeking)`
  - Position: peek panel origin computed from the vanilla screen's bounds (above or beside the container, matching peek's existing placement convention from the reverted Layer 3 attempt)
- `KeybindDispatch.handlePeek` wired minimally:
  - Read the hovered slot in `player.containerMenu` at keybind-press time
  - If the hovered slot contains a peekable (shulker / bundle / ender), call `ContainerPeekClient.openPeek(hoveredSlot.index)`
  - Just enough to open a peek session. Full F15 keybind behavior (peek-switch, peek-close, Alt-modifier semantics) still deferred.

### No optimistic grid updates

The POC dispatches `PeekMoveC2SPayload` and waits for `PeekSyncS2CPayload` before updating the grid. Matches Phase 11's "one error mode, no optimistic updates" decision for peek — the grid is a read-only view of server-authoritative state. If the server rejects a mutation and responds with `PeekErrorS2CPayload`, the client closes the peek and shows a toast; there is no optimistic grid state to roll back because there was none.

**Exception:** client-side cursor rendering (managed by `PeekDecoration`, not ClientSlot — see Scope boundary) updates immediately on pickup for responsiveness. The cursor item follows the mouse instantly; only the grid waits for server acknowledgment. If the server rejects the pickup, the error path closes peek entirely, so cursor consistency is moot — the whole session goes away.

Implementers should not add optimistic grid updates "for responsiveness" during POC work. The cursor handles responsiveness; the grid is authoritatively server-synced.

### Verify in dev-client

- Open a shulker box in inventory; hover over the shulker in the hotbar or main inventory; press the peek keybind → peek panel renders with 27 slot-shaped receptacles showing shulker contents.
- Hover a slot with an item → vanilla item tooltip appears (name, enchantments, etc.).
- Hover an empty slot → no tooltip.
- Left-click a slot with an item → item lifts to cursor (server round-trip via `PeekMoveC2SPayload ACTION_PICKUP`, server responds with `PeekSyncS2CPayload`).
- Left-click the same slot again with carried item → places item back into slot.
- Shift-click a slot with an item → item moves to player inventory (`ACTION_SHIFT_CLICK`).
- Bundle peek → slots beyond `activeSlots()` render as disabled (DARK background, 40%-dim item area, no hover highlight, no dispatch).
- Close the inventory screen (Escape or E) while peeking → peek session closes, panel vanishes, no stale state. (Already handled by `ContainerPeekClient`'s existing screen-close path; verify it still works with M6-backed panel.)
- Close peek (close menu, or re-press keybind on a different slot) → panel vanishes.

### F15 still defers

The POC validates that M6 is the right primitive shape. F15's full feature set stays deferred to Phase 13:

- Sort-within-peek (click-sequence protocol extension)
- Move-matching-into-peek (similar protocol extension)
- Double-click-collect (`ACTION_DOUBLE_CLICK_COLLECT` — ClientSlot detects the double-click; IP wires the dispatch and server handler)
- Drop-to-world (`ACTION_DROP` — Q-keybind over peek slot)
- Bundle variable-slot UI (bundle-specific decorations, partial-slot rendering, dynamic slot count beyond simple disable-past-N)
- Ender-chest-specific behaviors
- Sort-lock interaction within peek
- Cross-mod peek-panel integration (SP-F1 shulker-palette palette-toggle)

The POC proves M6 enables F15; it doesn't complete F15. IP's Phase 13 work picks up the deferred sub-features against the shipped M6 primitive with no further MenuKit changes expected.

---

## Integration points with existing MenuKit

ClientSlot composes with the existing surface. No changes to existing files are required; M6 is purely additive.

| MenuKit artifact | Interaction |
|-----|-----|
| `PanelElement` | `ClientSlot` implements. Conforms to the existing coordinate contract (panel-local `childX/Y` at construction; screen-space `mouseX/Y` in `mouseClicked`). |
| `Panel` | Holds ClientSlots alongside other PanelElements. No changes. |
| `ScreenPanelAdapter` | Dispatches `render` and `mouseClicked` to ClientSlots through the standard PanelElement iteration. No changes. |
| `Button` | Pattern reference for orchestration + chainable tooltip. No code dependency. |
| `ItemDisplay` | Pattern reference for item rendering. `SlotRendering.drawItem` uses the same `renderItem` / `renderItemDecorations` approach. No code dependency. |
| `PanelStyle` | `INSET` = non-default-size enabled fallback; `DARK` = disabled slot background. |
| `PanelRendering` | `renderSlotBackground` paints vanilla-accurate 18×18 slot visuals (primary path); `renderPanel` paints `INSET` / `DARK` fallbacks. |
| `Tooltip` (the element) | Unrelated. `Tooltip` is a panel-positioned persistent info box; ClientSlot's tooltip integration is the hover-triggered variant (same as Button's `.tooltip(...)`). |
| `RenderContext` | Used as-is. `ctx.isHovered`, `ctx.originX/Y`, `ctx.graphics()`, `ctx.hasMouseInput()`. |

No changes to MenuKit's `inject/`, `screen/`, or `mixin/` packages. ClientSlot is a pure additive primitive in `core/`.

---

## Scope boundary — what ClientSlot does not do

- **Not for vanilla menus.** ClientSlot doesn't register with `containerMenu.slots` and isn't visible to vanilla's slot-click dispatch. Real interactive slots inside a vanilla handler are M4's primitive.
- **Not persistent.** Storage is client-side and supplier-driven. Per-slot state that survives sessions or menu transitions is M1's primitive.
- **Not a vanilla Slot drop-in.** No `mayPlace`, no `canTakeItems`, no vanilla `setBackground(ResourceLocation)` (ClientSlot has `ghostIcon(Supplier<Identifier>)` instead), no quick-move routing. Those are vanilla-menu concerns.
- **Does not manage cursor-item state or rendering.** Consumers that implement pickup/place semantics (peek's click-to-lift, future consumers with similar mutation protocols) manage their own client-side cursor state and render the carried item independently. ClientSlot dispatches clicks; the consumer decides what the pickup looks like on screen and what state the cursor carries between clicks. Rationale: different consumers have different cursor semantics — peek uses its own client-cursor separate from vanilla's because peek mutations round-trip through packets; other consumers might reuse vanilla's carried stack; yet others might have no cursor concept. Keeping ClientSlot stateless on cursor preserves library-not-platform discipline. Peek's Phase 13 work maintains `PeekDecoration.clientCursorStack` (updated optimistically on pickup, reconciled on `PeekSyncS2CPayload` receipt) and renders it at mouse position in its own render pass, after all ClientSlots render.
- **Not a grid-layout helper.** ClientSlots are positioned individually by the consumer. A grid helper can ship later if multiple consumers ask.
- **No drag protocol.** No multi-slot drag-to-distribute semantics. If peek or another consumer needs drag, the consumer implements it client-side on top of ClientSlot.
- **No sound or animation policy.** ClientSlot renders and dispatches. If consumers want click sounds or flash animations, they implement them in the `ClickHandler`.
- **No keyboard shortcut handling.** Drop-item-key (Q) over a slot, number-key slot-swap, Ctrl+click — all vanilla behaviors for vanilla slots. Consumers can wire these through their own keybind / mixin plumbing; M6 doesn't abstract them.

---

## Phase 13 handoff

When M6 ships, IP's F15 picks up against the primitive:

1. Populate `PeekDecoration` per the verification plan (the POC is the first pass; F15 extends it).
2. Add sort-within-peek via a new action code or protocol extension.
3. Add double-click-collect by routing `ClickContext.doubleClick()` → `PeekMoveC2SPayload.ACTION_DOUBLE_CLICK_COLLECT`.
4. Add drop-to-world via a Q-keybind handler → `ACTION_DROP`.
5. Add bundle variable-slot UI (bundle-specific decorations, variable-capacity rendering beyond simple disable-past-N).
6. Expose the peek-panel's `Panel` ID as cross-mod integration surface — SP-F1 unblocks; shulker-palette adds a `Button` onto the same Panel containing the ClientSlots. No further MenuKit changes expected for this.

---

## Implementation flags

- **`setTooltipForNextFrame`.** 1.21.11 name (was `renderTooltip` earlier). Verified present in `Button.java` — reuse its call pattern.
- **`graphics.renderItem(stack, x, y)` + `graphics.renderItemDecorations(font, stack, x, y)`.** 1.21.11 API; verified in `ItemDisplay.java`.
- **`blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h, alpha)`.** For ghost-icon rendering with alpha. Verified in `Button.IconButton`.
- **`InputConstants.isKeyDown`.** Required for shift polling per COMMON_FRICTIONS #5. Same pattern as IP's `BulkMove`.
- **`System.currentTimeMillis()`** for double-click detection. Standard Java; no Minecraft API risk.
- **`PanelStyle.INSET` / `PanelStyle.DARK`.** Existing; verified in `PanelRendering` usage within `Button`.
- **Mouse-window for `InputConstants.isKeyDown`.** Use `Minecraft.getInstance().getWindow()` — in 1.21.11, `InputConstants.isKeyDown` takes the `Window` object directly, not a GLFW long handle. Matches COMMON_FRICTIONS #5 and IP `BulkMove`.

---

## Summary

**Primitive:** `ClientSlot extends PanelElement`, orchestration pattern from `Button`, item rendering from `ItemDisplay`. Backed by `Supplier<ItemStack>`, dispatches to consumer `ClickHandler`, always consumes in-bounds clicks.

**Shared infrastructure:** `SlotRendering` static utility factored for M4 reuse — slot background, hover highlight, item rendering, ghost icon. Parallel to existing `PanelRendering`.

**Verification:** minimum-viable peek integration in IP's `PeekDecoration` — left-click pickup, shift-click move-to-inventory, hover tooltips, disabled slots for bundle variable capacity. Full F15 defers to Phase 13 with no further MenuKit changes expected.

**Scope estimate:** ~200 lines `ClientSlot.java` + ~80 lines `SlotRendering.java` + ~60 lines of peek wiring in IP's `PeekDecoration.java` + minimal `KeybindDispatch.handlePeek` wiring (~15 lines).

**Additive.** No changes to existing MenuKit files. Existing consumers of `PanelElement`, `Panel`, `ScreenPanelAdapter`, `Button`, `ItemDisplay` continue working unchanged.

**Status: Draft — awaiting advisor review.** Expected 1-3 iteration rounds before implementation per the established cadence.
