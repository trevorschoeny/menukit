package com.trevlar.menukit.api.dialog;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.core.DialogLayout;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds a modal confirm/cancel dialog. It is an ordinary {@link Panel} made of
 * {@link TextLabel}s, {@link Button}s and the layout helpers, with the
 * {@link Panel#modal() modal flags} already set (opaque, dimsBehind,
 * tracksAsModal).
 *
 * <p>Add the returned Panel like any other. Its visibility is yours, through
 * {@link Panel#visibleWhen}: the dialog reads your state rather than keeping its
 * own. The Confirm and Cancel callbacks run on their buttons, and you change your
 * state to close it.
 *
 * <h3>Usage</h3>
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
 *     .visibleWhen(() -> confirmDeleteOpen);
 *
 * // Register the dialog with the host screen via ScreenPanelAdapter. The dialog
 * // is already placed (PanelPosition.center(), an overlay), so no region.
 * new ScreenPanelAdapter(deleteDialog)
 *     .on(MyMenuScreen.class);
 *
 * // Open it from another button:
 * Button.builder().label(Component.literal("Delete")).onClick(() -> confirmDeleteOpen = true).build();
 * }</pre>
 *
 * <h3>Where dialogs work</h3>
 *
 * On vanilla container screens, on other vanilla screens, and on {@code MKScreen}
 * subclasses. For an {@code MKScreen}, add the dialog Panel to the screen's panels
 * (visibility driven by {@link Panel#visibleWhen}): {@code dimsBehind} panels are
 * centred, and the dim, click-eating and key dispatch send input only to the
 * dialog's buttons. SlotKit's slot groups and the HUD don't apply: one anchors to slots,
 * the other takes no input.
 *
 * <h3>Multi-line body</h3>
 *
 * The body is a single {@link Component}. For a multi-line body, build the Panel
 * yourself the way this builder does.
 *
 * @see AlertDialog single-button variant
 */
public final class ConfirmDialog {

    /** Room between the dialog's frame and its content, on every side and either kind of screen. */
    public static final int PADDING = DialogLayout.GAP;
    /** Vertical gap between title, body, and button row sections: the same, so the buttons sit centred under the body. */
    public static final int SECTION_GAP = DialogLayout.GAP;
    /** Horizontal gap between buttons in the button bar. */
    public static final int BUTTON_GAP = DialogLayout.BUTTON_GAP;
    /** Default button width. */
    public static final int BUTTON_W = DialogLayout.BUTTON_W;
    /** Default button height, matching vanilla's confirm-screen buttons. */
    public static final int BUTTON_H = DialogLayout.BUTTON_H;

    private static final Component DEFAULT_CONFIRM_LABEL = Component.literal("Confirm");
    private static final Component DEFAULT_CANCEL_LABEL = Component.literal("Cancel");

    private static final AtomicLong AUTO_ID_COUNTER = new AtomicLong();

    private ConfirmDialog() {}

    /** Starts a ConfirmDialog builder. */
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
         * <p>One line. For a multi-line body, build the Panel yourself (see the
         * class description).
         */
        public Builder body(Component body) {
            this.body = Objects.requireNonNull(body, "body must not be null");
            return this;
        }

        /**
         * Runs when the player clicks Confirm. Required.
         *
         * <p>The dialog does not close itself: the callback runs the action and
         * changes the state its visibility reads. See the class description.
         */
        public Builder onConfirm(Runnable onConfirm) {
            this.onConfirm = Objects.requireNonNull(onConfirm, "onConfirm must not be null");
            return this;
        }

        /** Runs when the player clicks Cancel, or presses Escape. Required. */
        public Builder onCancel(Runnable onCancel) {
            this.onCancel = Objects.requireNonNull(onCancel, "onCancel must not be null");
            return this;
        }

        /** The Confirm button's label. Default "Confirm". */
        public Builder confirmLabel(Component label) {
            this.confirmLabel = Objects.requireNonNull(label, "confirmLabel must not be null");
            return this;
        }

        /** The Cancel button's label. Default "Cancel". */
        public Builder cancelLabel(Component label) {
            this.cancelLabel = Objects.requireNonNull(label, "cancelLabel must not be null");
            return this;
        }

        /**
         * The Panel's id. Defaults to a generated {@code "confirm-dialog-N"}; set it
         * when registering several confirm dialogs so their ids stay stable.
         */
        public Builder id(String id) {
            this.id = Objects.requireNonNull(id, "id must not be null");
            return this;
        }

        /**
         * Builds the dialog Panel: a {@link com.trevlar.menukit.api.panel.PanelStyle#RAISED} background,
         * {@link Panel#modal()} set (opaque, dimsBehind, tracksAsModal), and hidden
         * until you show it with
         * {@link Panel#visibleWhen(java.util.function.BooleanSupplier)}.
         *
         * @throws IllegalStateException if any required field
         *         (title, body, onConfirm, onCancel) is unset
         */
        public Panel build() {
            requireField(title, "title");
            requireField(body, "body");
            requireField(onConfirm, "onConfirm");
            requireField(onCancel, "onCancel");

            // DialogLayout: title, body and buttons each centred, the same margin on
            // every side, the buttons centred between the body and the frame. Cancel is
            // on the left and Confirm on the right, as in vanilla's ConfirmScreen.
            final Runnable cancelRun = onCancel;
            final Runnable confirmRun = onConfirm;
            String panelId = (id != null)
                    ? id
                    : "confirm-dialog-" + AUTO_ID_COUNTER.incrementAndGet();

            // onEscape runs onCancel, so Escape dismisses the dialog exactly as
            // clicking Cancel does instead of closing the screen underneath.
            return DialogLayout.panel(panelId, cancelRun,
                    DialogLayout.centred(TextLabel.builder().text(title).build()),
                    DialogLayout.centred(TextLabel.builder().text(body).build()),
                    DialogLayout.buttons(
                            DialogLayout.button(cancelLabel, cancelRun),
                            DialogLayout.button(confirmLabel, confirmRun)));
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
