package com.trevlar.menukit.api.element;

import com.trevlar.menukit.core.ControlLook;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.DialogLayout;
import com.trevlar.menukit.core.ItemEditing;
import com.trevlar.menukit.core.ItemEditing.EnchantChoice;
import com.trevlar.menukit.core.ItemSources;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * The dialog an {@link ItemPicker} opens, one panel top to bottom: the title, the slot
 * row, a search box over a grid of every item, an Advanced section with the options the
 * picker turns on (closed until opened), and the buttons. Built of MenuKit's own elements
 * ({@link TextField}, {@link ScrollContainer}, {@link Section}, {@link Slider},
 * {@link Checkbox}, {@link Button}) plus the slot row, the grid and the enchantment list
 * drawn here, laid out as every MenuKit dialog is ({@link DialogLayout}), so it draws in
 * the kind of screen it opens on like any dialog. Package-private: consumers reach it
 * through {@link ItemPicker}.
 *
 * <p>Its width comes from its content: the grid asks for its nine columns, its scroll
 * area adds the band's inset and the scrollbar lane ({@link ScrollContainer.Builder#fitWidth}),
 * and the search box and the other fields fill whatever that makes. Its height is the
 * grid's five rows and whatever Advanced shows; on a screen too short for that the grid
 * gives rows back, down to {@link #MIN_ROWS} ({@link ScrollContainer.Builder#fitRoom(int, int)}),
 * and past that the dialog scrolls.
 *
 * <p>The value lives with the consumer, through the picker's lens, written on every
 * change. The dialog keeps its view state: the slots as the row shows them (a list's
 * with its empty slot to add to at the end), each slot's count, the selected slot,
 * whether Advanced is open, the search, and the fields' text as typed.
 *
 * <p>A click on a grid entry puts it straight into the selected slot, which stays
 * selected. A filled slot's small remove button, or a right click, empties it (a list
 * drops it, the rest shifting left). Nothing comes from or goes to the player's inventory.
 */
final class ItemDialog {

    private static final AtomicLong IDS = new AtomicLong();

    /** The grid: a creative inventory's nine columns and five rows, down to two on a short screen. */
    static final int COLUMNS = 9;
    static final int ROWS = 5;
    static final int MIN_ROWS = 2;
    static final int GRID_WIDTH = COLUMNS * ItemPicker.SIZE;
    static final int FIELD_HEIGHT = 20;
    static final int GAP = DialogLayout.GAP;
    /** The least a filling field asks for, so a dialog with little else in it is still usable. */
    static final int FIELD_MIN_WIDTH = 60;

    /**
     * The enchantment list: a control button's size, a row (a control and a pixel
     * above and below it), and the rows on show before the list scrolls.
     */
    static final int CONTROL = 12;
    static final int ENCHANT_ROW = CONTROL + 2;
    static final int ENCHANT_ROWS = 4;
    static final int MIN_ENCHANT_ROWS = 2;
    /** In a row: the name's room from the left, the gap after a name or a level, and between controls. */
    static final int TEXT_PAD = 2;
    static final int ROW_GAP = 4;
    static final int CONTROL_GAP = 1;

    /**
     * The hotbar's selection, around the selected slot of the row (vanilla's Gui draws it
     * 24 x 23, 4 px out from the item's top-left), so with several slots it reads which one
     * Advanced edits and the grid fills.
     */
    static final Identifier SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");
    static final int SELECTION_W = 24, SELECTION_H = 23, SELECTION_OUT = 4;

    static final Component SEARCH_HINT = Component.literal("Search items");
    static final Component NAME_HINT = Component.literal("Name");
    /** How the components box says what it is for, while empty: short enough to fit it. */
    static final Component COMPONENTS_HINT = Component.literal("More: [unbreakable={}]");
    /** The longer how-to, on hover. */
    static final Component COMPONENTS_TOOLTIP = Component.literal(
            "Any other components, as in /give: [unbreakable={},lore=[\"Hi\"]]. Put ! before one for \"must not have it\": [!food].");
    static final Component FULL_STACK = Component.literal("Full stack");
    /** Advanced with an empty slot selected. */
    static final Component EMPTY_HINT = Component.literal("Pick an item first.");
    /** Lang keys, so a resource pack can say them its way. */
    static final Component ADVANCED = Component.translatable("menukit.item_picker.advanced");
    static final Component ADDED_HEADING = Component.translatable("menukit.item_picker.added");
    static final Component NOT_ADDED_HEADING = Component.translatable("menukit.item_picker.not_added");

    private final ItemPicker picker;
    private final ItemPicker.Options options;
    private final Panel panel;
    private final PanelElement title, row, buttons;
    private final TextField search;
    private final Grid grid;
    private final ScrollContainer gridBox;
    private final @Nullable Section advancedBox;
    private final @Nullable TextField nameField, componentsField;
    private final @Nullable EnchantList enchants;
    private final @Nullable ScrollContainer enchantBox;
    /** Advanced's column, and everything in it, top to bottom (rows and what is in them). */
    private final @Nullable PanelElement column;
    private final List<PanelElement> advancedParts = new ArrayList<>();

    // View state
    private boolean open = false;
    /** Opened and not yet drawn: the first frame takes focus for the search box ({@link ItemPicker#focusSearch}). */
    private boolean focusPending = false;
    private List<ItemMatch> before = List.of();
    /** The slots as the row shows them, empties included; each one's count beside it. */
    private final List<ItemStack> slots = new ArrayList<>();
    private final List<Integer> counts = new ArrayList<>();
    private int selected = 0;
    private boolean advanced = false;
    private String query = "";
    private double gridScroll = 0, enchantScroll = 0;
    private final Draft name = new Draft(), components = new Draft();
    /** The selected stack the enchantment choices were last made for, and the choices. */
    private @Nullable ItemStack choicesFor;
    private List<EnchantChoice> choices = List.of();

    ItemDialog(ItemPicker picker) {
        this.picker = picker;
        this.options = picker.options();

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
        // centered grid never shifts sideways as you type. Five rows, whatever the search
        // shows, so the dialog keeps its size as you type; fewer on a short screen.
        this.grid = new Grid();
        this.gridBox = ScrollContainer.builder().fitWidth().steadyLane().height(ROWS * ItemPicker.SIZE)
                .fitRoom(ItemPicker.SIZE, MIN_ROWS * ItemPicker.SIZE)
                .content(List.of(grid))
                .state(() -> gridScroll, v -> gridScroll = v)
                .build();

        this.title = DialogLayout.centered(TextLabel.builder().text(picker.label()).build());
        this.row = new SlotRow();
        List<Button> all = new ArrayList<>(options.buttons());
        all.add(DialogLayout.button(Component.literal("Cancel"), picker::cancel));
        all.add(DialogLayout.button(Component.literal("Done"), picker::done));
        this.buttons = DialogLayout.buttons(all.toArray(Button[]::new));

        String id = "menukit:item-dialog-" + IDS.incrementAndGet();
        if (!options.any()) {
            // Nothing to fill in past the item: no Advanced section.
            this.nameField = this.componentsField = null;
            this.enchants = null;
            this.enchantBox = null;
            this.column = null;
            this.advancedBox = null;
            this.panel = DialogLayout.panel(id, picker::cancel, title, row, search, gridBox, buttons)
                    .visibleWhen(this::isOpen);
            return;
        }

        Flow.Builder col = Flow.builder().at(0, 0).vertical().gap(GAP, GAP);
        TextLabel emptyHint = TextLabel.builder().role(TextRole.MUTED).text(EMPTY_HINT).fill()
                .visibleWhen(() -> !hasItem()).build();
        col.add(emptyHint);
        advancedParts.add(emptyHint);

        if (options.count()) {
            // The count: a slider over the setting's range for this item, and beside it,
            // where the setting offers it, "Full stack", which stands for the item's stack
            // size whatever it is. Not shown for an item whose range is one number.
            Checkbox full = options.fullStack() ? Checkbox.builder().label(FULL_STACK)
                    .state(() -> currentCount() == ItemMatch.STACK, this::setFullStack).build() : null;
            Slider<Double> slider = new Slider<>(Slider.builder().size(FIELD_MIN_WIDTH, FIELD_HEIGHT)
                    .state(this::countFraction, this::setCountFraction)
                    .label(f -> ItemPicker.countText(new ItemMatch(current(), Math.max(0, currentCount()))))
                    .disabledWhen(() -> currentCount() == ItemMatch.STACK)) {
                @Override
                public void layoutWithin(int budget) {
                    width = Math.max(1, budget - (full != null ? full.getWidth() + GAP : 0));
                }
            };
            Flow.Builder countRow = Flow.builder().gap(GAP, GAP).visibleWhen(this::countShown).add(slider);
            if (full != null) countRow.add(full);
            Flow built = countRow.build();
            col.add(built);
            advancedParts.addAll(List.of(built, slider));
            if (full != null) advancedParts.add(full);
        }

        if (options.name()) {
            this.nameField = TextField.builder().size(FIELD_MIN_WIDTH, FIELD_HEIGHT).fill()
                    .maxLength(50)   // an anvil's limit
                    .hint(NAME_HINT)
                    .label(NAME_HINT)
                    .visibleWhen(this::hasItem)
                    .state(() -> name.text(current(), selected, ItemEditing::name), this::typeName)
                    .build();
            col.add(nameField);
            advancedParts.add(nameField);
        } else {
            this.nameField = null;
        }

        if (options.enchantments()) {
            // Those on the item, then the ones that can be added, in one list as tall as
            // its rows up to ENCHANT_ROWS, then it scrolls.
            this.enchants = new EnchantList();
            // On a screen too short for the dialog it gives rows back after the grid has,
            // down to MIN_ENCHANT_ROWS, so the fields under it stay on the screen.
            this.enchantBox = ScrollContainer.builder().fitWidth().height(ENCHANT_ROWS * ENCHANT_ROW).fitHeight()
                    .fitRoom(ENCHANT_ROW, MIN_ENCHANT_ROWS * ENCHANT_ROW)
                    .content(List.of(enchants))
                    .state(() -> enchantScroll, v -> enchantScroll = v)
                    .visibleWhen(() -> hasItem() && !enchantChoices().isEmpty())
                    .build();
            col.add(enchantBox);
            advancedParts.add(enchantBox);
        } else {
            this.enchants = null;
            this.enchantBox = null;
        }

        if (options.components()) {
            this.componentsField = TextField.builder().size(FIELD_MIN_WIDTH, FIELD_HEIGHT).fill()
                    .maxLength(4096)
                    .hint(COMPONENTS_HINT)
                    .label(Component.literal("Components"))
                    .tooltip(COMPONENTS_TOOLTIP)
                    .visibleWhen(this::hasItem)
                    .state(() -> components.text(current(), selected,
                            s -> ItemEditing.componentsText(s, ItemSources.registries())), this::typeComponents)
                    .build();
            TextLabel error = TextLabel.builder().fill()
                    .text(() -> components.error != null ? components.error : Component.empty())
                    .visibleWhen(() -> hasItem() && components.error != null).build();
            col.add(componentsField).add(error);
            advancedParts.addAll(List.of(componentsField, error));
        } else {
            this.componentsField = null;
        }

        for (PanelElement extra : options.advanced()) {
            col.add(extra);
            advancedParts.add(extra);
        }
        this.column = col.build();
        this.advancedBox = Section.builder(ADVANCED)
                .state(() -> advanced, v -> advanced = v)
                .add(column)
                .build();
        this.panel = DialogLayout.panel(id, picker::cancel, title, row, search, gridBox, advancedBox, buttons)
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
     * slot to add to after it, the search cleared, Advanced open when that slot has a
     * field filled in.
     */
    void open(int slot) {
        before = picker.value();
        slots.clear();
        counts.clear();
        for (ItemMatch m : before) {
            slots.add(m.stack().copy());
            counts.add(m.count());
        }
        if (picker.isList() || slots.isEmpty()) {
            slots.add(ItemStack.EMPTY);
            counts.add(options.newCount());
        }
        selected = Math.max(0, Math.min(slot, slots.size() - 1));
        advanced = !current().getComponentsPatch().isEmpty();
        query = "";
        gridScroll = 0;
        enchantScroll = 0;
        name.reset();
        components.reset();
        open = true;
        focusPending = true;
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

    boolean advanced() {
        return advanced;
    }

    void setAdvanced(boolean open) {
        advanced = open;
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

    /** The selected slot's count, as stored: a number, or {@link ItemMatch#STACK}. */
    private int currentCount() {
        return selected < counts.size() ? counts.get(selected) : options.newCount();
    }

    /** Whether the count row shows: an item is selected, and its range is more than one number. */
    private boolean countShown() {
        return hasItem() && options.most(current()) > options.countLeast();
    }

    /** {@code n} inside the count's range for the selected item. */
    private int clampCount(int n, ItemStack stack) {
        return Math.max(options.countLeast(), Math.min(options.most(stack), n));
    }

    /** The slider's place: the count (a full stack at the far end) along the range. */
    private double countFraction() {
        int least = options.countLeast(), most = options.most(current());
        if (most <= least) return 0;
        int n = currentCount() == ItemMatch.STACK ? most : clampCount(currentCount(), current());
        return (n - least) / (double) (most - least);
    }

    private void setCountFraction(double f) {
        int least = options.countLeast(), most = options.most(current());
        setCount(least + (int) Math.round(f * (most - least)));
    }

    /** The slider: the selected slot's count, inside its range. */
    void setCount(int n) {
        if (selected >= counts.size() || !hasItem()) return;
        counts.set(selected, clampCount(n, current()));
        commit();
    }

    /** "Full stack": ticked, the count is {@link ItemMatch#STACK}; cleared, the far end of the slider. */
    void setFullStack(boolean full) {
        if (selected >= counts.size() || !hasItem()) return;
        counts.set(selected, full ? ItemMatch.STACK : options.most(current()));
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
            counts.add(options.newCount());
        }
        commit();
    }

    /** The slots that hold something, in order, each with its count, through the lens. */
    private void commit() {
        List<ItemMatch> out = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (!slots.get(i).isEmpty()) out.add(new ItemMatch(slots.get(i), counts.get(i)));
        }
        picker.write(out);
    }

    // Search

    /** The grid's entries for {@code q}: every item, with its variants unless the picker keeps only the kind. */
    List<ItemStack> entries(String q) {
        return ItemSources.search(picker.kindsOnly(), q);
    }

    /** Enter in the search box: the first match into the selected slot, as a click on it would. */
    private void putFirstMatch() {
        List<ItemStack> shown = entries(query);
        if (!shown.isEmpty()) fill(shown.get(0));
    }

    // Filling and emptying slots

    /**
     * A click on a grid entry: it into the selected slot, replacing what was there, its
     * count kept inside the new item's range. The slot stays selected, so another click
     * swaps the item; a list grows a new empty slot at its end, to select and fill next.
     */
    void fill(ItemStack entry) {
        if (selected >= slots.size()) return;
        ElementWidget.playClickSound();
        ItemStack one = entry.copyWithCount(1);
        slots.set(selected, one);
        int n = counts.get(selected);
        if (options.count() && n != ItemMatch.STACK) counts.set(selected, clampCount(n, one));
        changed();
    }

    /**
     * Slot {@code i}'s remove button: a list drops the entry, the ones after it
     * shifting left in their order, and the selection follows the slot it was on; a
     * single picker's slot is emptied, back to a new slot's count.
     */
    void remove(int i) {
        if (i < 0 || i >= slots.size() || slots.get(i).isEmpty()) return;
        ElementWidget.playClickSound();
        if (picker.isList()) {
            slots.remove(i);
            counts.remove(i);
            if (selected > i) selected--;
        } else {
            slots.set(i, ItemStack.EMPTY);
            counts.set(i, options.newCount());
        }
        changed();
        selected = Math.min(selected, slots.size() - 1);
    }

    // Advanced's fields

    private void typeName(String text) {
        ItemStack after = ItemEditing.withName(current(), text);
        replaceCurrent(after);
        name.typed(text, after, selected, null);
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
            case "components" -> typeComponents(text);
            default -> throw new IllegalArgumentException("no Advanced field " + field);
        }
    }

    String editText(String field) {
        return switch (field) {
            case "name" -> name.text(current(), selected, ItemEditing::name);
            case "components" -> components.text(current(), selected,
                    s -> ItemEditing.componentsText(s, ItemSources.registries()));
            default -> throw new IllegalArgumentException("no Advanced field " + field);
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
        EditBox box = componentsField != null ? componentsField.widget() : null;
        if (box == null) return;
        box.setTextColor(components.error == null ? TextField.TEXT_VALID : TextField.TEXT_INVALID);
        if (components.error != null && !box.isFocused()) components.reset();
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

    // Advanced's enchantments

    /** The enchantments that apply to the selected stack, made again only when it changes. */
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
     * Each box the dialog draws, in its content's coordinates, with the box it must stay
     * inside: every row in the dialog, each slot in the row (a filled one's remove button
     * in its corner, the selected one's selection frame in the dialog), the grid's columns
     * in the grid's viewport, a field's hint inside the field's text area, and, while
     * Advanced is open, everything shown in it inside the section, and each enchantment
     * row's name, level and controls inside their room in the row. {@code kind} is the
     * screen it is laid out for (the band's inset).
     */
    List<ItemPicker.Box> boxes(ScreenKind kind) {
        Font font = Minecraft.getInstance().font;
        List<ItemPicker.Box> out = new ArrayList<>();
        List<PanelElement> rows = new ArrayList<>(List.of(title, row, search, gridBox, buttons));
        if (advancedBox != null) rows.add(advancedBox);
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
            if (i == selected) {
                int out4 = SELECTION_OUT - SlotRendering.ITEM_INSET;
                out.add(box("selection", new int[]{slot[0] - out4, slot[1] - out4, SELECTION_W, SELECTION_H}, dialog));
            }
        }

        int[] s = rect(search, 0, 0);
        out.add(hint("search", SEARCH_HINT, s, font));
        int[] gb = rect(gridBox, 0, 0);
        int inset = ScrollContainer.bandInsetX(kind, true);
        out.add(box("grid columns", new int[]{gb[0] + inset + grid.left(), gb[1], GRID_WIDTH, gb[3]},
                new int[]{gb[0], gb[1], gridBox.viewportWidth(), gb[3]}));
        if (advancedBox == null || !advanced || column == null) return out;

        // Advanced's column in the section, and each shown thing in it.
        int bx = advancedBox.getChildX(), by = advancedBox.getChildY() + Section.HEADER_HEIGHT + Section.CONTENT_GAP;
        int[] body = {advancedBox.getChildX(), by, advancedBox.getWidth(), advancedBox.getHeight() - Section.HEADER_HEIGHT - Section.CONTENT_GAP};
        out.add(box("advanced", rect(column, bx, by), body));
        for (PanelElement e : advancedParts) {
            if (!e.isVisible() || !shownIn(e)) continue;
            int[] at = rect(e, bx, by);
            out.add(box(describe(e), at, body));
            if (e == componentsField) out.add(hint("components", COMPONENTS_HINT, at, font));
            if (e == nameField) out.add(hint("name", NAME_HINT, at, font));
        }
        if (enchantBox != null && enchants != null && enchantBox.isVisible()) {
            int[] eb = rect(enchantBox, bx, by);
            int insetX = ScrollContainer.bandInsetX(kind, true), insetY = ScrollContainer.bandInsetY(kind, true);
            int ox = eb[0] + insetX, oy = eb[1] + insetY;
            out.add(box("enchantment rows", new int[]{ox, eb[1], enchants.getWidth(), eb[3]},
                    new int[]{eb[0], eb[1], enchantBox.viewportWidth(), eb[3]}));
            enchants.boxes(out, ox, oy, font);
        }
        return out;
    }

    /** Whether an Advanced part is on show: it and the row it is in (a slider in a hidden row is not). */
    private boolean shownIn(PanelElement e) {
        for (PanelElement p : advancedParts) {
            if (p instanceof Flow f && f.contains(e) && !p.isVisible()) return false;
        }
        return true;
    }

    private static String describe(PanelElement e) {
        if (e instanceof TextLabel l) return "label \"" + l.getCurrentText().getString() + "\"";
        if (e instanceof TextField) return "field";
        return e.getClass().getSimpleName().isEmpty() ? e.getClass().getSuperclass().getSimpleName() : e.getClass().getSimpleName();
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
     * The setting's slots, nine to a row, centered: the selected one framed by the
     * hotbar's selection, a list's last (empty) one marked with a plus, each filled one
     * with its remove button in the top-right corner. A click on a slot selects it (the
     * grid fills it, Advanced edits it); a click on a remove button, or a right click,
     * empties that slot. Asks for its slots' width.
     */
    private final class SlotRow extends AbstractPanelElement {

        SlotRow() {
            this.height = ItemPicker.SIZE;
        }

        private int rows() {
            return Math.max(1, (slots.size() + COLUMNS - 1) / COLUMNS);
        }

        /** Left edge of the slots, from the row's: centered under the title. */
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
            if (focusPending) {
                focusPending = false;
                picker.focusSearch();
            }
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
            }
            // The selection over the slots, as the hotbar draws it over its own, then the
            // remove buttons over that, so each stays whole and clickable.
            if (selected < slots.size()) {
                int out = SELECTION_OUT - SlotRendering.ITEM_INSET;
                g.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION,
                        slotX(selected, ox) - out, slotY(selected, oy) - out, SELECTION_W, SELECTION_H);
            }
            for (int i = 0; i < slots.size(); i++) {
                if (!slots.get(i).isEmpty()) ItemPicker.drawRemove(g, slotX(i, ox), slotY(i, oy), i == hovered && onRemove);
            }
            if (onRemove) {
                MKTooltip.queue(g, ItemPicker.REMOVE_TOOLTIP, ctx.mouseX(), ctx.mouseY());
            } else if (hovered >= 0 && !slots.get(hovered).isEmpty()) {
                ItemPicker.itemTooltip(g, new ItemMatch(slots.get(hovered), counts.get(hovered)), options,
                        ctx.mouseX(), ctx.mouseY());
            }
        }

        /** Left click: on a filled slot's remove button, empties it; anywhere else on a slot, selects it. Right click empties it. */
        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if ((button != Click.LEFT && button != Click.RIGHT) || disabled(in)) return false;
            int i = slotAt(in.mouseX(), in.mouseY(), in.originX(), in.originY());
            if (i < 0) return false;
            if (button == Click.RIGHT || onRemove(i, in.mouseX(), in.mouseY(), in.originX(), in.originY())) remove(i);
            else select(i);
            return true;
        }
    }

    // The grid

    /**
     * The entries matching the search, nine to a row, as slots in the kind of screen.
     * Asks for its nine columns' width; given more, it centers them. As tall as its
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

        /** The columns' left edge from the grid's: centered in whatever width it was given. */
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
                        textColor(ctx, TextRole.MUTED), ctx.kind().textShadow());
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

        /** Left click on an entry: it into the selected slot. */
        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if (button != Click.LEFT || disabled(in)) return false;
            ItemStack entry = entryAt(in.originX(), in.originY(), in.mouseX(), in.mouseY());
            if (entry == null) return false;
            fill(entry);
            return true;
        }
    }

    // Advanced's enchantment list

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
     * The enchantments that apply to the selected stack, in two lists, one above the
     * other, each under its heading while it has any: "Added", those on the item, each
     * with its level, minus and plus controls (from I to its highest) and a remove
     * control; then "Not Added", those that can be added, each with an add control
     * (on at level I, and into the list above). One excluded by an enchantment on
     * the item is grayed, its add control off, its tooltip naming what it conflicts
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
                            textColor(ctx, TextRole.MUTED), kind.textShadow());
                    continue;
                }
                int color = c.blocked() ? kind.textColor(TextRole.DISABLED) : textColor(ctx, TextRole.BODY);
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

    /**
     * A control button: a raised control (vanilla's button in the Default look, MenuKit's
     * control art in the Panel look or vanilla's where a pack restyled it), its disabled face
     * while off, its glyph in vanilla's button text colors on vanilla's button.
     */
    private static void drawControl(GuiGraphicsExtractor g, ScreenKind screen, int x, int y, String glyph, boolean enabled, boolean hovered) {
        ScreenKind kind = ControlLook.kind(screen, ControlLook.Control.BUTTON);
        ControlLook.drawRaised(screen, g, x, y, CONTROL, CONTROL, enabled, hovered, ControlLook.Press.NONE);
        int color = enabled ? ElementConstants.TEXT_LIGHT : kind.textColor(TextRole.DISABLED);
        Text.renderCentered(g, Component.literal(glyph), x, y, CONTROL, CONTROL, color, true);
    }
}
