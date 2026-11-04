# Cycle strip

New in 7.1.0. The cycle strip is a small hotbar drawn just right of the vanilla one. It shows a ring of stacks that turns through the held hotbar slot, and it slides each time the ring turns. Any mod with a ring registers a source: a column of the inventory, a few extra slots behind a hotbar slot, anything that rotates stacks into the hand. MenuKit draws the strip, so a mod that uses it needs no other mod to have a HUD.

The type is `HudCycleStrip` in `com.trevlar.menukit.api.hud`. The build compiles every Java block on this page (the `validator-mk` snippets).

## What the player sees

- **A row:** a mini hotbar 4 pixels right of the vanilla hotbar, at the same height. The held stack is in the highlighted cell, the stack before it to its left, and the ones after it to its right.
- **A column:** one cell at that spot holding the held stack, with the rest of the ring stacked above it.
- **Both on the same slot:** a cross. The column rises from the row's highlighted cell, and the held stack is drawn once.
- **A turn:** the stacks slide one cell over 300 ms on a damped spring: past the cell a little, back a little, then settled. The frames stay still; only the stacks move.

The strip shows only while the selected hotbar slot has a ring. At most one row and one column draw at a time. If two sources of the same kind claim one slot, the one registered first wins.

## Write a source

Needs: MenuKit, on the client.

A source answers one question for a hotbar slot (0 to 8): which ring to draw there, or none. Return `null` to draw nothing, which is also how a source switches itself off. Check the feature's own settings in the same method. MenuKit asks every frame the HUD draws, so keep it cheap.

```java
final class PocketStrip implements HudCycleStrip.Source {
    static final PocketStrip INSTANCE = new PocketStrip();

    @Override
    public HudCycleStrip.@Nullable View viewFor(int hotbarSlot) {
        int pockets = pocketCount(hotbarSlot);
        if (!stripEnabled() || pockets == 0) return null;   // inactive: nothing drawn
        List<ItemStack> ring = new ArrayList<>();
        ring.add(heldStack(hotbarSlot));                      // the held stack first,
        for (int depth = 0; depth < pockets; depth++) {
            ring.add(pocketStack(hotbarSlot, depth));         // then each one a forward turn brings in
        }
        return new HudCycleStrip.View(ring, 0);
    }
}
```

A `View` is the ring and the index of the held stack in it. List the stacks in the order a forward turn brings them to the hand: one forward turn puts `ring.get(current + 1)` in the hand. The ring wraps, so `current` can be any number. Use `ItemStack.EMPTY` for an empty member, never `null`. A ring of fewer than two stacks draws nothing.

A source is a row unless it says otherwise. A column overrides `axis()`. If your stacks are already in top-to-bottom order with the held one last, reverse them into a ring:

```java
final class ColumnStrip implements HudCycleStrip.Source {
    @Override
    public HudCycleStrip.@Nullable View viewFor(int hotbarSlot) {
        List<ItemStack> topToBottom = columnStacks(hotbarSlot);   // held stack last
        return topToBottom.size() < 2 ? null : new HudCycleStrip.View(topToBottom.reversed(), 0);
    }

    @Override
    public HudCycleStrip.Axis axis() {
        return HudCycleStrip.Axis.COLUMN;
    }
}
```

## Register it

Call this once from the client entry point. Registration closes when the client finishes starting, like every MenuKit declaration, and a later call throws. Registering the same instance twice does nothing.

```java
HudCycleStrip.register(PocketStrip.INSTANCE);
```

## Slide it

The strip slides only when you tell it to. Right after the ring turns, and once your source already returns the new order, call `slide`:

```java
HudCycleStrip.slide(PocketStrip.INSTANCE, hotbarSlot,
        forward ? HudCycleStrip.Direction.FORWARD : HudCycleStrip.Direction.BACKWARD);
```

`FORWARD` moves a row's stacks right to left and a column's down toward the hotbar. `BACKWARD` is the mirror. Each source slides on its own clock, so turning the row in a cross leaves the column still. A second turn during a slide starts a new slide from the new order.

If the ring turns on the server, the new order reaches the client a moment later. Have your source return the order you expect until it arrives, or the strip slides the old order and then jumps.

## Arcs

New in 7.6.0. An arc draws a ring over the hotbar slot itself instead of beside the hotbar. Its members float as icons 2/5 their size, 6.4 pixels, each over a faint shadow of its own shape (the stack drawn dark, a pixel down and right, like a text shadow), in an arc just above the slot. Member 0, the next one a forward turn brings in, is on the left. An empty member draws nothing. Arcs draw over every hotbar slot that has a ring, not only the selected one, for rings of two to four stacks. All of this is the default style, and a source can change it (below).

A source asks for an arc through `axis()`. A mod that offers both looks (an arc, and a mini hotbar as an option) registers two sources, each returning `null` while its own setting is off, and slides both after a turn:

```java
final class PocketArc implements HudCycleStrip.Source {
    @Override
    public HudCycleStrip.@Nullable View viewFor(int hotbarSlot) {
        return arcEnabled() ? PocketStrip.INSTANCE.viewFor(hotbarSlot) : null;
    }

    @Override
    public HudCycleStrip.Axis axis() {
        return HudCycleStrip.Axis.ARC;
    }
}
```

A turn moves every stack in a straight line to the next point: arc to arc, arc to the slot, the slot to arc. Each one grows or shrinks between arc size and full size on the way, its shadow fading as it grows, on the source's slide, 300 ms on a spring by default. While a turn runs, and while your source returns a held stack the slot doesn't have yet (a turn it predicted), the arc draws the slot's stack and the hotbar draws that slot empty.

A screen draws the same arc over a hotbar slot's place on it, turn included, from a panel's custom element. Pass the center of that slot's 16 pixel item:

```java
HudCycleStrip.drawArc(graphics, arcSource, hotbarSlot, slotX + 8, slotY + 8);
```

On a screen the slot keeps drawing its own stack. The arc is for looking only and takes no input.

To move an arc's stacks somewhere yourself, `HudCycleStrip.arc(source, hotbarSlot)` lists them as they draw this frame, the held one first, each with an `ItemAnimation.Pose` relative to the held item's center. `style.points(members)` gives the resting points alone. Draw a stack anywhere at any size, with its shadow, with `drawArcItem(graphics, style, stack, x, y, scale)`, and time your own move with `slideProgress(start, style)`, passing your source's style so the move keeps the arc's timing.

### An arc's style

A source's `style()` sets how its ring looks and moves. Start from `HudCycleStrip.Style.builder()`, which holds the defaults, and set only what differs:

```java
static final HudCycleStrip.Style POCKETS = HudCycleStrip.Style.builder()
        .slide(400, Easing.EASE_OUT)
        .shadowOpacity(0.5f)
        .points(3, -7f, -8f, 0f, -10f, 7f, -8f)
        .build();

@Override
public HudCycleStrip.Style style() {
    return POCKETS;
}
```

| Setting | Default | What it does |
|---|---|---|
| `points(members, x, y, ...)` | one to three members, inside the slot's 20 px pitch | Where an arc of that many members rests, member 0 first, in GUI pixels from the held item's center (negative y is up). Any count from one up. A count with no points draws no arc, and `points(members)` with no values removes one. |
| `iconScale(scale)` | `0.4`, 6.4 px icons | How big the icons draw at rest. |
| `shadow(on)` | on | Whether each icon draws over a shadow of its own shape, in black. |
| `shadowOffset(px)` | `1` | How far down and right the shadow sits, in the icon's own pixels. |
| `shadowOpacity(opacity)` | `0.375` | The shadow's opacity, 0 to 1. |
| `shadowFades(fades)` | on | Whether the shadow fades out as an icon grows into the slot. Off, it stays at full strength. |
| `slide(millis, easing)` | 300 ms, `Easing.SPRING` | Every slide of the source, row, column and arc. Any `Easing` works; one that goes past 1 overshoots and comes back. |

The style is read when a slide starts and kept to its end, and read again every frame an arc draws, so return the same instance each time rather than building one per call.

## A change that moved only the held stack

A mod that moves the held stack without turning a ring (one that rotates whole inventory rows through the hotbar, say) can slide every ring that changed. Take a snapshot before the change and hand it back after:

```java
Map<HudCycleStrip.Source, HudCycleStrip.@Nullable View> before = HudCycleStrip.snapshot(hotbarSlot);
rotateHotbarRows();
HudCycleStrip.slideChanged(hotbarSlot, HudCycleStrip.Direction.FORWARD, before);
```

Only rings whose stacks changed move, and only their held cell slides, up or down as the vanilla hotbar would. An arc doesn't move for these: the vanilla slot shows the change itself. The mod never needs to know which sources exist.

For a mod that draws its own slide, in the vanilla hotbar for example, stamp the turn with `HudCycleStrip.SlideStart start = HudCycleStrip.begin();`, hold it (a field is fine), and read `HudCycleStrip.slideProgress(start)` and `slideDone(start)` each frame for the strip's timing and spring, so the two move together. For a source with its own style, pass it: `slideProgress(start, style)`. `SLIDE_MILLIS` is the default slide's length. `begin()` stamps the slide with MenuKit's own clock, so there is no raw millis value to accidentally stamp with the wrong one, vanilla's `System.currentTimeMillis()` included.

## Limits

- The strip has one place, right of the vanilla hotbar. Vanilla draws the offhand slot there for a left-handed player, and the attack indicator when it is set to the hotbar. They overlap.
- The strip is never clickable, like every HUD panel.
- A spectator's hotbar is the spectator menu, so no strip or arc draws for a spectator.
