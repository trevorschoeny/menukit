package com.trevorschoeny.menukit.core.dialog;

import com.trevorschoeny.menukit.core.Button;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelPosition;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.TextLabel;
import com.trevorschoeny.menukit.core.layout.Column;
import com.trevorschoeny.menukit.core.layout.CrossAlign;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builder for a modal confirm/cancel dialog. Composes a {@link Panel} from
 * existing primitives ({@link TextLabel}, {@link Button}, M8 layout helpers)
 * and ships it with {@link Panel#modal() modal flags} pre-configured
 * (opaque + dimsBehind + tracksAsModal).
 *
 * <p>The returned Panel is added to the consumer's UI like any other panel.
 * Visibility is consumer-managed via {@link Panel#showWhen} per Principle 8
 * ("elements are lenses, not stores"). Confirm and Cancel callbacks fire on
 * the corresponding buttons; consumers mutate their own state to dismiss.
 *
 * <h3>Canonical usage</h3>
 *
 * <pre>{@code
 * private boolean confirmDeleteOpen = false;
 *
 * Panel deleteDialog = ConfirmDialog.builder()
 *     .title(Component.literal("Delete sandbox?"))
 *     .body(Component.literal("This cannot be undone."))
 *     .onConfirm(() -> { confirmDeleteOpen = false; deleteSandbox(); })
 *     .onCancel(() -> confirmDeleteOpen = false)
 *     .build()
 *     .showWhen(() -> confirmDeleteOpen);
 *
 * // Register the dialog with the host screen via ScreenPanelAdapter.
 * new ScreenPanelAdapter(deleteDialog, MenuRegion.CENTER)
 *     .on(MyMenuScreen.class);
 *
 * // Trigger from another button:
 * deleteButton.onClick(() -> confirmDeleteOpen = true);
 * }</pre>
 *
 * <h3>Cross-context applicability</h3>
 *
 * Dialogs target MenuContext (vanilla container screen decoration) and
 * StandaloneContext (decorating vanilla standalone screens via mixin).
 * MenuKit-native screens ({@code MenuKitScreen} subclasses) need a separate
 * dispatch path that's filed as a follow-on architectural decision — see
 * {@code Design Docs/Elements/DIALOGS.md} §4.5. SlotGroupContext and HudContext
 * don't apply (anchor mismatch / no input dispatch).
 *
 * <h3>Multi-line body</h3>
 *
 * Body is a single {@link Component} in v1. For multi-line dialog bodies,
 * compose the Panel manually using the same M8 patterns this builder uses
 * internally (see DIALOGS.md §5 escape-hatch example). A multi-line
 * {@code TextLabel} variant is filed as a deferred primitive (see
 * {@code DEFERRED.md}).
 *
 * @see AlertDialog single-button variant
 */
public final class ConfirmDialog {

    /** Padding inside the dialog Panel between chrome and content. */
    public static final int PADDING = 8;
    /** Vertical gap between title, body, and button row sections. */
    public static final int SECTION_GAP = 6;
    /** Horizontal gap between buttons in the button bar. */
    public static final int BUTTON_GAP = 4;
    /** Default button width. */
    public static final int BUTTON_W = 60;
    /** Default button height — matches vanilla's confirm-screen button proportions. */
    public static final int BUTTON_H = 20;

    /** Default labels. */
    private static final Component DEFAULT_CONFIRM_LABEL = Component.literal("Confirm");
    private static final Component DEFAULT_CANCEL_LABEL = Component.literal("Cancel");

    /** Counter for auto-generated panel IDs. */
    private static final AtomicLong AUTO_ID_COUNTER = new AtomicLong();

    private ConfirmDialog() {}

    /** Begins a ConfirmDialog builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for a ConfirmDialog Panel. */
    public static final class Builder {
        private Component title;
        private Component body;
        private Runnable onConfirm;
        private Runnable onCancel;
        private Component confirmLabel = DEFAULT_CONFIRM_LABEL;
        private Component cancelLabel = DEFAULT_CANCEL_LABEL;
        private String id;

        Builder() {}

        /** Title text shown at the top of the dialog. Required. */
        public Builder title(Component title) {
            this.title = Objects.requireNonNull(title, "title must not be null");
            return this;
        }

        /**
         * Body text shown below the title. Required.
         *
         * <p>Single-line in v1. For multi-line bodies, compose the Panel
         * manually — see class javadoc and DIALOGS.md §5.
         */
        public Builder body(Component body) {
            this.body = Objects.requireNonNull(body, "body must not be null");
            return this;
        }

        /**
         * Fired when the user clicks the Confirm button. Required.
         *
         * <p>The library does not auto-dismiss the dialog — consumer's
         * callback is responsible for both running the confirmed action AND
         * mutating their visibility state to close the dialog. See class
         * javadoc for the canonical pattern.
         */
        public Builder onConfirm(Runnable onConfirm) {
            this.onConfirm = Objects.requireNonNull(onConfirm, "onConfirm must not be null");
            return this;
        }

        /** Fired when the user clicks the Cancel button. Required. */
        public Builder onCancel(Runnable onCancel) {
            this.onCancel = Objects.requireNonNull(onCancel, "onCancel must not be null");
            return this;
        }

        /** Override the Confirm button label. Default: "Confirm". */
        public Builder confirmLabel(Component label) {
            this.confirmLabel = Objects.requireNonNull(label, "confirmLabel must not be null");
            return this;
        }

        /** Override the Cancel button label. Default: "Cancel". */
        public Builder cancelLabel(Component label) {
            this.cancelLabel = Objects.requireNonNull(label, "cancelLabel must not be null");
            return this;
        }

        /**
         * Override the Panel's id. Default: auto-generated unique id of the
         * form {@code "confirm-dialog-N"}. Specify explicitly when
         * registering multiple confirm dialogs to ensure stable IDs.
         */
        public Builder id(String id) {
            this.id = Objects.requireNonNull(id, "id must not be null");
            return this;
        }

        /**
         * Construct the configured dialog Panel. The returned Panel:
         * <ul>
         *   <li>has {@link PanelStyle#RAISED} background</li>
         *   <li>has {@link Panel#modal()} pre-set (opaque + dimsBehind +
         *       tracksAsModal)</li>
         *   <li>starts hidden (initial visibility {@code false}) — consumer
         *       sets {@link Panel#showWhen(java.util.function.Supplier)} to
         *       drive visibility from their state</li>
         * </ul>
         *
         * @throws IllegalStateException if any required field
         *         (title, body, onConfirm, onCancel) is unset
         */
        public Panel build() {
            requireField(title, "title");
            requireField(body, "body");
            requireField(onConfirm, "onConfirm");
            requireField(onCancel, "onCancel");

            // Cross-axis CENTER on the outer Column means title and body
            // (which auto-size to font width) center against the wider
            // button row — they don't sit flush-left when the buttons are
            // wider than the text.
            //
            // Cancel is on the left, Confirm on the right (matches vanilla
            // ConfirmScreen's convention).
            //
            // Runnables wrap to Consumer<Button> for Button.spec — the
            // Button instance is unused; we just call the consumer's logic.
            final Runnable cancelRun = onCancel;
            final Runnable confirmRun = onConfirm;
            List<PanelElement> elements = Column.at(PADDING, PADDING)
                    .spacing(SECTION_GAP)
                    .crossAlign(CrossAlign.CENTER)
                    .add(TextLabel.spec(title))
                    .add(TextLabel.spec(body))
                    .addRow(r -> r.spacing(BUTTON_GAP)
                            .add(Button.spec(BUTTON_W, BUTTON_H, cancelLabel,
                                    btn -> cancelRun.run()))
                            .add(Button.spec(BUTTON_W, BUTTON_H, confirmLabel,
                                    btn -> confirmRun.run())))
                    .build();

            String panelId = (id != null)
                    ? id
                    : "confirm-dialog-" + AUTO_ID_COUNTER.incrementAndGet();

            return new Panel(panelId, elements,
                    /*visible=*/ false,
                    PanelStyle.RAISED,
                    PanelPosition.BODY,
                    /*toggleKey=*/ -1)
                    .modal();
        }

        private static void requireField(Object value, String name) {
            if (value == null) {
                throw new IllegalStateException(
                        "ConfirmDialog.Builder: required field '" + name +
                        "' was not set before build()");
            }
        }
    }
}
