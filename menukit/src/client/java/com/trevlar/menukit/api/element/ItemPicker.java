package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.element.ItemMatch.Match;
import com.trevlar.menukit.api.panel.Focus;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.ItemSources;
import com.trevlar.menukit.core.VanillaLook;
import com.trevlar.menukit.inject.ScreenPanelRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A setting whose value is an item, or a list of items: slots showing the items the
 * setting is about, and a lens onto them. Clicking a slot opens a dialog over the
 * screen to choose and edit them. Made for settings pages (Inventory Plus's lock
 * groups, Mod Hub's item options).
 *
 * <pre>{@code
 * // One item, the kind only (the 6.1.0 shape).
 * ItemPicker.builder()
 *         .state(() -> config.lockedItem(), config::setLockedItem)
 *         .build();
 *
 * // A list of exact stacks.
 * ItemPicker.builder()
 *         .stacks(() -> config.kit, kit -> config.kit = kit)
 *         .match(ItemMatch.Match.EXACT)
 *         .build();
 * }</pre>
 *
 * <h3>The value</h3>
 *
 * Always full stacks, the consumer's, read every frame and written on every change:
 * <ul>
 *   <li>{@code state(Supplier<Item>, Consumer<Item>)}: one item, the kind only
 *       ({@link Match#ITEM}); {@link Items#AIR} is none. The 6.1.0 lens, kept.</li>
 *   <li>{@code stack(get, set)}: one {@link ItemStack}; {@link ItemStack#EMPTY} is none.</li>
 *   <li>{@code stacks(get, set)}: a {@code List<ItemStack>}, in the player's order,
 *       with no empty entries.</li>
 *   <li>{@code rule(get, set)} and {@code rules(get, set)}: the same with each slot's
 *       {@link ItemMatch}, a stack and its own match mode. Needed when the player
 *       chooses the match ({@link Builder#playerChoosesMatch}).</li>
 * </ul>
 * Test a stack in play against the value with {@link ItemMatch}, never with a
 * comparison of your own: {@code rule.matches(stack)}, or
 * {@code ItemMatch.matches(config.stack, ItemMatch.Match.EXACT, stack)}.
 *
 * <h3>The match mode</h3>
 *
 * {@link Builder#match} declares how the setting compares stacks ({@link ItemMatch}
 * has the rules). {@link Match#ITEM} (the default) keeps the kind of item only: the
 * dialog has no Edit tab and no counts. {@link Match#EXACT} and {@link Match#CONTAINS}
 * keep the whole stack, and the dialog's Edit tab edits it. With
 * {@link Builder#playerChoosesMatch} the player picks, per slot, between "Any
 * &lt;item&gt;" ({@code CONTAINS}) and "Exactly this" ({@code EXACT}); the declared
 * mode is a new slot's.
 *
 * <h3>On the page</h3>
 *
 * One slot, or for a list a wrapping row of slots in order, {@link Builder#columns}
 * to a row, with an empty slot at the end to add one. Left click (or Enter on the
 * focused picker) opens the dialog at that slot; right click empties it (a list
 * drops it). Hovered, a slot shows the item's own tooltip, unless the builder gives
 * a {@code tooltip}.
 *
 * <h3>The dialog</h3>
 *
 * A modal dialog of the screen, in its kind, laid out as every MenuKit dialog
 * (equal margins, Cancel and Done centred, as tall as the shown tab):
 * <ul>
 *   <li><b>The slot row</b> at the top: the setting's slots (a list's wrapped, with
 *       an empty one at the end to add to). The selected one is outlined; the Edit
 *       tab edits it.</li>
 *   <li><b>Search</b>, the default tab: a search box, focused so typing starts at
 *       once, over a grid of every item from the creative inventory's search tab,
 *       so each variant (potions, tipped arrows, enchanted book levels, goat horns,
 *       firework durations, ominous bottles) is its own entry. Items move as in the
 *       creative inventory: a click on an entry takes one (shift-click or middle
 *       click a full stack; clicking it again adds one, a right click takes one
 *       back); a click on a slot places, picks up or swaps (a right click places
 *       one or takes half); dragging over slots spreads the stack (right-drag one
 *       each); a click on the grid while holding something else puts it back. Enter
 *       in the search box puts the first match into the selected slot.</li>
 *   <li><b>Edit</b>, for {@code EXACT} and {@code CONTAINS}: the selected slot's
 *       name, count, enchantments (only those that fit the item, up to their highest
 *       level, the ones a chosen enchantment excludes greyed; none for an item
 *       nothing enchants; an enchanted book's stored ones), every other component
 *       as text in vanilla's {@code /give} syntax (read by vanilla's own item
 *       parser, its errors shown as a command's), and the match choice when the
 *       player has one.</li>
 * </ul>
 * Changes are written as they are made; Done keeps them, Cancel and Escape write
 * back the value from before the dialog opened.
 *
 * <h3>A setting value, never a player's item</h3>
 *
 * Nothing here touches the player's inventory, a menu or the container behind a
 * screen, and nothing is sent to the server: the slots are not real slots, the item
 * held in the dialog is the dialog's own, and every stack is a picture of a value.
 *
 * <p>Each slot is 18 x 18, vanilla's slot size, MenuKit's recessed slot on a
 * container screen and vanilla's slot sprite on an options screen.
 */
public class ItemPicker extends AbstractPanelElement {

    /** A slot's size: 16 px of item and a 1 px frame each side. */
    public static final int SIZE = 18;
    /** Slots in a list's row unless {@link Builder#columns} says otherwise: a vanilla inventory row. */
    public static final int DEFAULT_COLUMNS = 9;

    // Vanilla's slot highlight (AbstractContainerScreen, 26.2): a 24 x 24 glow behind
    // the item and a sheen in front of it, both 4 px out from the item's corner.
    private static final Identifier HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
    private static final Identifier HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");

    private final Supplier<List<ItemMatch>> stateGet;
    private final Consumer<List<ItemMatch>> stateSet;
    private final boolean list;
    private final Match match;
    private final boolean playerChoosesMatch;
    private final Component label;
    private final int columns;

    // The vanilla stand-in for sound, focus and narration (ElementWidget), made at the
    // first screen attach, as a Button's is.
    private @Nullable ElementWidget widget;
    private @Nullable Screen screen;

    /** The dialog, built once with the picker; shown on whatever screen the picker is on. */
    private final ItemDialog dialog;

    protected ItemPicker(Builder b, Supplier<List<ItemMatch>> get, Consumer<List<ItemMatch>> set) {
        super(b);
        this.stateGet = get;
        this.stateSet = set;
        this.list = b.list;
        this.match = b.match;
        this.playerChoosesMatch = b.playerChoosesMatch;
        this.label = b.label != null ? b.label
                : Component.literal(b.list ? "Choose items" : "Choose an item");
        this.columns = b.columns;
        this.dialog = new ItemDialog(this);
    }

    /** Starts a picker. Give it a lens: {@code state}, {@code stack}, {@code stacks}, {@code rule} or {@code rules}. */
    public static Builder builder() {
        return new Builder();
    }

    // The value

    /** The first slot's item, the kind only ({@link Items#AIR} when empty): what a 6.1.0 picker shows. */
    public Item getCurrentItem() {
        List<ItemMatch> v = value();
        return v.isEmpty() ? Items.AIR : v.get(0).stack().getItem();
    }

    /** The setting's slots now, in order, none empty: each a stack and its match mode. */
    public List<ItemMatch> value() {
        ItemSources.ready();   // stacks need items' components, which no world may have bound yet
        List<ItemMatch> v = stateGet.get();
        if (v == null) return List.of();
        List<ItemMatch> out = new ArrayList<>(v.size());
        for (ItemMatch m : v) if (m != null && !m.stack().isEmpty()) out.add(m);
        return out;
    }

    /** The declared match mode: every slot's, or a new slot's when the player chooses. */
    public Match match() {
        return match;
    }

    /** Whether the setting is a list (else one item). */
    public boolean isList() {
        return list;
    }

    /** Whether the player chooses each slot's match between Any and Exactly. */
    public boolean playerChoosesMatch() {
        return playerChoosesMatch;
    }

    /**
     * Writes the slots through the lens: empties dropped, at most one for a single
     * picker, and in {@code ITEM} mode each the plain item, one of it.
     */
    void write(List<ItemMatch> slots) {
        List<ItemMatch> out = new ArrayList<>();
        for (ItemMatch m : slots) {
            if (m.stack().isEmpty()) continue;
            out.add(match == Match.ITEM ? new ItemMatch(new ItemStack(m.stack().getItem()), Match.ITEM) : m);
            if (!list) break;
        }
        stateSet.accept(List.copyOf(out));
    }

    /** Writes {@code slots} back exactly as they were (Cancel). */
    void restore(List<ItemMatch> slots) {
        stateSet.accept(slots);
    }

    /** What the setting is called: the dialog's title. */
    Component label() {
        return label;
    }

    // The page

    /** Slots on the page: one, or a list's items and the empty one after them. */
    private int slotCount() {
        return list ? value().size() + 1 : 1;
    }

    private int perRow() {
        return list ? Math.max(1, Math.min(columns, width / SIZE)) : 1;
    }

    private int rows() {
        return (slotCount() + perRow() - 1) / perRow();
    }

    @Override public int getHeight() { return list ? rows() * SIZE : SIZE; }
    /** A list taller than one row pushes what is below it down. */
    @Override public int extraLayoutHeight() { return getHeight() - SIZE; }
    @Override public void layoutWithin(int budget) { if (list) width = Math.max(SIZE, Math.min(authoredWidth, budget)); }
    @Override public void fillWidth(int width) {}
    @Override public boolean isInteractive() { return true; }

    /** The page slot at a screen point, the picker's top-left at {@code (x, y)}, or -1. */
    private int slotAt(double mx, double my, int x, int y) {
        if (mx < x || my < y) return -1;
        int col = (int) ((mx - x) / SIZE), row = (int) ((my - y) / SIZE);
        if (col >= perRow()) return -1;
        int i = row * perRow() + col;
        return i < slotCount() ? i : -1;
    }

    @Override
    public void render(RenderContext ctx) {
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        boolean disabled = disabled(ctx);
        List<ItemMatch> v = value();
        int hovered = ctx.hasMouseInput() && isHovered(ctx) ? slotAt(ctx.mouseX(), ctx.mouseY(), sx, sy) : -1;
        boolean focused = widget != null && widget.focused();
        if (widget != null) {
            widget.track(sx, sy, width, getHeight(), hovered >= 0, !disabled,
                    Component.empty().append(label).append(": ").append(narration(v)));
        }
        GuiGraphicsExtractor g = ctx.graphics();
        for (int i = 0; i < slotCount(); i++) {
            int x = sx + (i % perRow()) * SIZE, y = sy + (i / perRow()) * SIZE;
            ItemStack stack = i < v.size() ? v.get(i).stack() : ItemStack.EMPTY;
            boolean hot = !disabled && (i == hovered || (focused && i == 0));
            drawSlot(g, ctx.kind(), x, y, stack, hot, disabled);
            if (list && i == v.size()) drawAddMark(g, ctx.kind(), x, y);
        }
        if (hovered < 0) return;
        if (tooltipSupplier() != null) queueTooltip(ctx);
        else if (hovered < v.size()) itemTooltip(g, v.get(hovered), match, ctx.mouseX(), ctx.mouseY());
    }

    /** What the narrator reads for the value: the items' names, or "Empty". */
    private static Component narration(List<ItemMatch> v) {
        if (v.isEmpty()) return Component.literal("Empty");
        var out = Component.empty();
        for (int i = 0; i < v.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(v.get(i).stack().getHoverName());
        }
        return out;
    }

    /**
     * One slot holding {@code stack}, as the picker and its dialog draw it: the kind's
     * slot, vanilla's highlight behind and in front of the item while
     * {@code highlighted}, the item drawn as a picture (a fake item: no entity, no
     * inventory behind it) with its count and durability bar, faded while
     * {@code disabled}.
     */
    static void drawSlot(GuiGraphicsExtractor g, ScreenKind kind, int x, int y, ItemStack stack,
                         boolean highlighted, boolean disabled) {
        if (kind == ScreenKind.OPTIONS) VanillaLook.slot(g, x, y, disabled);
        else SlotRendering.drawSlotBackground(g, x, y, SIZE, disabled);
        int ix = x + SlotRendering.ITEM_INSET, iy = y + SlotRendering.ITEM_INSET;
        if (highlighted) g.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_BACK, ix - 4, iy - 4, 24, 24);
        if (!stack.isEmpty()) {
            g.fakeItem(stack, ix, iy);
            g.itemDecorations(Minecraft.getInstance().font, stack, ix, iy);
        }
        if (highlighted) g.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_FRONT, ix - 4, iy - 4, 24, 24);
        if (disabled && !stack.isEmpty()) g.fill(ix, iy, ix + 16, iy + 16, ElementConstants.DISABLED_OVERLAY);
    }

    /** One slot holding the plain {@code item}: the colour dialog's dyes. */
    static void drawSlot(GuiGraphicsExtractor g, ScreenKind kind, int x, int y, Item item,
                         boolean highlighted, boolean disabled) {
        ItemSources.ready();
        drawSlot(g, kind, x, y, item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item), highlighted, disabled);
    }

    /** A faint plus in the empty slot at the end of a list: add one here. */
    static void drawAddMark(GuiGraphicsExtractor g, ScreenKind kind, int x, int y) {
        int c = kind.textColor(TextRole.MUTED);
        g.fill(x + 6, y + 8, x + 12, y + 10, c);
        g.fill(x + 8, y + 6, x + 10, y + 12, c);
    }

    /**
     * A slot's tooltip: vanilla's own for the stack (name, enchantments, lore), and
     * under it how the setting compares it, when that is more than the kind.
     */
    static void itemTooltip(GuiGraphicsExtractor g, ItemMatch slot, Match declared, int mx, int my) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), slot.stack()));
        if (declared != Match.ITEM) lines.add(matchName(slot.match(), slot.stack()).copy().withStyle(ChatFormatting.GRAY));
        g.setTooltipForNextFrame(Minecraft.getInstance().font, lines, Optional.empty(), mx, my);
    }

    /** How a slot matches, as the player reads it: "Any Diamond Sword", "Exactly this", "Diamond Sword". */
    static Component matchName(Match match, ItemStack stack) {
        return switch (match) {
            case CONTAINS -> Component.literal("Any ").append(new ItemStack(stack.getItem()).getHoverName());
            case EXACT -> Component.literal("Exactly this");
            case ITEM -> new ItemStack(stack.getItem()).getHoverName();
        };
    }

    /** Left click opens the dialog at the slot; right click empties it (a list drops it). */
    @Override
    public boolean mouseClicked(InputContext in, int button) {
        if (disabled(in)) return false;
        int x = in.originX() + childX, y = in.originY() + childY;
        int i = slotAt(in.mouseX(), in.mouseY(), x, y);
        if (i < 0) return false;
        if (button == Click.RIGHT) {
            ElementWidget.playClickSound();
            List<ItemMatch> v = new ArrayList<>(value());
            if (i < v.size()) v.remove(i);
            write(v);
            return true;
        }
        if (button != Click.LEFT) return false;
        ElementWidget.playClickSound();
        openDialog(i);
        return true;
    }

    /** Enter or Space on the focused picker (vanilla played the sound). */
    private void press() {
        if (!ownDisabled()) openDialog(0);
    }

    // The dialog. Public for MenuKit's checks; the page and the dialog's own elements
    // are what call these in play.

    /** Opens the dialog with slot {@code slot} selected. Does nothing while it is open. */
    @ApiStatus.Internal
    public void openDialog(int slot) {
        if (dialog.isOpen()) return;
        dialog.open(slot);
        if (screen != null) {
            // Focus leaves the page, and typing goes straight to the search box.
            Focus.clear(screen);
            if (dialog.searchWidget() != null) screen.setFocused(dialog.searchWidget());
        }
    }

    @ApiStatus.Internal
    public boolean isDialogOpen() {
        return dialog.isOpen();
    }

    /** Done: keeps the value and closes the dialog. */
    @ApiStatus.Internal
    public void done() {
        dialog.close();
    }

    /** Cancel (and Escape): writes back the value from before the dialog opened, and closes it. */
    @ApiStatus.Internal
    public void cancel() {
        if (!dialog.isOpen()) return;
        List<ItemMatch> before = dialog.before();
        dialog.close();
        restore(before);
    }

    /** The dialog's panel: a modal on the screen the picker is on, open while {@link #isDialogOpen}. */
    @ApiStatus.Internal
    public Panel dialog() {
        return dialog.panel();
    }

    /** The dialog's slots, empties included (a list's holes and the slot to add to), as the slot row shows them. */
    @ApiStatus.Internal
    public List<ItemStack> dialogSlots() {
        return dialog.slots();
    }

    /** The selected slot. */
    @ApiStatus.Internal
    public int selectedSlot() {
        return dialog.selected();
    }

    /** What the dialog holds on the cursor. */
    @ApiStatus.Internal
    public ItemStack carried() {
        return dialog.carried();
    }

    /** The Search tab's entries for a query: the creative search contents, searched. */
    @ApiStatus.Internal
    public List<ItemStack> searchEntries(String query) {
        return dialog.entries(query);
    }

    /** A click on a grid entry, as the Search tab takes it ({@code shift} for shift-click). */
    @ApiStatus.Internal
    public void clickEntry(ItemStack entry, int button, boolean shift) {
        dialog.clickEntry(entry, button, shift);
    }

    /** A click on slot {@code slot} of the slot row (a press and release on it alone). */
    @ApiStatus.Internal
    public void clickSlot(int slot, int button) {
        dialog.clickSlot(slot, button);
    }

    /** A drag with {@code button} held over {@code slots} of the slot row, then released. */
    @ApiStatus.Internal
    public void dragSlots(List<Integer> slots, int button) {
        dialog.drag(slots, button);
    }

    /** Selects slot {@code slot} of the slot row, as a click on it in the Edit tab does. */
    @ApiStatus.Internal
    public void selectSlot(int slot) {
        dialog.select(slot);
    }

    /** Shows a tab of the dialog ({@code "search"} or {@code "edit"}), as a click on it does. */
    @ApiStatus.Internal
    public void showDialogTab(String id) {
        dialog.showTab(id);
    }

    /** Types into a field of the Edit tab ({@code "name"}, {@code "count"}, {@code "components"}), as a player does. */
    @ApiStatus.Internal
    public void typeEdit(String field, String text) {
        dialog.typeEdit(field, text);
    }

    /** What a field of the Edit tab shows now. */
    @ApiStatus.Internal
    public String editText(String field) {
        return dialog.editText(field);
    }

    /** The components box's error, or null while its text is valid. */
    @ApiStatus.Internal
    public @Nullable Component componentsError() {
        return dialog.componentsError();
    }

    /** Picks the selected slot's match (the player's choice), as its radio does. */
    @ApiStatus.Internal
    public void chooseMatch(Match match) {
        dialog.chooseMatch(match);
    }

    /** Whether the dialog has an Edit tab: only when the match mode keeps more than the item. */
    @ApiStatus.Internal
    public boolean hasEditTab() {
        return match != Match.ITEM;
    }

    /**
     * Joins the screen: the page's stand-in, and the dialog on the screen's dialog
     * host, so it can open over whatever screen the picker is on. A picker moved to
     * another screen starts with its dialog closed.
     */
    @Override
    public void onAttach(Screen screen) {
        if (widget == null) widget = new ElementWidget(this::press);
        widget.attach(screen);
        if (this.screen != screen) dialog.close();
        this.screen = screen;
        ScreenPanelRegistry.attachDialog(screen, dialog.panel());
    }

    @Override
    public void onDetach(Screen screen) {
        if (widget != null) widget.detach(screen);
        ScreenPanelRegistry.detachDialog(screen, dialog.panel());
    }

    /** Which lens the builder was given. */
    private enum Lens { ITEM, STACK, STACKS, RULE, RULES }

    /** Builds an {@link ItemPicker}. One lens is required. */
    public static class Builder extends AbstractPanelElement.Builder<ItemPicker, Builder> {
        private @Nullable Lens lens;
        private @Nullable Supplier<?> get;
        private @Nullable Consumer<?> set;
        private boolean list = false;
        private Match match = Match.ITEM;
        private boolean playerChoosesMatch = false;
        private @Nullable Component label;
        private int columns = DEFAULT_COLUMNS;

        protected Builder() {
            this.width = SIZE;
            this.height = SIZE;
        }

        @Override protected Builder self() { return this; }

        private Builder lens(Lens lens, Supplier<?> get, Consumer<?> set, boolean list) {
            require(this.lens == null, "one lens only: state, stack, stacks, rule or rules");
            this.lens = lens;
            this.get = Objects.requireNonNull(get, "get");
            this.set = Objects.requireNonNull(set, "set");
            this.list = list;
            return this;
        }

        /**
         * The 6.1.0 lens: one item, the kind only ({@link Match#ITEM}). {@code get} is
         * read every frame ({@link Items#AIR}, or {@code null}, for none); {@code set}
         * receives the item the player picks, or {@link Items#AIR} when they empty it.
         */
        public Builder state(Supplier<Item> get, Consumer<Item> set) {
            return lens(Lens.ITEM, get, set, false);
        }

        /** One stack: {@code get} read every frame, {@code set} handed every change ({@link ItemStack#EMPTY} for none). */
        public Builder stack(Supplier<ItemStack> get, Consumer<ItemStack> set) {
            return lens(Lens.STACK, get, set, false);
        }

        /** A list of stacks, in the player's order: {@code set} gets a new list with no empty entries. */
        public Builder stacks(Supplier<List<ItemStack>> get, Consumer<List<ItemStack>> set) {
            return lens(Lens.STACKS, get, set, true);
        }

        /** One slot with its own match mode ({@code set} gets an empty stack for none). */
        public Builder rule(Supplier<ItemMatch> get, Consumer<ItemMatch> set) {
            return lens(Lens.RULE, get, set, false);
        }

        /** A list of slots, each with its own match mode. */
        public Builder rules(Supplier<List<ItemMatch>> get, Consumer<List<ItemMatch>> set) {
            return lens(Lens.RULES, get, set, true);
        }

        /**
         * How the setting compares stacks ({@link ItemMatch}): {@link Match#ITEM} (the
         * default) keeps the kind of item only and shows no Edit tab; {@link Match#EXACT}
         * and {@link Match#CONTAINS} keep the whole stack and edit it.
         */
        public Builder match(Match match) {
            this.match = Objects.requireNonNull(match, "match");
            return this;
        }

        /**
         * The player chooses each slot's match, "Any &lt;item&gt;" ({@link Match#CONTAINS})
         * or "Exactly this" ({@link Match#EXACT}), in the Edit tab; {@link #match} is a
         * new slot's. Needs {@code rule} or {@code rules}, which store the choice.
         */
        public Builder playerChoosesMatch() {
            this.playerChoosesMatch = true;
            return this;
        }

        /** The dialog's title, and what the narrator reads. Default "Choose an item", or "Choose items" for a list. */
        public Builder label(Component label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        /** A list's slots to a row, fewer when the panel is narrower. Default {@link #DEFAULT_COLUMNS}. */
        public Builder columns(int columns) {
            require(columns >= 1, "columns must be at least 1");
            this.columns = columns;
            return this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public ItemPicker build() {
            require(lens != null, "a lens is required (state, stack, stacks, rule or rules); an ItemPicker shows the consumer's value");
            require(lens != Lens.ITEM || match == Match.ITEM,
                    "state(Supplier<Item>, Consumer<Item>) holds an Item, which has no count or components: use stack or stacks with match(" + match + ")");
            require(!playerChoosesMatch || lens == Lens.RULE || lens == Lens.RULES,
                    "playerChoosesMatch() needs rule(...) or rules(...), which store each slot's choice");
            require(!playerChoosesMatch || match != Match.ITEM,
                    "playerChoosesMatch() chooses between CONTAINS and EXACT: declare match(CONTAINS) or match(EXACT) for a new slot");
            // A list is a row of slots wide; a single picker one slot.
            if (list) this.width = columns * SIZE;
            Match m = match;
            Supplier<List<ItemMatch>> g;
            Consumer<List<ItemMatch>> s;
            switch (lens) {
                case ITEM -> {
                    Supplier<Item> get0 = (Supplier<Item>) get;
                    Consumer<Item> set0 = (Consumer<Item>) set;
                    g = () -> {
                        Item item = get0.get();
                        return item == null || item == Items.AIR ? List.of() : List.of(new ItemMatch(new ItemStack(item), Match.ITEM));
                    };
                    s = v -> set0.accept(v.isEmpty() ? Items.AIR : v.get(0).stack().getItem());
                }
                case STACK -> {
                    Supplier<ItemStack> get0 = (Supplier<ItemStack>) get;
                    Consumer<ItemStack> set0 = (Consumer<ItemStack>) set;
                    g = () -> {
                        ItemStack st = get0.get();
                        return st == null || st.isEmpty() ? List.of() : List.of(new ItemMatch(st, m));
                    };
                    s = v -> set0.accept(v.isEmpty() ? ItemStack.EMPTY : v.get(0).stack());
                }
                case STACKS -> {
                    Supplier<List<ItemStack>> get0 = (Supplier<List<ItemStack>>) get;
                    Consumer<List<ItemStack>> set0 = (Consumer<List<ItemStack>>) set;
                    g = () -> {
                        List<ItemStack> sts = get0.get();
                        if (sts == null) return List.of();
                        List<ItemMatch> out = new ArrayList<>();
                        for (ItemStack st : sts) if (st != null && !st.isEmpty()) out.add(new ItemMatch(st, m));
                        return out;
                    };
                    s = v -> set0.accept(v.stream().map(ItemMatch::stack).toList());
                }
                case RULE -> {
                    Supplier<ItemMatch> get0 = (Supplier<ItemMatch>) get;
                    Consumer<ItemMatch> set0 = (Consumer<ItemMatch>) set;
                    g = () -> {
                        ItemMatch r = get0.get();
                        return r == null || r.stack().isEmpty() ? List.of() : List.of(r);
                    };
                    s = v -> set0.accept(v.isEmpty() ? new ItemMatch(ItemStack.EMPTY, m) : v.get(0));
                }
                case RULES -> {
                    g = (Supplier<List<ItemMatch>>) get;
                    s = (Consumer<List<ItemMatch>>) set;
                }
                default -> throw new IllegalStateException("unknown lens " + lens);
            }
            return new ItemPicker(this, g, s);
        }
    }
}
