package com.trevorschoeny.menukit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * A custom YACL {@link Controller} for configuring multi-key keybinds directly
 * in the YACL config screen. Supports arbitrary key combos (up to 3 keys),
 * including mouse buttons, with live preview during capture.
 *
 * <p>Capture logic is delegated to {@link MKKeybindCapture}, the shared engine
 * used by both this YACL widget and the vanilla Controls screen mixin.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKKeybindController implements Controller<MKKeybind> {

    private final Option<MKKeybind> option;

    // The KeyMapping that owns this keybind option, if any. Used to exclude
    // self from conflict detection.
    final KeyMapping excludeMapping;

    /**
     * When set, the next {@link KeyBindsScreen} that opens will auto-scroll
     * to bring this KeyMapping into view. Consumed (nulled) by the
     * {@code MKKeyBindsScreenMixin} after scrolling.
     */
    public static KeyMapping pendingScrollTarget = null;

    /**
     * Creates a controller with no exclusion -- all registered KeyMappings are
     * checked for conflicts.
     */
    public MKKeybindController(Option<MKKeybind> option) {
        this(option, null);
    }

    /**
     * Creates a controller that excludes the given KeyMapping from conflict
     * detection (to avoid self-conflict).
     */
    public MKKeybindController(Option<MKKeybind> option, KeyMapping exclude) {
        this.option = option;
        this.excludeMapping = exclude;
    }

    @Override
    public Option<MKKeybind> option() {
        return option;
    }

    @Override
    public Component formatValue() {
        return option.pendingValue().getDisplayName();
    }

    @Override
    public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> widgetDimension) {
        return new KeybindWidget(this, screen, widgetDimension);
    }

    // ── Inner Widget ─────────────────────────────────────────────────────────

    /**
     * The interactive widget that renders inside the YACL option list.
     * Handles click-to-capture, live preview, conflict detection, and an
     * "Open in Controls" button.
     *
     * <p>All capture logic (key accumulation, high water mark tracking,
     * finalization) is delegated to {@link MKKeybindCapture}.
     */
    public static class KeybindWidget extends ControllerWidget<MKKeybindController> {

        // ── Shared Capture Engine ─────────────────────────────────────────

        private final MKKeybindCapture capture;

        // ── Conflict State ────────────────────────────────────────────────

        private List<MKKeybindConflicts.Conflict> cachedConflicts = List.of();
        private MKKeybind lastCheckedBind = null;

        // "Open in Controls" gear button
        private static final String GEAR_TEXT = "\u2699";
        private static final int GEAR_PADDING = 4;

        private final KeyMapping excludeMapping;

        public KeybindWidget(MKKeybindController control, YACLScreen screen, Dimension<Integer> dim) {
            super(control, screen, dim);
            this.excludeMapping = control.excludeMapping;

            // Wire up the shared capture engine with YACL-specific callbacks:
            // - onFinalize: apply the new binding to the YACL option
            // - onCancel: no-op (just stops capturing, binding unchanged)
            // - onUpdate: no-op (getValueText() reads from capture.getPreviewText())
            // - onClear: clear binding to UNBOUND
            this.capture = new MKKeybindCapture(
                    bind -> control.option().requestSet(bind),
                    () -> { /* cancel -- binding unchanged */ },
                    () -> { /* update -- live preview handled by getValueText() */ },
                    () -> control.option().requestSet(MKKeybind.UNBOUND)
            );
        }

        // ── Capture Control ───────────────────────────────────────────────

        /**
         * Starts a new capture session via the shared engine.
         * Registers this widget as the active capture target.
         */
        public void startCapture() {
            capture.start();
        }

        /**
         * Stops capture. Called internally by the capture engine callbacks
         * and by unfocus().
         */
        private void endCapture() {
            // No-op placeholder -- if future external routing needs arise,
            // cleanup logic goes here. Currently all release events are
            // handled directly by the widget's own override methods.
        }

        // ── Key Event Handling ────────────────────────────────────────────

        /**
         * Called when a key is pressed. Delegates to the shared capture engine.
         *
         * @return true if the event was consumed
         */
        public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
            if (capture.isCapturing()) {
                InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(keyCode);
                boolean consumed = capture.onKeyPressed(key);
                // If capture ended (finalize, cancel, or clear), clean up static ref
                if (!capture.isCapturing()) {
                    endCapture();
                }
                return consumed;
            }

            // Not capturing -- Enter/Space toggles capture mode
            if (isFocused() && (keyCode == InputConstants.KEY_RETURN
                    || keyCode == InputConstants.KEY_SPACE
                    || keyCode == InputConstants.KEY_NUMPADENTER)) {
                startCapture();
                playDownSound();
                return true;
            }

            return false;
        }

        /**
         * Called when a key is released. Delegates to the shared capture engine.
         *
         * @return true if the event was consumed
         */
        public boolean onKeyReleased(int keyCode, int scanCode) {
            if (!capture.isCapturing()) return false;

            InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(keyCode);
            boolean consumed = capture.onKeyReleased(key);
            if (!capture.isCapturing()) {
                endCapture();
            }
            return consumed;
        }

        /**
         * Called when a mouse button is clicked during capture.
         *
         * @return true if the event was consumed
         */
        public boolean onMouseClickedCapture(int button) {
            if (!capture.isCapturing()) return false;

            InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
            boolean consumed = capture.onMousePressed(key);
            if (!capture.isCapturing()) {
                endCapture();
            }
            return consumed;
        }

        /**
         * Called when a mouse button is released during capture.
         *
         * @return true if the event was consumed
         */
        public boolean onMouseReleasedCapture(int button) {
            if (!capture.isCapturing()) return false;

            InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
            boolean consumed = capture.onMouseReleased(key);
            if (!capture.isCapturing()) {
                endCapture();
            }
            return consumed;
        }

        // ── Conflict Detection ────────────────────────────────────────────

        private void refreshConflicts() {
            MKKeybind current = control.option().pendingValue();
            if (current != lastCheckedBind) {
                lastCheckedBind = current;
                cachedConflicts = MKKeybindConflicts.findConflicts(current, excludeMapping);
            }
        }

        private boolean hasConflicts() {
            refreshConflicts();
            return !cachedConflicts.isEmpty();
        }

        // ── Rendering ─────────────────────────────────────────────────────

        @Override
        protected Component getValueText() {
            if (capture.isCapturing()) {
                // Delegate to the shared engine's preview text
                return capture.getPreviewText();
            }

            Component base = control.formatValue();

            if (hasConflicts()) {
                return Component.literal("[ ")
                        .append(base.copy().withStyle(ChatFormatting.RED))
                        .append(" ]")
                        .withStyle(ChatFormatting.YELLOW);
            }

            return base;
        }

        @Override
        protected void drawValueText(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            Component valueText = getValueText();
            int valueWidth = textRenderer.width(valueText);
            int gearWidth = textRenderer.width(GEAR_TEXT);

            int valueX = getDimension().xLimit() - valueWidth - getXPadding();
            int gearX = valueX - gearWidth - GEAR_PADDING;
            int textY = getTextY();

            // Draw gear icon
            boolean gearHovered = isGearHovered(mouseX, mouseY, gearX, gearWidth);
            int gearColor = gearHovered ? 0xFFFFFFFF : 0xFFA0A0A0;
            graphics.drawString(textRenderer, GEAR_TEXT, gearX, textY, gearColor, true);

            // Draw value text
            graphics.drawString(textRenderer, valueText, valueX, textY, getValueColor(), true);

            // Conflict tooltip
            if (hasConflicts() && !capture.isCapturing()) {
                boolean overValue = mouseX >= valueX && mouseX <= getDimension().xLimit()
                        && mouseY >= getDimension().y() && mouseY <= getDimension().yLimit();
                if (overValue) {
                    List<Component> tooltipLines = MKKeybindConflicts.buildTooltipLines(cachedConflicts);
                    graphics.setComponentTooltipForNextFrame(textRenderer, tooltipLines, mouseX, mouseY);
                }
            }

            // Gear tooltip
            if (gearHovered && !capture.isCapturing()) {
                graphics.setTooltipForNextFrame(textRenderer,
                        Component.translatable("key.menukit.open_controls"),
                        mouseX, mouseY);
            }
        }

        @Override
        protected void drawHoveredControl(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (hasConflicts() && !capture.isCapturing()) {
                Component valueText = getValueText();
                int valueWidth = textRenderer.width(valueText);
                int gearWidth = textRenderer.width(GEAR_TEXT);
                int barX = getDimension().xLimit() - valueWidth - getXPadding()
                        - gearWidth - GEAR_PADDING - 6;
                graphics.fill(barX, getDimension().y() + 1,
                        barX + 3, getDimension().yLimit() - 1,
                        0xFFFFFF00);
            }
        }

        @Override
        protected int getHoveredControlWidth() {
            int gearWidth = textRenderer.width(GEAR_TEXT) + GEAR_PADDING;
            return getUnhoveredControlWidth() + gearWidth;
        }

        @Override
        protected int getUnhoveredControlWidth() {
            int gearWidth = textRenderer.width(GEAR_TEXT) + GEAR_PADDING;
            return textRenderer.width(getValueText()) + gearWidth;
        }

        // ── Gear Button Hit Testing ───────────────────────────────────────

        private boolean isGearHovered(double mouseX, double mouseY, int gearX, int gearWidth) {
            return mouseX >= gearX && mouseX <= gearX + gearWidth
                    && mouseY >= getDimension().y() && mouseY <= getDimension().yLimit();
        }

        private int getGearX() {
            Component valueText = getValueText();
            int valueWidth = textRenderer.width(valueText);
            int gearWidth = textRenderer.width(GEAR_TEXT);
            return getDimension().xLimit() - valueWidth - getXPadding() - gearWidth - GEAR_PADDING;
        }

        // ── Mouse Interaction ─────────────────────────────────────────────

        @Override
        public boolean onMouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver(mouseX, mouseY) || !isAvailable()) {
                return false;
            }

            // Check gear icon click -- opens vanilla KeyBindsScreen scrolled
            // to the relevant keybind entry
            int gearX = getGearX();
            int gearWidth = textRenderer.width(GEAR_TEXT);
            if (isGearHovered(mouseX, mouseY, gearX, gearWidth)) {
                Minecraft mc = Minecraft.getInstance();
                // Tell the mixin which KeyMapping to scroll to after init
                pendingScrollTarget = excludeMapping;
                mc.setScreen(new KeyBindsScreen(screen, mc.options));
                playDownSound();
                return true;
            }

            // During capture, mouse clicks add to the combo
            if (capture.isCapturing()) {
                return onMouseClickedCapture(button);
            }

            // Normal click: enter capture mode
            startCapture();
            playDownSound();
            return true;
        }

        // ── Key Release (YACL propagates via AbstractWidget.keyReleased) ──

        @Override
        public boolean onKeyReleased(int keyCode, int scanCode, int modifiers) {
            return onKeyReleased(keyCode, scanCode);
        }

        // ── Mouse Release (YACL propagates via AbstractWidget.mouseReleased) ──

        @Override
        public boolean onMouseReleased(double mouseX, double mouseY, int button) {
            if (capture.isCapturing()) {
                return onMouseReleasedCapture(button);
            }
            return false;
        }

        // ── Focus Management ──────────────────────────────────────────────

        @Override
        public void unfocus() {
            super.unfocus();
            if (capture.isCapturing()) {
                // Force-stop capture without changing binding. We reset
                // the engine by starting and immediately cancelling -- but
                // simpler to just let the cancel callback fire.
                // The capture engine's onCancel is a no-op for YACL,
                // so we just need to clear the static ref.
                // Note: We can't call capture's internal cancel directly,
                // so we use the key escape path.
                InputConstants.Key escKey = InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_ESCAPE);
                capture.onKeyPressed(escKey);
                endCapture();
            }
        }
    }
}
