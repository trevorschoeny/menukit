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
 * Builder for a modal acknowledge dialog. Composes a {@link Panel} from
 * existing primitives ({@link TextLabel}, {@link Button}, M8 layout helpers)
 * and ships it with {@link Panel#modal() modal flags} pre-configured
 * (opaque + dimsBehind + tracksAsModal).
 *
 * <p>Structurally a subset of {@link ConfirmDialog}: same shell (title +
 * body + button row), one button instead of two. Use case: information
 * surfaces the user must explicitly acknowledge before continuing —
 * operation-failed messages, completion confirmations, version-change notices.
 *
 * <h3>Canonical usage</h3>
 *
 * <pre>{@code
 * private boolean noAllaysAlertOpen = false;
 *
 * Panel alert = AlertDialog.builder()
 *     .title(Component.literal("No allays available"))
 *     .body(Component.literal("Move closer to an allay to issue commands."))
 *     .onAcknowledge(() -> noAllaysAlertOpen = false)
 *     .build()
 *     .showWhen(() -> noAllaysAlertOpen);
 *
 * new ScreenPanelAdapter(alert, MenuRegion.CENTER).on(MyMenuScreen.class);
 * }</pre>
 *
 * <p>See {@link ConfirmDialog} for cross-context applicability and the
 * multi-line body escape hatch — same constraints apply here.
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
    /** Default button height — matches vanilla's confirm-screen button proportions. */
    public static final int BUTTON_H = 20;

    /** Default acknowledge label. */
    private static final Component DEFAULT_ACKNOWLEDGE_LABEL = Component.literal("OK");

    /** Counter for auto-generated panel IDs. */
    private static final AtomicLong AUTO_ID_COUNTER = new AtomicLong();

    private AlertDialog() {}

    /** Begins an AlertDialog builder. */
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
         * <p>Single-line in v1. For multi-line bodies, compose the Panel
         * manually — see {@link ConfirmDialog} class javadoc and DIALOGS.md §5.
         */
        public Builder body(Component body) {
            this.body = Objects.requireNonNull(body, "body must not be null");
            return this;
        }

        /**
         * Fired when the user clicks the Acknowledge button. Required.
         *
         * <p>The library does not auto-dismiss the dialog — consumer's
         * callback is responsible for mutating their visibility state to
         * close the dialog (typically setting their {@code dialogOpen}
         * boolean to {@code false}).
         */
        public Builder onAcknowledge(Runnable onAcknowledge) {
            this.onAcknowledge = Objects.requireNonNull(onAcknowledge,
                    "onAcknowledge must not be null");
            return this;
        }

        /** Override the Acknowledge button label. Default: "OK". */
        public Builder acknowledgeLabel(Component label) {
            this.acknowledgeLabel = Objects.requireNonNull(label,
                    "acknowledgeLabel must not be null");
            return this;
        }

        /**
         * Override the Panel's id. Default: auto-generated unique id of the
         * form {@code "alert-dialog-N"}. Specify explicitly when registering
         * multiple alert dialogs to ensure stable IDs.
         */
        public Builder id(String id) {
            this.id = Objects.requireNonNull(id, "id must not be null");
            return this;
        }

        /**
         * Construct the configured dialog Panel. Modal-by-default via
         * {@link Panel#modal()} (sets opaque + dimsBehind + tracksAsModal);
         * starts hidden (visibility set via {@link Panel#showWhen}).
         *
         * @throws IllegalStateException if any required field
         *         (title, body, onAcknowledge) is unset
         */
        public Panel build() {
            requireField(title, "title");
            requireField(body, "body");
            requireField(onAcknowledge, "onAcknowledge");

            final Runnable ackRun = onAcknowledge;
            // Single button — wrap in a Row of one for layout consistency
            // with ConfirmDialog (so the body's cross-axis centering math
            // matches whether one or two buttons render).
            List<PanelElement> elements = Column.at(PADDING, PADDING)
                    .spacing(SECTION_GAP)
                    .crossAlign(CrossAlign.CENTER)
                    .add(TextLabel.spec(title))
                    .add(TextLabel.spec(body))
                    .addRow(r -> r
                            .add(Button.spec(BUTTON_W, BUTTON_H, acknowledgeLabel,
                                    btn -> ackRun.run())))
                    .build();

            String panelId = (id != null)
                    ? id
                    : "alert-dialog-" + AUTO_ID_COUNTER.incrementAndGet();

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
                        "AlertDialog.Builder: required field '" + name +
                        "' was not set before build()");
            }
        }
    }
}
