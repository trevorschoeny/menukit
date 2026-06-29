package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.trevorschoeny.menukit.core.layout.ElementSpec;

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
public class TextField extends AbstractPanelElement<TextField> {

    @Override protected TextField self() { return this; }

    // Non-final since Pass 3 column-fill (fillWidth); render() pushes it onto the EditBox.
    private int width;
    private final int height;

    // ── Deferred construction (Phase 18r-5 follow-up) ─────────────────
    //
    // The wrapped EditBox is NOT constructed in the TextField constructor
    // — it's lazily constructed in onAttach() via ensureEditBox(). The
    // reason: vanilla EditBox's constructor caches `Minecraft.getInstance()
    // .font` into its own `this.font` field at construction time. If a
    // consumer mod creates a TextField at Fabric `onInitializeClient` time
    // (the natural entry point for "wire up my UI"), `Minecraft.getInstance()
    // .font` is null — the font atlas hasn't loaded yet. That null gets
    // baked into the EditBox permanently → crash on first render. By
    // deferring EditBox construction to onAttach (which runs at screen-
    // init, well after font load), we guarantee a valid font reference.
    //
    // Builder config is stashed in these fields and replayed by
    // ensureEditBox() the first time the element is attached.
    private final Component label;
    private final @Nullable String initialValue;
    private final @Nullable Integer maxLength;
    private final @Nullable Boolean bordered;
    private final @Nullable Boolean editable;
    private final @Nullable Component hint;
    private final @Nullable Predicate<String> filter;
    private final @Nullable Consumer<String> onChange;
    private final @Nullable Consumer<String> onSubmit;

    /** Lazily constructed in onAttach() via ensureEditBox(). Null until first attach. */
    private @Nullable MKEditBox editBox;

    /** Track which screen we're attached to so detach knows what to remove from. */
    private @Nullable Screen attachedScreen;

    // tooltipSupplier hoisted to AbstractPanelElement (Phase 18r-2).

    private TextField(Builder b) {
        this.childX = b.childX;
        this.childY = b.childY;
        this.width = b.width;
        this.height = b.height;

        // Stash builder config for deferred EditBox construction.
        // See the field-block comment above for the rationale, and
        // ensureEditBox() for where these are applied.
        this.label = b.label;
        this.initialValue = b.initialValue;
        this.maxLength = b.maxLength;
        this.bordered = b.bordered;
        this.editable = b.editable;
        this.hint = b.hint;
        this.filter = b.filter;
        this.onChange = b.onChange;
        this.onSubmit = b.onSubmit;
    }

    /**
     * Lazily constructs the wrapped EditBox and applies stashed builder
     * config. Called from onAttach() — at that point Minecraft is fully
     * initialized and {@code Minecraft.getInstance().font} is non-null.
     *
     * <p>Idempotent: returns immediately if editBox is already constructed.
     */
    private void ensureEditBox() {
        if (editBox != null) return;
        var font = Minecraft.getInstance().font;
        // EditBox starts at (0, 0); per-frame render() updates to match
        // panel content origin.
        editBox = new MKEditBox(font, 0, 0, width, height,
                label, onSubmit);

        if (maxLength != null) editBox.setMaxLength(maxLength);
        if (bordered != null) editBox.setBordered(bordered);
        if (editable != null) editBox.setEditable(editable);
        if (hint != null) editBox.setHint(hint);
        if (filter != null) editBox.setFilter(filter);
        if (onChange != null) editBox.setResponder(onChange);
        if (initialValue != null) editBox.setValue(initialValue);
    }

    // ── PanelElement protocol ──────────────────────────────────────────

    @Override public int getWidth()  { return width; }
    @Override public int getHeight() { return height; }

    /** Column-fill (Pass 3): stretch the field to the column's widest extent.
     *  render() pushes the new width onto the wrapped EditBox each frame. */
    @Override public void fillWidth(int width) { this.width = width; }

    /** Interactive — handles click-to-focus/typing, so it claims (blocks vanilla behind) on a non-opaque panel. */
    @Override public boolean isInteractive() { return true; }

    @Override
    public void render(RenderContext ctx) {
        // Null-guard: EditBox is lazily constructed in onAttach(). If
        // render() somehow runs before onAttach (lifecycle bug, or a
        // detached element being defensively rendered), skip — a silent
        // skip is preferable to an NPE crash mid-frame.
        if (editBox == null) return;

        // Update wrapped EditBox screen-space coords to match the panel's
        // current content origin + this element's panel-local position.
        int screenX = ctx.originX() + childX;
        int screenY = ctx.originY() + childY;
        editBox.setX(screenX);
        editBox.setY(screenY);
        // Sync width per-frame too (mirrors X/Y) so column-fill (fillWidth) takes
        // effect: the EditBox is sized once at construction, so a post-build
        // width change must be pushed onto it here.
        editBox.setWidth(width);

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

        // Tooltip — fires over the text-field bounds. Useful for "what
        // format does this field accept" disclosure. Queued for end-of-
        // frame flush.
        Supplier<Component> tooltipSupplier = getTooltipSupplier();
        if (tooltipSupplier != null && ctx.hasMouseInput() && isHovered(ctx)) {
            Component ttText = tooltipSupplier.get();
            if (ttText != null) {
                MKTooltip.queue(ctx.graphics(), ttText,
                        ctx.mouseX(), ctx.mouseY());
            }
        }
    }

    // ── Chainable configuration ────────────────────────────────────────
    //
    // showWhen + tooltip return TextField for free via the SELF self-type.
    // Position + size are configured on the Builder (.at()/.size()).

    // ── Lifecycle ──────────────────────────────────────────────────────

    @Override
    public void onAttach(Screen screen) {
        // Use Screen.addWidget (via ScreenAccessor mixin) to register the
        // EditBox for input dispatch (children + narratables) WITHOUT
        // adding it to renderables. The element renders the EditBox
        // manually in render() so it draws AFTER the panel background —
        // sidestepping the "panel background covers widget" bug that
        // would happen if the EditBox were registered as a renderable
        // (panel backgrounds render after super.render in MKScreen).
        //
        // Idempotent: checking attachedScreen prevents double-attach.
        if (attachedScreen == screen) return;
        attachedScreen = screen;
        // Lazy-construct the EditBox here — Minecraft is fully
        // initialized by onAttach time, so font is non-null. See the
        // field-block comment above for why we can't construct earlier.
        ensureEditBox();
        // MKFocus.addWidget wraps the screen's input-pipeline registration
        // and opts the EditBox into MK-managed focus semantics — the
        // focus-janitor mixin will clear focus when the user clicks
        // outside the EditBox's bounds. See MKFocus class javadoc for
        // why this matters (MK's panel-eat suppresses vanilla's natural
        // focus-transition flow).
        MKFocus.addWidget(screen, editBox);
    }

    @Override
    public void onDetach(Screen screen) {
        if (attachedScreen == screen) {
            // Null-guard: if onDetach is called before onAttach ever
            // ran (e.g. a panel was discarded before its first attach),
            // editBox is still null. Skip removal rather than NPE.
            if (editBox != null) {
                MKFocus.removeWidget(screen, editBox);
            }
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
        // Pre-attach: EditBox doesn't exist yet — return the builder's
        // initialValue (or empty string) as the canonical pre-attach
        // value source. Once attached, return the live EditBox value.
        if (editBox == null) return initialValue != null ? initialValue : "";
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
        // Pre-attach: silently no-op. Consumers setting initial state
        // before attach should use Builder.initialValue(); setValue is
        // for post-attach imperative mutation. We can't lazy-construct
        // here because the consumer may be in onInitializeClient where
        // font is still null — exactly the crash this whole pattern
        // exists to prevent.
        if (editBox == null) return;
        editBox.setValue(value);
    }

    /** Returns whether the wrapped EditBox is currently focused. */
    public boolean isFocused() {
        // Pre-attach: no EditBox means no focus possible.
        if (editBox == null) return false;
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
         *
         * <p><b>Read-only vs. disabled (Phase 3b — Item 8).</b> TextField
         * uses {@code editable(false)} as its distinct READ-ONLY spelling —
         * deliberately NOT unified with the {@code disabledWhen} knob the
         * other builder widgets carry. They mean different things:
         * <ul>
         *   <li><b>read-only</b> ({@code editable(false)}): the field stays
         *       fully visible and the caret/selection are still movable; only
         *       <i>modification</i> (typing, paste, cut) is suppressed. Use
         *       for "you can read and copy this, but not change it."</li>
         *   <li><b>disabled</b> (the {@code disabledWhen} predicate on
         *       Slider/Dropdown/etc.): the control is greyed and wholly inert
         *       — no interaction at all.</li>
         * </ul>
         * A consumer who wants the greyed-and-inert look gates the whole
         * field's visibility (or wraps it) rather than reaching for a
         * disabled flag; read-only is the semantically-correct primitive
         * here, so the two are kept separate by design.
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

        /**
         * Layout terminal (Phase 3b — Item 6). Returns an {@link ElementSpec}
         * for use in {@link com.trevorschoeny.menukit.core.layout.Row} /
         * {@link com.trevorschoeny.menukit.core.layout.Column}. The spec's
         * reported dimensions are the configured {@code .size(w, h)}; the
         * layout helper calls {@link ElementSpec#at(int, int)}, which re-runs
         * this builder's full configuration positioned at the computed
         * coordinates.
         */
        public ElementSpec spec() {
            if (width <= 0 || height <= 0) {
                throw new IllegalStateException(
                        "TextField.Builder.spec(): .size(w, h) must be called with positive values; "
                        + "got width=" + width + ", height=" + height);
            }
            // Snapshot every configured field so each at(x,y) builds a fresh,
            // correctly-positioned TextField. (childX/childY are fixed at
            // construction per THESIS Principle 4 — ElementSpec supplies them.)
            final int w = width, h = height;
            final Component lbl = label;
            final String iv = initialValue;
            final Integer ml = maxLength;
            final Boolean bd = bordered;
            final Boolean ed = editable;
            final Component hn = hint;
            final Predicate<String> ft = filter;
            final Consumer<String> oc = onChange;
            final Consumer<String> os = onSubmit;
            return new ElementSpec() {
                @Override public int width()  { return w; }
                @Override public int height() { return h; }
                @Override public PanelElement at(int x, int y) {
                    Builder b = TextField.builder().at(x, y).size(w, h).label(lbl);
                    if (iv != null) b.initialValue(iv);
                    if (ml != null) b.maxLength(ml);
                    if (bd != null) b.bordered(bd);
                    if (ed != null) b.editable(ed);
                    if (hn != null) b.hint(hn);
                    if (ft != null) b.filter(ft);
                    if (oc != null) b.onChange(oc);
                    if (os != null) b.onSubmit(os);
                    return b.build();
                }
            };
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // MKEditBox — subclass that captures Enter for onSubmit
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
    private static final class MKEditBox extends EditBox {

        private final @Nullable Consumer<String> onSubmit;

        MKEditBox(net.minecraft.client.gui.Font font,
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
