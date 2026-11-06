package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.layout.CrossAlign;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.DialogLayout;
import com.trevlar.menukit.core.ItemEditing;
import com.trevlar.menukit.core.ItemEditing.EnchantChoice;
import com.trevlar.menukit.core.ItemSources;
import com.trevlar.menukit.inject.ItemPickerMemory;

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
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * The dialog an {@link ItemPicker} opens, one panel: the title, then the slot row, a
 * search box over a grid of every item and a "Show advanced settings" checkbox, with the
 * advanced settings the picker turns on in a second column to the right of the grid
 * while the box is ticked, then the buttons. The box is one remembered value for the
 * player ({@link ItemPickerMemory}). Built of MenuKit's own elements ({@link TextField},
 * {@link ScrollContainer}, {@link Checkbox}, {@link Slider}, {@link DropdownMulti},
 * {@link Dropdown}, {@link ListView}, {@link Button}) plus the slot row and the grid
 * drawn here, laid out as every MenuKit dialog is ({@link DialogLayout}), so it draws in
 * the kind of screen it opens on like any dialog. Package-private: consumers reach it
 * through {@link ItemPicker}.
 *
 * <p>Its width comes from its content: the grid asks for its nine columns, its scroll
 * area adds the band's inset and the scrollbar lane ({@link ScrollContainer.Builder#fitWidth}),
 * and the search box fills whatever that makes; the advanced column, when shown, adds
 * {@link #SIDE_WIDTH} beside it ({@link Flow.Builder#ownWidths()}), so the dialog grows
 * wider, not taller. Its height is the taller column; on a screen too short for that the
 * grid gives rows back, down to {@link #MIN_ROWS} ({@link ScrollContainer.Builder#fitRoom(int, int)}),
 * and past that the dialog scrolls. On a screen too narrow for both columns the
 * advanced one goes under the grid.
 *
 * <p>Enchantments are a {@link DropdownMulti} of those that fit the item (one that
 * conflicts with one picked is left out), and each one picked a row under it with a
 * level dropdown: blank for any level ({@link ItemMatch#anyLevel()}), or exactly the
 * level picked. No list in the dialog scrolls but the grid.
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

    /** The advanced column's width, and the gap between it and the grid's. */
    static final int SIDE_WIDTH = 176;
    static final int COLUMN_GAP = 2 * GAP;
    /** An enchantment row's level dropdown, and the gap between rows. */
    static final int LEVEL_W = 44, LEVEL_H = 16;
    static final int ENCHANT_GAP = 2;

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
    /** The advanced column with an empty slot selected. */
    static final Component EMPTY_HINT = Component.literal("Pick an item first.");
    static final Component ENCHANTMENTS = Component.literal("Enchantments");
    /** A level dropdown's blank: any level. Short, so its popover (as wide as the trigger) shows it whole. */
    static final Component ANY = Component.literal("Any");
    /** A lang key, so a resource pack can say it its way. */
    static final Component SHOW_ADVANCED = Component.translatable("menukit.item_picker.show_advanced");

    private final ItemPicker picker;
    private final ItemPicker.Options options;
    private final Panel panel;
    private final PanelElement title, row, buttons;
    private final TextField search;
    private final Grid grid;
    private final ScrollContainer gridBox;
    private final @Nullable Checkbox showBox;
    private final @Nullable TextField nameField, componentsField;
    private final @Nullable DropdownMulti<Holder<Enchantment>> enchantPicker;
    private final @Nullable ListView<EnchantChoice> enchantRows;
    /** The grid's column and the advanced one beside it, and the row that holds both. */
    private final @Nullable PanelElement left, column, body;
    /** Everything in the advanced column, top to bottom (rows and what is in them). */
    private final List<PanelElement> advancedParts = new ArrayList<>();
    /** Each enchantment row's name and level dropdown, by enchantment, as its list built them. */
    private final Map<Holder<Enchantment>, PanelElement[]> enchantRowParts = new HashMap<>();

    // View state
    private boolean open = false;
    /** Opened and not yet drawn: the first frame takes focus for the search box ({@link ItemPicker#focusSearch}). */
    private boolean focusPending = false;
    private List<ItemMatch> before = List.of();
    /** The slots as the row shows them, empties included, each with its count and the levels it leaves blank. */
    private final List<ItemMatch> slots = new ArrayList<>();
    private int selected = 0;
    private boolean advanced = false;
    private String query = "";
    private double gridScroll = 0;
    private final Draft name = new Draft(), components = new Draft();
    /** The selected stack the enchantment choices were last made for, and the choices. */
    private @Nullable ItemStack choicesFor;
    private List<EnchantChoice> choices = List.of();

    ItemDialog(ItemPicker picker) {
        this.picker = picker;
        this.options = picker.options();

        // The search box spans the grid's column, lined up with the grid's area under it.
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
            // Nothing to fill in past the item: no advanced settings.
            this.showBox = null;
            this.nameField = this.componentsField = null;
            this.enchantPicker = null;
            this.enchantRows = null;
            this.left = this.column = this.body = null;
            this.panel = DialogLayout.panel(id, picker::cancel, title, row, search, gridBox, buttons)
                    .visibleWhen(this::isOpen);
            return;
        }

        // The grid's column: the slot row, the search over the grid, and the box that
        // shows the advanced column beside it.
        this.showBox = Checkbox.builder().label(SHOW_ADVANCED).state(() -> advanced, this::setAdvanced).build();
        this.left = Flow.builder().vertical().gap(GAP, GAP).add(row).add(search).add(gridBox).add(showBox).build();

        Flow.Builder col = Flow.builder().vertical().gap(GAP, GAP).visibleWhen(() -> advanced);
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
            // A dropdown of the enchantments that fit the item, and each one picked a row
            // under it, with its level. Shown while the item takes any enchantment.
            this.enchantPicker = DropdownMulti.<Holder<Enchantment>>builder().size(SIDE_WIDTH, FIELD_HEIGHT)
                    .items(this::pickable)
                    .label(e -> e.value().description())
                    .triggerLabel(set -> set.isEmpty() ? ENCHANTMENTS
                            : Component.empty().append(ENCHANTMENTS).append(": " + set.size()))
                    .state(this::picked, this::setPicked)
                    .searchable()
                    .visibleWhen(() -> hasItem() && !enchantChoices().isEmpty())
                    .build();
            this.enchantRows = ListView.<EnchantChoice>builder()
                    .items(() -> hasItem() ? applied() : List.of())
                    .key(EnchantChoice::enchantment)
                    .row(c -> enchantRow(c.enchantment()))
                    .gap(ENCHANT_GAP)
                    .visibleWhen(() -> hasItem() && !applied().isEmpty())
                    .build();
            col.add(enchantPicker).add(enchantRows);
            advancedParts.addAll(List.of(enchantPicker, enchantRows));
        } else {
            this.enchantPicker = null;
            this.enchantRows = null;
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
        this.body = Flow.builder().ownWidths().gap(COLUMN_GAP, GAP).add(left).add(column).build();
        this.panel = DialogLayout.panel(id, picker::cancel, title, body, buttons)
                .visibleWhen(this::isOpen);
    }

    /**
     * One picked enchantment's row: its name, and (for one with more than one level) the
     * level dropdown, "Any" for any level, I to its highest for exactly that one.
     */
    private PanelElement enchantRow(Holder<Enchantment> e) {
        int max = e.value().getMaxLevel();
        TextLabel label = TextLabel.builder().text(e.value().description()).build();
        // A blank level only where the value can keep it (a rule; a plain stack can't).
        Dropdown<Integer> level = Dropdown.ofInts(options.blankLevels() ? 0 : 1, max).size(LEVEL_W, LEVEL_H)
                .label(n -> n == 0 ? ANY : levelText(n))
                .state(() -> enchantLevel(e), n -> setEnchantLevel(e, n))
                .visibleWhen(() -> max > 1)
                .build();
        enchantRowParts.put(e, new PanelElement[]{label, level});
        return Flow.builder().crossAlign(CrossAlign.CENTER).gap(GAP, GAP)
                .add(label).add(Flow.spacer()).add(level).build();
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
     * slot to add to after it, the search cleared, the advanced column shown when the
     * player left it shown or that slot has a field filled in.
     */
    void open(int slot) {
        before = picker.value();
        slots.clear();
        slots.addAll(before);
        if (picker.isList() || slots.isEmpty()) slots.add(emptySlot());
        selected = Math.max(0, Math.min(slot, slots.size() - 1));
        advanced = ItemPickerMemory.showAdvanced() || !current().getComponentsPatch().isEmpty();
        query = "";
        gridScroll = 0;
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
        return slots.stream().map(ItemMatch::stack).toList();
    }

    int selected() {
        return selected;
    }

    boolean advanced() {
        return advanced;
    }

    /** "Show advanced settings": the column shown or hidden, and the player's choice remembered for every picker. */
    void setAdvanced(boolean show) {
        advanced = show;
        ItemPickerMemory.setShowAdvanced(show);
    }

    void select(int slot) {
        if (slot >= 0 && slot < slots.size()) selected = slot;
    }

    // The value

    /** A slot with nothing in it, at a new slot's count. */
    private ItemMatch emptySlot() {
        return new ItemMatch(ItemStack.EMPTY, options.newCount());
    }

    /** The selected slot (empty past the end). */
    private ItemMatch slot() {
        return selected < slots.size() ? slots.get(selected) : emptySlot();
    }

    /** The selected slot's stack (empty for an empty slot). */
    private ItemStack current() {
        return slot().stack();
    }

    private boolean hasItem() {
        return !current().isEmpty();
    }

    /** The selected slot's count, as stored: a number, or {@link ItemMatch#STACK}. */
    private int currentCount() {
        return slot().count();
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
        if (selected >= slots.size() || !hasItem()) return;
        slots.set(selected, slot().withCount(clampCount(n, current())));
        commit();
    }

    /** "Full stack": ticked, the count is {@link ItemMatch#STACK}; cleared, the far end of the slider. */
    void setFullStack(boolean full) {
        if (selected >= slots.size() || !hasItem()) return;
        slots.set(selected, slot().withCount(full ? ItemMatch.STACK : options.most(current())));
        commit();
    }

    /**
     * The selected slot's stack replaced by {@code stack} (an edit), its count kept, and
     * the levels it leaves blank kept for the enchantments still on it; written through.
     */
    private void replaceCurrent(ItemStack stack, Set<Holder<Enchantment>> anyLevel) {
        if (selected >= slots.size()) return;
        Set<Holder<Enchantment>> kept = new LinkedHashSet<>(anyLevel);
        kept.retainAll(EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet());
        slots.set(selected, new ItemMatch(stack, slot().count(), kept));
        changed();
    }

    private void replaceCurrent(ItemStack stack) {
        replaceCurrent(stack, slot().anyLevel());
    }

    /**
     * After any change to the slots: a list keeps one empty slot at its end to add
     * to, then the value is written.
     */
    private void changed() {
        if (picker.isList() && (slots.isEmpty() || !slots.get(slots.size() - 1).stack().isEmpty())) {
            slots.add(emptySlot());
        }
        commit();
    }

    /** The slots that hold something, in order, through the lens. */
    private void commit() {
        picker.write(slots.stream().filter(m -> !m.stack().isEmpty()).toList());
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
        int n = slot().count();
        if (options.count() && n != ItemMatch.STACK) n = clampCount(n, one);
        // A new item starts with every level it shows (none left blank).
        slots.set(selected, new ItemMatch(one, n));
        changed();
    }

    /**
     * Slot {@code i}'s remove button: a list drops the entry, the ones after it
     * shifting left in their order, and the selection follows the slot it was on; a
     * single picker's slot is emptied, back to a new slot's count.
     */
    void remove(int i) {
        if (i < 0 || i >= slots.size() || slots.get(i).stack().isEmpty()) return;
        ElementWidget.playClickSound();
        if (picker.isList()) {
            slots.remove(i);
            if (selected > i) selected--;
        } else {
            slots.set(i, emptySlot());
        }
        changed();
        selected = Math.min(selected, slots.size() - 1);
    }

    // The advanced fields

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

    // The enchantments

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

    /** The enchantments on the selected stack, in the choices' order: one row each under the dropdown. */
    private List<EnchantChoice> applied() {
        return enchantChoices().stream().filter(EnchantChoice::applied).toList();
    }

    /** The dropdown's items: every choice but one an enchantment on the stack excludes. */
    private List<Holder<Enchantment>> pickable() {
        return enchantChoices().stream().filter(c -> !c.blocked()).map(EnchantChoice::enchantment).toList();
    }

    /** The dropdown's set: the enchantments on the selected stack. */
    Set<Holder<Enchantment>> picked() {
        Set<Holder<Enchantment>> out = new LinkedHashSet<>();
        for (EnchantChoice c : applied()) out.add(c.enchantment());
        return out;
    }

    /**
     * The dropdown's pick: the selected stack with exactly {@code next} on it. One newly
     * picked goes on with its level blank (any level), or at I where the value can't keep
     * a blank; one dropped comes off.
     */
    void setPicked(Set<Holder<Enchantment>> next) {
        ItemStack stack = current();
        Set<Holder<Enchantment>> anyLevel = new LinkedHashSet<>(slot().anyLevel());
        for (Holder<Enchantment> e : picked()) {
            if (!next.contains(e)) stack = ItemEditing.withEnchantment(stack, e, 0);
        }
        for (Holder<Enchantment> e : next) {
            if (choice(e) == null || picked().contains(e)) continue;
            stack = ItemEditing.withEnchantment(stack, e, 1);
            if (options.blankLevels()) anyLevel.add(e);
        }
        replaceCurrent(stack, anyLevel);
    }

    /** An enchantment row's level: 0 for blank (any level), else the level the stack must have. */
    int enchantLevel(Holder<Enchantment> enchantment) {
        if (slot().anyLevel().contains(enchantment)) return 0;
        EnchantChoice c = choice(enchantment);
        return c == null ? 0 : c.level();
    }

    /**
     * An enchantment row's level dropdown: 0 leaves the level blank (any level), on the
     * stack at I, which is not read; 1 or more is exactly that level, up to its highest.
     */
    void setEnchantLevel(Holder<Enchantment> enchantment, int level) {
        EnchantChoice c = choice(enchantment);
        if (c == null || !c.applied()) return;
        Set<Holder<Enchantment>> anyLevel = new LinkedHashSet<>(slot().anyLevel());
        if (level <= 0 && !options.blankLevels()) return;
        if (level <= 0) anyLevel.add(enchantment);
        else anyLevel.remove(enchantment);
        int on = level <= 0 ? 1 : Math.min(c.max(), level);
        replaceCurrent(ItemEditing.withEnchantment(current(), enchantment, on), anyLevel);
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
     * in the grid's viewport, a field's hint inside the field's text area, the grid's
     * column and the advanced one inside the dialog and clear of each other, and, while
     * the advanced column shows, everything shown in it inside it, each enchantment row's
     * name and level dropdown inside the row. {@code kind} is the screen it is laid out
     * for (the band's inset).
     */
    List<ItemPicker.Box> boxes(ScreenKind kind) {
        Font font = Minecraft.getInstance().font;
        List<ItemPicker.Box> out = new ArrayList<>();
        List<PanelElement> rows = body != null ? List.of(title, body, buttons) : List.of(title, row, search, gridBox, buttons);
        int w = 0, h = 0;
        for (PanelElement e : rows) {
            w = Math.max(w, e.getChildX() + e.getWidth());
            h = Math.max(h, e.getChildY() + e.getHeight());
        }
        int[] dialog = {0, 0, w, h};
        for (PanelElement e : rows) out.add(box("row " + describe(e), rect(e, 0, 0), dialog));

        // The grid's column, and what is in it (the dialog's rows, with no advanced settings).
        int[] leftRect = left != null ? rect(left, 0, 0) : dialog;
        List<PanelElement> leftParts = new ArrayList<>(List.of(row, search, gridBox));
        if (showBox != null) leftParts.add(showBox);
        if (left != null) {
            out.add(box("grid column", leftRect, dialog));
            for (PanelElement e : leftParts) out.add(box("in the grid column: " + describe(e), rect(e, 0, 0), leftRect));
        }

        int[] slotsRect = rect(row, 0, 0);
        SlotRow r = (SlotRow) row;
        for (int i = 0; i < slots.size(); i++) {
            int[] slot = {r.slotX(i, 0), r.slotY(i, 0), ItemPicker.SIZE, ItemPicker.SIZE};
            out.add(box("slot " + i, slot, slotsRect));
            // A filled slot's remove button, inside the slot's corner.
            if (!slots.get(i).stack().isEmpty()) {
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
        if (column == null || !column.isVisible()) return out;

        // The advanced column: in the dialog, beside the grid's (or, on a narrow screen,
        // under it), never over it; and each shown thing in it.
        int[] col = rect(column, 0, 0);
        out.add(box("advanced column", col, dialog));
        boolean beside = col[0] >= leftRect[0] + leftRect[2], under = col[1] >= leftRect[1] + leftRect[3];
        out.add(box("advanced column clear of the grid column" + (beside || under ? "" : " (overlaps)"),
                col, beside || under ? col : new int[]{0, 0, 0, 0}));
        for (PanelElement e : advancedParts) {
            if (!e.isVisible() || !shownIn(e)) continue;
            int[] at = rect(e, 0, 0);
            out.add(box(describe(e), at, col));
            if (e == componentsField) out.add(hint("components", COMPONENTS_HINT, at, font));
            if (e == nameField) out.add(hint("name", NAME_HINT, at, font));
        }
        if (enchantRows != null && enchantRows.isVisible()) {
            for (EnchantChoice c : applied()) {
                PanelElement[] parts = enchantRowParts.get(c.enchantment());
                if (parts == null) continue;
                String n = c.name().getString();
                int[] label = rect(parts[0], 0, 0);
                out.add(box("enchantment \"" + n + "\"", label, col));
                if (parts[1].isVisible()) {
                    int[] level = rect(parts[1], 0, 0);
                    out.add(box("level of " + n, level, col));
                    // The name and its level side by side, not over each other.
                    out.add(box("level of " + n + " right of its name" + (level[0] >= label[0] + label[2] ? "" : " (overlaps)"),
                            level, level[0] >= label[0] + label[2] ? level : new int[]{0, 0, 0, 0}));
                }
            }
        }
        return out;
    }

    /** Whether an advanced part is on show: it and the row it is in (a slider in a hidden row is not). */
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
            return i >= 0 && !slots.get(i).stack().isEmpty() && ItemPicker.overRemove(mx, my, slotX(i, ox), slotY(i, oy));
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
                ItemStack stack = slots.get(i).stack();
                ItemPicker.drawSlot(g, ctx.kind(), x, y, stack, i == hovered && !onRemove, false);
                if (picker.isList() && i == slots.size() - 1 && stack.isEmpty()) ItemPicker.drawAddMark(g, x, y);
            }
            // The selection over the slots, as the hotbar draws it over its own, then the
            // remove buttons over that, so each stays whole and clickable.
            if (selected < slots.size()) {
                int out = SELECTION_OUT - SlotRendering.ITEM_INSET;
                g.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION,
                        slotX(selected, ox) - out, slotY(selected, oy) - out, SELECTION_W, SELECTION_H);
            }
            for (int i = 0; i < slots.size(); i++) {
                if (!slots.get(i).stack().isEmpty()) ItemPicker.drawRemove(g, slotX(i, ox), slotY(i, oy), i == hovered && onRemove);
            }
            if (onRemove) {
                MKTooltip.queue(g, ItemPicker.REMOVE_TOOLTIP, ctx.mouseX(), ctx.mouseY());
            } else if (hovered >= 0 && !slots.get(hovered).stack().isEmpty()) {
                ItemPicker.itemTooltip(g, slots.get(hovered), options, ctx.mouseX(), ctx.mouseY());
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
}
