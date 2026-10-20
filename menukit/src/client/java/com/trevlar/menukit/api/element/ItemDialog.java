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
 * cut off and nothing leaves an empty margin, in either look.
 *
 * <p>The value lives with the consumer, through the picker's lens, written on every
 * change. The dialog keeps its view state: the slots as the row shows them (a list's
 * with its empty slot to add to at the end), each slot's match, the selected slot,
 * the tab, the search, and the Edit tab's text as typed.
 *
 * <p>A click on a grid entry puts one of it straight into the selected slot, which
 * stays selected; the Edit tab's count sets more. A filled slot's small remove
 * button empties it (a list drops it, the rest shifting left). Nothing comes from or
 * goes to the player's inventory.
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

    static final Component SEARCH_HINT = Component.literal("Search items");
    static final Component NAME_HINT = Component.literal("Name");
    /** How the components box says what it is for, while empty: short enough to fit it. */
    static final Component COMPONENTS_HINT = Component.literal("More: [unbreakable={}]");
    /** The longer how-to, on hover. */
    static final Component COMPONENTS_TOOLTIP = Component.literal(
            "Any other components, as in /give: [unbreakable={},lore=[\"Hi\"]]. Put ! before one to remove it: [!food].");
    /** The Edit tab with an empty slot selected. */
    static final Component EMPTY_HINT = Component.literal("Pick an item in Search first.");
    /** Above the enchantments on the item, and above the ones that are not (lang keys, so a resource pack can say them its way). */
    static final Component ADDED_HEADING = Component.translatable("menukit.item_picker.added");
    static final Component NOT_ADDED_HEADING = Component.translatable("menukit.item_picker.not_added");

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
                // Enter puts the first match in the selected slot, as a click on it
                // would, so typing a name and Enter fills it.
                .onSubmit(q -> putFirstMatch())
                .build();
        // As wide as the grid's nine columns, the band's inset and the scrollbar lane. The
        // lane stays while a search's results fit, as the creative inventory's does, so the
        // centred grid never shifts sideways as you type.
        this.grid = new Grid();
        this.gridBox = ScrollContainer.builder().at(0, FIELD_HEIGHT + GAP).fitWidth().steadyLane().height(ROWS * ItemPicker.SIZE)
                .content(List.of(grid))
                .state(() -> gridScroll, v -> gridScroll = v)
                .build();

        this.title = DialogLayout.centred(TextLabel.builder().text(picker.label()).build());
        this.row = new SlotRow();
        this.buttons = DialogLayout.cancelDone(picker::cancel, picker::done);

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
     * slot to add to after it, the Search tab, the search cleared.
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
        tab = "search";
        query = "";
        gridScroll = 0;
        enchantScroll = 0;
        name.reset();
        count.reset();
        components.reset();
        open = true;
    }

    /** Closed (Done, Cancel, a new screen). */
    void close() {
        open = false;
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

    /** Whether the selected item stacks and the picker compares counts, so its count can be set. */
    private boolean stacks() {
        return hasItem() && current().getMaxStackSize() > 1 && !picker.countIgnored();
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

    // Search

    /** The grid's entries for {@code q}: every item, with its variants unless the picker keeps only the kind. */
    List<ItemStack> entries(String q) {
        return ItemSources.search(picker.match() == Match.ITEM, q);
    }

    /** Enter in the search box: the first match into the selected slot, as a click on it would. */
    private void putFirstMatch() {
        List<ItemStack> shown = entries(query);
        if (!shown.isEmpty()) fill(shown.get(0));
    }

    // Filling and emptying slots

    /**
     * A click on a grid entry: one of it into the selected slot, replacing what was
     * there (the Edit tab's count sets more; with {@code ITEM} there is no count). The
     * slot stays selected, so another click swaps the item; a list grows a new empty
     * slot at its end, to select and fill next.
     */
    void fill(ItemStack entry) {
        if (selected >= slots.size()) return;
        ElementWidget.playClickSound();
        slots.set(selected, entry.copyWithCount(1));
        changed();
    }

    /**
     * Slot {@code i}'s remove button: a list drops the entry, the ones after it
     * shifting left in their order, and the selection follows the slot it was on; a
     * single picker's slot is emptied, back to the declared match for its next item.
     */
    void remove(int i) {
        if (i < 0 || i >= slots.size() || slots.get(i).isEmpty()) return;
        ElementWidget.playClickSound();
        if (picker.isList()) {
            slots.remove(i);
            matches.remove(i);
            if (selected > i) selected--;
        } else {
            slots.set(i, ItemStack.EMPTY);
            matches.set(i, picker.match());
        }
        changed();
        selected = Math.min(selected, slots.size() - 1);
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
        box.setTextColor(draft.error == null ? TextField.TEXT_VALID : TextField.TEXT_INVALID);
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

    /** The minus or plus control of an enchantment on the item: a level down or up, from I to its highest. */
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
     * tab in the tab's body, each slot in the row (and a filled one's remove button in
     * its corner), the grid's columns in the grid's viewport, a field's hint inside
     * the field's text area, and each enchantment row's name, level and controls
     * inside their room in the row. {@code kind} is the screen it is laid out for (the
     * band's inset).
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
            int[] slot = {r.slotX(i, 0), r.slotY(i, 0), ItemPicker.SIZE, ItemPicker.SIZE};
            out.add(box("slot " + i, slot, slotsRect));
            // A filled slot's remove button, inside the slot's corner.
            if (!slots.get(i).isEmpty()) {
                out.add(box("remove " + i, new int[]{slot[0] + ItemPicker.SIZE - ItemPicker.REMOVE_SIZE, slot[1],
                        ItemPicker.REMOVE_SIZE, ItemPicker.REMOVE_SIZE}, slot));
            }
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
            int viewport = gridBox.viewportWidth();
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
            int viewport = enchantBox.viewportWidth();
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
        int[] area = {at[0] + TextField.TEXT_INSET, at[1], Math.max(0, at[2] - 2 * TextField.TEXT_INSET), at[3]};
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
     * last (empty) one marked with a plus, each filled one with its remove button in
     * the top-right corner. A click on a slot selects it (the grid fills it, the Edit
     * tab edits it); a click on a remove button empties that slot. Asks for its slots'
     * width.
     */
    private final class SlotRow extends AbstractPanelElement {

        SlotRow() {
            this.height = ItemPicker.SIZE;
        }

        private int rows() {
            return Math.max(1, (slots.size() + COLUMNS - 1) / COLUMNS);
        }

        /** Left edge of the slots, from the row's: centred under the title. */
        int left() {
            return (getWidth() - naturalWidth()) / 2;
        }

        /** Slot {@code i}'s top-left from the row's content origin {@code (ox, oy)}. */
        int slotX(int i, int ox) {
            return ox + childX + left() + (i % COLUMNS) * ItemPicker.SIZE;
        }

        int slotY(int i, int oy) {
            return oy + childY + (i / COLUMNS) * ItemPicker.SIZE;
        }

        @Override public int naturalWidth() { return Math.min(Math.max(1, slots.size()), COLUMNS) * ItemPicker.SIZE; }
        @Override public int getWidth() { return Math.max(width, naturalWidth()); }
        @Override public int getHeight() { return rows() * ItemPicker.SIZE; }
        @Override public int extraLayoutHeight() { return getHeight() - ItemPicker.SIZE; }
        @Override public void layoutWithin(int budget) { width = budget; }
        @Override public void fillWidth(int width) {}
        @Override public boolean isInteractive() { return true; }

        private int slotAt(double mx, double my, int ox, int oy) {
            double lx = mx - (ox + childX + left()), ly = my - (oy + childY);
            if (lx < 0 || ly < 0) return -1;
            int col = (int) (lx / ItemPicker.SIZE), row = (int) (ly / ItemPicker.SIZE);
            if (col >= COLUMNS) return -1;
            int i = row * COLUMNS + col;
            return i < slots.size() ? i : -1;
        }

        /** Whether the point is on filled slot {@code i}'s remove button (its few pixels only). */
        private boolean onRemove(int i, double mx, double my, int ox, int oy) {
            return i >= 0 && !slots.get(i).isEmpty() && ItemPicker.overRemove(mx, my, slotX(i, ox), slotY(i, oy));
        }

        @Override
        public void render(RenderContext ctx) {
            checkFields();
            GuiGraphicsExtractor g = ctx.graphics();
            double mx = ctx.mouseX(), my = ctx.mouseY();
            int ox = ctx.originX(), oy = ctx.originY();
            int hovered = ctx.hasMouseInput() ? slotAt(mx, my, ox, oy) : -1;
            boolean onRemove = onRemove(hovered, mx, my, ox, oy);
            for (int i = 0; i < slots.size(); i++) {
                int x = slotX(i, ox), y = slotY(i, oy);
                ItemPicker.drawSlot(g, ctx.kind(), x, y, slots.get(i), i == hovered && !onRemove, false);
                if (picker.isList() && i == slots.size() - 1 && slots.get(i).isEmpty()) ItemPicker.drawAddMark(g, x, y);
                if (i == selected) g.outline(x, y, ItemPicker.SIZE, ItemPicker.SIZE, 0xFFFFFFFF);
                if (!slots.get(i).isEmpty()) ItemPicker.drawRemove(g, x, y, i == hovered && onRemove);
            }
            if (onRemove) {
                MKTooltip.queue(g, ItemPicker.REMOVE_TOOLTIP, ctx.mouseX(), ctx.mouseY());
            } else if (hovered >= 0 && !slots.get(hovered).isEmpty()) {
                ItemPicker.itemTooltip(g, new ItemMatch(slots.get(hovered), matches.get(hovered)), picker.match(),
                        ctx.mouseX(), ctx.mouseY());
            }
        }

        /** Left click: on a filled slot's remove button, empties it; anywhere else on a slot, selects it. */
        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if (button != Click.LEFT || disabled(in)) return false;
            int i = slotAt(in.mouseX(), in.mouseY(), in.originX(), in.originY());
            if (i < 0) return false;
            if (onRemove(i, in.mouseX(), in.mouseY(), in.originX(), in.originY())) remove(i);
            else select(i);
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
            if (hovered != null) {
                g.setTooltipForNextFrame(Minecraft.getInstance().font, hovered, ctx.mouseX(), ctx.mouseY());
            }
        }

        /** Left click on an entry: one of it into the selected slot. */
        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if (button != Click.LEFT || disabled(in)) return false;
            ItemStack entry = entryAt(in.originX(), in.originY(), in.mouseX(), in.mouseY());
            if (entry == null) return false;
            fill(entry);
            return true;
        }
    }

    // The Edit tab's enchantment list

    /**
     * A row of the enchantment list: an enchantment on the item, one that can be added,
     * or (no choice) a list's heading, "Added" ({@code applied}) or "Not Added".
     */
    private record EnchantRow(@Nullable EnchantChoice choice, boolean applied) {
        Component heading() {
            return applied ? ADDED_HEADING : NOT_ADDED_HEADING;
        }
    }

    /** One control button of a row: where it is from the row's left, its glyph, whether it does anything, and what it says. */
    private record Control(int x, String glyph, boolean enabled, Component tooltip, Runnable action) {}

    /**
     * The enchantments that fit the selected stack, in two lists, one above the
     * other, each under its heading while it has any: "Added", those on the item, each
     * with its level, minus and plus controls (from I to its highest) and a remove
     * control; then "Not Added", those that can be added, each with an add control
     * (on at level I, and into the list above). One excluded by an enchantment on
     * the item is greyed, its add control off, its tooltip naming what it conflicts
     * with. Asks for the room
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
            List<EnchantRow> out = new ArrayList<>();
            if (!applied.isEmpty()) out.add(new EnchantRow(null, true));
            out.addAll(applied);
            if (!more.isEmpty()) out.add(new EnchantRow(null, false));
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
            int name = Math.max(font.width(ADDED_HEADING), font.width(NOT_ADDED_HEADING));
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
                    g.text(font, fit(r.heading(), width - TEXT_PAD, font), x + TEXT_PAD, textY,
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
                    String s = fit(r.heading(), width - TEXT_PAD, font);
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

    /** A control button: vanilla's button in the Default look, MenuKit's raised one (dark when off) in the Panel look. */
    private static void drawControl(GuiGraphicsExtractor g, ScreenKind kind, int x, int y, String glyph, boolean enabled, boolean hovered) {
        if (kind == ScreenKind.DEFAULT) {
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
