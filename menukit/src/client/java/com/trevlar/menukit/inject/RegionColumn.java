package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.Attachment;
import com.trevlar.menukit.api.element.ChildDispatch;
import com.trevlar.menukit.api.element.InputContext;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.panel.ScreenKind;

import net.minecraft.client.gui.screens.Screen;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The column the container region's sections stack in (7.4.0): its sections top to bottom,
 * {@link RegionLayout#GAP} apart, a hidden one taking no room, as a vertical {@code Flow}
 * stacks them. Unlike a {@code Flow}, its sections change while the screen is open: a section
 * for something opened at runtime ({@code ContainerRegion.sections}) comes when its key does
 * and goes with it. A section that comes is attached to the screen at once, as one present at
 * the screen's init is, and one that goes is detached ({@link Attachment}), the way a
 * {@code ListView}'s rows are.
 *
 * <p>It never wraps into a second column and never fills a height: the region's scroll area
 * gives it all the height it wants. Render thread only.
 */
@ApiStatus.Internal
final class RegionColumn extends AbstractPanelElement {

    private List<AbstractPanelElement> children = List.of();
    private final Attachment attachment = new Attachment();
    // What the column was told, handed on to a section that comes later.
    private int budget = -1;
    private @Nullable ScreenKind kind;
    private boolean inDialog, inBand;
    private int height;

    RegionColumn() {}

    /**
     * The sections now, in stacking order: one that is new is told what the others were
     * (its width, its kind of screen) and attached; one that is gone is detached.
     */
    void set(List<AbstractPanelElement> next) {
        List<AbstractPanelElement> before = children;
        children = List.copyOf(next);
        for (AbstractPanelElement c : before) if (!children.contains(c)) attachment.removed(c);
        for (AbstractPanelElement c : children) {
            if (before.contains(c)) continue;
            if (kind != null) c.screenKind(kind);
            c.inDialog(inDialog);
            c.inBand(inBand);
            if (budget > 0) c.layoutWithin(budget);
            attachment.added(c);
        }
    }

    /** Places the shown sections top to bottom, GAP apart, and notes how tall they are together. */
    private void place() {
        int y = 0;
        boolean first = true;
        for (AbstractPanelElement c : children) {
            if (!c.isVisible()) continue;
            if (!first) y += RegionLayout.GAP;
            c.setChildPosition(childX, childY + y);
            y += c.getHeight();
            first = false;
        }
        height = y;
    }

    // Size: the widest section, as tall as they stack.

    @Override
    public int naturalWidth() {
        int widest = 0;
        for (PanelElement c : children) widest = Math.max(widest, c.naturalWidth());
        return widest;
    }

    @Override
    public int getWidth() {
        int widest = 0;
        for (PanelElement c : children) if (c.isVisible()) widest = Math.max(widest, c.getWidth());
        return widest;
    }

    @Override
    public int getHeight() {
        place();
        return height;
    }

    /** As a vertical {@code Flow}'s: the stack past its tallest section's first line. */
    @Override
    public int extraLayoutHeight() {
        place();
        int oneRow = 0;
        for (PanelElement c : children) if (c.isVisible()) oneRow = Math.max(oneRow, c.getHeight() - Math.max(0, c.extraLayoutHeight()));
        return Math.max(0, height - oneRow);
    }

    @Override
    public void layoutWithin(int budget) {
        this.budget = Math.max(1, budget);
        for (PanelElement c : children) c.layoutWithin(this.budget);
    }

    @Override public void fillWidth(int width) {}

    // Render and input: the sections share the column's origin.

    @Override
    public void render(RenderContext ctx) {
        place();
        ChildDispatch.render(children, childContext(ctx));
    }

    @Override public void renderCulled(RenderContext ctx) { ChildDispatch.renderCulled(children, childContext(ctx)); }
    @Override public void renderOverlay(RenderContext ctx) { ChildDispatch.renderOverlay(children, childContext(ctx)); }

    @Override
    public boolean isInteractive() {
        for (PanelElement c : children) if (c.isVisible() && c.isInteractive()) return true;
        return false;
    }

    @Override public boolean hitTest(InputContext in) { place(); return ChildDispatch.hitTest(children, in); }
    @Override public boolean mouseClicked(InputContext in, int button) { place(); return ChildDispatch.mouseClicked(children, in, button); }
    @Override public boolean presentsSlotAt(InputContext in) { place(); return ChildDispatch.presentsSlotAt(children, in); }

    @Override
    public boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        place();
        return ChildDispatch.mouseScrolled(children, in, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        ChildDispatch.mouseReleased(children, in, button);
        return false;
    }

    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        return ChildDispatch.keyPressed(children, in, keyCode, scanCode, modifiers);
    }

    @Override public int @Nullable [] getActiveOverlayBounds(InputContext in) { return ChildDispatch.activeOverlay(children, in); }
    @Override public void notifyClickOutsideOverlay(InputContext in) { ChildDispatch.notifyClickOutside(children, in); }
    @Override public int @Nullable [] takeRevealRequest() { place(); return ChildDispatch.takeRevealRequest(children); }

    @Override
    public void screenKind(ScreenKind kind) {
        this.kind = kind;
        ChildDispatch.screenKind(children, kind);
    }

    @Override
    public void inDialog(boolean inDialog) {
        this.inDialog = inDialog;
        ChildDispatch.inDialog(children, inDialog);
    }

    @Override
    public void inBand(boolean inBand) {
        this.inBand = inBand;
        ChildDispatch.inBand(children, inBand);
    }

    @Override public void onAttach(Screen screen) { attachment.attach(screen, children); }
    @Override public void onDetach(Screen screen) { attachment.detach(screen, children); }
}
