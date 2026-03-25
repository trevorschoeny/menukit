package com.trevorschoeny.menukit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A custom YACL {@link Controller} for configuring multi-key keybinds directly
 * in the YACL config screen. Supports modifier keys (Ctrl, Shift, Alt, Cmd)
 * combined with any base key.
 *
 * <p><b>Interaction model:</b>
 * <ol>
 *   <li>Shows current binding as a button (e.g., "Ctrl+K" or "Unbound")</li>
 *   <li>Click the button to enter capture mode ("Press a key...")</li>
 *   <li>Press any key (with optional modifiers) to set the binding</li>
 *   <li>Press Escape to cancel capture without changing the binding</li>
 *   <li>Press Delete or Backspace to clear the binding to UNBOUND</li>
 * </ol>
 *
 * <p>Modifier-only key presses (pressing just Ctrl without another key) are
 * ignored — the user must press a non-modifier key to commit the binding.
 * This prevents accidental bindings to bare modifier keys.
 *
 * <p><b>Usage in a YACL config builder:</b>
 * <pre>{@code
 * Option.<MKKeybind>createBuilder()
 *     .name(Component.literal("Sort Region"))
 *     .binding(MKKeybind.UNBOUND,
 *         () -> config.sortKeybind,
 *         val -> config.sortKeybind = val)
 *     .controller(MKKeybindController::new)
 *     .build()
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKKeybindController implements Controller<MKKeybind> {

    private final Option<MKKeybind> option;

    public MKKeybindController(Option<MKKeybind> option) {
        this.option = option;
    }

    @Override
    public Option<MKKeybind> option() {
        return option;
    }

    /**
     * Formats the current (pending) value for display in the option row.
     * Delegates to {@link MKKeybind#getDisplayName()} for "Ctrl+K" style text.
     */
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
     * Handles click-to-capture, key capture, and visual feedback.
     */
    public static class KeybindWidget extends ControllerWidget<MKKeybindController> {

        // When true, the widget is waiting for the user to press a key.
        // All key events are consumed in this state to prevent them from
        // reaching other handlers (e.g., ESC closing the screen).
        private boolean capturing = false;

        public KeybindWidget(MKKeybindController control, YACLScreen screen, Dimension<Integer> dim) {
            super(control, screen, dim);
        }

        // ── Rendering ────────────────────────────────────────────────────

        /**
         * Override value text rendering to show capture-mode feedback.
         * During capture: yellow italic "Press a key..." text.
         * Normal mode: the formatted keybind name from the controller.
         */
        @Override
        protected Component getValueText() {
            if (capturing) {
                // Yellow italic signals to the user that input is being captured.
                // Matches vanilla's keybind screen behavior.
                return Component.literal("Press a key...")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC);
            }
            return control.formatValue();
        }

        @Override
        protected void drawHoveredControl(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            // No extra hover decoration needed — the value text + button rect
            // already provide sufficient visual feedback.
        }

        @Override
        protected int getHoveredControlWidth() {
            // Use the same width whether hovered or not — avoids layout jumps.
            return getUnhoveredControlWidth();
        }

        // ── Mouse Interaction ────────────────────────────────────────────

        /**
         * Clicking the widget toggles capture mode. If already capturing,
         * a click cancels capture (same as pressing Escape).
         */
        @Override
        public boolean onMouseClicked(double mouseX, double mouseY, int button) {
            if (!isMouseOver(mouseX, mouseY) || !isAvailable()) {
                return false;
            }

            if (capturing) {
                // Click while capturing = cancel (user changed their mind)
                capturing = false;
            } else {
                // Enter capture mode — next key press will set the binding
                capturing = true;
            }

            playDownSound();
            return true;
        }

        // ── Key Capture ──────────────────────────────────────────────────

        /**
         * In capture mode, intercepts all key presses:
         * <ul>
         *   <li><b>Escape:</b> cancel capture, restore previous value</li>
         *   <li><b>Delete/Backspace:</b> clear binding to UNBOUND</li>
         *   <li><b>Modifier-only keys:</b> ignored (wait for a base key)</li>
         *   <li><b>Any other key:</b> commit the binding with current modifiers</li>
         * </ul>
         *
         * <p>Returns true to consume the event and prevent it from propagating
         * (e.g., ESC would otherwise close the YACL screen).
         */
        @Override
        public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
            if (capturing) {
                // ESC cancels capture — don't change the binding
                if (keyCode == InputConstants.KEY_ESCAPE) {
                    capturing = false;
                    return true; // Consume so the screen doesn't close
                }

                // DELETE or BACKSPACE clears the binding to UNBOUND
                if (keyCode == InputConstants.KEY_DELETE || keyCode == InputConstants.KEY_BACKSPACE) {
                    control.option().requestSet(MKKeybind.UNBOUND);
                    capturing = false;
                    return true;
                }

                // Ignore modifier-only presses — we want a base key + modifiers,
                // not a binding to just "Ctrl" by itself. The modifier state is
                // captured in the `modifiers` parameter when the base key arrives.
                if (MKKeybind.isModifierKey(keyCode)) {
                    return true; // Consume but don't commit
                }

                // Commit the binding: base key + whatever modifiers are held.
                // The GLFW modifiers bitmask is passed through directly — it
                // already reflects the physical Ctrl/Shift/Alt/Cmd state.
                MKKeybind newBind = new MKKeybind(keyCode, scanCode, modifiers);
                control.option().requestSet(newBind);
                capturing = false;
                return true;
            }

            // Not capturing — let the parent handle normal key navigation.
            // Enter/Space toggles capture mode (accessibility: keyboard-only users).
            if (isFocused() && (keyCode == InputConstants.KEY_RETURN
                    || keyCode == InputConstants.KEY_SPACE
                    || keyCode == InputConstants.KEY_NUMPADENTER)) {
                capturing = true;
                playDownSound();
                return true;
            }

            return false;
        }

        // ── Focus Management ─────────────────────────────────────────────

        /**
         * When the widget loses focus, cancel any in-progress capture.
         * This handles edge cases like the user tabbing away while in
         * capture mode.
         */
        @Override
        public void unfocus() {
            super.unfocus();
            capturing = false;
        }
    }
}
