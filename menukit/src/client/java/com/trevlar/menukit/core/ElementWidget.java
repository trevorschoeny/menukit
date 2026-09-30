package com.trevlar.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import org.jspecify.annotations.Nullable;

/**
 * The one shared helper that gives MenuKit's own controls vanilla's click sound,
 * narration and keyboard focus (§0066.4, §0020): a vanilla {@link AbstractButton}
 * standing in for the element in the screen's widget list. Used by
 * {@link Button}, {@link Toggle}, {@link Checkbox} and {@link Radio}.
 *
 * <h3>What vanilla does with it</h3>
 * <ul>
 *   <li><b>Keyboard focus.</b> It is a child of the screen, so Tab and the arrow
 *       keys move focus onto it in screen order. The element draws its hover
 *       look while this is focused.</li>
 *   <li><b>Activation.</b> Enter or Space on the focused widget runs vanilla's
 *       {@code AbstractButton.keyPressed}: the click sound, then the element's
 *       own press (a button's {@code onClick}, a toggle's flip).</li>
 *   <li><b>Narration.</b> It is a narratable of the screen; its message is the
 *       element's label and state, kept current every frame, so the narrator
 *       reads "Sort: ON" when it is focused or hovered.</li>
 * </ul>
 *
 * <h3>What it never does</h3>
 *
 * It is never drawn (added as a widget, not a renderable: the element draws
 * itself) and is deaf to the mouse ({@link #isMouseOver} is always false), since
 * MenuKit's host routes every click to the element itself; the element plays
 * {@link #playClickSound} on its own mouse press.
 *
 * <h3>Live only while drawn</h3>
 *
 * The element tells this widget where it is, whether it is enabled and what to
 * narrate, every frame it renders ({@link #track}). A widget whose element has
 * not rendered for {@link #LIVE_MS} (its panel hidden, its section closed, its
 * tab not shown) is inactive: Tab skips it and Enter does nothing, so focus
 * never lands on something the player cannot see. A disabled element's widget is
 * inactive the same way.
 */
final class ElementWidget extends AbstractButton {

    /**
     * How long after its element last rendered the widget stays focusable.
     * ponytail: a time window, not a frame count, because the host has no
     * per-frame hook that sees every element; it only has to outlast one frame at
     * the slowest frame rate a screen runs at. Tighten it to a frame stamp if a
     * hidden element ever keeps focus visibly.
     */
    static final long LIVE_MS = 250;

    private final Runnable press;
    private long renderedAt = Long.MIN_VALUE / 2;
    private @Nullable Screen screen;

    /** @param press what Enter or Space on the focused widget does: the element's own press */
    ElementWidget(Runnable press) {
        super(0, 0, 0, 0, Component.empty());
        this.press = press;
    }

    /**
     * Called by the element every frame it renders: where it is on screen, whether
     * it is hovered and enabled, and what the narrator should say.
     */
    void track(int x, int y, int w, int h, boolean hovered, boolean enabled, Component narration) {
        setX(x);
        setY(y);
        setWidth(w);
        setHeight(h);
        this.isHovered = hovered;
        this.active = enabled;
        setMessage(narration);
        this.renderedAt = Util.getMillis();
    }

    /** Whether the widget holds keyboard focus: the element draws its hover look while it does. */
    boolean focused() {
        return isFocused() && isActive();
    }

    // ── Screen membership ──────────────────────────────────────────────

    /** Joins {@code screen}'s widgets (and MenuKit's focus janitor). Idempotent. */
    void attach(Screen screen) {
        if (this.screen == screen) return;
        if (this.screen != null) detach(this.screen);
        this.screen = screen;
        MKFocus.addWidget(screen, this);
    }

    /** Leaves {@code screen}'s widgets. */
    void detach(Screen screen) {
        if (this.screen != screen) return;
        MKFocus.removeWidget(screen, this);
        this.screen = null;
    }

    // ── Vanilla's widget contract ──────────────────────────────────────

    @Override
    public boolean isActive() {
        return super.isActive() && Util.getMillis() - renderedAt < LIVE_MS;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        press.run();
    }

    /** Never drawn: the element draws itself. */
    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    /** Deaf to the mouse: MenuKit routes clicks to the element. */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /** Vanilla's button click, for an element's own mouse press. No-op with no game (a build-time check). */
    static void playClickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) playButtonClickSound(mc.getSoundManager());
    }
}
