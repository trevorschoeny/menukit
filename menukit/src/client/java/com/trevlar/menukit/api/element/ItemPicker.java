package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.Focus;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.DialogLayout;
import com.trevlar.menukit.core.ItemEditing.EnchantChoice;
import com.trevlar.menukit.core.ItemSources;
import com.trevlar.menukit.core.VanillaLook;
import com.trevlar.menukit.inject.ScreenPanelRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A setting whose value is an item, or a list of items: slots showing the items the
 * setting is about, and a lens onto them. Clicking a slot opens a dialog over the
 * screen to choose them and, with the options the setting turns on, the fields a
 * stack has to share with each. Made for settings pages, and openable on its own over
 * any screen ({@link #open(Screen)}).
 *
 * <pre>{@code
 * // One item, the kind only.
 * ItemPicker.builder()
 *         .state(() -> config.lockedItem(), config::setLockedItem)
 *         .build();
 *
 * // A list of rules, each with a name, enchantments and components to match.
 * ItemPicker.builder()
 *         .rules(() -> config.locks, locks -> config.locks = locks)
 *         .name().enchantments().components()
 *         .build();
 *
 * // One rule with a count, opened straight over a container screen from a key press.
 * ItemPicker reserve = ItemPicker.builder()
 *         .rule(() -> draft, r -> draft = r)
 *         .name().enchantments().components()
 *         .count()
 *         .button(Component.literal("Remove"), () -> { clear(); reserve.cancel(); })
 *         .onDone(() -> apply(draft))
 *         .build();
 * reserve.open(screen);
 * }</pre>
 *
 * <h3>The value</h3>
 *
 * Always the consumer's, read every frame and written on every change:
 * <ul>
 *   <li>{@code state(Supplier<Item>, Consumer<Item>)}: one item, the kind only;
 *       {@link Items#AIR} is none. The grid lists each item once, and the dialog has no
 *       advanced settings.</li>
 *   <li>{@code stack(get, set)}: one {@link ItemStack}, one of it; {@link ItemStack#EMPTY} is none.</li>
 *   <li>{@code stacks(get, set)}: a {@code List<ItemStack>}, in the player's order,
 *       with no empty entries.</li>
 *   <li>{@code rule(get, set)} and {@code rules(get, set)}: the same as {@link ItemMatch}
 *       rules, which carry the count slider's number too, and the enchantments whose level
 *       is left blank. Needed for {@link Builder#count}.</li>
 * </ul>
 * Test a stack in play against the value with {@link ItemMatch}, never with a
 * comparison of your own: {@code rule.matches(stack)}, or
 * {@code ItemMatch.matches(config.stack, stack)}. The item always counts; each field the
 * player fills in narrows the match, and a blank one is not checked.
 *
 * <h3>On the page</h3>
 *
 * One slot, or for a list a wrapping row of slots in order, {@link Builder#columns}
 * to a row, with an empty slot marked with a plus at the end to add one. Each filled
 * slot has a small remove button in its top-right corner ("Remove"): a click on it,
 * or a right click anywhere on the slot, empties it, and a list drops it, the items
 * after it moving up one in their order. Left click anywhere else on a slot (or
 * Enter on the focused picker) opens the dialog at that slot. Hovered, a slot shows
 * the item's own tooltip, unless the builder gives a {@code tooltip}.
 *
 * <h3>The dialog</h3>
 *
 * A modal dialog of the screen, in its kind, laid out as every MenuKit dialog (equal
 * margins, the buttons centered): the title, the grid's column with the advanced
 * column beside it, and the buttons.
 * <ul>
 *   <li><b>The title</b>, the setting's {@link Builder#label}.</li>
 *   <li><b>The slot row</b>: the setting's slots (a list's wrapped, with an empty one at
 *       the end to add to), the selected one framed by the hotbar's selection. A click on
 *       a slot selects it; the remove button or a right click empties it, and a list
 *       drops it, the ones after it moving left.</li>
 *   <li><b>The search box</b>, focused so typing starts at once, over <b>a grid</b> of
 *       every item from the creative inventory's search tab, so each variant (potions,
 *       tipped arrows, enchanted book levels, goat horns) is its own entry. A click on an
 *       entry puts it straight into the selected slot, replacing what was there; the slot
 *       stays selected. Enter in the search box puts the first match in the same way. The
 *       grid is five rows tall, fewer on a screen too short for the dialog.</li>
 *   <li><b>"Show advanced settings"</b>, a box under the grid. Ticked, the advanced
 *       settings show as a second column to the right of the grid, so the dialog grows
 *       wider, not taller. The player's choice is remembered for every picker (and the
 *       column shows anyway for a slot that has fields filled in). It holds the options
 *       the setting turns on, for the selected slot: {@link Builder#count} (a slider from
 *       N/A, no count, to the item's stack size), {@link Builder#name},
 *       {@link Builder#enchantments} (a dropdown of those that fit the item, each one
 *       picked a row under it with a level dropdown, blank for any level),
 *       {@link Builder#components} (every other component as text in vanilla's
 *       {@code /give} syntax, read by vanilla's own item parser, its errors shown as a
 *       command's), then the setting's own {@link Builder#advanced} rows. A blank field
 *       is not checked.</li>
 *   <li><b>The buttons</b>: the setting's own {@link Builder#button}s, then Cancel and Done.</li>
 * </ul>
 * Changes are written as they are made; Done keeps them (and runs {@link Builder#onDone}),
 * Cancel and Escape write back the value from before the dialog opened.
 *
 * <h3>A setting value, never a player's item</h3>
 *
 * Nothing here touches the player's inventory, a menu or the container behind a
 * screen, and nothing is sent to the server: the slots are not real slots, and every
 * stack is a picture of a value.
 *
 * <p>Each slot is 18 x 18, vanilla's slot size, MenuKit's recessed slot in the
 * Panel look and vanilla's slot sprite in the Default look.
 */
public class ItemPicker extends AbstractPanelElement {

    /** A slot's size: 16 px of item and a 1 px frame each side. */
    public static final int SIZE = 18;
    /** Slots in a list's row unless {@link Builder#columns} says otherwise: a vanilla inventory row. */
    public static final int DEFAULT_COLUMNS = 9;
    /** A filled slot's remove button: this many pixels square, over the slot's top-right corner. */
    static final int REMOVE_SIZE = 5;
    static final Component REMOVE_TOOLTIP = Component.translatable("menukit.item_picker.remove");
    // Dark with a white bar, lighter while hovered: reads in both looks' gray slots.
    static final int REMOVE_FILL = 0xFF2A2A2A, REMOVE_HOVER = 0xFF5E5E5E, REMOVE_BAR = 0xFFFFFFFF;
    // The add slot's plus: white over vanilla's text shadow for white (a quarter of it).
    private static final int ADD_MARK = 0xFFFFFFFF, ADD_SHADOW = 0xFF3F3F3F;

    // Vanilla's slot highlight (AbstractContainerScreen, 26.2): a 24 x 24 glow behind
    // the item and a sheen in front of it, both 4 px out from the item's corner.
    private static final Identifier HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
    private static final Identifier HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");

    private final Supplier<List<ItemMatch>> stateGet;
    private final Consumer<List<ItemMatch>> stateSet;
    private final boolean list;
    private final boolean kindsOnly;
    private final Options options;
    private final Component label;
    private final int columns;
    private final @Nullable Runnable onDone;

    // The vanilla stand-in for sound, focus and narration (ElementWidget), made at the
    // first screen attach, as a Button's is.
    private @Nullable ElementWidget widget;
    /** The screen the dialog is on: the page's, or the one {@link #open(Screen)} put it on. */
    private @Nullable Screen screen;

    /** The dialog, built once with the picker; shown on whatever screen the picker is on. */
    private final ItemDialog dialog;

    /**
     * What the dialog's advanced settings offer, as the builder turned them on: the name,
     * the enchantments (with a blank level for any level where the lens keeps rules, which
     * can say so; a plain stack can't), the components box, the count slider, and the
     * consumer's own rows and buttons.
     */
    record Options(boolean name, boolean enchantments, boolean blankLevels, boolean components,
                   boolean count, List<PanelElement> advanced, List<Button> buttons) {

        /** Whether the dialog has advanced settings at all. */
        boolean any() {
            return name || enchantments || components || count || !advanced.isEmpty();
        }
    }

    protected ItemPicker(Builder b, Supplier<List<ItemMatch>> get, Consumer<List<ItemMatch>> set) {
        super(b);
        this.stateGet = get;
        this.stateSet = set;
        this.list = b.list;
        this.kindsOnly = b.lens == Lens.ITEM;
        this.options = new Options(b.name, b.enchantments, b.lens == Lens.RULE || b.lens == Lens.RULES, b.components, b.count,
                List.copyOf(b.advanced), List.copyOf(b.buttons));
        this.label = b.label != null ? b.label
                : Component.literal(b.list ? "Choose items" : "Choose an item");
        this.columns = b.columns;
        this.onDone = b.onDone;
        this.dialog = new ItemDialog(this);
    }

    /** Starts a picker. Give it a lens: {@code state}, {@code stack}, {@code stacks}, {@code rule} or {@code rules}. */
    public static Builder builder() {
        return new Builder();
    }

    // The value

    /** The first slot's item, the kind only ({@link Items#AIR} when empty). */
    public Item getCurrentItem() {
        List<ItemMatch> v = value();
        return v.isEmpty() ? Items.AIR : v.get(0).stack().getItem();
    }

    /** The setting's slots now, in order, none empty: each a rule and its count. */
    public List<ItemMatch> value() {
        ItemSources.ready();   // stacks need items' components, which no world may have bound yet
        List<ItemMatch> v = stateGet.get();
        if (v == null) return List.of();
        List<ItemMatch> out = new ArrayList<>(v.size());
        for (ItemMatch m : v) if (m != null && !m.stack().isEmpty()) out.add(m);
        return out;
    }

    /** Whether the setting is a list (else one item). */
    public boolean isList() {
        return list;
    }

    /** Whether the setting keeps the kind of item only ({@code state(Item)}): the grid lists each item once. */
    boolean kindsOnly() {
        return kindsOnly;
    }

    Options options() {
        return options;
    }

    /**
     * Writes the slots through the lens: empties dropped, at most one for a single
     * picker, the plain item for a kind-only one.
     */
    void write(List<ItemMatch> slots) {
        List<ItemMatch> out = new ArrayList<>();
        for (ItemMatch m : slots) {
            if (m.stack().isEmpty()) continue;
            out.add(kindsOnly ? new ItemMatch(new ItemStack(m.stack().getItem())) : m);
            if (!list) break;
        }
        stateSet.accept(List.copyOf(out));
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

    /** Page slot {@code i}'s top-left, the picker's at {@code (x, y)}. */
    private int slotX(int i, int x) {
        return x + (i % perRow()) * SIZE;
    }

    private int slotY(int i, int y) {
        return y + (i / perRow()) * SIZE;
    }

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
        // The hovered slot's remove button, when the mouse is on its few pixels (a disabled picker has none).
        boolean onRemove = !disabled && hovered >= 0 && hovered < v.size()
                && overRemove(ctx.mouseX(), ctx.mouseY(), slotX(hovered, sx), slotY(hovered, sy));
        for (int i = 0; i < slotCount(); i++) {
            int x = slotX(i, sx), y = slotY(i, sy);
            ItemStack stack = i < v.size() ? v.get(i).stack() : ItemStack.EMPTY;
            boolean hot = !disabled && ((i == hovered && !onRemove) || (focused && i == 0));
            drawSlot(g, ctx.kind(), x, y, stack, hot, disabled);
            if (list && i == v.size()) drawAddMark(g, x, y);
            if (!disabled && i < v.size()) drawRemove(g, x, y, i == hovered && onRemove);
        }
        if (hovered < 0) return;
        if (onRemove) MKTooltip.queue(g, REMOVE_TOOLTIP, ctx.mouseX(), ctx.mouseY());
        else if (tooltipSupplier() != null) queueTooltip(ctx);
        else if (hovered < v.size()) itemTooltip(g, v.get(hovered), options, ctx.mouseX(), ctx.mouseY());
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
        if (kind == ScreenKind.DEFAULT) VanillaLook.slot(g, x, y, disabled);
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

    /** One slot holding the plain {@code item}: the color dialog's dyes. */
    static void drawSlot(GuiGraphicsExtractor g, ScreenKind kind, int x, int y, Item item,
                         boolean highlighted, boolean disabled) {
        ItemSources.ready();
        drawSlot(g, kind, x, y, item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item), highlighted, disabled);
    }

    /** Whether a point is on the remove button of the slot at {@code (x, y)}: its few pixels, no more. */
    static boolean overRemove(double mx, double my, int x, int y) {
        int rx = x + SIZE - REMOVE_SIZE;
        return mx >= rx && mx < rx + REMOVE_SIZE && my >= y && my < y + REMOVE_SIZE;
    }

    /** A filled slot's remove button, over its top-right corner: a small dark square with a white minus. */
    static void drawRemove(GuiGraphicsExtractor g, int x, int y, boolean hovered) {
        int rx = x + SIZE - REMOVE_SIZE;
        g.fill(rx, y, rx + REMOVE_SIZE, y + REMOVE_SIZE, hovered ? REMOVE_HOVER : REMOVE_FILL);
        g.fill(rx + 1, y + REMOVE_SIZE / 2, rx + REMOVE_SIZE - 1, y + REMOVE_SIZE / 2 + 1, REMOVE_BAR);
    }

    /**
     * The plus in the empty slot at the end of a list: add one here. White with a dark
     * shadow a pixel down and right, as vanilla draws white text, so it reads at rest
     * in both looks' gray slots (the remove button's minus is white too).
     */
    static void drawAddMark(GuiGraphicsExtractor g, int x, int y) {
        plus(g, x + 1, y + 1, ADD_SHADOW);
        plus(g, x, y, ADD_MARK);
    }

    /** A 6 x 6 plus of 2 px bars, centered in the slot at {@code (x, y)}. */
    private static void plus(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 6, y + 8, x + 12, y + 10, color);
        g.fill(x + 8, y + 6, x + 10, y + 12, color);
    }

    /**
     * A slot's tooltip: vanilla's own for the stack (name, enchantments, lore), an
     * enchantment whose level the rule leaves blank reading "Sharpness (any level)", and
     * under it the rule's count, when the setting has one and it is set.
     */
    static void itemTooltip(GuiGraphicsExtractor g, ItemMatch slot, Options options, int mx, int my) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), slot.stack()));
        ItemEnchantments on = EnchantmentHelper.getEnchantmentsForCrafting(slot.stack());
        for (Holder<Enchantment> e : slot.anyLevel()) {
            // Vanilla's line for it (its level as the stack holds it), swapped for one with no level.
            int at = lines.indexOf(Enchantment.getFullname(e, on.getLevel(e)));
            if (at >= 0 && e.value().getMaxLevel() > 1) {
                ChatFormatting color = e.is(EnchantmentTags.CURSE) ? ChatFormatting.RED : ChatFormatting.GRAY;
                lines.set(at, e.value().description().copy().append(" (any level)").withStyle(color));
            }
        }
        if (options.count() && slot.count() != ItemMatch.NONE) lines.add(countText(slot).copy().withStyle(ChatFormatting.GRAY));
        g.setTooltipForNextFrame(Minecraft.getInstance().font, lines, Optional.empty(), mx, my);
    }

    /** "Count: 12", or "Count: N/A" for none set. */
    static Component countText(ItemMatch slot) {
        return Component.literal("Count: " + (slot.count() == ItemMatch.NONE ? "N/A" : String.valueOf(slot.count())));
    }

    /**
     * Left click on a filled slot's remove button, or right click on any slot, empties
     * it (a list drops it, the rest moving up); left click anywhere else opens the
     * dialog at the slot.
     */
    @Override
    public boolean mouseClicked(InputContext in, int button) {
        if (disabled(in)) return false;
        int x = in.originX() + childX, y = in.originY() + childY;
        int i = slotAt(in.mouseX(), in.mouseY(), x, y);
        if (i < 0) return false;
        boolean onRemove = i < value().size() && overRemove(in.mouseX(), in.mouseY(), slotX(i, x), slotY(i, y));
        if (button == Click.RIGHT || (button == Click.LEFT && onRemove)) {
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

    // The dialog

    /**
     * Opens the dialog over {@code screen}, its first slot selected, with no picker on the
     * screen's page: from a key press over a slot, say. The picker needs no {@code at} or
     * panel for this. Build it once and open it as often as needed; it stays on a screen
     * until that screen closes, and a dialog open on another screen closes first. Escape,
     * Cancel and Done close it as they do from the page.
     */
    public void open(Screen screen) {
        Objects.requireNonNull(screen, "screen");
        if (this.screen != screen) dialog.close();
        this.screen = screen;
        ScreenPanelRegistry.attachDialog(screen, dialog.panel());
        openDialog(0);
    }

    /** Whether the dialog is open. */
    public boolean isDialogOpen() {
        return dialog.isOpen();
    }

    /** Done: keeps the value, closes the dialog, and runs {@link Builder#onDone}. */
    public void done() {
        if (!dialog.isOpen()) return;
        dialog.close();
        if (onDone != null) onDone.run();
    }

    /** Cancel (and Escape): writes back the value from before the dialog opened, and closes it. */
    public void cancel() {
        if (!dialog.isOpen()) return;
        List<ItemMatch> before = dialog.before();
        dialog.close();
        stateSet.accept(before);   // exactly as it was, not through write's tidying
    }

    // The rest of the dialog is public for MenuKit's checks; the page and the dialog's
    // own elements are what call these in play.

    /** Opens the dialog with slot {@code slot} selected. Does nothing while it is open. */
    @ApiStatus.Internal
    public void openDialog(int slot) {
        if (dialog.isOpen()) return;
        dialog.open(slot);
        focusSearch();
    }

    /**
     * Focus leaves the page, and typing goes straight to the search box. Run when the
     * dialog opens and again as it first draws: a click on the page's slot opens it from
     * inside the screen's click, and the screen then focuses what it clicked (the page's
     * stand-in), which would leave typing with the page.
     */
    void focusSearch() {
        if (screen == null || dialog.searchWidget() == null) return;
        Focus.clear(screen);
        screen.setFocused(dialog.searchWidget());
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

    /** The grid's entries for a query: the creative search contents, searched. */
    @ApiStatus.Internal
    public List<ItemStack> searchEntries(String query) {
        return dialog.entries(query);
    }

    /** A click on a grid entry: it into the selected slot. */
    @ApiStatus.Internal
    public void pickEntry(ItemStack entry) {
        dialog.fill(entry);
    }

    /** A click on the remove button of slot {@code slot} of the slot row. */
    @ApiStatus.Internal
    public void removeSlot(int slot) {
        dialog.remove(slot);
    }

    /** Selects slot {@code slot} of the slot row, as a click on it does. */
    @ApiStatus.Internal
    public void selectSlot(int slot) {
        dialog.select(slot);
    }

    /** Ticks or clears "Show advanced settings", as a click on it does (remembered for every picker). */
    @ApiStatus.Internal
    public void setAdvancedOpen(boolean open) {
        dialog.setAdvanced(open);
    }

    /** Whether the advanced column shows. */
    @ApiStatus.Internal
    public boolean advancedOpen() {
        return dialog.advanced();
    }

    /** Types into an advanced field ({@code "name"}, {@code "components"}), as a player does. */
    @ApiStatus.Internal
    public void typeEdit(String field, String text) {
        dialog.typeEdit(field, text);
    }

    /** What an advanced field shows now. */
    @ApiStatus.Internal
    public String editText(String field) {
        return dialog.editText(field);
    }

    /** The components box's error, or null while its text is valid. */
    @ApiStatus.Internal
    public @Nullable Component componentsError() {
        return dialog.componentsError();
    }

    /** Sets the selected slot's count, as the slider does: {@link ItemMatch#NONE}, or up to the item's stack size. */
    @ApiStatus.Internal
    public void setCount(int count) {
        dialog.setCount(count);
    }

    /** The enchantments that fit the selected slot's item, by name, each with its level on it (0: not on it). */
    @ApiStatus.Internal
    public List<EnchantChoice> enchantChoices() {
        return dialog.enchantChoices();
    }

    /** The enchantments the dropdown has ticked for the selected slot. */
    @ApiStatus.Internal
    public Set<Holder<Enchantment>> pickedEnchants() {
        return dialog.picked();
    }

    /** Ticks exactly {@code enchantments} in the dropdown, as the player's toggles do. */
    @ApiStatus.Internal
    public void pickEnchants(Set<Holder<Enchantment>> enchantments) {
        dialog.setPicked(enchantments);
    }

    /** A picked enchantment's level dropdown: 0 blank (any level), else that level. */
    @ApiStatus.Internal
    public int enchantLevel(Holder<Enchantment> enchantment) {
        return dialog.enchantLevel(enchantment);
    }

    /** Picks {@code level} in a picked enchantment's level dropdown (0: blank, any level). */
    @ApiStatus.Internal
    public void setEnchantLevel(Holder<Enchantment> enchantment, int level) {
        dialog.setEnchantLevel(enchantment, level);
    }

    /**
     * Something the dialog draws, {@code x, y, w, h} in its content's coordinates, and
     * the box {@code px, py, pw, ph} it must stay inside.
     */
    @ApiStatus.Internal
    public record Box(String what, int x, int y, int w, int h, int px, int py, int pw, int ph) {
        public boolean inside() {
            return x >= px && y >= py && x + w <= px + pw && y + h <= py + ph;
        }

        @Override
        public String toString() {
            return what + " [" + x + "," + y + " " + w + "x" + h + "] in [" + px + "," + py + " " + pw + "x" + ph + "]";
        }
    }

    /** Everything the dialog draws, laid out for {@code kind}, each with the box it must stay inside. */
    @ApiStatus.Internal
    public List<Box> dialogBoxes(ScreenKind kind) {
        return dialog.boxes(kind);
    }

    /** Whether the dialog has advanced settings: only when the setting turns on something for them. */
    @ApiStatus.Internal
    public boolean hasAdvanced() {
        return options.any();
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
        private boolean name, enchantments, components, count;
        private final List<PanelElement> advanced = new ArrayList<>();
        private final List<Button> buttons = new ArrayList<>();
        private @Nullable Runnable onDone;
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
         * One item, the kind only. {@code get} is read every frame ({@link Items#AIR}, or
         * {@code null}, for none); {@code set} receives the item the player picks, or
         * {@link Items#AIR} when they empty it. The grid lists each item once, and the
         * advanced settings are not on offer.
         */
        public Builder state(Supplier<Item> get, Consumer<Item> set) {
            return lens(Lens.ITEM, get, set, false);
        }

        /** One stack, one of it: {@code get} read every frame, {@code set} handed every change ({@link ItemStack#EMPTY} for none). */
        public Builder stack(Supplier<ItemStack> get, Consumer<ItemStack> set) {
            return lens(Lens.STACK, get, set, false);
        }

        /** A list of stacks, in the player's order: {@code set} gets a new list with no empty entries. */
        public Builder stacks(Supplier<List<ItemStack>> get, Consumer<List<ItemStack>> set) {
            return lens(Lens.STACKS, get, set, true);
        }

        /** One rule ({@code set} gets an empty one for none). */
        public Builder rule(Supplier<ItemMatch> get, Consumer<ItemMatch> set) {
            return lens(Lens.RULE, get, set, false);
        }

        /** A list of rules, in the player's order. */
        public Builder rules(Supplier<List<ItemMatch>> get, Consumer<List<ItemMatch>> set) {
            return lens(Lens.RULES, get, set, true);
        }

        /** The advanced settings offer a name field: filled in, the stack must have that name. */
        public Builder name() {
            this.name = true;
            return this;
        }

        /**
         * The advanced settings offer the enchantments that fit the selected item, in a
         * dropdown, each one picked with a level: each must be on the stack at that level,
         * or at any level when it is left blank ({@link ItemMatch#anyLevel()}). A blank
         * level needs {@code rule} or {@code rules}, which store it; with a stack lens
         * every level is picked.
         */
        public Builder enchantments() {
            this.enchantments = true;
            return this;
        }

        /**
         * The advanced settings offer a box for every other component, in vanilla's {@code /give}
         * syntax ({@code [unbreakable={},!food]}): each one listed must match, and
         * {@code !x} means the stack must not have it.
         */
        public Builder components() {
            this.components = true;
            return this;
        }

        /**
         * The advanced settings offer a count slider: a number for the consumer
         * ({@link ItemMatch#count()}), never matched. Its far left is N/A, no count
         * ({@link ItemMatch#NONE}); its far right the item's stack size (64 diamonds,
         * 16 ender pearls). An item that doesn't stack (a sword) has no slider, and no
         * count. Needs {@code rule} or {@code rules}, which store it.
         */
        public Builder count() {
            this.count = true;
            return this;
        }

        /** A row of the consumer's own under the advanced fields, laid out to the column's width. */
        public Builder advanced(PanelElement row) {
            advanced.add(Objects.requireNonNull(row, "row"));
            return this;
        }

        /**
         * A dialog button of the consumer's, left of Cancel and Done ("Remove"). Its action
         * runs as it is; call {@link ItemPicker#done()} or {@link ItemPicker#cancel()} from
         * it to close the dialog.
         */
        public Builder button(Component label, Runnable onClick) {
            buttons.add(DialogLayout.button(Objects.requireNonNull(label, "label"), Objects.requireNonNull(onClick, "onClick")));
            return this;
        }

        /** Runs after Done closes the dialog: where a consumer that edits a draft applies it. */
        public Builder onDone(Runnable onDone) {
            this.onDone = Objects.requireNonNull(onDone, "onDone");
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
            require(lens != Lens.ITEM || !(name || enchantments || components || count || !advanced.isEmpty()),
                    "state(Supplier<Item>, Consumer<Item>) holds an Item, which has no name, enchantments, components or count: use stack, stacks, rule or rules");
            require(!count || lens == Lens.RULE || lens == Lens.RULES,
                    "count() needs rule(...) or rules(...), which store each slot's count");
            // A list is a row of slots wide; a single picker one slot.
            if (list) this.width = columns * SIZE;
            Supplier<List<ItemMatch>> g;
            Consumer<List<ItemMatch>> s;
            switch (lens) {
                case ITEM -> {
                    Supplier<Item> get0 = (Supplier<Item>) get;
                    Consumer<Item> set0 = (Consumer<Item>) set;
                    g = () -> {
                        Item item = get0.get();
                        return item == null || item == Items.AIR ? List.of() : List.of(new ItemMatch(new ItemStack(item)));
                    };
                    s = v -> set0.accept(v.isEmpty() ? Items.AIR : v.get(0).stack().getItem());
                }
                case STACK -> {
                    Supplier<ItemStack> get0 = (Supplier<ItemStack>) get;
                    Consumer<ItemStack> set0 = (Consumer<ItemStack>) set;
                    g = () -> {
                        ItemStack st = get0.get();
                        return st == null || st.isEmpty() ? List.of() : List.of(new ItemMatch(st, 1));
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
                        for (ItemStack st : sts) if (st != null && !st.isEmpty()) out.add(new ItemMatch(st, 1));
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
                    s = v -> set0.accept(v.isEmpty() ? new ItemMatch(ItemStack.EMPTY, 1) : v.get(0));
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
