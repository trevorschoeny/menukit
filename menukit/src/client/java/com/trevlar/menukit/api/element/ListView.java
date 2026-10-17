package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.ScreenKind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A list whose rows follow the consumer's data while the screen is open (7.3.0): one
 * row element per item, added, removed and reordered as the items change, each row
 * kept for as long as its item's key is in the list.
 *
 * <pre>{@code
 * ListView.<Sandbox>builder()
 *         .items(() -> sandboxes.list())                       // read every frame; the data is yours
 *         .key(Sandbox::id)                                    // stable identity across refreshes
 *         .row(s -> sandboxRow(s))                             // one row element per item
 *         .selection(() -> selectedId, id -> selectedId = id)  // optional: one selected key
 *         .gap(2)
 *         .empty(TextLabel.builder().text(Component.literal("No sandboxes yet")).build())
 *         .build();
 * }</pre>
 *
 * <h3>Keyed rows</h3>
 *
 * Every frame the list reads {@code items()} and compares its keys with the last ones
 * (cheap: nothing is built when they are the same). A new key gets a row from the
 * factory, attached to the screen at once, exactly as an element present at the
 * screen's init is ({@link Attachment}: a text field builds its {@code EditBox}, a
 * button registers its focus stand-in). A key that is gone has its row detached: focus
 * it held is let go, its stand-ins leave the screen, an open popover closes. A key that
 * stays keeps its row, the same instance, so a rename field keeps its text and focus
 * and a key button stays in capture while the data refreshes. Rows are in the order of
 * {@code items()}. Two items with the same key show one row, the first.
 *
 * <p>A row is built from the item as it was when its key first appeared. What a row
 * shows that can change (a name, a count) it reads through a supplier from your data,
 * by key, as every MenuKit element reads its value.
 *
 * <h3>Rows are ordinary elements</h3>
 *
 * A row is any {@link AbstractPanelElement}, usually a {@link Flow}, so the list can
 * place it: rows are stacked top to bottom, {@code gap} apart, each given the list's
 * whole width, a hidden row taking no room. They are drawn, hit-tested, claimed and
 * made inert like any child, through {@link ChildDispatch}, and they draw for the
 * kind of screen they are on.
 *
 * <h3>Height follows the rows</h3>
 *
 * Its height is its rows', read every frame, so a {@link ScrollContainer} around it
 * scrolls it, its range changing as rows come and go. A list longer than its room
 * belongs in one: put the list in a {@code ScrollContainer.builder().fillHeight()}
 * under a fixed header, and only the list scrolls.
 *
 * <h3>Selection: the consumer's, by key</h3>
 *
 * With {@code selection(get, set)}, one key is selected, held by you (a lens, read every
 * frame). A click on a row where none of its controls takes the click selects it; a row
 * reads your field to show its actions only while selected
 * ({@code visibleWhen(() -> id.equals(selectedId))}). The selected row has a one-pixel
 * outline, white while the list has the keyboard, grey otherwise. After a click in the
 * list, Up and Down move the selection (not while a text field has the keys). When the
 * selection changes other than by a click (your code selecting a new item, the arrow
 * keys), the list asks the scroll area around it to bring the selected row into view
 * ({@link PanelElement#takeRevealRequest}). A selected key whose item is gone clears the
 * selection: {@code set(null)}.
 *
 * <h3>Empty</h3>
 *
 * {@code empty(element)} is shown, in the list's place, while {@code items()} is empty.
 *
 * @param <T> the item type
 */
public final class ListView<T> extends AbstractPanelElement {

    /** Selected outline while the list has the keyboard, and while it does not. */
    private static final int OUTLINE_FOCUSED = 0xFFFFFFFF, OUTLINE = 0xFF808080;

    private final Supplier<? extends @Nullable List<? extends T>> items;
    private final Function<? super T, ?> key;
    private final Function<? super T, ? extends PanelElement> rowFactory;
    private final @Nullable Supplier<?> selectedGet;
    private final @Nullable Consumer<@Nullable Object> selectedSet;
    private final int gap;
    private final @Nullable PanelElement empty;

    // The rows: the keys in items() order, and the row for each key, kept across frames.
    private List<Object> keys = List.of();
    private Map<Object, PanelElement> rows = Map.of();
    /** The rows in order: what render and input dispatch to. */
    private List<PanelElement> shown = List.of();

    /** Where the rows are attached; a row made while the screen is open attaches at once. */
    private final Attachment attachment = new Attachment();
    /** Handed down to every row, including the ones made later. */
    private ScreenKind kind = ScreenKind.PANEL;
    private boolean inDialog = false;
    private boolean inBand = false;

    /** The width the panel gives the list; until the first pass, the widest row's. */
    private int budget = -1;
    /** The rows' total height at the last placing. */
    private int rowsHeight = 0;

    // Selection view state: whether the list has the keyboard (after a click in it),
    // the selection last seen (to notice a change by code), and a pending reveal.
    private boolean focused = false;
    private @Nullable Object lastSelected;
    private boolean selectedByClick = false;
    private boolean reveal = false;

    private ListView(Builder<T, ?> b) {
        super(b);
        this.items = b.items;
        this.key = b.key;
        this.rowFactory = b.row;
        this.selectedGet = b.selectedGet;
        this.selectedSet = b.selectedSet;
        this.gap = b.gap;
        this.empty = b.empty;
    }

    /**
     * Starts a list of {@code T}. Give {@link Builder#items}, {@link Builder#key} (before
     * {@code selection}: it fixes the key's type) and {@link Builder#row}.
     */
    public static <T> Builder<T, Object> builder() {
        return new Builder<>();
    }

    // Keyed reconciliation

    /**
     * Brings the rows in line with {@code items()}: a row made and attached for each new
     * key, the row of each vanished key detached, the rest kept. Nothing is made or
     * moved when the keys are the same as last time.
     */
    private void sync() {
        List<? extends T> now = items.get();
        if (now == null) now = List.of();
        if (sameKeys(now)) {
            noticeSelection();
            return;
        }
        List<Object> nextKeys = new ArrayList<>(now.size());
        Map<Object, PanelElement> nextRows = new HashMap<>(now.size() * 2);
        List<PanelElement> nextShown = new ArrayList<>(now.size());
        for (T item : now) {
            Object k = key.apply(item);
            if (nextRows.containsKey(k)) continue;   // a repeated key: one row, the first
            PanelElement row = rows.get(k);
            if (row == null) row = makeRow(item);
            nextKeys.add(k);
            nextRows.put(k, row);
            nextShown.add(row);
        }
        // Rows whose key is gone leave the screen: focus let go, stand-ins removed, popovers closed.
        for (Map.Entry<Object, PanelElement> old : rows.entrySet()) {
            if (!nextRows.containsKey(old.getKey())) attachment.removed(old.getValue());
        }
        keys = nextKeys;
        rows = nextRows;
        shown = nextShown;
        // A selected key whose item is gone clears the selection, through the consumer's setter.
        if (selectedGet != null && selectedSet != null) {
            Object selected = selectedGet.get();
            if (selected != null && !rows.containsKey(selected)) selectedSet.accept(null);
        }
        noticeSelection();
    }

    /** Whether {@code now}'s keys are exactly the current ones, in order. */
    private boolean sameKeys(List<? extends T> now) {
        if (now.size() != keys.size()) return false;
        for (int i = 0; i < now.size(); i++) {
            if (!Objects.equals(key.apply(now.get(i)), keys.get(i))) return false;
        }
        return true;
    }

    /** One new row: from the factory, handed what every row is, attached when the screen is open. */
    private PanelElement makeRow(T item) {
        PanelElement row = Objects.requireNonNull(rowFactory.apply(item), "ListView: the row factory returned null");
        if (!(row instanceof AbstractPanelElement)) {
            throw new IllegalStateException("ListView: a row must be an AbstractPanelElement (a Flow, a Button, ...) "
                    + "so the list can place it; got " + row.getClass().getName());
        }
        row.screenKind(kind);
        row.inDialog(inDialog);
        row.inBand(inBand);
        if (budget > 0) row.layoutWithin(budget);
        attachment.added(row);
        return row;
    }

    /**
     * A selection that changed since last seen, other than by a click in the list (the
     * consumer's code, the arrow keys), asks to be scrolled into view.
     */
    private void noticeSelection() {
        if (selectedGet == null) return;
        Object selected = selectedGet.get();
        if (!Objects.equals(selected, lastSelected)) {
            if (selected != null && !selectedByClick) reveal = true;
            lastSelected = selected;
        }
        selectedByClick = false;
    }

    /** Stacks the shown rows top to bottom from the list's own position, {@code gap} apart. */
    private void place() {
        int y = 0;
        boolean first = true;
        for (PanelElement row : shown) {
            if (!row.isVisible()) continue;
            if (!first) y += gap;
            ((AbstractPanelElement) row).setChildPosition(childX, childY + y);
            y += row.getHeight();
            first = false;
        }
        rowsHeight = y;
        if (shown.isEmpty() && empty instanceof AbstractPanelElement e) e.setChildPosition(childX, childY);
    }

    /** Syncs and places: the rows as they are this frame. */
    private void settle() {
        sync();
        place();
    }

    private boolean showsEmpty() {
        return shown.isEmpty() && empty != null && empty.isVisible();
    }

    /** What render and input reach: the rows, or the empty element while there are none. */
    private List<PanelElement> children() {
        if (!shown.isEmpty()) return shown;
        return empty != null ? List.of(empty) : List.of();
    }

    // Size: as wide as it is given, as tall as its rows.

    @Override
    public int naturalWidth() {
        sync();
        int widest = 0;
        for (PanelElement row : children()) widest = Math.max(widest, row.naturalWidth());
        return widest;
    }

    @Override
    public int getWidth() {
        return budget > 0 ? budget : naturalWidth();
    }

    @Override
    public int getHeight() {
        settle();
        if (shown.isEmpty()) return showsEmpty() ? empty.getHeight() : 0;
        return rowsHeight;
    }

    /** Takes the width it is given and hands it to every row. */
    @Override
    public void layoutWithin(int budget) {
        this.budget = Math.max(1, budget);
        sync();
        for (PanelElement row : children()) row.layoutWithin(this.budget);
    }

    @Override
    public void fillWidth(int width) {
        layoutWithin(width);
    }

    @Override
    public void screenKind(ScreenKind kind) {
        this.kind = kind;
        ChildDispatch.screenKind(new ArrayList<>(rows.values()), kind);
        if (empty != null) empty.screenKind(kind);
    }

    @Override
    public void inDialog(boolean inDialog) {
        this.inDialog = inDialog;
        ChildDispatch.inDialog(new ArrayList<>(rows.values()), inDialog);
        if (empty != null) empty.inDialog(inDialog);
    }

    @Override
    public void inBand(boolean inBand) {
        this.inBand = inBand;
        ChildDispatch.inBand(new ArrayList<>(rows.values()), inBand);
        if (empty != null) empty.inBand(inBand);
    }

    // Render

    @Override
    public void render(RenderContext ctx) {
        settle();
        if (ctx.kind() != kind) screenKind(ctx.kind());
        RenderContext inner = childContext(ctx);
        if (shown.isEmpty()) {
            if (showsEmpty()) empty.render(inner);
            return;
        }
        // Rows wholly outside the clip they draw under are skipped (a long list in a
        // scroll area), unless a popover of theirs is open.
        var g = ctx.graphics();
        ScreenRectangle clip = g != null && g.scissorStack != null ? g.scissorStack.peek() : null;
        InputContext probe = inner.input();
        PanelElement selectedRow = selectedRow();
        for (PanelElement row : shown) {
            if (!row.isVisible()) continue;
            int top = ctx.originY() + row.getChildY();
            boolean open = row.getActiveOverlayBounds(probe) != null;
            if (!open && clip != null && (top + row.getHeight() <= clip.top() || top >= clip.bottom())) continue;
            row.render(inner);
        }
        // The selected row's outline, over its card's edge.
        if (selectedRow != null && selectedRow.isVisible() && g != null) {
            int x = ctx.originX() + childX, y = ctx.originY() + selectedRow.getChildY();
            int w = getWidth(), h = selectedRow.getHeight();
            int c = focused ? OUTLINE_FOCUSED : OUTLINE;
            g.fill(x, y, x + w, y + 1, c);
            g.fill(x, y + h - 1, x + w, y + h, c);
            g.fill(x, y + 1, x + 1, y + h - 1, c);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
        }
    }

    @Override
    public void renderOverlay(RenderContext ctx) {
        ChildDispatch.renderOverlay(children(), childContext(ctx));
    }

    /** The selected row, or {@code null}. */
    private @Nullable PanelElement selectedRow() {
        if (selectedGet == null) return null;
        Object selected = selectedGet.get();
        return selected == null ? null : rows.get(selected);
    }

    /** The selected row's place, once, when the selection changed other than by a click. */
    @Override
    public int @Nullable [] takeRevealRequest() {
        settle();
        if (!reveal) return ChildDispatch.takeRevealRequest(children());
        reveal = false;
        PanelElement row = selectedRow();
        if (row == null || !row.isVisible()) return null;
        return new int[]{row.getChildY(), row.getHeight()};
    }

    // Input

    @Override public boolean isInteractive() { return true; }

    /** The shown row under the point, its whole width, or {@code null} (a gap, or outside). */
    private @Nullable PanelElement rowAt(InputContext in) {
        for (PanelElement row : shown) {
            if (!row.isVisible()) continue;
            if (in.isOver(childX, row.getChildY(), getWidth(), row.getHeight())) return row;
        }
        return null;
    }

    /** Over a row's whole width (its empty area selects it), or over the empty element. */
    @Override
    public boolean hitTest(InputContext in) {
        settle();
        if (shown.isEmpty()) return showsEmpty() && empty.hitTest(in);
        return rowAt(in) != null;
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        settle();
        InputContext inner = in.disabledIf(ownDisabled());
        if (inner.disabled()) return false;
        if (ChildDispatch.overlayOwner(children(), inner) != null) return ChildDispatch.mouseClicked(children(), inner, button);
        if (shown.isEmpty()) return ChildDispatch.mouseClicked(children(), inner, button);
        PanelElement row = rowAt(inner);
        if (row == null) return false;
        focused = true;
        // A control in the row takes its own click; the row's empty area selects it.
        if (row.hitTest(inner) && row.mouseClicked(inner, button)) return true;
        if (button != Click.LEFT || selectedSet == null) return true;
        Object k = keyOf(row);
        if (k != null && !Objects.equals(k, selectedGet.get())) {
            selectedByClick = true;
            selectedSet.accept(k);
        }
        return true;
    }

    private @Nullable Object keyOf(PanelElement row) {
        for (Map.Entry<Object, PanelElement> e : rows.entrySet()) if (e.getValue() == row) return e.getKey();
        return null;
    }

    /** A click outside the list takes the keyboard from it; every row hears about it for its popovers. */
    @Override
    public void notifyClickOutsideOverlay(InputContext in) {
        ChildDispatch.notifyClickOutside(children(), in);
        if (!in.isOver(childX, childY, getWidth(), getHeight())) focused = false;
    }

    @Override
    public boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        settle();
        return ChildDispatch.mouseScrolled(children(), in.disabledIf(ownDisabled()), scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        ChildDispatch.mouseReleased(children(), in, button);
        return false;
    }

    /**
     * Keys go to the rows first (a focused field, an open popover). Then, while the list
     * has the keyboard and no text field is capturing it, Up and Down move the selection
     * one shown row, and the new row is scrolled into view.
     */
    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        InputContext inner = in.disabledIf(ownDisabled());
        if (ChildDispatch.keyPressed(children(), inner, keyCode, scanCode, modifiers)) return true;
        if (inner.disabled() || !focused || selectedSet == null) return false;
        if (keyCode != InputConstants.KEY_UP && keyCode != InputConstants.KEY_DOWN) return false;
        if (textCaptured()) return false;
        List<Object> order = new ArrayList<>();
        for (Object k : keys) if (rows.get(k).isVisible()) order.add(k);
        if (order.isEmpty()) return false;
        int at = order.indexOf(selectedGet.get());
        int to = at < 0 ? 0 : keyCode == InputConstants.KEY_UP ? Math.max(0, at - 1) : Math.min(order.size() - 1, at + 1);
        if (to != at) selectedSet.accept(order.get(to));
        return true;
    }

    /** Whether a text field has the keyboard on the open screen (then the arrows are its). */
    private static boolean textCaptured() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc == null || mc.gui == null ? null : mc.gui.screen();
        return screen != null && screen.isInputCaptured();
    }

    @Override
    public int @Nullable [] getActiveOverlayBounds(InputContext in) {
        return ChildDispatch.activeOverlay(children(), in);
    }

    @Override
    public boolean presentsSlotAt(InputContext in) {
        return ChildDispatch.presentsSlotAt(children(), in);
    }

    // Lifecycle: the rows there now attach with the list; later ones as they come.

    @Override
    public void onAttach(Screen screen) {
        sync();
        attachment.attach(screen, everyChild());
    }

    @Override
    public void onDetach(Screen screen) {
        attachment.detach(screen, everyChild());
        focused = false;
    }

    private List<PanelElement> everyChild() {
        List<PanelElement> all = new ArrayList<>(shown);
        if (empty != null) all.add(empty);
        return all;
    }

    // For MenuKit's checks.

    /** The row shown for {@code key} now, or {@code null}. */
    public @Nullable PanelElement rowFor(Object key) {
        sync();
        return rows.get(key);
    }

    /** Whether the list has the keyboard (after a click in it, until a click outside). */
    public boolean hasKeyboard() {
        return focused;
    }

    /**
     * Builds a {@link ListView}. {@link #items}, {@link #key} and {@link #row} are
     * required; call {@code key} before {@code selection}, since it fixes the key's type.
     *
     * @param <T> the item type
     * @param <K> the key type
     */
    public static final class Builder<T, K> extends AbstractPanelElement.Builder<ListView<T>, Builder<T, K>> {
        private @Nullable Supplier<? extends @Nullable List<? extends T>> items;
        private @Nullable Function<? super T, ?> key;
        private @Nullable Function<? super T, ? extends PanelElement> row;
        private @Nullable Supplier<?> selectedGet;
        private @Nullable Consumer<@Nullable Object> selectedSet;
        private int gap = 2;
        private @Nullable PanelElement empty;

        Builder() {}

        @Override protected Builder<T, K> self() { return this; }

        /** Required: the items, read every frame. The list is yours; the view never changes it. */
        public Builder<T, K> items(Supplier<? extends @Nullable List<? extends T>> items) {
            this.items = Objects.requireNonNull(items, "items");
            return this;
        }

        /**
         * Required: an item's stable identity (an id, not its index), compared with
         * {@code equals}. A row lives as long as its key.
         */
        @SuppressWarnings("unchecked")
        public <K2> Builder<T, K2> key(Function<? super T, ? extends K2> key) {
            this.key = Objects.requireNonNull(key, "key");
            return (Builder<T, K2>) (Object) this;
        }

        /** Required: one row element per item, made when its key first appears. */
        public Builder<T, K> row(Function<? super T, ? extends PanelElement> row) {
            this.row = Objects.requireNonNull(row, "row");
            return this;
        }

        /**
         * The selection, by key: {@code get} is read every frame ({@code null} for none);
         * {@code set} receives a newly selected key, or {@code null} when the selected
         * item is gone. Without it nothing is selectable.
         */
        @SuppressWarnings("unchecked")
        public Builder<T, K> selection(Supplier<@Nullable K> get, Consumer<@Nullable K> set) {
            Objects.requireNonNull(set, "set");
            this.selectedGet = Objects.requireNonNull(get, "get");
            this.selectedSet = k -> set.accept((K) k);
            return this;
        }

        /** Pixels between rows. Default 2. */
        public Builder<T, K> gap(int px) {
            if (px < 0) throw new IllegalArgumentException("gap must not be negative, got " + px);
            this.gap = px;
            return this;
        }

        /** Shown in the list's place while there are no items. */
        public Builder<T, K> empty(PanelElement empty) {
            this.empty = Objects.requireNonNull(empty, "empty");
            return this;
        }

        @Override
        public ListView<T> build() {
            require(items != null, "items(supplier) is required");
            require(key != null, "key(function) is required: rows are kept by key, not by index");
            require(row != null, "row(factory) is required");
            return new ListView<>(this);
        }
    }
}
