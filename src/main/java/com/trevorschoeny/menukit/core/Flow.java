package com.trevorschoeny.menukit.core;

import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/**
 * The general reactive flow-wrap primitive — a {@link PanelElement} that holds a
 * run of child elements and FLOWS the visible ones left-to-right into the panel's
 * available content width, WRAPPING to new rows that grow downward when the run
 * exceeds that width. The element analogue of {@code SlotFlowElement} (which does
 * the same for slots, on a uniform 18px pitch); {@code Flow} handles
 * heterogeneous, variable-width children — buttons, badges, icons, labels.
 *
 * <h3>Why this exists — comprehensive wrapping</h3>
 *
 * Reactive wrapping already covers text ({@link TextLabel} wraps to its budget),
 * a button's label, and slots ({@code SlotFlowElement}). The one shape it did NOT
 * cover was a horizontal RUN of elements wrapping to multiple rows: {@link
 * com.trevorschoeny.menukit.core.layout.Row} is a BUILD-TIME helper (it bakes
 * positions once at {@code build()} and does not exist at runtime, so it can't
 * reflow when the width changes). {@code Flow} is the runtime container that
 * closes that gap, so wrapping is comprehensive across every content kind.
 *
 * <h3>Reactive sizing — same contract as SlotFlowElement</h3>
 *
 * {@link #naturalWidth()} reports the ALL-children single-row width (visibility-
 * independent), so the owning {@link Panel}'s {@code min(naturalWidth, ceiling)}
 * resolves to the screen-edge ceiling whenever the run is wider than the room
 * available — a budget that's stable across child reveals. The flow wraps the
 * VISIBLE children into that budget and {@link #getWidth()}/{@link #getHeight()}
 * report the HUG of the wrapped layout (so the panel stays as small as the
 * content, never reserving the full single-row width). Reactive to reveals and
 * to a window resize, no empty space, no flash.
 *
 * <h3>Owns its children's positions (§0047)</h3>
 *
 * The children live INSIDE this element, not in the panel's element list — so the
 * panel's reflow never sees them; the flow positions them itself each layout pass
 * via {@code setChildPosition} into panel-content coordinates ({@code flow.childX
 * + cellX}, {@code flow.childY + cellY}). Because the children then share the
 * panel-content coordinate space the flow lives in, render is a plain
 * {@code child.render(ctx)} and input forwarding hit-tests each child against the
 * same content origin — no offset bookkeeping. Position is pure presentation; a
 * child's identity is untouched.
 *
 * <h3>First-class interactive host</h3>
 *
 * Like {@link ScrollContainer}, {@code Flow} forwards the full input + lifecycle
 * surface to its children (click, scroll, release, key, attach/detach, overlay),
 * so a wrapping toolbar of {@link Button}s behaves exactly as the same buttons
 * would directly on the panel. A child whose interaction surface differs from its
 * bounds (a {@link Dropdown} popover) works because the flow forwards the overlay
 * claim. {@code mouseClicked} has no {@link RenderContext}, so the flow caches the
 * panel-content origin from {@link #render}/{@link #hitTest} (the ScrollContainer
 * idiom) to locate children at click time.
 */
public final class Flow extends AbstractPanelElement<Flow> {

    @Override protected Flow self() { return this; }

    /** Default inter-child gap on both axes, in pixels. */
    public static final int DEFAULT_GAP = 4;

    private final List<PanelElement> children;
    private int gapX = DEFAULT_GAP;
    private int gapY = DEFAULT_GAP;

    /** The wrap ceiling — the content width the flow may occupy before wrapping.
     *  {@code <= 0} until the panel feeds one via {@link #layoutWithin}; until then
     *  {@link #effectiveCap()} falls back to the all-children single-row width
     *  (so frame 0, before any layout pass, reads as one row). */
    private int budget = -1;

    // Cached hug dimensions from the last relayout, plus the (budget, visibility,
    // child-size) signature that produced them — so the four geometry/input
    // accessors that each need current positions don't re-walk the children every
    // call when nothing changed (the positions persist on the children between
    // passes; only this element writes them).
    private int lastWidth = 0;
    private int lastHeight = 0;
    private int lastLayoutSig = Integer.MIN_VALUE;
    /** Forces the first {@link #relayout} regardless of the sentinel, so a first
     *  signature that happened to hash to {@code Integer.MIN_VALUE} can't skip the
     *  initial layout (which would leave lastWidth/lastHeight at 0 for a frame). */
    private boolean everLaidOut = false;

    private Flow(List<PanelElement> children) {
        this.children = List.copyOf(children);
    }

    /** Begins a flow over the given children (declaration order = flow order). */
    public static Flow of(List<PanelElement> children) {
        return new Flow(children);
    }

    /** Sets the inter-child gap (same value on both axes). Chainable. */
    public Flow gap(int px) {
        return gap(px, px);
    }

    /** Sets the horizontal + vertical inter-child gaps independently. Chainable. */
    public Flow gap(int horizontal, int vertical) {
        this.gapX = Math.max(0, horizontal);
        this.gapY = Math.max(0, vertical);
        return this;
    }

    // ── Reactive sizing ────────────────────────────────────────────────

    /**
     * Stores the panel's content-width budget as the wrap ceiling. Because
     * {@link #naturalWidth()} reports the (larger) all-children single-row width,
     * the panel's {@code min(naturalWidth, ceiling)} hands us the screen-edge
     * ceiling — stable across child reveals (it depends on screen geometry, not
     * which children are visible).
     */
    @Override
    public void layoutWithin(int budget) {
        this.budget = Math.max(1, budget);
    }

    /**
     * The ALL-children single-row width (every child's width plus the gaps
     * between them), intentionally visibility-INDEPENDENT so the panel's
     * {@code min(naturalWidth, ceiling)} resolves to the stable screen-edge
     * ceiling. Never the per-frame hug width — that is {@link #getWidth()}.
     */
    @Override
    public int naturalWidth() {
        int total = 0, count = 0;
        for (PanelElement c : children) {
            total += c.getWidth();
            count++;
        }
        if (count > 1) total += (count - 1) * gapX;
        return Math.max(1, total);
    }

    /** The wrap ceiling actually in force this frame (fed budget, or the natural
     *  single-row width before the first {@link #layoutWithin}). */
    private int effectiveCap() {
        return budget > 0 ? budget : naturalWidth();
    }

    /**
     * Lays out the VISIBLE children into rows that wrap at {@link #effectiveCap()},
     * writing each child's panel-content position via {@code setChildPosition} and
     * caching the resulting hug {@link #lastWidth}/{@link #lastHeight}. Gated by a
     * cheap signature so it only re-walks when the budget, a child's visibility, or
     * a child's size actually changed (positions persist on the children between
     * passes — only this element writes them).
     */
    private void relayout() {
        int sig = layoutSignature();
        if (everLaidOut && sig == lastLayoutSig) return;
        everLaidOut = true;
        lastLayoutSig = sig;

        int cap = effectiveCap();
        int x = 0;          // running x within the current row (content-relative)
        int rowTop = 0;     // top y of the current row (content-relative)
        int rowH = 0;       // tallest child in the current row
        int maxRowW = 0;    // widest completed/in-progress row
        boolean firstInRow = true;

        for (PanelElement c : children) {
            if (!c.isVisible()) continue;
            int cw = c.getWidth();
            int ch = c.getHeight();
            // Wrap when this child (plus the gap before it) would exceed the cap —
            // but never wrap the first child of a row (a child wider than the cap
            // takes its own row and overflows rather than vanishing).
            if (!firstInRow && x + gapX + cw > cap) {
                maxRowW = Math.max(maxRowW, x);
                rowTop += rowH + gapY;
                x = 0;
                rowH = 0;
                firstInRow = true;
            }
            if (!firstInRow) x += gapX;
            if (c instanceof AbstractPanelElement<?> ape) {
                // Position in panel-content space so render + hit-test share the
                // flow's coordinate frame (no per-cell origin shift needed).
                ape.setChildPosition(childX + x, childY + rowTop);
            }
            x += cw;
            rowH = Math.max(rowH, ch);
            firstInRow = false;
        }
        maxRowW = Math.max(maxRowW, x);
        lastWidth = maxRowW;
        lastHeight = (rowH == 0 && rowTop == 0) ? 0 : rowTop + rowH;
    }

    /** Cheap, allocation-free hash of the wrap inputs — the budget plus each
     *  child's visibility + size — so {@link #relayout} skips re-walking when
     *  nothing that affects the layout has changed. */
    private int layoutSignature() {
        int sig = 31 * 1 + effectiveCap();
        sig = sig * 31 + childX;
        sig = sig * 31 + childY;
        sig = sig * 31 + gapX;
        sig = sig * 31 + gapY;
        for (PanelElement c : children) {
            boolean vis = c.isVisible();
            sig = sig * 31 + (vis ? 1 : 0);
            if (vis) {
                sig = sig * 31 + c.getWidth();
                sig = sig * 31 + c.getHeight();
            }
        }
        return sig;
    }

    @Override
    public int getWidth() {
        relayout();
        return lastWidth;
    }

    @Override
    public int getHeight() {
        relayout();
        return lastHeight;
    }

    /**
     * Extra height the flow occupies BEYOND a single row because it wrapped — so
     * the owning {@link Panel}'s reflow pushes elements below the flow down by
     * exactly the wrap's growth (the same contract a multi-line {@link Button}
     * honors). The author positions the next sibling assuming one row
     * ({@link #singleRowHeight()}); a wrap then pushes it down, never over it.
     */
    @Override
    public int extraLayoutHeight() {
        relayout();
        return Math.max(0, lastHeight - singleRowHeight());
    }

    /** The flow's height if every visible child sat on one row — the tallest
     *  visible child (zero when none are visible). */
    private int singleRowHeight() {
        int h = 0;
        for (PanelElement c : children) {
            if (c.isVisible()) h = Math.max(h, c.getHeight());
        }
        return h;
    }

    // ── Render ─────────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
        relayout();
        cacheContentOrigin(ctx.originX(), ctx.originY());
        for (PanelElement c : children) {
            if (c.isVisible()) c.render(ctx);
        }
    }

    @Override
    public void renderOverlay(RenderContext ctx) {
        for (PanelElement c : children) {
            if (c.isVisible()) c.renderOverlay(ctx);
        }
    }

    // ── Input forwarding (the ScrollContainer container-host idiom) ─────

    /** Interactive iff any child is — so the flow claims clicks (blocks vanilla
     *  behind it on a non-opaque panel) only where a child would. */
    @Override
    public boolean isInteractive() {
        for (PanelElement c : children) {
            if (c.isVisible() && c.isInteractive()) return true;
        }
        return false;
    }

    @Override
    public boolean hitTest(double mouseX, double mouseY, int contentX, int contentY) {
        cacheContentOrigin(contentX, contentY);
        relayout();
        for (PanelElement c : children) {
            if (c.isVisible() && c.hitTest(mouseX, mouseY, contentX, contentY)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!cachedOriginValid) return false;
        relayout();
        // Active child overlay (a Dropdown popover) claims first, anywhere in its
        // bounds — mirrors the dispatcher's two-pass order.
        for (PanelElement c : children) {
            if (!c.isVisible()) continue;
            int[] ov = c.getActiveOverlayBounds();
            if (ov != null && mouseX >= ov[0] && mouseX < ov[0] + ov[2]
                    && mouseY >= ov[1] && mouseY < ov[1] + ov[3]) {
                return c.mouseClicked(mouseX, mouseY, button);
            }
        }
        for (PanelElement c : children) {
            if (!c.isVisible()) continue;
            if (c.hitTest(mouseX, mouseY, cachedContentX, cachedContentY)
                    && c.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!cachedOriginValid) return false;
        relayout();
        // Active child overlay (a scrollable Dropdown popover) claims the wheel
        // anywhere in its bounds — symmetric with mouseClicked's Pass-0, so a
        // popover that hangs outside the flow still scrolls its own item list.
        for (PanelElement c : children) {
            if (!c.isVisible()) continue;
            int[] ov = c.getActiveOverlayBounds();
            if (ov != null && mouseX >= ov[0] && mouseX < ov[0] + ov[2]
                    && mouseY >= ov[1] && mouseY < ov[1] + ov[3]) {
                return c.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            }
        }
        for (PanelElement c : children) {
            if (!c.isVisible()) continue;
            if (c.hitTest(mouseX, mouseY, cachedContentX, cachedContentY)
                    && c.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Release is broadcast un-hit-tested (drag-ends off-element resolve), so
        // forward to every visible child regardless of position.
        for (PanelElement c : children) {
            if (c.isVisible()) c.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (PanelElement c : children) {
            if (c.isVisible() && c.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    @Override
    public int @org.jspecify.annotations.Nullable [] getActiveOverlayBounds() {
        for (PanelElement c : children) {
            if (!c.isVisible()) continue;
            int[] ov = c.getActiveOverlayBounds();
            if (ov != null) return ov;
        }
        return null;
    }

    @Override
    public void notifyClickOutsideOverlay(double mouseX, double mouseY) {
        for (PanelElement c : children) {
            if (c.isVisible()) c.notifyClickOutsideOverlay(mouseX, mouseY);
        }
    }

    @Override
    public void onAttach(Screen screen) {
        for (PanelElement c : children) c.onAttach(screen);
    }

    @Override
    public void onDetach(Screen screen) {
        for (PanelElement c : children) c.onDetach(screen);
    }

    // ── Content-origin cache (so mouseClicked can locate children) ─────
    // mouseClicked/mouseScrolled get screen coords but no RenderContext, so the
    // panel-content origin is cached from render()/hitTest() — the same one-frame-
    // fresh cache ScrollContainer uses. The dispatcher hit-tests the flow before
    // calling these, so the cache is always populated by then.

    private int cachedContentX = 0;
    private int cachedContentY = 0;
    private boolean cachedOriginValid = false;

    private void cacheContentOrigin(int contentX, int contentY) {
        this.cachedContentX = contentX;
        this.cachedContentY = contentY;
        this.cachedOriginValid = true;
    }
}
