package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.element.ItemMatch.Match;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.DialogLayout;
import com.trevlar.menukit.core.ItemEditing;
import com.trevlar.menukit.core.ItemEditing.EnchantChoice;
import com.trevlar.menukit.core.ItemSources;
import com.trevlar.menukit.core.PanelRendering;
import com.trevlar.menukit.core.PressedTracker;
import com.trevlar.menukit.core.VanillaLook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * The dialog an {@link ItemPicker} opens: the slot row, a Search tab (a search box
 * and a grid of every item) and, unless the picker keeps only the kind of item, an
 * Edit tab for the selected slot; Cancel and Done. Built of MenuKit's own elements
 * ({@link Tabs}, {@link TextField}, {@link ScrollContainer}, {@link Radio},
 * {@link Button}) plus the slot row, the grid and the enchantment list drawn here,
 * laid out as every MenuKit dialog is ({@link DialogLayout}), as wide and as tall as
 * the shown tab, so it draws in the kind of screen it opens on like any dialog.
 * Package-private: consumers reach it through {@link ItemPicker}.
 *
 * <p>Its width comes from its content: the grid asks for its nine columns, its scroll
 * area adds the band's inset and the scrollbar lane ({@link ScrollContainer.Builder#fitWidth}),
 * and the search box and the other fields fill whatever that makes, so nothing is
 * cut off and nothing leaves an empty margin, on either kind of screen.
 *
 * <p>The value lives with the consumer, through the picker's lens, written on every
 * change. The dialog keeps its view state: the slots as the row shows them (a list's
 * emptied slots stay where they were until it closes, so an item can go back), each
 * slot's match, the selected slot, what the cursor holds, the tab, the search, and
 * the Edit tab's text as typed.
 *
 * <p>The cursor's stack is the dialog's own. Taking an item from the grid makes one;
 * nothing comes from or goes to the player's inventory. A click anywhere but the slot
 * row and the grid drops it, and so does closing the dialog.
 */
final class ItemDialog {

    private static final AtomicLong IDS = new AtomicLong();

    /** The grid: a creative inventory's nine columns and five rows. */
    static final int COLUMNS = 9;
    static final int ROWS = 5;
    static final int GRID_WIDTH = COLUMNS * ItemPicker.SIZE;
    static final int FIELD_HEIGHT = 20;
    static final int GAP = DialogLayout.GAP;
    /** The count field's width: three digits. */
    static final int COUNT_WIDTH = 30;
    /** The least a filling field asks for, so a dialog with little else in it is still usable. */
    static final int FIELD_MIN_WIDTH = 60;
    /** Vanilla's EditBox draws its text this far inside its border, each side. */
    static final int FIELD_TEXT_INSET = 4;

    /**
     * The enchantment list: a control button's size, a row (a control and a pixel
     * above and below it), and the rows on show before the list scrolls.
     */
    static final int CONTROL = 12;
    static final int ENCHANT_ROW = CONTROL + 2;
    static final int ENCHANT_ROWS = 4;
    /** In a row: the name's room from the left, the gap after a name or a level, and between controls. */
    static final int TEXT_PAD = 2;
    static final int ROW_GAP = 4;
    static final int CONTROL_GAP = 1;

    /** What a field shows while its text is not valid, and vanilla's EditBox colour otherwise. */
    private static final int TEXT_OK = 0xFFE0E0E0;
    private static final int TEXT_BAD = 0xFFFF5555;

    static final Component SEARCH_HINT = Component.literal("Search items");
    static final Component NAME_HINT = Component.literal("Name");
    /** How the components box says what it is for, while empty: short enough to fit it. */
    static final Component COMPONENTS_HINT = Component.literal("More: [unbreakable={}]");
    /** The longer how-to, on hover. */
    static final Component COMPONENTS_TOOLTIP = Component.literal(
            "Any other components, as in /give: [unbreakable={},lore=[\"Hi\"]]. Put ! before one to remove it: [!food].");
    /** The Edit tab with an empty slot selected. */
    static final Component EMPTY_HINT = Component.literal("Pick an item in Search first.");
    /** Between the enchantments on the item and the ones that can be added. */
    static final Component ADD_HEADING = Component.literal("Can be added");

    private final ItemPicker picker;
    private final Panel panel;
    private final PanelElement title, row, buttons;
    private final TextField search;
    private final Grid grid;
    private final ScrollContainer gridBox;
    private final @Nullable Tabs tabs;
    private final @Nullable TextField nameField, countField, componentsField;
    private final @Nullable EnchantList enchants;
    private final @Nullable ScrollContainer enchantBox;
    /** The Edit tab's column, and everything in it, top to bottom (rows and what is in them). */
    private final @Nullable PanelElement edit;
    private final List<PanelElement> editParts = new ArrayList<>();

    // View state
    private boolean open = false;
    private List<ItemMatch> before = List.of();
    /** The slots as the row shows them, empties included; each one's match beside it. */
    private final List<ItemStack> slots = new ArrayList<>();
    private final List<Match> matches = new ArrayList<>();
    private int selected = 0;
    private ItemStack carried = ItemStack.EMPTY;
    /** A drag over the row: the slots it has covered, and the button held (-1: none). */
    private final LinkedHashSet<Integer> dragged = new LinkedHashSet<>();
    private int dragButton = -1;
    private String tab = "search";
    private String query = "";
    private double gridScroll = 0, enchantScroll = 0;
    private final Draft name = new Draft(), count = new Draft(), components = new Draft();
    /** The selected stack the enchantment choices were last made for, and the choices. */
    private @Nullable ItemStack choicesFor;
    private List<EnchantChoice> choices = List.of();

    ItemDialog(ItemPicker picker) {
        this.picker = picker;
        boolean edits = picker.match() != Match.ITEM;

        // The search box spans the dialog, lined up with the grid's area under it.
        this.search = TextField.builder().at(0, 0).size(FIELD_MIN_WIDTH, FIELD_HEIGHT).fill()
                .maxLength(64)
                .hint(SEARCH_HINT)
                .label(SEARCH_HINT)
                .state(() -> query, q -> {
                    query = q;
                    gridScroll = 0;   // a new search starts at the top of its results
                })
                // Enter puts the first match in the selected slot, so typing a name
                // and Enter fills it.
                .onSubmit(q -> putFirstMatch())
                .build();
        // As wide as the grid's nine columns, the band's inset and the scrollbar lane.
        this.grid = new Grid();
        this.gridBox = ScrollContainer.builder().at(0, FIELD_HEIGHT + GAP).fitWidth().height(ROWS * ItemPicker.SIZE)
                .content(List.of(grid))
                .state(() -> gridScroll, v -> gridScroll = v)
                .build();

        this.title = DialogLayout.centred(TextLabel.builder().text(picker.label()).build());
        this.row = new SlotRow();
        this.buttons = DialogLayout.buttons(
                Button.builder().label(Component.literal("Cancel"))
                        .size(DialogLayout.BUTTON_W, DialogLayout.BUTTON_H).onClick(picker::cancel).build(),
                Button.builder().label(Component.literal("Done"))
                        .size(DialogLayout.BUTTON_W, DialogLayout.BUTTON_H).onClick(picker::done).build());

        String id = "menukit:item-dialog-" + IDS.incrementAndGet();
        if (!edits) {
            // The kind of item only: nothing to edit, so no tabs, just the search.
            this.nameField = this.countField = this.componentsField = null;
            this.enchants = null;
            this.enchantBox = null;
            this.edit = null;
            this.tabs = null;
            this.panel = DialogLayout.panel(id, picker::cancel, title, row, search, gridBox, buttons)
                    .visibleWhen(this::isOpen);
            return;
        }

        // The name takes its row, less the count beside it when the item stacks.
        this.nameField = new TextField(TextField.builder().size(FIELD_MIN_WIDTH, FIELD_HEIGHT)
                .maxLength(50)   // an anvil's limit
                .hint(NAME_HINT)
                .label(NAME_HINT)
                .state(() -> name.text(current(), selected, ItemEditing::name), this::typeName)) {
            @Override
            public void layoutWithin(int budget) {
                width = Math.max(1, budget - (stacks() ? COUNT_WIDTH + GAP : 0));
            }
        };
        this.countField = TextField.builder().size(COUNT_WIDTH, FIELD_HEIGHT)
                .maxLength(3)
                .label(Component.literal("Count"))
                .tooltip(() -> Component.literal("Count, 1 to " + current().getMaxStackSize()))
                .visibleWhen(this::stacks)
                .state(() -> count.text(current(), selected, s -> String.valueOf(s.getCount())), this::typeCount)
                .build();
        this.componentsField = TextField.builder().size(FIELD_MIN_WIDTH, FIELD_HEIGHT).fill()
                .maxLength(4096)
                .hint(COMPONENTS_HINT)
                .label(Component.literal("Components"))
                .tooltip(COMPONENTS_TOOLTIP)
                .visibleWhen(this::hasItem)
                .state(() -> components.text(current(), selected,
                        s -> ItemEditing.componentsText(s, ItemSources.registries())), this::typeComponents)
                .build();

        Flow.Builder column = Flow.builder().at(0, 0).vertical().gap(GAP, GAP);
        TextLabel emptyHint = TextLabel.builder().role(TextRole.MUTED).text(EMPTY_HINT).fill()
                .visibleWhen(() -> current().isEmpty()).build();
        Flow nameRow = Flow.builder().gap(GAP, GAP).visibleWhen(this::hasItem)
                .add(nameField)
                .add(countField)
                .build();
        column.add(emptyHint).add(nameRow);
        editParts.addAll(List.of(emptyHint, nameRow, nameField, countField));
        if (picker.playerChoosesMatch()) {
            // One choice of two, both on show: round radios, so they never read as checkboxes.
            RadioGroup<Match> choice = RadioGroup.state(this::currentMatch, this::chooseMatch);
            Radio<Match> any = Radio.builder(choice, Match.CONTAINS)
                    .label(() -> ItemPicker.matchName(Match.CONTAINS, current())).build();
            Radio<Match> exact = Radio.builder(choice, Match.EXACT)
                    .label(() -> ItemPicker.matchName(Match.EXACT, current())).build();
            Flow matchRow = Flow.builder().gap(8, 3).visibleWhen(this::hasItem).add(any).add(exact).build();
            column.add(matchRow);
            editParts.addAll(List.of(matchRow, any, exact));
        }
        // The enchantments: those on the item, then the ones that can be added, in one
        // list as tall as its rows up to ENCHANT_ROWS, then it scrolls.
        this.enchants = new EnchantList();
        this.enchantBox = ScrollContainer.builder().fitWidth().height(ENCHANT_ROWS * ENCHANT_ROW).fitHeight()
                .content(List.of(enchants))
                .state(() -> enchantScroll, v -> enchantScroll = v)
                .visibleWhen(() -> hasItem() && !enchantChoices().isEmpty())
                .build();
        TextLabel error = TextLabel.builder().fill()
                .text(() -> components.error != null ? components.error : Component.empty())
                .visibleWhen(() -> hasItem() && components.error != null).build();
        column.add(enchantBox).add(componentsField).add(error);
        editParts.addAll(List.of(enchantBox, componentsField, error));
        this.edit = column.build();

        this.tabs = Tabs.builder()
                .state(() -> tab, t -> tab = t)
                .fitShownTab()
                .tab("search", Component.literal("Search"), List.of(search, gridBox))
                .tab("edit", Component.literal("Edit"), List.of(edit))
                .build();
        this.panel = DialogLayout.panel(id, picker::cancel, title, row, tabs, buttons)
                .visibleWhen(this::isOpen);
    }

    Panel panel() {
        return panel;
    }

    boolean isOpen() {
        return open;
    }

    /** The search box's vanilla widget, to focus when the dialog opens (null before the screen's init). */
    @Nullable EditBox searchWidget() {
        return search.widget();
    }

    // Opening and closing

    /**
     * Opened at slot {@code slot}: the value as it is now (kept for Cancel), a list's
     * slot to add to after it, the Search tab, nothing held, the search cleared.
     */
    void open(int slot) {
        before = picker.value();
        slots.clear();
        matches.clear();
        for (ItemMatch m : before) {
            slots.add(m.stack().copy());
            matches.add(m.match());
        }
        if (picker.isList() || slots.isEmpty()) {
            slots.add(ItemStack.EMPTY);
            matches.add(picker.match());
        }
        selected = Math.max(0, Math.min(slot, slots.size() - 1));
        dropCarried();
        tab = "search";
        query = "";
        gridScroll = 0;
        enchantScroll = 0;
        name.reset();
        count.reset();
        components.reset();
        open = true;
    }

    /** Closed (Done, Cancel, a new screen): whatever the cursor held is gone. */
    void close() {
        open = false;
        dropCarried();
    }

    /** The cursor lets go of what it holds: it is simply gone (it was never anyone's). */
    private void dropCarried() {
        carried = ItemStack.EMPTY;
        dragged.clear();
        dragButton = -1;
    }

    List<ItemMatch> before() {
        return before;
    }

    List<ItemStack> slots() {
        return List.copyOf(slots);
    }

    int selected() {
        return selected;
    }

    ItemStack carried() {
        return carried;
    }

    void showTab(String id) {
        tab = id;
    }

    void select(int slot) {
        if (slot >= 0 && slot < slots.size()) selected = slot;
    }

    // The value

    /** The selected slot's stack (empty for an empty slot). */
    private ItemStack current() {
        return selected < slots.size() ? slots.get(selected) : ItemStack.EMPTY;
    }

    private boolean hasItem() {
        return !current().isEmpty();
    }

    /** Whether the selected item stacks, so its count can be set. */
    private boolean stacks() {
        return hasItem() && current().getMaxStackSize() > 1;
    }

    private Match currentMatch() {
        return selected < matches.size() ? matches.get(selected) : picker.match();
    }

    void chooseMatch(Match match) {
        if (selected >= matches.size() || current().isEmpty()) return;
        matches.set(selected, match);
        commit();
    }

    /** The selected slot replaced by {@code stack} (an edit), written through. */
    private void replaceCurrent(ItemStack stack) {
        if (selected >= slots.size()) return;
        slots.set(selected, stack);
        changed();
    }

    /**
     * After any change to the slots: a list keeps one empty slot at its end to add
     * to, then the value is written.
     */
    private void changed() {
        if (picker.isList() && (slots.isEmpty() || !slots.get(slots.size() - 1).isEmpty())) {
            slots.add(ItemStack.EMPTY);
            matches.add(picker.match());
        }
        commit();
    }

    /** The slots that hold something, in order, each with its match, through the lens. */
    private void commit() {
        List<ItemMatch> out = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (!slots.get(i).isEmpty()) out.add(new ItemMatch(slots.get(i), matches.get(i)));
        }
        picker.write(out);
    }

    /** The most of {@code stack} a slot or the cursor holds: one when the picker keeps only the kind. */
    private int limit(ItemStack stack) {
        return picker.match() == Match.ITEM ? 1 : stack.getMaxStackSize();
    }

    // Search

    /** The grid's entries for {@code q}: every item, with its variants unless the picker keeps only the kind. */
    List<ItemStack> entries(String q) {
        return ItemSources.search(picker.match() == Match.ITEM, q);
    }

    /** Enter in the search box: the first match into the selected slot, then on to the next empty one. */
    private void putFirstMatch() {
        List<ItemStack> shown = entries(query);
        if (shown.isEmpty() || selected >= slots.size()) return;
        ElementWidget.playClickSound();
        slots.set(selected, shown.get(0).copyWithCount(1));
        changed();
        if (picker.isList()) selected = slots.size() - 1;
    }

    // Moving items, as the creative inventory moves them (CreativeModeInventoryScreen.slotClicked)

    /**
     * A click on a grid entry: with nothing held, take one (shift: a full stack; the
     * middle button: a full stack); holding the same, left adds one (shift: fills
     * it) and right takes one away; holding something else, left puts it back and
     * right puts one back.
     */
    void clickEntry(ItemStack entry, int button, boolean shift) {
        int lim = limit(entry);
        if (button == Click.MIDDLE) {
            if (carried.isEmpty()) carried = entry.copyWithCount(lim);
            return;
        }
        if (!carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, entry)) {
            if (button == Click.LEFT) {
                if (shift) carried.setCount(lim);
                else if (carried.getCount() < lim) carried.grow(1);
            } else {
                carried.shrink(1);
            }
        } else if (carried.isEmpty()) {
            carried = entry.copyWithCount(shift ? lim : 1);
        } else if (button == Click.LEFT) {
            carried = ItemStack.EMPTY;
        } else {
            carried.shrink(1);
        }
        if (carried.isEmpty()) carried = ItemStack.EMPTY;
    }

    /**
     * A click on a slot of the row, as on a slot of any container: with nothing held,
     * left picks it all up and right picks up half; holding something, left places
     * it all (or as much as fits on the same item) and right places one; a
     * different item swaps with what is held.
     */
    void clickSlot(int i, int button) {
        if (i < 0 || i >= slots.size() || (button != Click.LEFT && button != Click.RIGHT)) return;
        selected = i;
        ItemStack in = slots.get(i);
        boolean left = button == Click.LEFT;
        if (carried.isEmpty()) {
            if (in.isEmpty()) return;
            int take = left ? in.getCount() : (in.getCount() + 1) / 2;
            carried = in.copyWithCount(take);
            slots.set(i, take == in.getCount() ? ItemStack.EMPTY : in.copyWithCount(in.getCount() - take));
        } else if (in.isEmpty()) {
            int put = left ? Math.min(carried.getCount(), limit(carried)) : 1;
            slots.set(i, carried.copyWithCount(put));
            carried.shrink(put);
        } else if (ItemStack.isSameItemSameComponents(in, carried)) {
            int room = limit(in) - in.getCount();
            int put = Math.min(room, left ? carried.getCount() : 1);
            if (put > 0) {
                slots.set(i, in.copyWithCount(in.getCount() + put));
                carried.shrink(put);
            }
        } else if (carried.getCount() <= limit(carried)) {
            slots.set(i, carried);
            carried = in;
        }
        if (carried.isEmpty()) carried = ItemStack.EMPTY;
        changed();
    }

    /** A drag over {@code over}, then released. */
    void drag(List<Integer> over, int button) {
        dragged.clear();
        dragged.addAll(over);
        dragButton = button;
        endDrag();
    }

    /** Whether a drag may spread onto slot {@code i}: empty, or the same item with room. */
    private boolean takesDrag(int i) {
        ItemStack in = slots.get(i);
        return in.isEmpty() || (ItemStack.isSameItemSameComponents(in, carried) && in.getCount() < limit(in));
    }

    /**
     * The drag's release: over one slot it is a click there; over more, the held
     * stack spreads as vanilla's does, evenly with the left button (what does not
     * divide stays held), one each with the right.
     */
    private void endDrag() {
        List<Integer> over = new ArrayList<>(dragged);
        int button = dragButton;
        dragged.clear();
        dragButton = -1;
        if (over.size() <= 1) {
            if (!over.isEmpty()) clickSlot(over.get(0), button);
            return;
        }
        List<Integer> take = over.stream().filter(i -> i < slots.size() && takesDrag(i)).toList();
        if (take.isEmpty() || carried.isEmpty()) return;
        int each = button == Click.LEFT ? Math.max(1, carried.getCount() / take.size()) : 1;
        for (int i : take) {
            if (carried.isEmpty()) break;
            ItemStack in = slots.get(i);
            int put = Math.min(Math.min(each, limit(carried) - in.getCount()), carried.getCount());
            if (put <= 0) continue;
            slots.set(i, carried.copyWithCount(in.getCount() + put));
            carried.shrink(put);
        }
        if (carried.isEmpty()) carried = ItemStack.EMPTY;
        selected = take.get(0);
        changed();
    }

    /**
     * A click anywhere, before it is dispatched (every click on the screen is offered
     * to {@link PanelElement#notifyClickOutsideOverlay} first): holding something, a
     * click that is on neither a slot of the row (which places it) nor the grid (which
     * takes, adds or puts back, as the creative inventory does) drops it. {@code in}
     * is at the dialog's content origin.
     */
    private void clickAnywhere(InputContext in) {
        if (!open || carried.isEmpty()) return;
        if (((SlotRow) row).slotAt(in) >= 0) return;
        int[] g = gridViewport();
        if (g != null && in.isOver(g[0], g[1], g[2], g[3])) return;
        dropCarried();
    }

    /** The grid's area ({@code x, y, w, h} in the dialog's content), or null while the Edit tab is shown. */
    private int @Nullable [] gridViewport() {
        if (tabs == null) return rect(gridBox, 0, 0);
        if (!tab.equals("search")) return null;
        int[] clip = tabs.bodyClip();
        return rect(gridBox, tabs.getChildX() + clip[0], tabs.getChildY() + clip[1]);
    }

    // The Edit tab's fields

    private void typeName(String text) {
        ItemStack after = ItemEditing.withName(current(), text);
        replaceCurrent(after);
        name.typed(text, after, selected, null);
    }

    private void typeCount(String text) {
        int n = ItemEditing.parseCount(current(), text);
        if (n > 0) {
            ItemStack after = current().copyWithCount(n);
            replaceCurrent(after);
            count.typed(text, after, selected, null);
        } else {
            count.typed(text, current(), selected, Component.literal("Not a count from 1 to " + current().getMaxStackSize()));
        }
    }

    private void typeComponents(String text) {
        ItemEditing.Edit edit = ItemEditing.withComponentsText(current(), text, ItemSources.registries());
        if (edit.stack() != null) {
            replaceCurrent(edit.stack());
            components.typed(text, edit.stack(), selected, null);
        } else {
            components.typed(text, current(), selected, edit.error());
        }
    }

    void typeEdit(String field, String text) {
        switch (field) {
            case "name" -> typeName(text);
            case "count" -> typeCount(text);
            case "components" -> typeComponents(text);
            default -> throw new IllegalArgumentException("no Edit field " + field);
        }
    }

    String editText(String field) {
        return switch (field) {
            case "name" -> name.text(current(), selected, ItemEditing::name);
            case "count" -> count.text(current(), selected, s -> String.valueOf(s.getCount()));
            case "components" -> components.text(current(), selected,
                    s -> ItemEditing.componentsText(s, ItemSources.registries()));
            default -> throw new IllegalArgumentException("no Edit field " + field);
        };
    }

    @Nullable Component componentsError() {
        return components.error;
    }

    /**
     * Once a frame: a field whose text is not valid shows red, and goes back to the
     * slot's value once it loses focus.
     */
    private void checkFields() {
        check(countField, count);
        check(componentsField, components);
    }

    private static void check(@Nullable TextField field, Draft draft) {
        EditBox box = field != null ? field.widget() : null;
        if (box == null) return;
        box.setTextColor(draft.error == null ? TEXT_OK : TEXT_BAD);
        if (draft.error != null && !box.isFocused()) draft.reset();
    }

    /**
     * A field's text as typed, kept while it stands for the slot's stack: typing that
     * is valid changes the stack and the text stays as typed; the slot changing, or
     * its stack changing from elsewhere, replaces the text with the stack's.
     */
    private static final class Draft {
        String text = "";
        @Nullable ItemStack shownFor;
        int slot = -1;
        @Nullable Component error;

        String text(ItemStack current, int slot, Function<ItemStack, String> format) {
            if (slot != this.slot || shownFor == null || !ItemStack.matches(current, shownFor)) {
                text = current.isEmpty() ? "" : format.apply(current);
                shownFor = current.copy();
                this.slot = slot;
                error = null;
            }
            return text;
        }

        void typed(String text, ItemStack stands, int slot, @Nullable Component error) {
            this.text = text;
            this.shownFor = stands.copy();
            this.slot = slot;
            this.error = error;
        }

        void reset() {
            shownFor = null;
            error = null;
        }
    }

    // The Edit tab's enchantments

    /** The enchantments that fit the selected stack, made again only when it changes. */
    List<EnchantChoice> enchantChoices() {
        ItemStack s = current();
        if (choicesFor == null || !ItemStack.matches(s, choicesFor)) {
            choicesFor = s.copy();
            choices = ItemEditing.enchantChoices(s, ItemSources.registries());
        }
        return choices;
    }

    private @Nullable EnchantChoice choice(Holder<Enchantment> enchantment) {
        for (EnchantChoice c : enchantChoices()) if (c.enchantment().equals(enchantment)) return c;
        return null;
    }

    /** The add control of an enchantment not on the item: on at level I, unless one on it excludes it. */
    void addEnchant(Holder<Enchantment> enchantment) {
        EnchantChoice c = choice(enchantment);
        if (c == null || c.applied() || c.blocked()) return;
        setEnchantment(c, 1);
    }

    /** The - or + of an enchantment on the item: a level down or up, from I to its highest, no further. */
    void stepEnchant(Holder<Enchantment> enchantment, int by) {
        EnchantChoice c = choice(enchantment);
        if (c == null || !c.applied()) return;
        int level = Math.max(1, Math.min(c.max(), c.level() + by));
        if (level != c.level()) setEnchantment(c, level);
    }

    /** The remove control of an enchantment on the item: off it, back with those that can be added. */
    void removeEnchant(Holder<Enchantment> enchantment) {
        EnchantChoice c = choice(enchantment);
        if (c != null && c.applied()) setEnchantment(c, 0);
    }

    /** The selected stack with {@code choice} at {@code level}, written through. */
    private void setEnchantment(EnchantChoice choice, int level) {
        replaceCurrent(ItemEditing.withEnchantment(current(), choice.enchantment(), level));
    }

    /** A level as vanilla writes it: I to X, a number past that. */
    static Component levelText(int level) {
        return level <= 10 ? Component.translatable("enchantment.level." + level) : Component.literal(String.valueOf(level));
    }

    // For MenuKit's checks: everything the dialog draws, and what it must stay inside

    /**
     * Each box the dialog draws on the shown tab, in its content's coordinates, with
     * the box it must stay inside: every row in the dialog, every element of the shown
     * tab in the tab's body, each slot in the row, the grid's columns in the grid's
     * viewport, a field's hint inside the field's text area, and each enchantment
     * row's name, level and controls inside their room in the row. {@code kind} is the
     * screen it is laid out for (the band's inset).
     */
    List<ItemPicker.Box> boxes(ScreenKind kind) {
        Font font = Minecraft.getInstance().font;
        List<ItemPicker.Box> out = new ArrayList<>();
        List<PanelElement> rows = new ArrayList<>(List.of(title, row, buttons));
        if (tabs != null) rows.add(tabs);
        else rows.addAll(List.of(search, gridBox));
        int w = 0, h = 0;
        for (PanelElement e : rows) {
            w = Math.max(w, e.getChildX() + e.getWidth());
            h = Math.max(h, e.getChildY() + e.getHeight());
        }
        int[] dialog = {0, 0, w, h};
        for (PanelElement e : rows) out.add(box("row " + describe(e), rect(e, 0, 0), dialog));

        int[] slotsRect = rect(row, 0, 0);
        SlotRow r = (SlotRow) row;
        for (int i = 0; i < slots.size(); i++) {
            out.add(box("slot " + i, new int[]{slotsRect[0] + r.left() + (i % COLUMNS) * ItemPicker.SIZE,
                    slotsRect[1] + (i / COLUMNS) * ItemPicker.SIZE, ItemPicker.SIZE, ItemPicker.SIZE}, slotsRect));
        }

        // The shown tab's body: where it is and what clips it.
        int bx = 0, by = 0;
        int[] body = dialog;
        if (tabs != null) {
            int[] clip = tabs.bodyClip();
            bx = tabs.getChildX() + clip[0];
            by = tabs.getChildY() + clip[1];
            body = new int[]{bx, by, clip[2], clip[3]};
        }
        if (tabs == null || tab.equals("search")) {
            int[] s = rect(search, bx, by);
            if (tabs != null) out.add(box("search", s, body));
            out.add(hint("search", SEARCH_HINT, s, font));
            int[] gb = rect(gridBox, bx, by);
            if (tabs != null) out.add(box("grid area", gb, body));
            int inset = ScrollContainer.bandInsetX(kind, true);
            int viewport = gb[2] - ScrollContainer.TRACK_WIDTH - ScrollContainer.SCROLLER_GUTTER;
            out.add(box("grid columns", new int[]{gb[0] + inset + grid.left(), gb[1], GRID_WIDTH, gb[3]},
                    new int[]{gb[0], gb[1], viewport, gb[3]}));
            return out;
        }
        if (edit == null || enchants == null || enchantBox == null) return out;
        // The edit column in the body, and each shown thing in it.
        out.add(box("edit", rect(edit, bx, by), body));
        for (PanelElement e : editParts) {
            if (!e.isVisible() || (e != edit && !shownIn(e))) continue;
            int[] at = rect(e, bx, by);
            out.add(box(describe(e), at, body));
            if (e == componentsField) out.add(hint("components", COMPONENTS_HINT, at, font));
            if (e == nameField) out.add(hint("name", NAME_HINT, at, font));
        }
        if (enchantBox.isVisible()) {
            int[] eb = rect(enchantBox, bx, by);
            int insetX = ScrollContainer.bandInsetX(kind, true), insetY = ScrollContainer.bandInsetY(kind, true);
            int viewport = eb[2] - ScrollContainer.TRACK_WIDTH - ScrollContainer.SCROLLER_GUTTER;
            int ox = eb[0] + insetX, oy = eb[1] + insetY;
            out.add(box("enchantment rows", new int[]{ox, eb[1], enchants.getWidth(), eb[3]},
                    new int[]{eb[0], eb[1], viewport, eb[3]}));
            enchants.boxes(out, ox, oy, font);
        }
        return out;
    }

    /** Whether an Edit part is on show: it and the row it is in (a field in a hidden row is not). */
    private boolean shownIn(PanelElement e) {
        for (PanelElement p : editParts) {
            if (p instanceof Flow f && f.contains(e) && !p.isVisible()) return false;
        }
        return true;
    }

    private static String describe(PanelElement e) {
        if (e instanceof TextLabel l) return "label \"" + l.getCurrentText().getString() + "\"";
        if (e instanceof Radio<?> r) return "radio " + r.getValue();
        if (e instanceof TextField) return "field";
        return e.getClass().getSimpleName();
    }

    /** A field's hint, from where the field's text starts, inside the field's text area. */
    private static ItemPicker.Box hint(String field, Component hint, int[] at, Font font) {
        int[] area = {at[0] + FIELD_TEXT_INSET, at[1], Math.max(0, at[2] - 2 * FIELD_TEXT_INSET), at[3]};
        return box(field + " hint \"" + hint.getString() + "\"", new int[]{area[0], at[1], font.width(hint), at[3]}, area);
    }

    private static int[] rect(PanelElement e, int ox, int oy) {
        return new int[]{ox + e.getChildX(), oy + e.getChildY(), e.getWidth(), e.getHeight()};
    }

    private static ItemPicker.Box box(String what, int[] r, int[] p) {
        return new ItemPicker.Box(what, r[0], r[1], r[2], r[3], p[0], p[1], p[2], p[3]);
    }

    // The slot row

    /**
     * The setting's slots, nine to a row, centred: the selected one outlined, a list's
     * last (empty) one marked with a plus. Takes clicks and drags as a container's
     * slots do on the Search tab, and only selects on the Edit tab. Draws what the
     * cursor holds over everything, in the overlay pass. Asks for its slots' width.
     */
    private final class SlotRow extends AbstractPanelElement {

        SlotRow() {
            this.height = ItemPicker.SIZE;
        }

        private int perRow() {
            return COLUMNS;
        }

        private int rows() {
            return Math.max(1, (slots.size() + perRow() - 1) / perRow());
        }

        /** Left edge of the slots, from the row's: centred under the title. */
        int left() {
            return (getWidth() - naturalWidth()) / 2;
        }

        @Override public int naturalWidth() { return Math.min(Math.max(1, slots.size()), perRow()) * ItemPicker.SIZE; }
        @Override public int getWidth() { return Math.max(width, naturalWidth()); }
        @Override public int getHeight() { return rows() * ItemPicker.SIZE; }
        @Override public int extraLayoutHeight() { return getHeight() - ItemPicker.SIZE; }
        @Override public void layoutWithin(int budget) { width = budget; }
        @Override public void fillWidth(int width) {}
        @Override public boolean isInteractive() { return true; }

        int slotAt(InputContext in) {
            return slotAt(in.mouseX(), in.mouseY(), in.originX(), in.originY());
        }

        private int slotAt(double mx, double my, int ox, int oy) {
            double lx = mx - (ox + childX + left()), ly = my - (oy + childY);
            if (lx < 0 || ly < 0) return -1;
            int col = (int) (lx / ItemPicker.SIZE), row = (int) (ly / ItemPicker.SIZE);
            if (col >= perRow()) return -1;
            int i = row * perRow() + col;
            return i < slots.size() ? i : -1;
        }

        @Override
        public void render(RenderContext ctx) {
            checkFields();
            GuiGraphicsExtractor g = ctx.graphics();
            int x0 = ctx.originX() + childX + left(), y0 = ctx.originY() + childY;
            int hovered = ctx.hasMouseInput() ? slotAt(ctx.mouseX(), ctx.mouseY(), ctx.originX(), ctx.originY()) : -1;
            // A drag spreads onto each slot it passes that can take it, while the
            // held stack has more items than slots covered (vanilla's rule).
            if (dragButton >= 0) {
                boolean held = dragButton != Click.LEFT || PressedTracker.isLeftMouseDown();
                if (!held) endDrag();
                else if (hovered >= 0 && !dragged.contains(hovered) && takesDrag(hovered)
                        && carried.getCount() > dragged.size()) dragged.add(hovered);
            }
            for (int i = 0; i < slots.size(); i++) {
                int x = x0 + (i % perRow()) * ItemPicker.SIZE, y = y0 + (i / perRow()) * ItemPicker.SIZE;
                ItemPicker.drawSlot(g, ctx.kind(), x, y, slots.get(i), i == hovered || dragged.contains(i), false);
                if (picker.isList() && i == slots.size() - 1 && slots.get(i).isEmpty()) ItemPicker.drawAddMark(g, ctx.kind(), x, y);
                if (i == selected) g.outline(x, y, ItemPicker.SIZE, ItemPicker.SIZE, 0xFFFFFFFF);
            }
            if (hovered >= 0 && carried.isEmpty() && !slots.get(hovered).isEmpty()) {
                ItemPicker.itemTooltip(g, new ItemMatch(slots.get(hovered), matches.get(hovered)), picker.match(),
                        ctx.mouseX(), ctx.mouseY());
            }
        }

        /** What the cursor holds, at the mouse, over the whole dialog. */
        @Override
        public void renderOverlay(RenderContext ctx) {
            if (carried.isEmpty() || !ctx.hasMouseInput()) return;
            GuiGraphicsExtractor g = ctx.graphics();
            g.fakeItem(carried, ctx.mouseX() - 8, ctx.mouseY() - 8);
            g.itemDecorations(Minecraft.getInstance().font, carried, ctx.mouseX() - 8, ctx.mouseY() - 8);
        }

        /** Every click on the screen passes here first: one off the row and the grid drops what is held. */
        @Override
        public void notifyClickOutsideOverlay(InputContext in) {
            clickAnywhere(in);
        }

        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if (disabled(in)) return false;
            int i = slotAt(in);
            if (i < 0) return false;
            if (picker.hasEditTab() && tab.equals("edit")) {
                // The Edit tab edits the selected slot: a click only picks which.
                select(i);
                return true;
            }
            if (!carried.isEmpty() && (button == Click.LEFT || button == Click.RIGHT)) {
                // Holding something: a press starts a drag; its release decides.
                select(i);
                dragged.clear();
                dragged.add(i);
                dragButton = button;
                return true;
            }
            clickSlot(i, button);
            return true;
        }

        @Override
        public boolean mouseReleased(InputContext in, int button) {
            if (dragButton < 0 || button != dragButton) return false;
            endDrag();
            return true;
        }
    }

    // The Search tab's grid

    /**
     * The entries matching the search, nine to a row, as slots in the kind of screen.
     * Asks for its nine columns' width; given more, it centres them. As tall as its
     * rows, read every frame, so the scroll area around it follows the search. Draws
     * only the rows on screen.
     */
    private final class Grid extends AbstractPanelElement {

        Grid() {
            this.width = this.authoredWidth = GRID_WIDTH;
        }

        private List<ItemStack> shown() {
            return entries(query);
        }

        private int rows() {
            return (shown().size() + COLUMNS - 1) / COLUMNS;
        }

        /** The columns' left edge from the grid's: centred in whatever width it was given. */
        int left() {
            return Math.max(0, (width - GRID_WIDTH) / 2);
        }

        @Override public int getHeight() { return Math.max(1, rows()) * ItemPicker.SIZE; }
        @Override public void layoutWithin(int budget) { width = Math.max(GRID_WIDTH, budget); }
        @Override public void fillWidth(int width) {}
        @Override public boolean isInteractive() { return true; }

        private @Nullable ItemStack entryAt(int ox, int oy, double mx, double my) {
            double lx = mx - (ox + childX + left()), ly = my - (oy + childY);
            if (lx < 0 || ly < 0 || lx >= GRID_WIDTH) return null;
            int i = (int) (ly / ItemPicker.SIZE) * COLUMNS + (int) (lx / ItemPicker.SIZE);
            List<ItemStack> shown = shown();
            return i < shown.size() ? shown.get(i) : null;
        }

        @Override
        public void render(RenderContext ctx) {
            GuiGraphicsExtractor g = ctx.graphics();
            int gx = ctx.originX() + childX + left(), gy = ctx.originY() + childY;
            List<ItemStack> shown = shown();
            if (shown.isEmpty()) {
                g.text(Minecraft.getInstance().font, "No items match", gx + 2, gy + 5,
                        ctx.kind().textColor(TextRole.MUTED), ctx.kind().textShadow());
                return;
            }
            // Hover only where the grid shows: the scroll area clips it.
            ItemStack hovered = ctx.hasMouseInput() && g.containsPointInScissor(ctx.mouseX(), ctx.mouseY())
                    ? entryAt(ctx.originX(), ctx.originY(), ctx.mouseX(), ctx.mouseY()) : null;
            int first = Math.max(0, -gy / ItemPicker.SIZE);
            int last = Math.min(rows() - 1, (g.guiHeight() - gy) / ItemPicker.SIZE);
            for (int row = first; row <= last; row++) {
                for (int col = 0; col < COLUMNS; col++) {
                    int i = row * COLUMNS + col;
                    if (i >= shown.size()) break;
                    ItemStack entry = shown.get(i);
                    ItemPicker.drawSlot(g, ctx.kind(), gx + col * ItemPicker.SIZE, gy + row * ItemPicker.SIZE,
                            entry, entry == hovered, false);
                }
            }
            if (hovered != null && carried.isEmpty()) {
                g.setTooltipForNextFrame(Minecraft.getInstance().font, hovered, ctx.mouseX(), ctx.mouseY());
            }
        }

        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if (disabled(in)) return false;
            ItemStack entry = entryAt(in.originX(), in.originY(), in.mouseX(), in.mouseY());
            if (entry == null) return false;
            clickEntry(entry, button, Minecraft.getInstance().hasShiftDown());
            return true;
        }
    }

    // The Edit tab's enchantment list

    /** A row of the enchantment list: an enchantment on the item, one that can be added, or (no choice) the heading between. */
    private record EnchantRow(@Nullable EnchantChoice choice, boolean applied) {}

    /** One control button of a row: where it is from the row's left, its glyph, whether it does anything, and what it says. */
    private record Control(int x, String glyph, boolean enabled, Component tooltip, Runnable action) {}

    /**
     * The enchantments that fit the selected stack, in two lists, one above the
     * other: those on the item, each with its level, a - and a + (from I to its
     * highest, no further) and a remove control; then, under a heading when both
     * show, those that can be added, each with an add control (on at level I, and
     * into the list above). One excluded by an enchantment on the item is greyed, its
     * add control off, its tooltip naming what it conflicts with. Asks for the room
     * its longest name, the level and the controls need; every row's controls line up
     * at the right, whatever moves between the lists. A name that still does not fit
     * (a narrow screen) is cut with an ellipsis, and its tooltip has it in full.
     */
    private final class EnchantList extends AbstractPanelElement {

        EnchantList() {
            this.height = ENCHANT_ROW;
        }

        List<EnchantRow> rows() {
            List<EnchantRow> applied = new ArrayList<>(), more = new ArrayList<>();
            for (EnchantChoice c : enchantChoices()) (c.applied() ? applied : more).add(new EnchantRow(c, c.applied()));
            List<EnchantRow> out = new ArrayList<>(applied);
            if (!applied.isEmpty() && !more.isEmpty()) out.add(new EnchantRow(null, false));
            out.addAll(more);
            return out;
        }

        /** The widest any level of any of the choices is written. */
        private int levelColumn(Font font) {
            int w = 0;
            for (EnchantChoice c : enchantChoices()) {
                for (int l = 1; l <= Math.min(c.max(), 10); l++) w = Math.max(w, font.width(levelText(l)));
            }
            return w;
        }

        /** A control's left edge, counted from the right: 0 the last (remove, or add), then + and -. */
        private int controlX(int fromRight) {
            return width - 1 - CONTROL - fromRight * (CONTROL + CONTROL_GAP);
        }

        /** The right edge of an applied row's level. */
        private int levelRight() {
            return controlX(2) - ROW_GAP;
        }

        /** The right edge of a row's name: before the level on an applied row, before the add control on the other. */
        private int nameRight(boolean applied, Font font) {
            return applied ? levelRight() - levelColumn(font) - ROW_GAP : controlX(0) - ROW_GAP;
        }

        @Override
        public int naturalWidth() {
            Font font = Minecraft.getInstance().font;
            int name = font.width(ADD_HEADING);
            for (EnchantChoice c : enchantChoices()) name = Math.max(name, font.width(c.name()));
            // The widest a row gets: an applied one, its name, level and three controls.
            return TEXT_PAD + name + ROW_GAP + levelColumn(font) + ROW_GAP + 3 * CONTROL + 2 * CONTROL_GAP + 1;
        }

        @Override public int getHeight() { return Math.max(1, rows().size()) * ENCHANT_ROW; }
        @Override public void layoutWithin(int budget) { width = Math.max(1, budget); }
        @Override public void fillWidth(int width) {}
        @Override public boolean isInteractive() { return true; }

        /** A row's controls. */
        private List<Control> controls(EnchantRow r) {
            EnchantChoice c = r.choice();
            if (c == null) return List.of();
            Holder<Enchantment> e = c.enchantment();
            if (r.applied()) {
                int l = c.level();
                return List.of(
                        new Control(controlX(2), "-", l > 1, l > 1
                                ? Component.literal("Lower to ").append(levelText(l - 1))
                                : Component.literal("Lowest level"), () -> stepEnchant(e, -1)),
                        new Control(controlX(1), "+", l < c.max(), l < c.max()
                                ? Component.literal("Raise to ").append(levelText(l + 1))
                                : Component.literal("Highest level"), () -> stepEnchant(e, 1)),
                        new Control(controlX(0), "×", true, Component.literal("Remove"), () -> removeEnchant(e)));
            }
            return List.of(new Control(controlX(0), "+", !c.blocked(),
                    c.blocked() ? conflict(c) : Component.literal("Add at level I"), () -> addEnchant(e)));
        }

        /** "Conflicts with Sharpness": what on the item excludes it. */
        private Component conflict(EnchantChoice c) {
            var out = Component.literal("Conflicts with ");
            for (int i = 0; i < c.conflicts().size(); i++) {
                if (i > 0) out.append(", ");
                out.append(c.conflicts().get(i).value().description());
            }
            return out;
        }

        /** The name's tooltip: in full and up to what level, or what it conflicts with. */
        private Component nameTooltip(EnchantChoice c) {
            if (c.blocked()) return conflict(c);
            return Component.empty().append(c.name()).append(", up to ").append(levelText(c.max()));
        }

        /** {@code text} cut to {@code room} with an ellipsis when it would not fit. */
        private String fit(Component text, int room, Font font) {
            String s = text.getString();
            if (font.width(s) <= room) return s;
            return font.plainSubstrByWidth(s, Math.max(0, room - font.width("..."))) + "...";
        }

        @Override
        public void render(RenderContext ctx) {
            GuiGraphicsExtractor g = ctx.graphics();
            Font font = Minecraft.getInstance().font;
            ScreenKind kind = ctx.kind();
            int x = ctx.originX() + childX, y = ctx.originY() + childY;
            boolean mouse = ctx.hasMouseInput() && g.containsPointInScissor(ctx.mouseX(), ctx.mouseY());
            List<EnchantRow> rows = rows();
            for (int i = 0; i < rows.size(); i++) {
                EnchantRow r = rows.get(i);
                int ry = y + i * ENCHANT_ROW;
                int textY = Text.centeredTextY(ry, ry + ENCHANT_ROW);
                EnchantChoice c = r.choice();
                if (c == null) {
                    g.text(font, fit(ADD_HEADING, width - TEXT_PAD, font), x + TEXT_PAD, textY,
                            kind.textColor(TextRole.MUTED), kind.textShadow());
                    continue;
                }
                int color = kind.textColor(c.blocked() ? TextRole.DISABLED : TextRole.BODY);
                int nameRight = nameRight(r.applied(), font);
                g.text(font, fit(c.name(), nameRight - TEXT_PAD, font), x + TEXT_PAD, textY, color, kind.textShadow());
                if (mouse && ctx.mouseY() >= ry && ctx.mouseY() < ry + ENCHANT_ROW && ctx.mouseX() >= x + TEXT_PAD
                        && ctx.mouseX() < x + nameRight) {
                    MKTooltip.queue(g, nameTooltip(c), ctx.mouseX(), ctx.mouseY());
                }
                if (r.applied()) {
                    Component level = levelText(c.level());
                    g.text(font, level, x + levelRight() - font.width(level), textY, color, kind.textShadow());
                }
                for (Control k : controls(r)) {
                    int cx = x + k.x(), cy = ry + 1;
                    boolean over = mouse && ctx.mouseX() >= cx && ctx.mouseX() < cx + CONTROL
                            && ctx.mouseY() >= cy && ctx.mouseY() < cy + CONTROL;
                    drawControl(g, kind, cx, cy, k.glyph(), k.enabled(), over);
                    if (over) MKTooltip.queue(g, k.tooltip(), ctx.mouseX(), ctx.mouseY());
                }
            }
        }

        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if (button != Click.LEFT || disabled(in)) return false;
            double lx = in.mouseX() - (in.originX() + childX), ly = in.mouseY() - (in.originY() + childY);
            List<EnchantRow> rows = rows();
            int i = (int) Math.floor(ly / ENCHANT_ROW);
            if (lx < 0 || ly < 0 || i >= rows.size()) return false;
            double cy = ly - i * ENCHANT_ROW - 1;
            for (Control k : controls(rows.get(i))) {
                if (lx < k.x() || lx >= k.x() + CONTROL || cy < 0 || cy >= CONTROL) continue;
                if (!k.enabled()) return true;
                ElementWidget.playClickSound();
                k.action().run();
                return true;
            }
            return false;
        }

        /** Each row's name, level and controls, with their glyphs, inside the room they have. */
        void boxes(List<ItemPicker.Box> out, int ox, int oy, Font font) {
            List<EnchantRow> rows = rows();
            for (int i = 0; i < rows.size(); i++) {
                EnchantRow r = rows.get(i);
                int ry = oy + i * ENCHANT_ROW;
                int[] rowRect = {ox, ry, width, ENCHANT_ROW};
                EnchantChoice c = r.choice();
                if (c == null) {
                    String s = fit(ADD_HEADING, width - TEXT_PAD, font);
                    out.add(box("heading \"" + s + "\"", new int[]{ox + TEXT_PAD, ry, font.width(s), ENCHANT_ROW}, rowRect));
                    continue;
                }
                int nameRight = nameRight(r.applied(), font);
                String s = fit(c.name(), nameRight - TEXT_PAD, font);
                out.add(box("name \"" + s + "\"", new int[]{ox + TEXT_PAD, ry, font.width(s), ENCHANT_ROW},
                        new int[]{ox, ry, nameRight, ENCHANT_ROW}));
                if (r.applied()) {
                    Component level = levelText(c.level());
                    int col = levelColumn(font);
                    out.add(box("level " + level.getString() + " of " + s,
                            new int[]{ox + levelRight() - font.width(level), ry, font.width(level), ENCHANT_ROW},
                            new int[]{ox + levelRight() - col, ry, col, ENCHANT_ROW}));
                }
                for (Control k : controls(r)) {
                    int[] at = {ox + k.x(), ry + 1, CONTROL, CONTROL};
                    out.add(box("control " + k.glyph() + " of " + s, at, rowRect));
                    int gw = font.width(k.glyph());
                    out.add(box("glyph " + k.glyph() + " of " + s,
                            new int[]{at[0] + (CONTROL - gw) / 2, at[1] + 2, gw, font.lineHeight - 1}, at));
                }
            }
        }
    }

    /** A control button: vanilla's button on an options screen, MenuKit's raised one (dark when off) on a container's. */
    private static void drawControl(GuiGraphicsExtractor g, ScreenKind kind, int x, int y, String glyph, boolean enabled, boolean hovered) {
        if (kind == ScreenKind.OPTIONS) {
            VanillaLook.button(g, x, y, CONTROL, CONTROL, enabled, hovered && enabled);
        } else if (!enabled) {
            PanelRendering.renderPanel(g, x, y, CONTROL, CONTROL, PanelStyle.DARK);
        } else {
            PanelRendering.renderPanel(g, x, y, CONTROL, CONTROL, PanelStyle.RAISED);
            if (hovered) g.fill(x + 1, y + 1, x + CONTROL - 1, y + CONTROL - 1, ElementConstants.HOVER_OVERLAY);
        }
        int color = enabled ? ElementConstants.TEXT_LIGHT : kind.textColor(TextRole.DISABLED);
        Text.renderCentered(g, Component.literal(glyph), x, y, CONTROL, CONTROL, color, true);
    }
}
