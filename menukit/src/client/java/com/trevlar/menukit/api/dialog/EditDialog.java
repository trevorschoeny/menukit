package com.trevlar.menukit.api.dialog;

import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.core.DialogLayout;

import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Builds a modal dialog that edits a copy of something and writes it back only on Save
 * (7.3.0): a title, your controls, Cancel and Save. The same shell as
 * {@link ConfirmDialog} (an ordinary {@link Panel}, the {@link Panel#modal() modal
 * flags} set, centered, as wide and as tall as its content) with your controls where
 * the confirm's body is.
 *
 * <p>The copy is yours, the draft. Your controls are lenses onto it, so nothing the
 * player does in the dialog touches the real thing. Open the dialog by filling the
 * draft from the item and setting the state its visibility reads; Save runs
 * {@code onSave}, which writes the draft to the item and closes it; Cancel and Escape
 * run {@code onCancel}, which closes it, and the draft is dropped (the next open fills
 * it again). As with {@link ConfirmDialog}, the dialog keeps no state of its own and
 * does not close itself.
 *
 * <pre>{@code
 * private @Nullable String editing;              // the item the dialog edits, null while closed
 * private final Draft draft = new Draft();       // the copy it edits
 *
 * Panel editDialog = EditDialog.builder()
 *     .title(() -> Component.literal("Edit " + items.get(editing).name()))
 *     .content(Flow.builder().vertical().gap(4)
 *             .add(TextField.builder().size(160, 20).state(() -> draft.name, v -> draft.name = v).build())
 *             .build())
 *     .onSave(() -> { items.get(editing).setName(draft.name); editing = null; })
 *     .onCancel(() -> editing = null)
 *     .build()
 *     .visibleWhen(() -> editing != null);
 *
 * // Open it from your own button, on a row, say:
 * Button.builder().label(Component.literal("Edit"))
 *     .onClick(() -> { draft.name = item.name(); editing = item.id(); })
 *     .build();
 * }</pre>
 *
 * Add the Panel where a {@link ConfirmDialog} goes: to an {@code MKScreen}'s panels, or
 * to another screen through {@code ScreenPanelAdapter}.
 *
 * @see ConfirmDialog
 */
public final class EditDialog {

    private static final AtomicLong AUTO_ID_COUNTER = new AtomicLong();

    private EditDialog() {}

    /** Starts an EditDialog builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for an EditDialog Panel. */
    public static final class Builder {
        private Supplier<Component> title;
        private PanelElement content;
        private Runnable onSave;
        private Runnable onCancel;
        private Component saveLabel = Component.translatable("menukit.dialog.save");
        private Component cancelLabel = CommonComponents.GUI_CANCEL;
        private String id;

        Builder() {}

        /** Title text shown at the top of the dialog. Required (this or the supplier). */
        public Builder title(Component title) {
            Objects.requireNonNull(title, "title must not be null");
            return title(() -> title);
        }

        /** Title text read every frame the dialog is shown: the item it edits, say. */
        public Builder title(Supplier<Component> title) {
            this.title = Objects.requireNonNull(title, "title must not be null");
            return this;
        }

        /**
         * The controls, lenses onto your draft, shown under the title. Required. One
         * element; put several in a vertical {@code Flow}. The dialog is as wide as it.
         */
        public Builder content(PanelElement content) {
            this.content = Objects.requireNonNull(content, "content must not be null");
            return this;
        }

        /** Runs on Save: write the draft back and change the state that shows the dialog. Required. */
        public Builder onSave(Runnable onSave) {
            this.onSave = Objects.requireNonNull(onSave, "onSave must not be null");
            return this;
        }

        /** Runs on Cancel and on Escape: close the dialog, leaving the draft unwritten. Required. */
        public Builder onCancel(Runnable onCancel) {
            this.onCancel = Objects.requireNonNull(onCancel, "onCancel must not be null");
            return this;
        }

        /** The Save button's label. Default "Save". */
        public Builder saveLabel(Component label) {
            this.saveLabel = Objects.requireNonNull(label, "saveLabel must not be null");
            return this;
        }

        /** The Cancel button's label. Default "Cancel". */
        public Builder cancelLabel(Component label) {
            this.cancelLabel = Objects.requireNonNull(label, "cancelLabel must not be null");
            return this;
        }

        /** The Panel's id. Defaults to a generated {@code "edit-dialog-N"}. */
        public Builder id(String id) {
            this.id = Objects.requireNonNull(id, "id must not be null");
            return this;
        }

        /**
         * Builds the dialog Panel, hidden until you show it with
         * {@link Panel#visibleWhen(java.util.function.BooleanSupplier)}.
         *
         * @throws IllegalStateException if title, content, onSave or onCancel is unset
         */
        public Panel build() {
            require(title, "title");
            require(content, "content");
            require(onSave, "onSave");
            require(onCancel, "onCancel");
            // Every MenuKit dialog's layout: the title centered, the content, Cancel left
            // and Save right, centered under it; Escape cancels instead of closing the screen.
            return DialogLayout.panel(id != null ? id : "edit-dialog-" + AUTO_ID_COUNTER.incrementAndGet(), onCancel,
                    DialogLayout.centered(TextLabel.builder().text(title).build()),
                    content,
                    DialogLayout.buttons(
                            DialogLayout.button(cancelLabel, onCancel),
                            DialogLayout.button(saveLabel, onSave)));
        }

        private static void require(Object value, String name) {
            if (value == null) {
                throw new IllegalStateException("EditDialog.Builder: required field '" + name + "' was not set before build()");
            }
        }
    }
}
