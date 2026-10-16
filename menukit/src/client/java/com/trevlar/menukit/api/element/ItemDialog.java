package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.element.ItemMatch.Match;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.core.DialogLayout;
import com.trevlar.menukit.core.ItemEditing;
import com.trevlar.menukit.core.ItemSources;
import com.trevlar.menukit.core.PressedTracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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
 * laid out as every MenuKit dialog is ({@link DialogLayout}) and as tall as the
 * shown tab, so it draws in the kind of screen it opens on like any dialog.
 * Package-private: consumers reach it through {@link ItemPicker}.
 *
 * <p>The value lives with the consumer, through the picker's lens, written on every
 * change. The dialog keeps its view state: the slots as the row shows them (a list's
 * emptied slots stay where they were until it closes, so an item can go back), each
 * slot's match, the selected slot, what the cursor holds, the tab, the search, and
 * the Edit tab's text as typed.
 *
 * <p>The cursor's stack is the dialog's own. Taking an item from the grid makes one;
 * nothing comes from or goes to the player's inventory, and closing the dialog drops
 * whatever it holds.
 */
final class ItemDialog {

    private static final AtomicLong IDS = new AtomicLong();

    /** The grid: a creative inventory's nine columns and five rows. */
    static final int COLUMNS = 9;
    static final int ROWS = 5;
    static final int GRID_WIDTH = COLUMNS * ItemPicker.SIZE;
    /** The dialog's content width: the grid and its scrollbar lane. */
    static final int WIDTH = GRID_WIDTH + ScrollContainer.TRACK_WIDTH + ScrollContainer.SCROLLER_GUTTER;
    static final int FIELD_HEIGHT = 20;
    static final int GAP = DialogLayout.GAP;
    /** The enchantment list: rows of 12, four on show, the rest scroll. */
    static final int ENCHANT_ROW = 12;
    static final int ENCHANT_ROWS = 4;
    /** The count field's width: three digits. */
    static final int COUNT_WIDTH = 30;

    /** What a field shows while its text is not valid, and vanilla's EditBox colour otherwise. */
    private static final int TEXT_OK = 0xFFE0E0E0;
    private static final int TEXT_BAD = 0xFFFF5555;

    /** How the components box says what it is for, while empty. */
    static final Component COMPONENTS_HINT = Component.literal("Other components, as in /give: [unbreakable={}]");

    private final ItemPicker picker;
    private final Panel panel;
    private final TextField search;
    private final @Nullable TextField nameField, countField, componentsField;

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

    ItemDialog(ItemPicker picker) {
        this.picker = picker;
        boolean edits = picker.match() != Match.ITEM;

        this.search = TextField.builder().at(0, 0).size(WIDTH, FIELD_HEIGHT)
                .maxLength(64)
                .hint(Component.literal("Search items"))
                .label(Component.literal("Search items"))
                .state(() -> query, q -> {
                    query = q;
                    gridScroll = 0;   // a new search starts at the top of its results
                })
                // Enter puts the first match in the selected slot, so typing a name
                // and Enter fills it.
                .onSubmit(q -> putFirstMatch())
                .build();
        ScrollContainer grid = ScrollContainer.builder().at(0, FIELD_HEIGHT + GAP).size(WIDTH, ROWS * ItemPicker.SIZE)
                .content(List.of(new Grid()))
                .state(() -> gridScroll, v -> gridScroll = v)
                .build();

        PanelElement title = DialogLayout.centred(TextLabel.builder().text(picker.label()).build());
        PanelElement row = new SlotRow();
        PanelElement buttons = DialogLayout.buttons(
                Button.builder().label(Component.literal("Cancel"))
                        .size(DialogLayout.BUTTON_W, DialogLayout.BUTTON_H).onClick(picker::cancel).build(),
                Button.builder().label(Component.literal("Done"))
                        .size(DialogLayout.BUTTON_W, DialogLayout.BUTTON_H).onClick(picker::done).build());

        String id = "menukit:item-dialog-" + IDS.incrementAndGet();
        if (!edits) {
            // The kind of item only: nothing to edit, so no tabs, just the search.
            this.nameField = this.countField = this.componentsField = null;
            this.panel = DialogLayout.panel(id, picker::cancel, title, row, search, grid, buttons)
                    .visibleWhen(this::isOpen);
            return;
        }

        this.nameField = TextField.builder().size(WIDTH - COUNT_WIDTH - GAP, FIELD_HEIGHT)
                .maxLength(50)   // an anvil's limit
                .hint(Component.literal("Name"))
                .label(Component.literal("Name"))
                .state(() -> name.text(current(), selected, ItemEditing::name), this::typeName)
                .build();
        this.countField = TextField.builder().size(COUNT_WIDTH, FIELD_HEIGHT)
                .maxLength(3)
                .label(Component.literal("Count"))
                .tooltip(() -> Component.literal("Count, 1 to " + current().getMaxStackSize()))
                .state(() -> count.text(current(), selected, s -> String.valueOf(s.getCount())), this::typeCount)
                .build();
        this.componentsField = TextField.builder().size(WIDTH, FIELD_HEIGHT)
                .maxLength(4096)
                .hint(COMPONENTS_HINT)
                .label(Component.literal("Components"))
                .tooltip(Component.literal("Every other component, as in /give: [unbreakable={},lore=[\"Hi\"]]. "
                        + "Type ! before one to remove it: [!food]."))
                .state(() -> components.text(current(), selected,
                        s -> ItemEditing.componentsText(s, ItemSources.registries())), this::typeComponents)
                .build();

        RadioGroup<Match> choice = RadioGroup.state(this::currentMatch, this::chooseMatch);
        Flow.Builder edit = Flow.builder().at(0, 0).vertical().gap(GAP, GAP)
                .add(TextLabel.builder().role(TextRole.MUTED)
                        .text(Component.literal("Put an item in this slot from the Search tab first."))
                        .visibleWhen(() -> current().isEmpty()).build())
                // The name and, for an item that stacks, the count beside it.
                .add(Flow.builder().gap(GAP, GAP).visibleWhen(this::hasItem)
                        .add(nameField)
                        .add(Flow.builder().gap(GAP, GAP).visibleWhen(() -> current().getMaxStackSize() > 1)
                                .add(countField).build())
                        .build());
        if (picker.playerChoosesMatch()) {
            edit.add(Flow.builder().gap(8, 2).visibleWhen(this::hasItem)
                    .add(Radio.builder(choice, Match.CONTAINS)
                            .label(() -> ItemPicker.matchName(Match.CONTAINS, current())).build())
                    .add(Radio.builder(choice, Match.EXACT)
                            .label(() -> ItemPicker.matchName(Match.EXACT, current())).build())
                    .build());
        }
        EnchantList enchants = new EnchantList();
        edit.add(ScrollContainer.builder().size(WIDTH, ENCHANT_ROWS * ENCHANT_ROW)
                        .content(List.of(enchants))
                        .state(() -> enchantScroll, v -> enchantScroll = v)
                        .visibleWhen(() -> hasItem() && !enchants.choices().isEmpty())
                        .build())
                .add(componentsField)
                .add(TextLabel.builder()
                        .text(() -> components.error != null ? components.error : Component.empty())
                        .visibleWhen(() -> hasItem() && components.error != null).build());

        Tabs tabs = Tabs.builder()
                .state(() -> tab, t -> tab = t)
                .fitShownTab()
                .tab("search", Component.literal("Search"), List.of(search, grid))
                .tab("edit", Component.literal("Edit"), List.of(edit.build()))
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
        carried = ItemStack.EMPTY;
        dragged.clear();
        dragButton = -1;
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

    // The slot row

    /**
     * The setting's slots, nine to a row, centred: the selected one outlined, a list's
     * last (empty) one marked with a plus. Takes clicks and drags as a container's
     * slots do on the Search tab, and only selects on the Edit tab. Draws what the
     * cursor holds over everything, in the overlay pass.
     */
    private final class SlotRow extends AbstractPanelElement {

        SlotRow() {
            this.width = this.authoredWidth = WIDTH;
            this.height = ItemPicker.SIZE;
        }

        private int perRow() {
            return COLUMNS;
        }

        private int rows() {
            return Math.max(1, (slots.size() + perRow() - 1) / perRow());
        }

        /** Left edge of the slots, from the row's: centred under the title. */
        private int left() {
            return (width - Math.min(Math.max(1, slots.size()), perRow()) * ItemPicker.SIZE) / 2;
        }

        @Override public int getHeight() { return rows() * ItemPicker.SIZE; }
        @Override public int extraLayoutHeight() { return getHeight() - ItemPicker.SIZE; }
        @Override public void layoutWithin(int budget) { width = Math.max(authoredWidth, budget); }
        @Override public void fillWidth(int width) {}
        @Override public boolean isInteractive() { return true; }

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

        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if (disabled(in)) return false;
            int i = slotAt(in.mouseX(), in.mouseY(), in.originX(), in.originY());
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
     * As tall as its rows, read every frame, so the scroll area around it follows the
     * search. Draws only the rows on screen.
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

        @Override public int getHeight() { return Math.max(1, rows()) * ItemPicker.SIZE; }
        @Override public void layoutWithin(int budget) {}
        @Override public void fillWidth(int width) {}
        @Override public boolean isInteractive() { return true; }

        private @Nullable ItemStack entryAt(int ox, int oy, double mx, double my) {
            double lx = mx - (ox + childX), ly = my - (oy + childY);
            if (lx < 0 || ly < 0 || lx >= GRID_WIDTH) return null;
            int i = (int) (ly / ItemPicker.SIZE) * COLUMNS + (int) (lx / ItemPicker.SIZE);
            List<ItemStack> shown = shown();
            return i < shown.size() ? shown.get(i) : null;
        }

        @Override
        public void render(RenderContext ctx) {
            GuiGraphicsExtractor g = ctx.graphics();
            int gx = ctx.originX() + childX, gy = ctx.originY() + childY;
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

    // The Edit tab's enchantments

    /**
     * The enchantments that fit the selected stack, a row each: the name, then a
     * minus, the level (or Off) and a plus. A blocked one (excluded by one on the
     * item) is greyed and its plus does nothing; its tooltip says why.
     */
    private final class EnchantList extends AbstractPanelElement {
        /** The stack the list was last made for, and the list. */
        private @Nullable ItemStack madeFor;
        private List<ItemEditing.EnchantChoice> made = List.of();

        EnchantList() {
            this.width = this.authoredWidth = GRID_WIDTH;
            this.height = ENCHANT_ROW;
        }

        List<ItemEditing.EnchantChoice> choices() {
            ItemStack s = current();
            if (madeFor == null || !ItemStack.matches(s, madeFor)) {
                madeFor = s.copy();
                made = ItemEditing.enchantChoices(s, ItemSources.registries());
            }
            return made;
        }

        @Override public int getHeight() { return Math.max(1, choices().size()) * ENCHANT_ROW; }
        @Override public void layoutWithin(int budget) { width = Math.min(authoredWidth, budget); }
        @Override public void fillWidth(int width) {}
        @Override public boolean isInteractive() { return true; }

        // The controls at the row's right: minus, level, plus.
        private int plusX() { return width - 10; }
        private int levelX() { return width - 40; }
        private int minusX() { return width - 50; }

        @Override
        public void render(RenderContext ctx) {
            GuiGraphicsExtractor g = ctx.graphics();
            var font = Minecraft.getInstance().font;
            int x = ctx.originX() + childX, y = ctx.originY() + childY;
            List<ItemEditing.EnchantChoice> list = choices();
            for (int i = 0; i < list.size(); i++) {
                ItemEditing.EnchantChoice c = list.get(i);
                int ry = y + i * ENCHANT_ROW;
                boolean over = ctx.hasMouseInput() && g.containsPointInScissor(ctx.mouseX(), ctx.mouseY())
                        && ctx.mouseY() >= ry && ctx.mouseY() < ry + ENCHANT_ROW && ctx.mouseX() >= x && ctx.mouseX() < x + width;
                if (over) g.fill(x, ry, x + width, ry + ENCHANT_ROW, ElementConstants.HOVER_OVERLAY);
                TextRole role = c.blocked() ? TextRole.DISABLED : c.level() > 0 ? TextRole.HEADING : TextRole.BODY;
                int color = ctx.kind().textColor(role);
                g.text(font, c.name(), x + 2, ry + 2, color, ctx.kind().textShadow());
                g.text(font, "-", x + minusX() + 3, ry + 2, color, ctx.kind().textShadow());
                Component level = c.level() == 0 ? Component.literal("Off") : Component.translatable("enchantment.level." + c.level());
                g.text(font, level, x + levelX() + (30 - font.width(level)) / 2, ry + 2, color, ctx.kind().textShadow());
                g.text(font, "+", x + plusX() + 3, ry + 2, color, ctx.kind().textShadow());
                if (over) MKTooltip.queue(g, tooltip(c), ctx.mouseX(), ctx.mouseY());
            }
        }

        private Component tooltip(ItemEditing.EnchantChoice c) {
            if (c.blocked()) return Component.literal("Does not go with an enchantment already on it");
            return Component.empty().append(c.name()).append(", up to level " + c.max() + ". Click + or - to change it.");
        }

        @Override
        public boolean mouseClicked(InputContext in, int button) {
            if (button != Click.LEFT || disabled(in)) return false;
            double lx = in.mouseX() - (in.originX() + childX), ly = in.mouseY() - (in.originY() + childY);
            List<ItemEditing.EnchantChoice> list = choices();
            int i = (int) (ly / ENCHANT_ROW);
            if (lx < 0 || ly < 0 || i >= list.size()) return false;
            ItemEditing.EnchantChoice c = list.get(i);
            int level = c.level();
            if (lx >= plusX()) {
                if (c.blocked() || level >= c.max()) return true;
                level++;
            } else if (lx >= minusX() && lx < minusX() + 10) {
                if (level == 0) return true;
                level--;
            } else {
                return false;
            }
            ElementWidget.playClickSound();
            setEnchantment(c, level);
            return true;
        }
    }

    /** The selected stack with {@code choice} at {@code level}, written through. */
    private void setEnchantment(ItemEditing.EnchantChoice choice, int level) {
        replaceCurrent(ItemEditing.withEnchantment(current(), choice.enchantment(), level));
    }
}
