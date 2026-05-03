package com.trevorschoeny.menukit.core;

import com.trevorschoeny.menukit.mixin.ScreenAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Single-line editable text field. Phase 14d-3 — wraps vanilla
 * {@link EditBox} via composition rather than reimplementing the input
 * mechanism, per the *find-the-vanilla-flag-that-already-centralizes-the-
 * behavior* heuristic. ~600 LOC of vanilla-tested mechanism (selection
 * model, IME, validation, copy/paste, word navigation, cursor blink, hint
 * text, IBEAM hover cursor) inherited cleanly. Library owns layout
 * integration + lifecycle; vanilla owns the input mechanism.
 *
 * <h3>Lifecycle</h3>
 *
 * Lifecycle hooks ({@link #onAttach} / {@link #onDetach}) register the
 * wrapped EditBox with the host screen via {@code addRenderableWidget}
 * so vanilla's screen widget pipeline routes charTyped/keyPressed events
 * to it when focused. Without this, IME / focus / tab navigation don't
 * work — the EditBox renders but isn't reachable from the input pipeline.
 *
 * <h3>Lens pattern (Principle 8)</h3>
 *
 * Consumer holds the value; {@code onChange} fires on every mutation.
 * Imperative escape hatch via {@link #setValue(String)} for programmatic
 * mutation (e.g., a Clear button, server-pushed update). Canonical
 * pattern: keep consumer state authoritative via onChange; reach for
 * setValue only when programmatic mutation is the source of truth.
 *
 * <h3>Visibility-driven lifecycle gotcha (Q7 deferred per
 * {@code DEFERRED.md} 14d-3)</h3>
 *
 * If a panel containing a focused TextField is hidden mid-screen-life,
 * keystrokes still route to the (invisible) field via vanilla's widget
 * pipeline. v1 fires onAttach at screen init only (regardless of panel
 * visibility), onDetach at screen close. Recommended consumer pattern:
 * blur the field via {@code screen.setFocused(null)} before hiding the
 * panel, OR avoid hiding panels containing focused fields.
 *
 * <h3>Modal-with-text-input (Q4 deferred per {@code DEFERRED.md} 14d-3)</h3>
 *
 * v1 ships TextField for non-modal panels. Inside a {@code tracksAsModal}
 * panel, M9's keyboard mixin eats keystrokes (except Escape) before
 * vanilla's pipeline routes them to the focused widget — text input
 * doesn't work. Refining the modal-keyboard mixin to dispatch to focused
 * widgets first is a fold-on-evidence trigger; until then, keep TextField
 * out of modal panels.
 *
 * <h3>Cross-context applicability</h3>
 *
 * <ul>
 *   <li><b>MenuContext:</b> yes — text inputs on inventory-attached panels.</li>
 *   <li><b>StandaloneContext:</b> yes — MenuKit-native screens.</li>
 *   <li><b>SlotGroupContext:</b> no — slot-group anchors are for
 *       slot-related decorations; text input shape-mismatched.</li>
 *   <li><b>HudContext:</b> no — HUDs are render-only.</li>
 * </ul>
 */
public class TextField implements PanelElement {

    private final int childX;
    private final int childY;
    private final int width;
    private final int height;
    private final MenuKitEditBox editBox;

    /** Track which screen we're attached to so detach knows what to remove from. */
    private @Nullable Screen attachedScreen;

    private TextField(Builder b) {
        this.childX = b.childX;
        this.childY = b.childY;
        this.width = b.width;
        this.height = b.height;

        var font = Minecraft.getInstance().font;
        // EditBox starts at (0, 0); per-frame render() updates to match
        // panel content origin.
        this.editBox = new MenuKitEditBox(font, 0, 0, width, height,
                b.label, b.onSubmit);

        if (b.maxLength != null) editBox.setMaxLength(b.maxLength);
        if (b.bordered != null) editBox.setBordered(b.bordered);
        if (b.editable != null) editBox.setEditable(b.editable);
        if (b.hint != null) editBox.setHint(b.hint);
        if (b.filter != null) editBox.setFilter(b.filter);
        if (b.onChange != null) editBox.setResponder(b.onChange);
        if (b.initialValue != null) editBox.setValue(b.initialValue);
    }

    // ── PanelElement protocol ──────────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth()  { return width; }
    @Override public int getHeight() { return height; }

    @Override
    public void render(RenderContext ctx) {
        // Update wrapped EditBox screen-space coords to match the panel's
        // current content origin + this element's panel-local position.
        int screenX = ctx.originX() + childX;
        int screenY = ctx.originY() + childY;
        editBox.setX(screenX);
        editBox.setY(screenY);

        // Render the EditBox manually here so it draws AFTER the panel
        // background (which renders between super.render and this point).
        // The EditBox is registered with the screen via Screen.addWidget
        // (children + narratables only — NOT renderables), so it
        // participates in input dispatch / focus / charTyped / keyPressed
        // but doesn't auto-render during super.render. This sidesteps the
        // "EditBox covered by panel background" bug that would happen if
        // it were registered as a renderable.
        if (ctx.hasMouseInput()) {
            editBox.render(ctx.graphics(), ctx.mouseX(), ctx.mouseY(), 0f);
        } else {
            // HudContext or other input-less render path — render with
            // sentinel mouse coords so EditBox.isHovered returns false.
            editBox.render(ctx.graphics(), -1, -1, 0f);
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    @Override
    public void onAttach(Screen screen) {
        // Use Screen.addWidget (via ScreenAccessor mixin) to register the
        // EditBox for input dispatch (children + narratables) WITHOUT
        // adding it to renderables. The element renders the EditBox
        // manually in render() so it draws AFTER the panel background —
        // sidestepping the "panel background covers widget" bug that
        // would happen if the EditBox were registered as a renderable
        // (panel backgrounds render after super.render in MenuKitScreen).
        //
        // Idempotent: checking attachedScreen prevents double-attach.
        if (attachedScreen == screen) return;
        attachedScreen = screen;
        ((ScreenAccessor) screen).menuKit$addWidget(editBox);
    }

    @Override
    public void onDetach(Screen screen) {
        if (attachedScreen == screen) {
            ((ScreenAccessor) screen).menuKit$removeWidget(editBox);
            attachedScreen = null;
        }
    }

    // ── Imperative API ─────────────────────────────────────────────────

    /**
     * Returns the current value of the text field. Snapshot — re-read for
     * latest. Canonical pattern is to track value via the {@code onChange}
     * lens; getValue is the read-side counterpart for direct access.
     */
    public String getValue() {
        return editBox.getValue();
    }

    /**
     * Imperative escape hatch — programmatically sets the field's value.
     * Per Q5 advisor verdict: complements the lens-based onChange API
     * for cases where programmatic mutation is the source of truth
     * (e.g., a Clear button, undo/reset, server-pushed update).
     *
     * <p>Canonical pattern is to keep consumer state authoritative via
     * the onChange callback. Use setValue only when the source of truth
     * is genuinely outside the field's input flow.
     *
     * <p>Triggers the configured {@code onChange} responder (vanilla
     * EditBox semantic — setValue calls onValueChange which fires the
     * responder).
     */
    public void setValue(String value) {
        editBox.setValue(value);
    }

    /** Returns whether the wrapped EditBox is currently focused. */
    public boolean isFocused() {
        return editBox.isFocused();
    }

    // ── Builder ────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int childX = 0;
        private int childY = 0;
        private int width = -1;
        private int height = -1;
        private Component label = Component.empty();
        private @Nullable String initialValue;
        private @Nullable Integer maxLength;
        private @Nullable Boolean bordered;
        private @Nullable Boolean editable;
        private @Nullable Component hint;
        private @Nullable Predicate<String> filter;
        private @Nullable Consumer<String> onChange;
        private @Nullable Consumer<String> onSubmit;

        private Builder() {}

        /** Panel-local position. Default (0, 0). */
        public Builder at(int childX, int childY) {
            this.childX = childX;
            this.childY = childY;
            return this;
        }

        /** Required: width × height in pixels. */
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Narration label (passed through to EditBox constructor).
         * Optional; defaults to empty Component.
         */
        public Builder label(Component label) {
            this.label = Objects.requireNonNull(label, "label must not be null");
            return this;
        }

        /** Optional initial value. Default: empty string. */
        public Builder initialValue(String value) {
            this.initialValue = Objects.requireNonNull(value, "value must not be null");
            return this;
        }

        /** Optional max character length. Default: 256 (vanilla EditBox default is 32). */
        public Builder maxLength(int maxLength) {
            if (maxLength <= 0) {
                throw new IllegalArgumentException("maxLength must be > 0, got " + maxLength);
            }
            this.maxLength = maxLength;
            return this;
        }

        /**
         * Optional bordered mode (text_field sprite background). Default
         * true. Set false for borderless inline fields (e.g., chat-style).
         */
        public Builder bordered(boolean bordered) {
            this.bordered = bordered;
            return this;
        }

        /**
         * Optional read-only mode. Default true (editable). Set false
         * for display-only fields where typing/paste/cut are suppressed
         * but cursor/selection are still movable.
         */
        public Builder editable(boolean editable) {
            this.editable = editable;
            return this;
        }

        /**
         * Optional placeholder text shown when the field is empty AND
         * unfocused. Default: no hint.
         */
        public Builder hint(Component hint) {
            this.hint = Objects.requireNonNull(hint, "hint must not be null");
            return this;
        }

        /**
         * Optional input filter. Called BEFORE every value mutation;
         * if the filter rejects the candidate value, the mutation is
         * skipped. Default: pass-all.
         */
        public Builder filter(Predicate<String> filter) {
            this.filter = Objects.requireNonNull(filter, "filter must not be null");
            return this;
        }

        /**
         * Optional value-change callback (lens write). Fires on every
         * mutation: typing, paste, delete, programmatic setValue.
         * Default: no callback.
         */
        public Builder onChange(Consumer<String> onChange) {
            this.onChange = Objects.requireNonNull(onChange, "onChange must not be null");
            return this;
        }

        /**
         * Optional submission callback. Fires when the player presses
         * Enter while the field is focused. Default: no callback.
         */
        public Builder onSubmit(Consumer<String> onSubmit) {
            this.onSubmit = Objects.requireNonNull(onSubmit, "onSubmit must not be null");
            return this;
        }

        public TextField build() {
            if (width <= 0 || height <= 0) {
                throw new IllegalStateException(
                        "TextField.Builder: .size(w, h) must be called with positive values; "
                        + "got width=" + width + ", height=" + height);
            }
            return new TextField(this);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // MenuKitEditBox — subclass that captures Enter for onSubmit
    // ──────────────────────────────────────────────────────────────────

    /**
     * EditBox subclass that captures Enter key (and KP_Enter) while
     * focused, fires the registered onSubmit callback before delegating
     * to super.keyPressed for any other keys.
     *
     * <p>Per Q3 advisor verdict: subclass over mixin — subclass is
     * per-element scoped (only affects MenuKit's wrapped EditBoxes),
     * mixin would affect ALL EditBox instances ecosystem-wide.
     */
    private static final class MenuKitEditBox extends EditBox {

        private final @Nullable Consumer<String> onSubmit;

        MenuKitEditBox(net.minecraft.client.gui.Font font,
                       int x, int y, int width, int height,
                       Component label,
                       @Nullable Consumer<String> onSubmit) {
            super(font, x, y, width, height, label);
            this.onSubmit = onSubmit;
        }

        @Override
        public boolean keyPressed(KeyEvent keyEvent) {
            if (this.isFocused() && keyEvent.isConfirmation()) {
                if (onSubmit != null) {
                    onSubmit.accept(this.getValue());
                }
                return true;
            }
            return super.keyPressed(keyEvent);
        }
    }
}
