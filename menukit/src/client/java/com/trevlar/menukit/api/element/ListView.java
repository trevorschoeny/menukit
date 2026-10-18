package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.dialog.ConfirmDialog;
import com.trevlar.menukit.api.panel.Focus;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.DialogLayout;
import com.trevlar.menukit.core.VanillaLook;
import com.trevlar.menukit.inject.ScreenPanelRegistry;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
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
 * kind of screen they are on. A row that changes height (a {@link Flow} child that
 * hides or shows, a {@link Section} that opens) moves the rows below it on the next
 * frame: the rows are placed from their heights every frame. A row that is a
 * {@code Section} keeps its open state for as long as its key, through renames and
 * refreshes, so a list of sections expands in place with no more API.
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
 * <h3>Editing (7.3.0)</h3>
 *
 * Every option below is optional, and a list with none of them is the list above,
 * exactly. The data stays yours: the list only calls back, and shows what
 * {@code items()} returns next, so a refused add or move (a cap, a rule) changes
 * nothing and there is nothing to undo.
 *
 * <pre>{@code
 * ListView.<LockGroup>builder()
 *     .items(groups::all).key(LockGroup::id).row(this::groupRow)
 *     .add(Component.literal("+ Slot group"), () -> groups.addSlotGroup())   // one button per kind
 *     .add(Component.literal("+ Item group"), () -> groups.addItemGroup())
 *     .remove(id -> groups.delete(id))                                       // a remove button on each row
 *     .confirmRemove(g -> Component.literal("Delete " + g.name() + "?"))     // asks first
 *     .move((id, toIndex) -> groups.move(id, toIndex))                       // up and down on each row
 *     .pinned(LockGroup::isDefault)                                          // no remove, no arrows
 *     .build();
 * }</pre>
 *
 * <ul>
 *   <li><b>Add</b> ({@link Builder#add}): one button per kind, at the right end of the
 *       list's {@linkplain #toolbar() toolbar}.
 *       The action returns the new item's key, or {@code null} and the list finds the
 *       key that appeared; either way the new row is scrolled into view, and a search
 *       query that would hide it is cleared. {@link Builder#addDisabledWhen} greys them.</li>
 *   <li><b>Remove</b> ({@link Builder#remove}): a remove button (a cross) at each
 *       row's trailing edge; {@link Builder#confirmRemove} asks first, in a {@link ConfirmDialog} whose body
 *       names the item, read when it shows.</li>
 *   <li><b>Move</b> ({@link Builder#move}): up and down buttons on each row, the top
 *       row's up and the bottom row's down greyed. {@code toIndex} is the place in
 *       {@code items()}. With a row selected, Alt+Up and Alt+Down move it.</li>
 *   <li><b>Pinned</b> ({@link Builder#pinned}): those rows have no remove button and
 *       no arrows, and no other row moves past them. Their order is still yours.</li>
 *   <li><b>Edit in a dialog</b> ({@link Builder#editDialog}): an Edit button on each
 *       row opens a dialog on a copy (the draft); Save writes it, Cancel and Escape
 *       drop it. Removing the row closes the dialog without saving.</li>
 *   <li><b>Disabled, with a reason</b> ({@link Builder#disabledReason}): while the
 *       list's own {@code disabledWhen} holds, the reason shows above the greyed rows.</li>
 *   <li><b>Search</b> ({@link Builder#search}) and <b>sort</b> ({@link Builder#sort}):
 *       in the toolbar, the search field on the left taking the free width, the sort
 *       picker and its direction after it. Search is case-insensitive
 *       "contains" on the item's text; "No matches" shows when nothing does, distinct
 *       from {@code empty}. A selected row the query hides stays selected. While a
 *       query is typed the arrows are greyed. Two or more sorts get a picker
 *       ("Sort: Name") and a direction button (an arrow beside bars, its tooltip
 *       "Ascending" or "Descending"; a click flips it); the first is the default. A list can't
 *       both sort and move: {@code build()} throws.</li>
 * </ul>
 *
 * <h3>The toolbar</h3>
 *
 * Add, search and sort sit in one toolbar the list makes, {@link #toolbar()}: an
 * ordinary element you place above the list's scroll area, so it stays put while the
 * rows scroll. It is as wide as the rows, so its right end lines up with theirs
 * whether or not the scrollbar shows; place it at the scroll area's left edge, the
 * rows' {@code gap} above it, so it reads as part of the list. A list whose toolbar
 * you never ask for draws it at its own top, that gap above the rows, and it scrolls
 * with them. It greys with the list.
 *
 * <h3>The row's controls, in the row</h3>
 *
 * Edit, up, down and remove sit inside the row, on its first line at its trailing
 * edge, and the row's own content has the rest of that line. In a boxed {@link Flow}
 * (a card) they are inside the card; in a {@link Section}, alone or in a card, they
 * are on its header line and what opens under it has the whole width. Anything else
 * (a bare text field) is narrowed and they sit beside it. The list draws nothing of
 * its own around a row: the row stays yours, whatever is on its first line or under
 * it. Each control keeps its slot whether or not it shows, so the columns line up
 * down the list; every one is the look's own button at one height, the icon ones
 * (up, down, remove) square, a button's gap apart.
 *
 * @param <T> the item type
 */
public final class ListView<T> extends AbstractPanelElement {

    /** Selected outline while the list has the keyboard, and while it does not. */
    private static final int OUTLINE_FOCUSED = 0xFFFFFFFF, OUTLINE = 0xFF808080;

    /** Between the reason and the rows: a Flow's gap. The toolbar the list draws sits the rows' own gap above them. */
    static final int PART_GAP = Flow.DEFAULT_GAP;
    /** The toolbar's height: a vanilla button's, as are its add buttons, search field and sort picker. */
    static final int ADD_HEIGHT = Button.DEFAULT_HEIGHT;
    /** The toolbar's search field and sort picker widths. */
    static final int SEARCH_WIDTH = 120, SORT_WIDTH = 100;

    // The icons on the row's buttons and the sort direction: white with vanilla's text
    // shadow, drawn so the glyph and its shadow sit pixel-centred in the button.
    private static final Identifier MOVE_UP = icon("move_up"), MOVE_DOWN = icon("move_down"), REMOVE = icon("remove");
    private static final Identifier ASCENDING = icon("sort_ascending"), DESCENDING = icon("sort_descending");
    // mockup: the same glyphs padded to 16 px, for the toolbar's 20 px buttons (E), so they draw 1:1 like the sort icon.
    private static final Identifier BAR_UP = icon("toolbar_move_up"), BAR_DOWN = icon("toolbar_move_down"), BAR_REMOVE = icon("toolbar_remove");

    private static Identifier icon(String name) {
        return Identifier.fromNamespaceAndPath("menukit", "icon/" + name);
    }

    private static final AtomicLong IDS = new AtomicLong();

    private final Supplier<? extends @Nullable List<? extends T>> items;
    private final Function<? super T, ?> key;
    private final Function<? super T, ? extends PanelElement> rowFactory;
    private final @Nullable Supplier<?> selectedGet;
    private final @Nullable Consumer<@Nullable Object> selectedSet;
    private final int gap;
    private final @Nullable PanelElement empty;

    // Editing (7.3.0): each null or empty when its option is off.
    private final @Nullable Consumer<Object> remove;
    private final @Nullable Function<? super T, Component> confirmRemove;
    private final @Nullable BiConsumer<Object, Integer> move;
    private final @Nullable Predicate<? super T> pinned;
    private final @Nullable EditDialog<T, ?> edit;
    private final @Nullable Function<? super T, String> searchText;
    private final List<SortBy<T>> sorts;
    private final @Nullable Supplier<Sort> sortGet;
    private final @Nullable Consumer<Sort> sortSet;
    /** Whether any editing option is on: only then does the list track items by key every frame. */
    private final boolean edits;

    // mockup: the round-3 comparison ("A-inside" ... "F-reorder-mode"), or null for the
    // shipped list; remove with Builder#mockup once Trev picks one.
    private final @Nullable String mockup;
    /** mockup: F's "Reorder" toggle: the rows have their controls only while it is on. */
    private boolean reorderMode = false;

    // The elements the list makes for those options: null when the option is off.
    private final @Nullable PanelElement reason;
    private final @Nullable PanelElement noMatches;
    private final @Nullable PanelElement toolbar;
    private final @Nullable Panel confirm;
    private final List<AddKind> adds;
    /** Whether the consumer took the toolbar to place ({@link #toolbar()}); until then the list draws it at its top. */
    private boolean toolbarTaken = false;

    // The rows: the keys in items() order, and the row for each key, kept across frames.
    private List<Object> keys = List.of();
    private Map<Object, PanelElement> rows = Map.of();
    /** The rows in order: what render and input dispatch to. */
    private List<PanelElement> shown = List.of();
    /** The keys of {@link #shown}, in the same order (the search and sort's view). */
    private List<Object> shownKeys = List.of();

    // Editing view state: each key's item now, and its place in items().
    private Map<Object, T> itemByKey = Map.of();
    private Map<Object, Integer> indexByKey = Map.of();
    private String query = "";
    private Sort ownSort = new Sort(0, false);
    /** The key whose remove is waiting on the confirm dialog, or null. */
    private @Nullable Object removing;
    /** The key whose edit dialog is open, and the dialog. */
    private @Nullable Object editing;
    private @Nullable Panel editPanel;
    /** An add just ran: the key it returned, or the keys before it to find the new one. */
    private boolean adding = false;
    private @Nullable Object addedKey;
    private Set<Object> keysBeforeAdd = Set.of();
    /** A row to scroll into view once (an added row, a moved one), or null. */
    private @Nullable Object revealKey;

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
    /** The whole list's height at the last placing: the reason, the rows and the add row. */
    private int totalHeight = 0;

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
        this.remove = b.remove;
        this.confirmRemove = b.confirmRemove;
        this.move = b.move;
        this.pinned = b.pinned;
        this.edit = b.edit;
        this.searchText = b.searchText;
        this.sorts = List.copyOf(b.sorts);
        this.sortGet = b.sortGet;
        this.sortSet = b.sortSet;
        this.edits = !b.adds.isEmpty() || remove != null || move != null || pinned != null || edit != null
                || searchText != null || !sorts.isEmpty();
        this.adds = List.copyOf(b.adds);
        this.mockup = b.mockup;
        this.reason = b.disabledReason == null ? null
                : TextLabel.builder().text(b.disabledReason).visibleWhen(this::ownDisabled).build();
        this.noMatches = searchText == null ? null
                : TextLabel.builder().role(TextRole.MUTED).text(Component.translatable("menukit.list.no_matches")).build();
        this.toolbar = b.adds.isEmpty() && searchText == null && sorts.size() < 2 ? null : makeToolbar(b.adds, b.addDisabled);
        this.confirm = confirmRemove == null ? null : makeConfirm();
    }

    /**
     * Starts a list of {@code T}. Give {@link Builder#items}, {@link Builder#key} (before
     * {@code selection} and the editing options: it fixes the key's type) and {@link Builder#row}.
     */
    public static <T> Builder<T, Object> builder() {
        return new Builder<>();
    }

    // The elements the editing options add

    /**
     * The toolbar: the search field on the left taking the width the rest leaves; then,
     * at the right end, with two or more sorts the picker and its direction, and the add
     * buttons, one per kind. Each part only when its option is on; with no search the
     * buttons alone, at the right. Every part greys with the list, wherever the toolbar
     * is placed, and the add buttons also while {@code addDisabledWhen} holds.
     */
    private PanelElement makeToolbar(List<AddKind> adds, @Nullable BooleanSupplier addDisabled) {
        BooleanSupplier off = this::ownDisabled;
        PanelElement search = searchText == null
                ? Flow.builder().build()   // nothing on the left: the buttons keep the right end
                : TextField.builder().size(SEARCH_WIDTH, ADD_HEIGHT).fill()
                        .hint(Component.translatable("menukit.list.search"))
                        .label(Component.translatable("menukit.list.search"))
                        .state(() -> query, q -> query = q)
                        .disabledWhen(off)
                        .build();
        List<PanelElement> buttons = new ArrayList<>();
        if (sorts.size() >= 2) {
            List<Integer> options = new ArrayList<>();
            for (int i = 0; i < sorts.size(); i++) options.add(i);
            buttons.add(Dropdown.<Integer>builder().size(SORT_WIDTH, ADD_HEIGHT)
                    .items(options)
                    .label(i -> sorts.get(i).label())
                    .triggerLabel(i -> Component.translatable("menukit.list.sort", sorts.get(i).label()))
                    .state(() -> sorting().option(), i -> setSorting(new Sort(i, sorting().reversed())))
                    .disabledWhen(off)
                    .build());
            // The direction: an icon button showing the order now (an arrow beside bars
            // that lengthen the way the values grow), its tooltip naming it; a click flips it.
            buttons.add(Button.builder().size(ADD_HEIGHT, ADD_HEIGHT)
                    .icon(() -> sorting().reversed() ? DESCENDING : ASCENDING)
                    .tooltip(() -> Component.translatable(sorting().reversed() ? "menukit.list.descending" : "menukit.list.ascending"))
                    .onClick(() -> setSorting(new Sort(sorting().option(), !sorting().reversed())))
                    .disabledWhen(off)
                    .build());
        }
        BooleanSupplier addOff = addDisabled == null ? off : () -> ownDisabled() || addDisabled.getAsBoolean();
        for (AddKind a : adds) {
            buttons.add(Button.builder().label(a.label()).size(0, ADD_HEIGHT)
                    .onClick(() -> runAdd(a.action())).disabledWhen(addOff).build());
        }
        if (isMockup("E")) buttons.addAll(selectionButtons(off));
        if (isMockup("F")) buttons.add(reorderToggle(off));
        // The search field and the buttons share one line the way a row and its controls
        // do, the whole as wide as the rows: placed over the scroll area, its right end
        // is theirs whether or not the scrollbar takes its lane.
        return new ListRow(search, buttons, () -> budget);
    }

    /** mockup: whether this list is the round-3 variant starting with {@code letter}. */
    private boolean isMockup(String letter) {
        return mockup != null && mockup.startsWith(letter);
    }

    /**
     * mockup (E): up, down and remove in the toolbar, acting on the selected row, greyed
     * with nothing selected or where the action isn't allowed (a pinned row, either end).
     */
    private List<PanelElement> selectionButtons(BooleanSupplier off) {
        Supplier<@Nullable Object> sel = () -> selectedGet == null ? null : selectedGet.get();
        return List.of(
                barIcon(BAR_UP, "menukit.list.move_up", off, () -> sel.get() != null && canMove(sel.get(), -1),
                        () -> moveBy(sel.get(), -1)),
                barIcon(BAR_DOWN, "menukit.list.move_down", off, () -> sel.get() != null && canMove(sel.get(), 1),
                        () -> moveBy(sel.get(), 1)),
                barIcon(BAR_REMOVE, "menukit.list.remove", off,
                        () -> remove != null && sel.get() != null && rows.containsKey(sel.get()) && !isPinned(sel.get()),
                        () -> askRemove(sel.get())));
    }

    /** mockup (E): a square toolbar icon button, as tall as the add button. */
    private PanelElement barIcon(Identifier icon, String tooltip, BooleanSupplier off, BooleanSupplier allowed, Runnable action) {
        return Button.builder().icon(icon).size(ADD_HEIGHT, ADD_HEIGHT)
                .tooltip(Component.translatable(tooltip))
                .disabledWhen(() -> off.getAsBoolean() || !allowed.getAsBoolean())
                .onClick(() -> { if (allowed.getAsBoolean()) action.run(); })
                .build();
    }

    /**
     * mockup (F): the "Reorder" toggle. On, every row gets its arrows and remove (as in
     * A); off, the rows are plain. It reads as pressed while on: the Panel look's inset
     * face, and in the Default look MenuKit's pressed overlay on vanilla's button.
     */
    private PanelElement reorderToggle(BooleanSupplier off) {
        Toggle.Builder b = Toggle.builder().size(0, ADD_HEIGHT)
                .label(Component.literal("Reorder"))
                .state(() -> reorderMode, on -> setReorderMode(on))
                .disabledWhen(off);
        return new Toggle(b) {
            @Override
            protected void renderBackground(RenderContext ctx, int sx, int sy, boolean on, boolean disabled, boolean hovered) {
                super.renderBackground(ctx, sx, sy, on, disabled, hovered);
                if (on && !disabled && ctx.kind() == ScreenKind.DEFAULT) {
                    VanillaLook.pressedOverlay(ctx.graphics(), sx, sy, getWidth(), getHeight());
                }
            }
        };
    }

    /** mockup (F): flips the mode and makes every row again, with or without its controls. */
    private void setReorderMode(boolean on) {
        reorderMode = on;
        for (PanelElement row : rows.values()) attachment.removed(row);
        rows = Map.of();
        keys = List.of();
        shown = List.of();
        shownKeys = List.of();
    }

    /** The confirm for a remove, built once: its body names the item it is about, read while it shows. */
    private Panel makeConfirm() {
        return ConfirmDialog.builder()
                .id("menukit:list-remove-" + IDS.incrementAndGet())
                .title(Component.translatable("menukit.list.remove_title"))
                .body(() -> {
                    T item = removing == null ? null : itemByKey.get(removing);
                    return item == null ? Component.empty() : confirmRemove.apply(item);
                })
                .confirmLabel(Component.translatable("menukit.list.remove"))
                .onConfirm(() -> {
                    Object k = removing;
                    removing = null;
                    if (k != null) remove.accept(k);
                })
                .onCancel(() -> removing = null)
                .build()
                .visibleWhen(() -> removing != null);
    }

    /**
     * The toolbar: the add buttons, the search field and the sort control, whichever the
     * list has, an ordinary element to put above the list's scroll area so it stays put
     * while the rows scroll. Once this is called the list leaves the toolbar to you; a
     * list whose toolbar is never asked for draws it at its own top, above the rows (it
     * then scrolls with them). The same element every call.
     *
     * @throws IllegalStateException when the list has no {@code add}, no {@code search} and fewer than two {@code sort}s
     */
    public PanelElement toolbar() {
        if (toolbar == null) {
            throw new IllegalStateException("ListView: toolbar() is for a list with add(...), search(...) or two or more sort(...)");
        }
        toolbarTaken = true;
        return toolbar;
    }

    /** The toolbar while the list draws it itself (nobody took it), or {@code null}. */
    private @Nullable PanelElement ownToolbar() {
        return toolbarTaken ? null : toolbar;
    }

    // Keyed reconciliation

    /**
     * Brings the rows in line with {@code items()}: a row made and attached for each new
     * key, the row of each vanished key detached, the rest kept. Nothing is made or
     * moved when the keys are the same as last time. With an editing option on, the
     * items are also read by key every frame, an add's new row found, and the search
     * and sort's view of the rows made again.
     */
    private void sync() {
        List<? extends T> now = items.get();
        if (now == null) now = List.of();
        if (!sameKeys(now)) reconcile(now);
        if (edits) {
            byKey(now);
            if (adding) revealAdded();
            if (searchText != null || !sorts.isEmpty()) view();
        }
        noticeSelection();
    }

    /** The rows for a new set of keys, in {@code now}'s order. */
    private void reconcile(List<? extends T> now) {
        List<Object> nextKeys = new ArrayList<>(now.size());
        Map<Object, PanelElement> nextRows = new HashMap<>(now.size() * 2);
        List<PanelElement> nextShown = new ArrayList<>(now.size());
        for (T item : now) {
            Object k = key.apply(item);
            if (nextRows.containsKey(k)) continue;   // a repeated key: one row, the first
            PanelElement row = rows.get(k);
            if (row == null) row = makeRow(item, k);
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
        shownKeys = nextKeys;
        // A selected key whose item is gone clears the selection, through the consumer's setter.
        if (selectedGet != null && selectedSet != null) {
            Object selected = selectedGet.get();
            if (selected != null && !rows.containsKey(selected)) selectedSet.accept(null);
        }
        // A removed row's dialogs close: its edit without saving, its confirm with nothing to confirm.
        if (editing != null && !rows.containsKey(editing)) closeEdit();
        if (removing != null && !rows.containsKey(removing)) removing = null;
    }

    /** Whether {@code now}'s keys are exactly the current ones, in order. */
    private boolean sameKeys(List<? extends T> now) {
        if (now.size() != keys.size()) return false;
        for (int i = 0; i < now.size(); i++) {
            if (!Objects.equals(key.apply(now.get(i)), keys.get(i))) return false;
        }
        return true;
    }

    /** Each key's item now (the first with that key) and its place in {@code items()}. */
    private void byKey(List<? extends T> now) {
        Map<Object, T> items = new HashMap<>(now.size() * 2);
        Map<Object, Integer> index = new HashMap<>(now.size() * 2);
        for (T item : now) {
            Object k = key.apply(item);
            if (items.putIfAbsent(k, item) == null) index.put(k, index.size());
        }
        itemByKey = items;
        indexByKey = index;
    }

    /**
     * The search and sort's view: the keys whose text holds the query, in the chosen
     * order. Made every frame (the items, the query and the sort can each change).
     */
    // ponytail: filters and sorts every frame; cache on the keys, query and sort if a list of thousands shows it.
    private void view() {
        List<Object> view = new ArrayList<>(keys);
        if (searchText != null && !query.isEmpty()) view.removeIf(k -> !matches(k));
        if (!sorts.isEmpty()) {
            Sort s = sorting();
            Comparator<? super T> order = sorts.get(s.option()).order();
            Comparator<Object> byItem = (a, b) -> order.compare(itemByKey.get(a), itemByKey.get(b));
            view.sort(s.reversed() ? byItem.reversed() : byItem);
        }
        List<PanelElement> rowsShown = new ArrayList<>(view.size());
        for (Object k : view) rowsShown.add(rows.get(k));
        shownKeys = view;
        shown = rowsShown;
    }

    /** Whether the item with key {@code k} holds the query: case-insensitive "contains". */
    private boolean matches(Object k) {
        T item = itemByKey.get(k);
        if (item == null || searchText == null) return false;
        String text = searchText.apply(item);
        return text != null && text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    /** One new row: from the factory, with the list's controls when it has any, attached when the screen is open. */
    private PanelElement makeRow(T item, Object k) {
        PanelElement row = Objects.requireNonNull(rowFactory.apply(item), "ListView: the row factory returned null");
        if (!(row instanceof AbstractPanelElement)) {
            throw new IllegalStateException("ListView: a row must be an AbstractPanelElement (a Flow, a Button, ...) "
                    + "so the list can place it; got " + row.getClass().getName());
        }
        // mockup: E's rows have no controls (they're in the toolbar); F's only in reorder mode.
        List<PanelElement> controls = isMockup("E") || (isMockup("F") && !reorderMode) ? List.of() : controls(k);
        if (!controls.isEmpty()) row = new ListRow(row, controls, lane(), gap);
        row.screenKind(kind);
        row.inDialog(inDialog);
        row.inBand(inBand);
        if (budget > 0) row.layoutWithin(budget);
        attachment.added(row);
        return row;
    }

    /** mockup: where the row's controls go for this variant; the shipped list's is INSIDE. */
    private ListRow.Lane lane() {
        if (isMockup("B")) return ListRow.Lane.DIVIDER;
        if (isMockup("C")) return ListRow.Lane.BOXED;
        if (isMockup("D")) return ListRow.Lane.BARE;
        return ListRow.Lane.INSIDE;
    }

    /** The row's controls for key {@code k}, in order: Edit, up, down, remove. */
    private List<PanelElement> controls(Object k) {
        List<PanelElement> controls = new ArrayList<>(4);
        if (edit != null) {
            controls.add(Button.builder().label(Component.translatable("menukit.list.edit"))
                    .size(0, ListRow.CONTROL).onClick(() -> openEdit(k)).build());
        }
        if (move != null) {
            controls.add(rowIcon(MOVE_UP, "menukit.list.move_up", k)
                    .disabledWhen(() -> !canMove(k, -1)).onClick(() -> moveBy(k, -1)).build());
            controls.add(rowIcon(MOVE_DOWN, "menukit.list.move_down", k)
                    .disabledWhen(() -> !canMove(k, 1)).onClick(() -> moveBy(k, 1)).build());
        }
        if (remove != null) controls.add(rowIcon(REMOVE, "menukit.list.remove", k).onClick(() -> askRemove(k)).build());
        return controls;
    }

    /**
     * A square icon button for a row (up, down, remove): the look's own button at the
     * controls' height, greyed as every disabled button is, hidden on a pinned row.
     */
    private Button.Builder rowIcon(Identifier icon, String tooltip, Object k) {
        return Button.builder().icon(icon).size(ListRow.CONTROL, ListRow.CONTROL)
                .tooltip(Component.translatable(tooltip))
                .visibleWhen(() -> !isPinned(k));
    }

    private boolean isPinned(Object k) {
        if (pinned == null) return false;
        T item = itemByKey.get(k);
        return item != null && pinned.test(item);
    }

    /**
     * Whether the row of key {@code k} can move {@code by} one place: not past either
     * end, not past a pinned row, and not while a query is typed (a move in a filtered
     * view would land somewhere you can't see).
     */
    private boolean canMove(Object k, int by) {
        if (move == null || !query.isEmpty() || isPinned(k)) return false;
        Integer at = indexByKey.get(k);
        if (at == null) return false;
        int to = at + by;
        return to >= 0 && to < keys.size() && !isPinned(keys.get(to));
    }

    private void moveBy(Object k, int by) {
        if (!canMove(k, by)) return;
        move.accept(k, indexByKey.get(k) + by);
        revealKey = k;
    }

    /** A remove: at once, or once the confirm dialog says so. */
    private void askRemove(Object k) {
        if (remove == null || isPinned(k)) return;
        if (confirm == null) remove.accept(k);
        else removing = k;
    }

    /** Runs an add and remembers what to look for: the key it returned, or whichever key is new. */
    private void runAdd(Supplier<?> action) {
        keysBeforeAdd = new HashSet<>(keys);
        addedKey = action.get();
        adding = true;
    }

    /** The row an add made, found on the next frame: scrolled into view, with a query that hides it cleared. */
    private void revealAdded() {
        adding = false;
        Object k = addedKey != null && itemByKey.containsKey(addedKey) ? addedKey : null;
        if (k == null) {
            for (Object candidate : keys) {
                if (!keysBeforeAdd.contains(candidate)) {
                    k = candidate;
                    break;
                }
            }
        }
        addedKey = null;
        keysBeforeAdd = Set.of();
        if (k == null) return;   // the consumer refused it: nothing to show
        if (searchText != null && !query.isEmpty() && !matches(k)) query = "";
        revealKey = k;
    }

    // The edit dialog

    /** Opens the edit dialog for key {@code k} on a fresh draft of its item. Nothing without a screen. */
    private void openEdit(Object k) {
        Screen screen = attachment.screen();
        T item = itemByKey.get(k);
        if (edit == null || screen == null || item == null) return;
        closeEdit();
        editing = k;
        editPanel = edit.panel(item, k, this::closeEdit, () -> editing != null);
        ScreenPanelRegistry.attachDialog(screen, editPanel);
        // Focus leaves the Edit button, so Enter in the dialog is not the button's.
        Focus.clear(screen);
    }

    /** Closes the edit dialog, dropping its draft. */
    private void closeEdit() {
        Screen screen = attachment.screen();
        if (editPanel != null && screen != null) ScreenPanelRegistry.detachDialog(screen, editPanel);
        editPanel = null;
        editing = null;
    }

    // Sort

    /** The sort in use: the consumer's, through {@code sortState}, or the list's own; its option kept in range. */
    private Sort sorting() {
        Sort s = sortGet != null ? sortGet.get() : ownSort;
        if (s == null) s = new Sort(0, false);
        if (s.option() < 0 || s.option() >= sorts.size()) s = new Sort(0, s.reversed());
        return s;
    }

    private void setSorting(Sort s) {
        if (sortSet != null) sortSet.accept(s);
        else ownSort = s;
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

    /**
     * Stacks the parts from the list's own position: the toolbar while the list draws
     * it, the disabled reason, then the shown rows {@code gap} apart (or the empty
     * element, or "No matches"). The toolbar sits {@code gap} above what follows, as
     * one more row would, so it reads as the list's own; the reason {@link #PART_GAP}.
     */
    private void place() {
        int y = 0;
        PanelElement bar = ownToolbar();
        if (bar != null) {
            ((AbstractPanelElement) bar).setChildPosition(childX, childY);
            y = bar.getHeight() + gap;
        }
        if (reason != null && reason.isVisible()) {
            ((AbstractPanelElement) reason).setChildPosition(childX, childY + y);
            y += reason.getHeight() + PART_GAP;
        }
        int top = y;
        boolean first = true;
        for (PanelElement row : shown) {
            if (!row.isVisible()) continue;
            if (!first) y += gap;
            ((AbstractPanelElement) row).setChildPosition(childX, childY + y);
            y += row.getHeight();
            first = false;
        }
        rowsHeight = y - top;
        PanelElement stand = standIn();
        if (stand != null) {
            if (stand instanceof AbstractPanelElement e) e.setChildPosition(childX, childY + y);
            if (stand.isVisible()) y += stand.getHeight();
        }
        totalHeight = y;
    }

    /** Syncs and places: the rows as they are this frame. */
    private void settle() {
        sync();
        place();
    }

    /** What shows in the rows' place when none do: "No matches" while a query hides every row, else the empty element. */
    private @Nullable PanelElement standIn() {
        if (!shown.isEmpty()) return null;
        return !keys.isEmpty() && noMatches != null ? noMatches : empty;
    }

    private boolean showsEmpty() {
        return shown.isEmpty() && empty != null && empty.isVisible();
    }

    /** What render and input reach: the rows, or the empty element while there are none, and the list's own parts. */
    private List<PanelElement> children() {
        List<PanelElement> parts = parts();
        PanelElement stand = standIn();
        if (parts.isEmpty()) {
            if (!shown.isEmpty()) return shown;
            return stand != null ? List.of(stand) : List.of();
        }
        List<PanelElement> all = new ArrayList<>(shown.size() + parts.size() + 1);
        all.addAll(shown);
        if (stand != null) all.add(stand);
        all.addAll(parts);
        return all;
    }

    /** The list's own parts besides the rows: the toolbar while it draws it, and the reason. */
    private List<PanelElement> parts() {
        PanelElement bar = ownToolbar();
        if (reason == null && bar == null) return List.of();
        List<PanelElement> parts = new ArrayList<>(2);
        if (bar != null) parts.add(bar);
        if (reason != null) parts.add(reason);
        return parts;
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
        return totalHeight;
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
        ChildDispatch.screenKind(extras(), kind);
    }

    @Override
    public void inDialog(boolean inDialog) {
        this.inDialog = inDialog;
        ChildDispatch.inDialog(new ArrayList<>(rows.values()), inDialog);
        ChildDispatch.inDialog(extras(), inDialog);
    }

    @Override
    public void inBand(boolean inBand) {
        this.inBand = inBand;
        ChildDispatch.inBand(new ArrayList<>(rows.values()), inBand);
        ChildDispatch.inBand(extras(), inBand);
    }

    /** Every element the list holds besides its rows, shown or not. */
    private List<PanelElement> extras() {
        List<PanelElement> all = new ArrayList<>(4);
        if (empty != null) all.add(empty);
        if (noMatches != null) all.add(noMatches);
        all.addAll(parts());
        return all;
    }

    // Render

    @Override
    public void render(RenderContext ctx) {
        settle();
        if (ctx.kind() != kind) screenKind(ctx.kind());
        RenderContext inner = childContext(ctx);
        // The reason is what explains the grey, so it is not greyed itself.
        if (reason != null && reason.isVisible()) reason.render(ctx);
        PanelElement bar = ownToolbar();
        if (bar != null) bar.render(inner);
        if (shown.isEmpty()) {
            PanelElement stand = standIn();
            if (stand != null && stand.isVisible()) stand.render(inner);
            return;
        }
        // Rows wholly outside the clip they draw under are skipped (a long list in a
        // scroll area), unless a popover of theirs is open.
        var g = ctx.graphics();
        ScreenRectangle clip = g != null && g.scissorStack != null ? g.scissorStack.peek() : null;
        InputContext probe = inner.input();
        PanelElement selectedRow = selectedRow();
        // mockup (E): the Panel look's selected card gets a wash under its content (the outline alone was lost on its border).
        if (isMockup("E")) for (PanelElement row : shown) if (row instanceof Flow f) f.mockupSelected = row == selectedRow;
        for (PanelElement row : shown) {
            if (!row.isVisible()) continue;
            int top = ctx.originY() + row.getChildY();
            boolean open = row.getActiveOverlayBounds(probe) != null;
            if (!open && clip != null && (top + row.getHeight() <= clip.top() || top >= clip.bottom())) continue;
            row.render(inner);
        }
        // The selected row's outline, over its card's edge.
        if (selectedRow != null && selectedRow.isVisible() && g != null && !(isMockup("E") && ctx.kind() != ScreenKind.DEFAULT)) {
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

    /** The selected row while it shows (a query can hide it), or {@code null}. */
    private @Nullable PanelElement selectedRow() {
        if (selectedGet == null) return null;
        Object selected = selectedGet.get();
        PanelElement row = selected == null ? null : rows.get(selected);
        return row != null && shown.contains(row) ? row : null;
    }

    /**
     * Once, the place of a row to scroll into view: one an add made or a move moved, or
     * the selected row when the selection changed other than by a click.
     */
    @Override
    public int @Nullable [] takeRevealRequest() {
        settle();
        if (revealKey != null) {
            PanelElement row = rows.get(revealKey);
            revealKey = null;
            if (row != null && row.isVisible() && shown.contains(row)) return new int[]{row.getChildY(), row.getHeight()};
        }
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

    /** Over a row's whole width (its empty area selects it), over the empty element, or over the toolbar or the reason. */
    @Override
    public boolean hitTest(InputContext in) {
        settle();
        if (ChildDispatch.hitTest(parts(), in)) return true;
        if (shown.isEmpty()) {
            PanelElement stand = standIn();
            return stand != null && stand.isVisible() && stand.hitTest(in);
        }
        return rowAt(in) != null;
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        settle();
        InputContext inner = in.disabledIf(ownDisabled());
        if (inner.disabled()) return false;
        if (ChildDispatch.overlayOwner(children(), inner) != null) return ChildDispatch.mouseClicked(children(), inner, button);
        List<PanelElement> parts = parts();
        if (ChildDispatch.hitTest(parts, inner)) return ChildDispatch.mouseClicked(parts, inner, button);
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
     * one shown row, and the new row is scrolled into view; with {@code move}, Alt+Up and
     * Alt+Down move the selected row instead.
     */
    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        InputContext inner = in.disabledIf(ownDisabled());
        if (ChildDispatch.keyPressed(children(), inner, keyCode, scanCode, modifiers)) return true;
        if (inner.disabled() || !focused || selectedSet == null) return false;
        if (keyCode != InputConstants.KEY_UP && keyCode != InputConstants.KEY_DOWN) return false;
        if (textCaptured()) return false;
        int by = keyCode == InputConstants.KEY_UP ? -1 : 1;
        if (move != null && (modifiers & InputConstants.MOD_ALT) != 0) {
            Object selected = selectedGet.get();
            if (selected != null) moveBy(selected, by);
            return true;
        }
        List<Object> order = new ArrayList<>();
        for (Object k : shownKeys) if (rows.get(k).isVisible()) order.add(k);
        if (order.isEmpty()) return false;
        int at = order.indexOf(selectedGet.get());
        int to = at < 0 ? 0 : by < 0 ? Math.max(0, at - 1) : Math.min(order.size() - 1, at + 1);
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

    // Lifecycle: the rows there now attach with the list; later ones as they come. The
    // confirm joins the screen's dialogs; a list moved to another screen starts with its
    // dialogs closed.

    @Override
    public void onAttach(Screen screen) {
        sync();
        if (attachment.screen() != null && attachment.screen() != screen) {
            closeEdit();
            removing = null;
        }
        attachment.attach(screen, everyChild());
        if (confirm != null) ScreenPanelRegistry.attachDialog(screen, confirm);
        if (editPanel != null) ScreenPanelRegistry.attachDialog(screen, editPanel);
    }

    @Override
    public void onDetach(Screen screen) {
        if (confirm != null) ScreenPanelRegistry.detachDialog(screen, confirm);
        if (editPanel != null) ScreenPanelRegistry.detachDialog(screen, editPanel);
        attachment.detach(screen, everyChild());
        focused = false;
    }

    /** Every row, shown or not (a query hides some), and the list's other elements. */
    private List<PanelElement> everyChild() {
        List<PanelElement> all = new ArrayList<>(keys.size() + 4);
        for (Object k : keys) all.add(rows.get(k));
        all.addAll(extras());
        return all;
    }

    // For MenuKit's checks.

    /** The row shown for {@code key} now (the one the factory made), or {@code null}. */
    public @Nullable PanelElement rowFor(Object key) {
        sync();
        PanelElement row = rows.get(key);
        return row instanceof ListRow r ? r.row() : row;
    }

    /** Whether the list has the keyboard (after a click in it, until a click outside). */
    public boolean hasKeyboard() {
        return focused;
    }

    /** As a click on the remove button of the row of {@code key}: removes, or opens the confirm. */
    @ApiStatus.Internal
    public void clickRemove(Object key) {
        sync();
        askRemove(key);
    }

    /** As a click on the Edit button of the row of {@code key}: opens its dialog. */
    @ApiStatus.Internal
    public void clickEdit(Object key) {
        sync();
        openEdit(key);
    }

    /** mockup: as a click on the row of {@code key}: selects it, and the list has the keyboard. */
    @ApiStatus.Internal
    public void clickRow(Object key) {
        sync();
        focused = true;
        selectedByClick = true;
        if (selectedSet != null) selectedSet.accept(key);
    }

    /** mockup: as a click on F's "Reorder" toggle. */
    @ApiStatus.Internal
    public void clickReorder() {
        setReorderMode(!reorderMode);
    }

    /** As a click on the add button of the {@code kind}th {@code add(...)}, from 0. */
    @ApiStatus.Internal
    public void clickAdd(int kind) {
        sync();
        runAdd(adds.get(kind).action());
    }

    /** As typing in the search field: the query now. */
    @ApiStatus.Internal
    public void typeQuery(String query) {
        this.query = Objects.requireNonNull(query, "query");
    }

    /** The keys of the rows shown now, top to bottom (the search and sort's view). */
    public List<Object> shownKeys() {
        sync();
        return List.copyOf(shownKeys);
    }

    /**
     * The sort in use (7.3.0): which {@link Builder#sort} option, in the order they were
     * given, and whether it runs backwards. A record, for {@link Builder#sortState}.
     *
     * @param option   the sort's place among the {@code sort(...)} calls, from 0
     * @param reversed whether the order runs backwards
     */
    public record Sort(int option, boolean reversed) {}

    /** One kind of add: its button's label and what it does. */
    private record AddKind(Component label, Supplier<?> action) {}

    /** One sort: its label in the picker and its order. */
    private record SortBy<T>(Component label, Comparator<? super T> order) {}

    /** The edit dialog's parts: its title, the draft from an item, the draft's controls, and Save. */
    private record EditDialog<T, D>(Function<? super T, Component> title, Function<? super T, ? extends D> draft,
                                    Function<? super D, ? extends PanelElement> controls,
                                    BiConsumer<Object, ? super D> save) {

        /**
         * The dialog for one item, every MenuKit dialog's layout: the title, the draft's
         * controls, Cancel and Save centred. Save writes the draft, then closes; Cancel
         * and Escape close, dropping it.
         */
        Panel panel(T item, Object k, Runnable close, BooleanSupplier shown) {
            D d = draft.apply(item);
            return DialogLayout.panel("menukit:list-edit-" + IDS.incrementAndGet(), close,
                            DialogLayout.centred(TextLabel.builder().text(title.apply(item)).build()),
                            controls.apply(d),
                            DialogLayout.buttons(
                                    DialogLayout.button(CommonComponents.GUI_CANCEL, close),
                                    DialogLayout.button(Component.translatable("menukit.list.save"), () -> {
                                        save.accept(k, d);
                                        close.run();
                                    })))
                    .visibleWhen(shown);
        }
    }

    /**
     * Builds a {@link ListView}. {@link #items}, {@link #key} and {@link #row} are
     * required; call {@code key} before {@code selection} and the editing options, since
     * it fixes the key's type.
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
        private final List<AddKind> adds = new ArrayList<>();
        private @Nullable BooleanSupplier addDisabled;
        private @Nullable Consumer<Object> remove;
        private @Nullable Function<? super T, Component> confirmRemove;
        private @Nullable BiConsumer<Object, Integer> move;
        private @Nullable Predicate<? super T> pinned;
        private @Nullable EditDialog<T, ?> edit;
        private @Nullable Component disabledReason;
        private @Nullable Function<? super T, String> searchText;
        private final List<SortBy<T>> sorts = new ArrayList<>();
        private @Nullable Supplier<Sort> sortGet;
        private @Nullable Consumer<Sort> sortSet;
        private @Nullable String mockup;

        Builder() {}

        /**
         * mockup: one of the round-3 comparison variants, by its shot name ("A-inside",
         * "B-divider", "C-outside-boxed", "D-outside-bare", "E-selection-toolbar",
         * "F-reorder-mode"). Not API: removed once one is picked.
         */
        @ApiStatus.Internal
        public Builder<T, K> mockup(String variant) {
            this.mockup = Objects.requireNonNull(variant, "variant");
            return this;
        }

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

        // Editing (7.3.0)

        /**
         * An add button in the {@link ListView#toolbar() toolbar}, labelled {@code label}; call once per kind of
         * item. {@code action} adds to your data and returns the new item's key (or
         * {@code null}: the list finds the key that appeared). The new row is scrolled
         * into view, and a search query that would hide it is cleared.
         */
        public Builder<T, K> add(Component label, Supplier<? extends @Nullable K> action) {
            adds.add(new AddKind(Objects.requireNonNull(label, "label"), Objects.requireNonNull(action, "action")));
            return this;
        }

        /** An add button whose action returns nothing: the list finds the key that appeared. */
        public Builder<T, K> add(Component label, Runnable action) {
            Objects.requireNonNull(action, "action");
            return add(label, () -> {
                action.run();
                return null;
            });
        }

        /** Greys every add button while {@code condition} holds, read every frame (a copy running, a cap reached). */
        public Builder<T, K> addDisabledWhen(BooleanSupplier condition) {
            this.addDisabled = Objects.requireNonNull(condition, "condition");
            return this;
        }

        /** A remove button on each row's first line, at its trailing edge; a click hands the row's key to {@code remove}. */
        @SuppressWarnings("unchecked")
        public Builder<T, K> remove(Consumer<? super K> remove) {
            Objects.requireNonNull(remove, "remove");
            this.remove = k -> remove.accept((K) k);
            return this;
        }

        /**
         * Asks before a remove: a confirm dialog whose body is {@code body} of the item,
         * read while it shows ("Delete Item 2? Its 3 locks move to Slot lock."). Needs
         * {@link #remove}.
         */
        public Builder<T, K> confirmRemove(Function<? super T, Component> body) {
            this.confirmRemove = Objects.requireNonNull(body, "body");
            return this;
        }

        /**
         * Up and down buttons on each row: a click hands the row's key and the place it
         * should go in {@code items()} to {@code move}. Can't be on a list with {@link #sort}.
         */
        @SuppressWarnings("unchecked")
        public Builder<T, K> move(BiConsumer<? super K, Integer> move) {
            Objects.requireNonNull(move, "move");
            this.move = (k, to) -> move.accept((K) k, to);
            return this;
        }

        /** Rows that stay put: no remove button, no arrows, and no other row moves past them. Read every frame. */
        public Builder<T, K> pinned(Predicate<? super T> pinned) {
            this.pinned = Objects.requireNonNull(pinned, "pinned");
            return this;
        }

        /**
         * An Edit button on each row that opens a dialog on a copy: {@code draft} makes
         * the copy from the item, {@code controls} the dialog's controls onto it (lenses
         * on the draft), and Save hands the key and the draft to {@code save}. Cancel and
         * Escape drop the draft.
         *
         * @param title    the dialog's title, from the item ("Edit Item 2")
         * @param draft    a copy of what the dialog edits, made each time it opens
         * @param controls the controls, made each time it opens, reading and writing the draft
         * @param save     what Save does with the key and the draft
         * @param <D>      the draft's type
         */
        @SuppressWarnings("unchecked")
        public <D> Builder<T, K> editDialog(Function<? super T, Component> title, Function<? super T, ? extends D> draft,
                                            Function<? super D, ? extends PanelElement> controls,
                                            BiConsumer<? super K, ? super D> save) {
            Objects.requireNonNull(save, "save");
            this.edit = new EditDialog<T, D>(Objects.requireNonNull(title, "title"), Objects.requireNonNull(draft, "draft"),
                    Objects.requireNonNull(controls, "controls"), (k, d) -> save.accept((K) k, d));
            return this;
        }

        /** Shown above the greyed rows while the list's {@code disabledWhen} holds: why it is greyed. */
        public Builder<T, K> disabledReason(Component reason) {
            this.disabledReason = Objects.requireNonNull(reason, "reason");
            return this;
        }

        /**
         * A search field in the {@link ListView#toolbar() toolbar}: a row shows while its
         * item's {@code text} holds the query, case-insensitive. The query is the list's
         * own view state.
         */
        public Builder<T, K> search(Function<? super T, String> text) {
            this.searchText = Objects.requireNonNull(text, "text");
            return this;
        }

        /**
         * A way to sort the rows, labelled {@code label} in the sort picker; call once per
         * way. The first is the default. With two or more, the toolbar has a picker and a
         * direction toggle. Can't be on a list with {@link #move}: a sorted list has no
         * order of its own to move a row in.
         */
        public Builder<T, K> sort(Component label, Comparator<? super T> order) {
            sorts.add(new SortBy<>(Objects.requireNonNull(label, "label"), Objects.requireNonNull(order, "order")));
            return this;
        }

        /** Keeps the sort choice yours (a lens), so it is remembered; otherwise it is the list's view state. */
        public Builder<T, K> sortState(Supplier<Sort> get, Consumer<Sort> set) {
            this.sortGet = Objects.requireNonNull(get, "get");
            this.sortSet = Objects.requireNonNull(set, "set");
            return this;
        }

        @Override
        public ListView<T> build() {
            require(items != null, "items(supplier) is required");
            require(key != null, "key(function) is required: rows are kept by key, not by index");
            require(row != null, "row(factory) is required");
            require(sorts.isEmpty() || move == null, "sort(...) and move(...) can't both be on one list: "
                    + "a sorted list has no order of its own for up and down to change");
            require(confirmRemove == null || remove != null, "confirmRemove(...) needs remove(...): it asks before one");
            require(addDisabled == null || !adds.isEmpty(), "addDisabledWhen(...) needs an add(...) to grey");
            require(sortGet == null || !sorts.isEmpty(), "sortState(...) needs a sort(...)");
            require(disabledReason == null || disabledWhen != null,
                    "disabledReason(...) needs disabledWhen(...): the reason shows while it holds");
            return new ListView<>(this);
        }
    }
}
