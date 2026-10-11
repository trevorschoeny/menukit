package com.trevlar.menukit.api.dialog;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.layout.Column;
import com.trevlar.menukit.api.layout.CrossAlign;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds a modal dialog with one acknowledge button. It is an ordinary
 * {@link Panel} made of {@link TextLabel}s, a {@link Button} and the layout
 * helpers, with the {@link Panel#modal() modal flags} already set (opaque,
 * dimsBehind, tracksAsModal).
 *
 * <p>The same shell as {@link ConfirmDialog} (title, body, button row) with one
 * button instead of two. For messages the player has to acknowledge before going
 * on: a failed operation, a finished one, a version-change notice.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * private boolean noAllaysAlertOpen = false;
 *
 * Panel alert = AlertDialog.builder()
 *     .title(Component.literal("No allays available"))
 *     .body(Component.literal("Move closer to an allay to issue commands."))
 *     .onAcknowledge(() -> noAllaysAlertOpen = false)
 *     .build()
 *     .visibleWhen(() -> noAllaysAlertOpen);
 *
 * new ScreenPanelAdapter(alert).on(MyMenuScreen.class);
 * }</pre>
 *
 * <p>See {@link ConfirmDialog} for where it can be shown and how to get a
 * multi-line body; the same applies here.
 *
 * @see ConfirmDialog two-button confirm/cancel variant
 */
public final class AlertDialog {

    /** Padding inside the dialog Panel between chrome and content. */
    public static final int PADDING = 8;
    /** Vertical gap between title, body, and button row sections. */
    public static final int SECTION_GAP = 6;
    /** Default button width. */
    public static final int BUTTON_W = 60;
    /** Default button height, matching vanilla's confirm-screen buttons. */
    public static final int BUTTON_H = 20;

    private static final Component DEFAULT_ACKNOWLEDGE_LABEL = Component.literal("OK");

    private static final AtomicLong AUTO_ID_COUNTER = new AtomicLong();

    private AlertDialog() {}

    /** Starts an AlertDialog builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for an AlertDialog Panel. */
    public static final class Builder {
        private Component title;
        private Component body;
        private Runnable onAcknowledge;
        private Component acknowledgeLabel = DEFAULT_ACKNOWLEDGE_LABEL;
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
         * <p>One line. For a multi-line body, build the Panel yourself; see
         * {@link ConfirmDialog}.
         */
        public Builder body(Component body) {
            this.body = Objects.requireNonNull(body, "body must not be null");
            return this;
        }

        /**
         * Runs when the player clicks the acknowledge button, or presses Escape.
         * Required.
         *
         * <p>The dialog does not close itself: the callback closes it by changing
         * the state its visibility reads (typically setting a {@code dialogOpen}
         * flag to {@code false}).
         */
        public Builder onAcknowledge(Runnable onAcknowledge) {
            this.onAcknowledge = Objects.requireNonNull(onAcknowledge,
                    "onAcknowledge must not be null");
            return this;
        }

        /** The acknowledge button's label. Default "OK". */
        public Builder acknowledgeLabel(Component label) {
            this.acknowledgeLabel = Objects.requireNonNull(label,
                    "acknowledgeLabel must not be null");
            return this;
        }

        /**
         * The Panel's id. Defaults to a generated {@code "alert-dialog-N"}; set it
         * when registering several alert dialogs so their ids stay stable.
         */
        public Builder id(String id) {
            this.id = Objects.requireNonNull(id, "id must not be null");
            return this;
        }

        /**
         * Builds the dialog Panel: modal through {@link Panel#modal()} (opaque,
         * dimsBehind, tracksAsModal) and hidden until {@link Panel#visibleWhen}
         * shows it.
         *
         * @throws IllegalStateException if any required field
         *         (title, body, onAcknowledge) is unset
         */
        public Panel build() {
            requireField(title, "title");
            requireField(body, "body");
            requireField(onAcknowledge, "onAcknowledge");

            final Runnable ackRun = onAcknowledge;
            // The single button still goes in a Row, as in ConfirmDialog, so the
            // centring works the same with one button or two.
            List<PanelElement> elements = Column.at(PADDING, PADDING)
                    .spacing(SECTION_GAP)
                    .crossAlign(CrossAlign.CENTER)
                    .add(TextLabel.builder().text(title).build())
                    .add(TextLabel.builder().text(body).build())
                    .addRow(r -> r
                            .add(Button.builder().label(acknowledgeLabel).size(BUTTON_W, BUTTON_H)
                                    .onClick(ackRun).build()))
                    .build();

            String panelId = (id != null)
                    ? id
                    : "alert-dialog-" + AUTO_ID_COUNTER.incrementAndGet();

            // onEscape runs onAcknowledge, so Escape dismisses the alert exactly as
            // clicking OK does instead of closing the screen underneath.
            return Panel.builder(panelId)
                    .elements(elements)
                    .visible(false)
                    .style(PanelStyle.RAISED)
                    .position(PanelPosition.center())
                    .build()
                    .modal()
                    .onEscape(ackRun);
        }

        private static void requireField(Object value, String name) {
            if (value == null) {
                throw new IllegalStateException(
                        "AlertDialog.Builder: required field '" + name +
                        "' was not set before build()");
            }
        }
    }
}
