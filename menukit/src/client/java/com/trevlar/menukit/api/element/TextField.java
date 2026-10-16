package com.trevlar.menukit.api.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A single-line text field: vanilla's {@link EditBox}, laid out and
 * lensed by MenuKit. Vanilla owns the input mechanism (selection,
 * IME, copy and paste, word navigation, the caret, the hint, the I-beam cursor);
 * MenuKit owns placement, the lens and the disabled cascade. The vanilla widget
 * is exposed through {@link #widget()}.
 *
 * <pre>{@code
 * TextField.builder().size(120, 16)
 *         .state(() -> draft, v -> draft = v)
 *         .hint(Component.literal("Name"))
 *         .onSubmit(this::rename)
 *         .build();
 * }</pre>
 *
 * <h3>The lens</h3>
 *
 * {@code state(get, set)} is required. Every edit (typing, paste, delete) hands
 * the new text to {@code set}; every frame the field shows what {@code get}
 * returns, so a value changed elsewhere (a Clear button writing the consumer's
 * field) shows at once. Nothing is stored beyond vanilla's own edit buffer, which
 * the lens overwrites whenever they differ.
 *
 * <h3>Enter</h3>
 *
 * With an {@code onSubmit}, Enter on the focused field calls it with the text and
 * is consumed. Without one, Enter is not the field's: it reaches the screen (a
 * dialog's default button, say).
 *
 * <h3>Filtering</h3>
 *
 * There is no filter: vanilla removed {@code EditBox.setFilter} in 26.x. A
 * consumer that wants to refuse some text does so in its setter (the lens shows
 * the refusal on the next frame, as the field snaps back to what {@code get}
 * returns).
 *
 * <h3>Lifecycle</h3>
 *
 * The EditBox is built at the screen's init ({@link #onAttach}), not with the
 * element, because vanilla's EditBox captures the font in its constructor and a
 * consumer may build its UI before the font is loaded. It is hosted by a
 * {@link WidgetHost} (since 7.2.0). The host places it, draws it after the panel
 * background, routes the mouse to it on any panel (a standalone screen's or one
 * over a vanilla screen) and gives it vanilla's keyboard focus. A field whose panel is hidden, or whose tab is not
 * shown, lets go of focus rather than typing unseen.
 */
public class TextField extends AbstractPanelElement {

    /** Vanilla's EditBox draws its text (and hint) this far inside its border, each side. */
    static final int TEXT_INSET = 4;
    /**
     * The text colours a dialog's field shows: vanilla's EditBox default while the
     * text is valid, red while it is not (an item dialog's count or components, a
     * colour dialog's hex).
     */
    static final int TEXT_VALID = 0xFFE0E0E0, TEXT_INVALID = 0xFFFF5555;

    private final Supplier<String> stateGet;
    private final Consumer<String> stateSet;
    private final Component label;
    private final @Nullable Integer maxLength;
    private final boolean bordered;
    private final boolean editable;
    private final @Nullable Component hint;
    private final @Nullable Consumer<String> onSubmit;

    /** As wide as the room it is given ({@link Builder#fill}). */
    private final boolean fills;

    /** Hosts the EditBox, made at the first attach (the font must exist), at this field's (0, 0). */
    private final WidgetHost<MKEditBox> host;

    protected TextField(Builder b) {
        super(b);
        this.stateGet = b.stateGet;
        this.stateSet = b.stateSet;
        this.label = b.label;
        this.maxLength = b.maxLength;
        this.bordered = b.bordered;
        this.editable = b.editable;
        this.hint = b.hint;
        this.onSubmit = b.onSubmit;
        this.fills = b.fill;
        this.host = WidgetHost.builder().widget(this::makeEditBox).size(width, height).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The vanilla widget this element draws and registers, or
     * {@code null} before the field's first screen init.
     */
    public @Nullable EditBox widget() {
        return host.widget();
    }

    /** The EditBox the host makes on first need: MenuKit's settings, then the consumer's text. */
    private MKEditBox makeEditBox() {
        MKEditBox box = new MKEditBox(Minecraft.getInstance().font, width, height, label, onSubmit);
        if (maxLength != null) box.setMaxLength(maxLength);
        box.setBordered(bordered);
        box.setEditable(editable);
        if (hint != null) box.setHint(hint);
        // The consumer's text first, then the responder: showing it is not an edit.
        box.setValue(stateGet.get());
        box.setResponder(stateSet);
        return box;
    }

    @Override public boolean isInteractive() { return true; }

    /** Its size's width; filling, at least its hint's, so the hint is never cut off. */
    @Override
    public int naturalWidth() {
        if (!fills || hint == null) return authoredWidth;
        return Math.max(authoredWidth, Minecraft.getInstance().font.width(hint) + 2 * TEXT_INSET);
    }

    /** Capped to the room it is given; filling, it takes all of it. */
    @Override
    public void layoutWithin(int budget) {
        width = fills ? Math.max(1, budget) : Math.min(authoredWidth, budget);
    }

    @Override
    public void render(RenderContext ctx) {
        MKEditBox box = host.widget();
        if (box != null) {
            // The lens, every frame: show what the consumer holds when it differs from
            // the edit buffer (a value changed elsewhere, or a setter that refused).
            String wanted = stateGet.get();
            if (wanted != null && !wanted.equals(box.getValue())) box.showWithoutReporting(wanted);
            box.setEditable(editable && !disabled(ctx));
            box.active = !disabled(ctx);
        }
        host.fillWidth(width);
        host.render(childContext(ctx).at(ctx.originX() + childX, ctx.originY() + childY));
        queueTooltip(ctx);
    }

    /** The host's input context: this field's own origin, and its own disabledWhen added. */
    private InputContext hostInput(InputContext in) {
        host.fillWidth(width);
        return in.at(in.originX() + childX, in.originY() + childY).disabledIf(ownDisabled());
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        return host.mouseClicked(hostInput(in), button);
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        return host.mouseReleased(hostInput(in), button);
    }

    @Override
    public boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        return host.mouseScrolled(hostInput(in), scrollX, scrollY);
    }

    @Override
    public void onAttach(Screen screen) {
        host.onAttach(screen);
    }

    @Override
    public void onDetach(Screen screen) {
        host.onDetach(screen);
    }

    public static class Builder extends AbstractPanelElement.Builder<TextField, Builder> {
        private @Nullable Supplier<String> stateGet;
        private @Nullable Consumer<String> stateSet;
        private Component label = Component.empty();
        private @Nullable Integer maxLength;
        private boolean bordered = true;
        private boolean editable = true;
        private @Nullable Component hint;
        private @Nullable Consumer<String> onSubmit;
        private boolean fill = false;

        protected Builder() {}

        @Override protected Builder self() { return this; }

        /** Required: size in pixels. */
        @Override
        public Builder size(int width, int height) {
            return super.size(width, height);
        }

        /**
         * As wide as the room its panel gives it (7.2.0); it asks for the size's width,
         * or its hint's when that is wider. For a field that spans a dialog or a
         * column, lining up with what is above and below it whatever their width.
         */
        public Builder fill() {
            this.fill = true;
            return this;
        }

        /**
         * Required: the lens. {@code get} is read every frame; {@code set} receives
         * the text after every edit. A {@code null} from {@code get} leaves the
         * field as it is.
         */
        public Builder state(Supplier<String> get, Consumer<String> set) {
            this.stateGet = Objects.requireNonNull(get, "get");
            this.stateSet = Objects.requireNonNull(set, "set");
            return this;
        }

        /** What the narrator calls the field. Default empty. */
        public Builder label(Component label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        /** The longest text it takes. Default vanilla's, 32. */
        public Builder maxLength(int maxLength) {
            if (maxLength <= 0) throw new IllegalArgumentException("maxLength must be > 0, got " + maxLength);
            this.maxLength = maxLength;
            return this;
        }

        /** Whether it draws vanilla's text-field frame. Default true. */
        public Builder bordered(boolean bordered) {
            this.bordered = bordered;
            return this;
        }

        /**
         * Read-only when false: visible, selectable and copyable, but not
         * changeable. Distinct from {@code disabledWhen}, which greys it and makes
         * it wholly inert. Default true.
         */
        public Builder editable(boolean editable) {
            this.editable = editable;
            return this;
        }

        /** Grey text shown while it is empty and unfocused. */
        public Builder hint(Component hint) {
            this.hint = Objects.requireNonNull(hint, "hint");
            return this;
        }

        /** What Enter on the focused field does, with its text. Without it, Enter is not consumed. */
        public Builder onSubmit(Consumer<String> onSubmit) {
            this.onSubmit = Objects.requireNonNull(onSubmit, "onSubmit");
            return this;
        }

        @Override
        public TextField build() {
            require(width > 0 && height > 0, "size(w, h) is required");
            require(stateGet != null, "state(get, set) is required; a TextField shows the consumer's text");
            return new TextField(this);
        }
    }

    /**
     * Vanilla's EditBox with MenuKit's two differences: Enter goes to
     * {@code onSubmit} only when there is one, and the lens can show the
     * consumer's text without reporting it back as an edit. A subclass, not a
     * mixin, so only MenuKit's fields are affected.
     */
    private static final class MKEditBox extends EditBox {

        private final @Nullable Consumer<String> onSubmit;
        private @Nullable Consumer<String> responder;
        private boolean showing = false;

        MKEditBox(net.minecraft.client.gui.Font font, int width, int height, Component label,
                  @Nullable Consumer<String> onSubmit) {
            super(font, 0, 0, width, height, label);
            this.onSubmit = onSubmit;
            super.setResponder(value -> {
                if (!showing && responder != null) responder.accept(value);
            });
        }

        @Override
        public void setResponder(Consumer<String> responder) {
            this.responder = responder;
        }

        /** Shows {@code value} without handing it to the responder: the consumer's own text. */
        void showWithoutReporting(String value) {
            showing = true;
            try {
                setValue(value);
            } finally {
                showing = false;
            }
        }

        @Override
        public boolean keyPressed(KeyEvent keyEvent) {
            if (onSubmit != null && isFocused() && keyEvent.isConfirmation()) {
                onSubmit.accept(getValue());
                return true;
            }
            return super.keyPressed(keyEvent);
        }
    }
}
