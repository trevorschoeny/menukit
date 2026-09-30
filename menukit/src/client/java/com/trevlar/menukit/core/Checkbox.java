package com.trevlar.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A boolean setting as a small square with a check mark and a label beside it:
 * the settings-screen convention, and a lens onto the consumer's boolean (§0026,
 * §0066).
 *
 * <pre>{@code
 * Checkbox.builder()
 *         .label(Component.literal("Armor restock"))
 *         .state(IPConfig::armorRestock, IPConfig::setArmorRestock)
 *         .disabledWhen(() -> !IPConfig.restockEnabled())
 *         .build();
 * }</pre>
 *
 * <p>{@code state(get, set)} is required: the check shows what {@code get} says
 * every frame, a click hands the new value to {@code set}, and nothing is stored,
 * so a setter that also changes other settings, or a menu kept across a trip to
 * another screen, shows the true value with no refresh step.
 *
 * <p>Sizes itself from its label: {@code BOX_SIZE + BOX_GAP + label width}
 * wide, one box tall; a label wider than the room the panel gives wraps beside the
 * box and the element grows taller. A click anywhere on it (box or label) flips
 * it, with vanilla's click sound; Tab focuses it and Enter or Space flips it; the
 * narrator reads the label and its state.
 *
 * <p>The check mark is vanilla's {@code icon/checkmark} sprite and the box
 * MenuKit's {@link PanelStyle#INSET}, so a resource pack that retextures vanilla's
 * GUI sprites restyles checkboxes too.
 */
public class Checkbox extends AbstractPanelElement {

    /** Size of the checkbox square, in pixels. */
    public static final int BOX_SIZE = ElementConstants.BOX_SIZE;

    /** Horizontal gap between the square and the label. */
    public static final int LABEL_GAP = ElementConstants.BOX_GAP;

    /** Vanilla check-mark sprite used for the checked state (9x8 pixels). */
    public static final Identifier CHECKMARK_SPRITE = Identifier.withDefaultNamespace("icon/checkmark");

    private static final int CHECKMARK_WIDTH = 9;
    private static final int CHECKMARK_HEIGHT = 8;

    private final Supplier<Component> label;
    private final BooleanSupplier stateGet;
    private final Consumer<Boolean> stateSet;
    // The vanilla stand-in for sound, focus and narration (ElementWidget), made at
    // the first screen attach, not with the element: a Containers menu builds its
    // panels on the dedicated server too, where no screen class exists.
    private @Nullable ElementWidget widget;

    // The label's wrap width beside the box (the one wrap helper): 0 = one line.
    // Recomputed every layout pass, so reversible.
    private int wrapWidth = 0;

    protected Checkbox(Builder b) {
        super(b);
        this.label = b.label;
        this.stateGet = b.stateGet;
        this.stateSet = b.stateSet;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ── Size: from the label ───────────────────────────────────────────

    @Override
    public int naturalWidth() {
        Component text = label.get();
        int w = text != null ? Minecraft.getInstance().font.width(text) : 0;
        return BOX_SIZE + LABEL_GAP + w;
    }

    @Override
    public int getWidth() {
        return wrapWidth > 0 ? BOX_SIZE + LABEL_GAP + wrapWidth : naturalWidth();
    }

    @Override
    public int getHeight() {
        Component text = label.get();
        if (text == null || wrapWidth <= 0) return BOX_SIZE;
        return Math.max(BOX_SIZE, MKText.lineCount(text, wrapWidth) * Minecraft.getInstance().font.lineHeight);
    }

    /** Wraps the label into the room beside the box when it does not fit on one line. */
    @Override
    public void layoutWithin(int budget) {
        wrapWidth = MKText.wrapWidth(label.get(), budget - BOX_SIZE - LABEL_GAP);
    }

    @Override
    public int extraLayoutHeight() {
        return Math.max(0, getHeight() - BOX_SIZE);
    }

    /** Intrinsic: a column fill does not stretch a checkbox. */
    @Override
    public void fillWidth(int width) {}

    @Override public boolean isInteractive() { return true; }

    // ── Rendering ──────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        boolean disabled = disabled(ctx);
        boolean checked = stateGet.getAsBoolean();
        boolean hovered = isHovered(ctx);
        Component text = label.get();
        if (widget != null) widget.track(sx, sy, getWidth(), getHeight(), hovered, !disabled, CommonComponents.optionNameValue(
                text != null ? text : Component.empty(), CommonComponents.optionStatus(checked)));
        var g = ctx.graphics();

        PanelRendering.renderPanel(g, sx, sy, BOX_SIZE, BOX_SIZE, disabled ? PanelStyle.DARK : PanelStyle.INSET);
        if (!disabled && (hovered || (widget != null && widget.focused()))) {
            g.fill(sx + 1, sy + 1, sx + BOX_SIZE - 1, sy + BOX_SIZE - 1, ElementConstants.HOVER_OVERLAY);
        }
        if (checked) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, CHECKMARK_SPRITE,
                    sx, sy + (BOX_SIZE - CHECKMARK_HEIGHT) / 2, CHECKMARK_WIDTH, CHECKMARK_HEIGHT);
        }
        if (text != null) {
            int color = disabled ? ElementConstants.TEXT_DISABLED : ElementConstants.TEXT_DARK;
            int textX = sx + BOX_SIZE + LABEL_GAP;
            // One line centres on the box; a wrapped block starts level with the box
            // top (centred against a 10px box it would float off it).
            int textY = wrapWidth > 0 ? sy : MKText.centeredTextY(sy, sy + BOX_SIZE);
            MKText.drawWrapped(g, text, wrapWidth, textX, textY, 0, color, false);
        }
        if (hovered) queueTooltip(ctx);
    }

    // ── Input ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        if (button != Click.LEFT || disabled(in)) return false;
        ElementWidget.playClickSound();
        flip();
        return true;
    }

    private void flip() {
        stateSet.accept(!stateGet.getAsBoolean());
    }

    private void press() {
        if (!ownDisabled()) flip();
    }

    @Override
    public void onAttach(net.minecraft.client.gui.screens.Screen screen) {
        if (widget == null) widget = new ElementWidget(this::press);
        widget.attach(screen);
    }

    @Override
    public void onDetach(net.minecraft.client.gui.screens.Screen screen) {
        if (widget != null) widget.detach(screen);
    }

    // ── Builder ────────────────────────────────────────────────────────

    public static class Builder extends AbstractPanelElement.Builder<Checkbox, Builder> {
        private @Nullable Supplier<Component> label;
        private @Nullable BooleanSupplier stateGet;
        private @Nullable Consumer<Boolean> stateSet;

        protected Builder() {}

        @Override protected Builder self() { return this; }

        /** Required: the label beside the box. */
        public Builder label(Component label) {
            Objects.requireNonNull(label, "label");
            return label(() -> label);
        }

        /**
         * A label read every frame. A label that changes width moves the panel's
         * layout with it; keep the alternatives about the same width.
         */
        public Builder label(Supplier<Component> label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        /** Required: the lens. {@code get} is read every frame; {@code set} receives the new value. */
        public Builder state(BooleanSupplier get, Consumer<Boolean> set) {
            this.stateGet = Objects.requireNonNull(get, "get");
            this.stateSet = Objects.requireNonNull(set, "set");
            return this;
        }

        @Override
        public Checkbox build() {
            require(label != null, "label(...) is required");
            require(stateGet != null, "state(get, set) is required; a Checkbox shows the consumer's value");
            return new Checkbox(this);
        }
    }
}
